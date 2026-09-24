/*
 * Build on ci.jenkins.io.
 *
 * buildPlugin() comes from the jenkins-infra pipeline library and handles the
 * whole matrix: compile, test, spotbugs, spotless, and the plugin compatibility
 * tester. Keep this thin -- anything clever here diverges from how every other
 * plugin is built.
 */
buildPlugin(
    // The hosting checker requires jdk to be one of [21, 25].
    useContainerAgent: true,
    configurations: [
        [platform: 'linux',   jdk: 21],
        [platform: 'windows', jdk: 25],
    ]
)
