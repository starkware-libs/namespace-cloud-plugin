package io.jenkins.plugins.namespacecloud;

import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import namespace.cloud.compute.v1beta.Compute;

/**
 * Destroys Namespace instances this controller created but no longer tracks.
 *
 * <p>An instance is orphaned when its Jenkins node is gone — the controller
 * crashed between creating the instance and registering the node, an admin
 * deleted the node by hand, or a config change dropped the cloud. Namespace's
 * per-instance deadline eventually reclaims these anyway, but that can be hours
 * of paid compute; this closes the gap to minutes.
 *
 * <p>Only instances labelled with {@link NamespaceCloud#LABEL_CLOUD} matching a
 * cloud defined on <em>this</em> controller are considered, so two Jenkins
 * instances sharing one Namespace workspace never reap each other's agents.
 */
@Extension
public class OrphanedInstanceReaper extends AsyncPeriodicWork {

    private static final Logger LOGGER = Logger.getLogger(OrphanedInstanceReaper.class.getName());

    /**
     * Grace period before an instance with no node is considered orphaned.
     * Must comfortably exceed the time between CreateInstance returning and the
     * node being registered, or the reaper would destroy instances that are
     * still being provisioned.
     */
    private static final long GRACE_MILLIS = TimeUnit.MINUTES.toMillis(15);

    public OrphanedInstanceReaper() {
        super("Namespace orphaned instance reaper");
    }

    @Override
    public long getRecurrencePeriod() {
        return TimeUnit.MINUTES.toMillis(10);
    }

    @Override
    protected Level getNormalLoggingLevel() {
        return Level.FINE;
    }

    @Override
    protected void execute(TaskListener listener) {
        for (hudson.slaves.Cloud c : Jenkins.get().clouds) {
            if (c instanceof NamespaceCloud cloud) {
                try {
                    reap(cloud);
                } catch (Exception e) {
                    // One misconfigured cloud must not stop the others.
                    LOGGER.log(Level.WARNING, e, () -> "Could not reap instances for cloud " + cloud.name);
                }
            }
        }
    }

    private void reap(NamespaceCloud cloud) throws Exception {
        Set<String> liveAgents = new HashSet<>();
        for (hudson.model.Node n : Jenkins.get().getNodes()) {
            if (n instanceof NamespaceAgent agent && cloud.name.equals(agent.getCloudName())) {
                liveAgents.add(agent.getNodeName());
            }
        }

        long now = System.currentTimeMillis();
        for (Compute.InstanceMetadata instance : cloud.client().listByLabel(NamespaceCloud.LABEL_CLOUD, cloud.name)) {
            if (instance.getStatus() == Compute.InstanceMetadata.Status.DESTROYED
                    || instance.getStatus() == Compute.InstanceMetadata.Status.DESTROYING) {
                continue;
            }

            String agentName = labelValue(instance, NamespaceCloud.LABEL_AGENT);
            if (agentName == null || liveAgents.contains(agentName)) {
                continue;
            }

            long createdAt = instance.getCreatedAt().getSeconds() * 1000L;
            if (now - createdAt < GRACE_MILLIS) {
                // Probably still being provisioned; leave it alone this round.
                continue;
            }

            LOGGER.log(
                    Level.INFO,
                    "Reaping orphaned Namespace instance {0} (agent {1} no longer exists)",
                    new Object[] {instance.getInstanceId(), agentName});
            cloud.client()
                    .destroyInstance(instance.getInstanceId(), "Orphaned: Jenkins node " + agentName + " is gone");
        }
    }

    private static String labelValue(Compute.InstanceMetadata instance, String name) {
        return instance.getLabelsList().stream()
                .filter(l -> name.equals(l.getName()))
                .map(namespace.stdlib.Labels.Label::getValue)
                .findFirst()
                .orElse(null);
    }
}
