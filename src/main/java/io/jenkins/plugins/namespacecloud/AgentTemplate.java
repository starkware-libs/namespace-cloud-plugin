package io.jenkins.plugins.namespacecloud;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.namespacecloud.launcher.AgentLaunchStrategy;
import io.jenkins.plugins.namespacecloud.launcher.InboundLaunchStrategy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;
import namespace.cloud.compute.v1beta.Compute;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/**
 * A machine profile: the shape of instance to create, and how to connect to it,
 * for jobs whose label expression matches {@link #getLabels()}.
 *
 * <p>The shape is declared here rather than referenced by name in Namespace.
 * Namespace's named profiles ({@code github.v1beta.ProfileService}) exist only
 * for GitHub Actions runners and are served from a private endpoint, so
 * {@code ComputeService.CreateInstance} takes an explicit
 * {@link Compute.InstanceShape} and that is what this supplies.
 *
 * <p>A job selects a profile the ordinary Jenkins way — {@code agent { label 'ns-linux-8x16' }}
 * in a Pipeline, or "Restrict where this project can be run" in a freestyle job.
 */
public class AgentTemplate extends AbstractDescribableImpl<AgentTemplate> {

    private final String name;
    private String labels = "";
    private int vcpu = 4;
    private int memoryGb = 8;
    private String arch = "amd64";
    private int numExecutors = 1;
    private String remoteFs = "/home/jenkins/agent";
    private int idleMinutes = 10;
    private int launchTimeoutSeconds = 300;
    private int maxLifetimeMinutes = 120;
    private int instanceCap = 10;
    private String environment = "";
    private String cacheVolumes = "";
    private AgentLaunchStrategy launchStrategy = new InboundLaunchStrategy();

    @DataBoundConstructor
    public AgentTemplate(String name) {
        this.name = name == null ? "" : name.trim();
    }

    /** Also the agent-name prefix, so nodes are traceable back to their profile. */
    public String getName() {
        return name;
    }

    public String getLabels() {
        return labels;
    }

    @DataBoundSetter
    public void setLabels(String labels) {
        this.labels = labels == null ? "" : labels.trim();
    }

    public int getVcpu() {
        return vcpu;
    }

    @DataBoundSetter
    public void setVcpu(int vcpu) {
        this.vcpu = vcpu;
    }

    public int getMemoryGb() {
        return memoryGb;
    }

    @DataBoundSetter
    public void setMemoryGb(int memoryGb) {
        this.memoryGb = memoryGb;
    }

    public String getArch() {
        return arch == null || arch.isBlank() ? "amd64" : arch;
    }

    @DataBoundSetter
    public void setArch(String arch) {
        this.arch = arch;
    }

    /**
     * The shape sent to {@code CreateInstance}.
     *
     * <p>Memory is configured in GB but the API takes megabytes, so the
     * conversion happens here rather than at each call site.
     */
    public Compute.InstanceShape toInstanceShape() {
        return Compute.InstanceShape.newBuilder()
                .setVirtualCpu(getVcpu())
                .setMemoryMegabytes(getMemoryGb() * 1024)
                .setMachineArch(getArch())
                .setOs("linux")
                .build();
    }

    public int getNumExecutors() {
        return Math.max(1, numExecutors);
    }

    @DataBoundSetter
    public void setNumExecutors(int numExecutors) {
        this.numExecutors = numExecutors;
    }

    public String getRemoteFs() {
        return remoteFs == null || remoteFs.isBlank() ? "/home/jenkins/agent" : remoteFs;
    }

    @DataBoundSetter
    public void setRemoteFs(String remoteFs) {
        this.remoteFs = remoteFs;
    }

    public int getIdleMinutes() {
        return idleMinutes;
    }

    @DataBoundSetter
    public void setIdleMinutes(int idleMinutes) {
        this.idleMinutes = idleMinutes;
    }

    /**
     * How long to wait for the agent to connect before giving up and destroying
     * the instance. Bounds the damage when an agent can never reach the
     * controller.
     */
    public int getLaunchTimeoutSeconds() {
        return launchTimeoutSeconds <= 0 ? 300 : launchTimeoutSeconds;
    }

    @DataBoundSetter
    public void setLaunchTimeoutSeconds(int launchTimeoutSeconds) {
        this.launchTimeoutSeconds = launchTimeoutSeconds;
    }

    public int getMaxLifetimeMinutes() {
        return maxLifetimeMinutes;
    }

    @DataBoundSetter
    public void setMaxLifetimeMinutes(int maxLifetimeMinutes) {
        this.maxLifetimeMinutes = maxLifetimeMinutes;
    }

    public int getInstanceCap() {
        return instanceCap <= 0 ? Integer.MAX_VALUE : instanceCap;
    }

    @DataBoundSetter
    public void setInstanceCap(int instanceCap) {
        this.instanceCap = instanceCap;
    }

    public String getEnvironment() {
        return environment;
    }

    @DataBoundSetter
    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getCacheVolumes() {
        return cacheVolumes;
    }

