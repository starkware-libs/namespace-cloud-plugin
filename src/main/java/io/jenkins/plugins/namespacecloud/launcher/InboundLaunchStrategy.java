package io.jenkins.plugins.namespacecloud.launcher;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.RelativePath;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.JNLPLauncher;
import hudson.util.ComboBoxModel;
import io.jenkins.plugins.namespacecloud.client.NamespaceClient;
import java.util.LinkedHashMap;
import java.util.Map;
import namespace.cloud.compute.v1beta.Compute;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Runs the Jenkins inbound agent as a Namespace-managed container, which dials
 * out to the controller over WebSocket.
 *
 * <p>Namespace-managed containers (those declared at creation time) are treated
 * as critical: if the agent process exits, the instance fails as a whole, which
 * makes a dead agent visible rather than leaving an idle VM burning budget.
 */
public class InboundLaunchStrategy extends AgentLaunchStrategy {

    /**
     * Default agent image. The JDK tag should track the controller's Java major
     * version so remoting negotiates a matching protocol.
     */
    public static final String DEFAULT_IMAGE = AgentImages.CLEAN_JDK21;

    private String image = DEFAULT_IMAGE;
    private boolean webSocket = true;
    private boolean exposeDockerSocket;

    @DataBoundConstructor
    public InboundLaunchStrategy() {}

    public String getImage() {
        return image == null || image.isBlank() ? DEFAULT_IMAGE : image;
    }

    @DataBoundSetter
    public void setImage(String image) {
        this.image = image;
    }

    public boolean isWebSocket() {
        return webSocket;
    }

    @DataBoundSetter
    public void setWebSocket(boolean webSocket) {
        this.webSocket = webSocket;
    }

    public boolean isExposeDockerSocket() {
        return exposeDockerSocket;
    }

    @DataBoundSetter
    public void setExposeDockerSocket(boolean exposeDockerSocket) {
        this.exposeDockerSocket = exposeDockerSocket;
    }

    @Override
    public boolean requiresNodeRegisteredFirst() {
        // The agent connects by itself; the controller must already know the
        // node name and its secret or it will refuse the connection.
        return true;
    }

    @Override
    public void configureInstance(@NonNull Compute.CreateInstanceRequest.Builder builder, @NonNull LaunchContext ctx) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("JENKINS_URL", ctx.jenkinsUrl());
        env.put("JENKINS_AGENT_NAME", ctx.agentName());
        env.put("JENKINS_SECRET", ctx.agentSecret());
        env.put("JENKINS_AGENT_WORKDIR", ctx.remoteFs());
        if (webSocket) {
            env.put("JENKINS_WEB_SOCKET", "true");
        }
        env.putAll(ctx.template().getEnvironmentMap());

        Compute.ContainerRequest.Builder container = Compute.ContainerRequest.newBuilder()
                .setName("jenkins-agent")
                .setImageRef(getImage())
                .putAllEnvironment(env)
                // SERVICE, not JOB: the agent is long-lived for the life of the
                // instance rather than a one-shot process.
                .setWorkloadType(Compute.ContainerRequest.WorkloadType.SERVICE);

        if (exposeDockerSocket) {
            // Namespace runs dockerd in the instance; handing the socket to the
            // agent lets builds use Docker without docker-in-docker.
            container.setDockerSockPath("/var/run/docker.sock");
        }

        builder.addContainers(container.build());
    }

    @Override
    @NonNull
    public ComputerLauncher createLauncher(
            @NonNull NamespaceClient client, @NonNull Compute.InstanceMetadata instance, @NonNull LaunchContext ctx) {
        JNLPLauncher launcher = new JNLPLauncher();
        launcher.setWebSocket(webSocket);
        return launcher;
    }

    @Extension
    @Symbol("inbound")
    public static class DescriptorImpl extends AgentLaunchStrategyDescriptor {
        @Override
        @NonNull
        public String getDisplayName() {
            return "Inbound agent (WebSocket)";
        }

        /**
         * Suggests the stock agent images plus anything in the workspace
         * registry. The cloud's credential lives two levels up in the form
         * (cloud -> template -> launch strategy).
         */
        public ComboBoxModel doFillImageItems(@RelativePath("../..") @QueryParameter String credentialsId) {
            return AgentImages.suggest(credentialsId);
        }

        /** Says why the suggestion list looks the way it does. */
        public hudson.util.FormValidation doCheckImage(
                @RelativePath("../..") @QueryParameter String credentialsId, @QueryParameter String value) {
            if (value == null || value.isBlank()) {
                return hudson.util.FormValidation.error("An agent image is required.");
            }
            return AgentImages.describeAvailability(credentialsId);
        }
    }
}
