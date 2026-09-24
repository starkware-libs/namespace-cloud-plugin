package io.jenkins.plugins.namespacecloud.launcher;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.jenkins.plugins.namespacecloud.AgentTemplate;

/**
 * Everything a launch strategy needs to know about the agent being provisioned,
 * assembled before the Namespace instance is created.
 *
 * @param agentName the Jenkins node name, also used as the instance label value
 * @param agentSecret the inbound-agent HMAC for {@code agentName}; empty for SSH
 * @param jenkinsUrl the externally reachable controller URL
 * @param remoteFs the agent working directory inside the instance
 */
public record LaunchContext(
        @NonNull String agentName,
        @NonNull String agentSecret,
        @NonNull String jenkinsUrl,
        @NonNull String remoteFs,
        @NonNull AgentTemplate template) {}