    @DataBoundSetter
    public void setCacheVolumes(String cacheVolumes) {
        this.cacheVolumes = cacheVolumes;
    }

    @NonNull
    public AgentLaunchStrategy getLaunchStrategy() {
        return launchStrategy == null ? new InboundLaunchStrategy() : launchStrategy;
    }

    @DataBoundSetter
    public void setLaunchStrategy(AgentLaunchStrategy launchStrategy) {
        this.launchStrategy = launchStrategy;
    }

    /** Parses the {@code KEY=VALUE} lines from the environment textarea. */
    public Map<String, String> getEnvironmentMap() {
        Map<String, String> out = new LinkedHashMap<>();
        if (environment == null) {
            return out;
        }
        for (String line : environment.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq > 0) {
                out.put(
                        trimmed.substring(0, eq).trim(),
                        trimmed.substring(eq + 1).trim());
            }
        }
        return out;
    }

    /** Parses {@code mountPoint:tag:sizeGb} lines from the cache-volume textarea. */
    public List<CacheVolume> getCacheVolumeList() {
        List<CacheVolume> out = new ArrayList<>();
        if (cacheVolumes == null) {
            return out;
        }
        for (String line : cacheVolumes.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split(":");
            if (parts.length < 2) {
                continue;
            }
            long sizeGb = 10;
            if (parts.length >= 3) {
                try {
                    sizeGb = Long.parseLong(parts[2].trim());
                } catch (NumberFormatException e) {
                    // Keep the default rather than failing provisioning over a
                    // malformed size; doCheckCacheVolumes flags it in the UI.
                }
            }
            out.add(new CacheVolume(parts[0].trim(), parts[1].trim(), sizeGb));
        }
        return out;
    }

    /**
     * A Namespace cache volume, reattached across instances that share the tag.
     * Sized in GB, as Namespace's own volume settings are.
     */
    public record CacheVolume(String mountPoint, String tag, long sizeGb) {
        /** The API takes megabytes. */
        public long sizeMb() {
            return sizeGb * 1024;
        }
    }

    public Node.Mode getMode() {
        // Profiles are label-targeted, so only take work that asks for them.
        return Node.Mode.EXCLUSIVE;
    }

    public boolean matches(Label label) {
        if (label == null) {
            return false;
        }
        return label.matches(Label.parse(getLabels()));
    }

    @Extension
    @Symbol("namespaceTemplate")
    public static class DescriptorImpl extends Descriptor<AgentTemplate> {
        @Override
        @NonNull
        public String getDisplayName() {
            return "Namespace agent profile";
        }

        @POST
        public FormValidation doCheckName(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (value == null || value.isBlank()) {
                return FormValidation.error("Required. Used as the agent name prefix.");
            }
            if (!value.matches("[a-zA-Z0-9][a-zA-Z0-9_-]*")) {
                return FormValidation.error("Use letters, digits, '-' and '_' only.");
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckLabels(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (value == null || value.isBlank()) {
                return FormValidation.warning(
                        "Without a label this profile only serves jobs with no label restriction.");
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckVcpu(@QueryParameter int value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (value < 1) {
                return FormValidation.error("At least 1 vCPU.");
            }
            return FormValidation.ok();
        }

        /**
         * Namespace bills by shape, and a lopsided vCPU:memory ratio is usually
         * a typo rather than an intent, so warn without blocking.
         */
        @POST
        public FormValidation doCheckMemoryGb(@QueryParameter int value, @QueryParameter int vcpu) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (value < 1) {
                return FormValidation.error("At least 1 GB.");
            }
            if (vcpu > 0) {
                double gbPerCpu = (double) value / vcpu;
                if (gbPerCpu < 1) {
                    return FormValidation.warning(
                            String.format("Only %.1f GB per vCPU; most build images want at least 2.", gbPerCpu));
                }
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckLaunchTimeoutSeconds(@QueryParameter int value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (value < 30) {
                return FormValidation.error("At least 30s; an instance needs time to boot and pull the agent image.");
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckMaxLifetimeMinutes(@QueryParameter int value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (value < 1) {
                return FormValidation.error(
                        "Must be positive. This is the instance deadline Namespace enforces server-side.");
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckCacheVolumes(@QueryParameter String value) {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            if (value == null || value.isBlank()) {
                return FormValidation.ok();
            }
            for (String line : value.split("\\R")) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) {
                    continue;
                }
                String[] parts = t.split(":");
                if (parts.length < 2) {
                    return FormValidation.error("Expected mountPoint:tag[:sizeGb] — got \"" + t + "\"");
                }
                if (parts.length >= 3) {
                    try {
                        Long.parseLong(parts[2].trim());
                    } catch (NumberFormatException e) {
                        return FormValidation.error("Size must be a number of GB — got \"" + parts[2].trim() + "\"");
                    }
                }
            }
            return FormValidation.ok();
        }

        @POST
        public ListBoxModel doFillArchItems() {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            ListBoxModel m = new ListBoxModel();
            m.add("x86-64 (amd64)", "amd64");
            m.add("ARM64 (arm64)", "arm64");
            return m;
        }
    }
}
