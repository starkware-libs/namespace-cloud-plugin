package io.jenkins.plugins.namespacecloud.launcher;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.security.ACL;
import hudson.slaves.ComputerLauncher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.namespacecloud.client.NamespaceClient;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;
import namespace.cloud.compute.v1beta.Compute;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/**
 * Connects by SSH from the controller into the instance.
 *
 * <p>Use this when the agent cannot dial out to Jenkins. The controller must be
 * able to reach Namespace's regional ingress, and the token additionally needs
 * the {@code instance:ssh} and {@code ingress:access} grants.
 *
 * <p>Namespace authorises SSH against public keys injected at instance creation,
 * so the configured private-key credential and {@link #getAuthorizedKey()} must
 * be halves of the same keypair. The public half is supplied explicitly rather
 * than derived, because Jenkins private-key credentials may be passphrase
 * protected and deriving the public half would require unlocking them at
 * configuration time.
 */
public class SshLaunchStrategy extends AgentLaunchStrategy {

    /** The image must contain a JRE; the controller copies {@code agent.jar} into it. */
    public static final String DEFAULT_IMAGE = AgentImages.CLEAN_JDK21;

    private String image = DEFAULT_IMAGE;
    private String credentialsId;
    // False positive: this holds the PUBLIC half of an SSH keypair, which is
    // published to the instance on purpose and is not a secret. The CodeQL rule
    // matches on field name and type alone.
    @SuppressWarnings("lgtm[jenkins/plaintext-storage]")
    private String authorizedKey;

    private String javaPath;

    @DataBoundConstructor
    public SshLaunchStrategy(String credentialsId, String authorizedKey) {
        this.credentialsId = credentialsId;
        this.authorizedKey = authorizedKey;
    }

    public String getImage() {
        return image == null || image.isBlank() ? DEFAULT_IMAGE : image;
    }

    @DataBoundSetter
    public void setImage(String image) {
        this.image = image;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getAuthorizedKey() {
        return authorizedKey;
    }

    public String getJavaPath() {
        return javaPath;
    }

    @DataBoundSetter
    public void setJavaPath(String javaPath) {
        this.javaPath = javaPath;
    }

    @Override
    public boolean requiresSshGrants() {
        return true;
    }

    @Override
    public void configureInstance(@NonNull Compute.CreateInstanceRequest.Builder builder, @NonNull LaunchContext ctx) {
        Map<String, String> env = new LinkedHashMap<>(ctx.template().getEnvironmentMap());

        // The container must stay up for the controller to SSH into it; unlike
        // the inbound strategy nothing inside it starts the agent.
        builder.addContainers(Compute.ContainerRequest.newBuilder()
                .setName("jenkins-agent")
                .setImageRef(getImage())
                .putAllEnvironment(env)
                .addAllEntrypoint(List.of("/bin/sh", "-c"))
                .addArgs("trap : TERM INT; sleep infinity & wait")
                .setWorkloadType(Compute.ContainerRequest.WorkloadType.SERVICE)
                .build());

        if (authorizedKey != null && !authorizedKey.isBlank()) {
            builder.setExperimental(builder.getExperimental().toBuilder()
                    .addAuthorizedSshKeys(authorizedKey.trim())
                    .build());
        }
    }

    @Override
    @NonNull
    public ComputerLauncher createLauncher(
            @NonNull NamespaceClient client, @NonNull Compute.InstanceMetadata instance, @NonNull LaunchContext ctx)
            throws IOException {
        Compute.GetSSHConfigResponse cfg = client.sshConfig(instance.getInstanceId());
        String endpoint = cfg.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new IOException("Namespace returned no SSH endpoint for instance " + instance.getInstanceId());
        }

        String host = endpoint;
        int port = 22;
        int colon = endpoint.lastIndexOf(':');
        if (colon > 0 && colon < endpoint.length() - 1) {
            try {
                port = Integer.parseInt(endpoint.substring(colon + 1));
                host = endpoint.substring(0, colon);
            } catch (NumberFormatException e) {
                // Endpoint had no port suffix; fall back to the default.
            }
        }

        SSHLauncher launcher = new SSHLauncher(host, port, credentialsId);
        // Namespace mints fresh host keys per instance, so pinning them is not
        // possible; the transport is already authenticated by the ingress.
        launcher.setSshHostKeyVerificationStrategy(
                new hudson.plugins.sshslaves.verifiers.NonVerifyingKeyVerificationStrategy());
        if (javaPath != null && !javaPath.isBlank()) {
            launcher.setJavaPath(javaPath);
        }
        return launcher;
    }

    @Extension
    @Symbol("ssh")
    public static class DescriptorImpl extends AgentLaunchStrategyDescriptor {
        @Override
        @NonNull
        public String getDisplayName() {
            return "SSH into the instance";
        }

        @POST
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String credentialsId) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            StandardListBoxModel result = new StandardListBoxModel();
            if (item == null) {
                if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                    return result.includeCurrentValue(credentialsId);
                }
            } else if (!item.hasPermission(Item.EXTENDED_READ) && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
                return result.includeCurrentValue(credentialsId);
            }
            return result.includeEmptyValue()
                    .includeMatchingAs(
                            ACL.SYSTEM2,
                            Jenkins.get(),
                            SSHUserPrivateKey.class,
                            Collections.<DomainRequirement>emptyList(),
                            CredentialsMatchers.always())
                    .includeCurrentValue(credentialsId);
        }

        @POST
        public hudson.util.ComboBoxModel doFillImageItems(
                @hudson.RelativePath("../..") @QueryParameter String credentialsId) {
            return AgentImages.suggest(credentialsId);
        }

        @POST
        public FormValidation doCheckAuthorizedKey(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (value == null || value.isBlank()) {
                return FormValidation.error("Required: Namespace authorises SSH against this public key.");
            }
            String v = value.trim();
            if (!v.startsWith("ssh-") && !v.startsWith("ecdsa-") && !v.startsWith("sk-")) {
                return FormValidation.error("Expected an OpenSSH public key, e.g. \"ssh-ed25519 AAAA... jenkins\".");
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckCredentialsId(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (value == null || value.isBlank()) {
                return FormValidation.error("Select the private key matching the authorized key above.");
            }
            return FormValidation.ok();
        }
    }
}
