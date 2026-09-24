package io.jenkins.plugins.namespacecloud;

import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.slaves.AbstractCloudComputer;

/** Computer for a {@link NamespaceAgent}; exposes the backing instance id in the UI. */
public class NamespaceComputer extends AbstractCloudComputer<NamespaceAgent> {

    public NamespaceComputer(NamespaceAgent agent) {
        super(agent);
    }

    /** Shown on the agent's status page so an operator can correlate with {@code nsc list}. */
    @Nullable
    public String getInstanceId() {
        NamespaceAgent node = getNode();
        return node == null ? null : node.getInstanceId();
    }

    @Nullable
    public String getTemplateName() {
        NamespaceAgent node = getNode();
        return node == null ? null : node.getTemplateName();
    }
}
