/*
 * Build on ci.jenkins.io.
 *
 * buildPlugin() comes from the jenkins-infra pipeline library and handles the
 * whole matrix: compile, test, spotbugs, spotless, and the plugin compatibility
 * tester. Keep this thin -- anything clever here diverges from how every other
 * plugin is built.
 */
buildPlugin(
    // Run the full build on the oldest supported LTS, and a second pass on the
    // newest, so a Java or core incompatibility shows up before release.
    useContainerAgent: true,
    configurations: [
        [platform: 'linux',   jdk: 21],
        [platform: 'windows', jdk: 17],
    ]
)
