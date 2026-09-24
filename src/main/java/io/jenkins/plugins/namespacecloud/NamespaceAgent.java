package io.jenkins.plugins.namespacecloud;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.ComputerLauncher;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 * A Jenkins node backed by one Namespace instance.
 *
 * <p>The node is single-use: once it goes idle past the profile's idle timeout
 * (or the build finishes), {@link #_terminate} destroys the instance. Namespace
 * also enforces the instance deadline set at creation, so an instance is
 * reclaimed even if this controller never gets the chance to destroy it.
 */
public class NamespaceAgent extends AbstractCloudSlave {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(NamespaceAgent.class.getName());

    private final String cloudName;
    private final String instanceId;
    private final String templateName;

    public NamespaceAgent(
            @NonNull String name,
            @NonNull String remoteFs,
            @NonNull ComputerLauncher launcher,
            @NonNull String cloudName,
            @NonNull String instanceId,
            @NonNull AgentTemplate template)
            throws Descriptor.FormException, IOException {
        super(name, remoteFs, launcher);
        this.cloudName = cloudName;
        this.instanceId = instanceId;
        this.templateName = template.getName();

        setNumExecutors(template.getNumExecutors());
        setMode(template.getMode());
        setLabelString(template.getLabels());
        setNodeDescription("Namespace instance " + instanceId + " (profile: " + template.getName() + ")");
        setRetentionStrategy(new CloudRetentionStrategy(Math.max(1, template.getIdleMinutes())));
    }

    public String getCloudName() {
        return cloudName;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public String getTemplateName() {
        return templateName;
    }

    @Nullable
    public NamespaceCloud getCloud() {
        hudson.slaves.Cloud c = Jenkins.get().clouds.getByName(cloudName);
        return c instanceof NamespaceCloud nc ? nc : null;
    }

    @Override
    public AbstractCloudComputer<NamespaceAgent> createComputer() {
        return new NamespaceComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        NamespaceCloud cloud = getCloud();
        if (cloud == null) {
            LOGGER.log(
                    Level.WARNING,
                    "Cloud \"{0}\" is gone; cannot destroy Namespace instance {1}. "
                            + "It will expire at its deadline.",
                    new Object[] {cloudName, instanceId});
            return;
        }
        listener.getLogger().println("Destroying Namespace instance " + instanceId);
        try {
            cloud.destroyInstance(instanceId, "Jenkins agent " + name + " terminated");
            listener.getLogger().println("Destroyed Namespace instance " + instanceId);
        } catch (RuntimeException e) {
            // Never rethrow: a failure here would leave the node stuck in
            // Jenkins. The instance deadline is the backstop.
            LOGGER.log(Level.WARNING, e, () -> "Failed to destroy Namespace instance " + instanceId);
            listener.getLogger()
                    .println("Failed to destroy instance " + instanceId + ": " + e.getMessage()
                            + " — it will be reclaimed at its deadline.");
        }
    }

    @Extension
    public static class DescriptorImpl extends SlaveDescriptor {
        @Override
        @NonNull
        public String getDisplayName() {
            return "Namespace agent";
        }

        @Override
        public boolean isInstantiable() {
            // Only the cloud creates these.
            return false;
        }
    }
}
