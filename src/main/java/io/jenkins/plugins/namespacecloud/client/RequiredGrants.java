package io.jenkins.plugins.namespacecloud.client;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The Namespace permissions this plugin needs, and the {@code nsc token create}
 * invocation that mints a token carrying exactly those grants.
 *
 * <p>Resource types and actions are those documented at
 * <a href="https://namespace.so/docs/security/permissions">namespace.so/docs/security/permissions</a>.
 * Keeping the list here (rather than scattered across call sites) means the
 * configuration page can show the operator a copy-pasteable command.
 */
public final class RequiredGrants {

    /**
     * A single {@code resource_type} plus the actions needed on it.
     *
     * <p>A JavaBean rather than a record because Jelly's expression language
     * resolves {@code getX()}, not a record's {@code x()} accessor.
     */
    public static final class Grant {
        private final String resourceType;
        private final List<String> actions;
        private final String why;
        private final boolean optional;

        public Grant(String resourceType, List<String> actions, String why, boolean optional) {
            this.resourceType = resourceType;
            this.actions = List.copyOf(actions);
            this.why = why;
            this.optional = optional;
        }

        public String getResourceType() {
            return resourceType;
        }

        public List<String> getActions() {
            return actions;
        }

        /** {@code "create, get, list"} — for table display. */
        public String getActionsCsv() {
            return String.join(", ", actions);
        }

        public String getWhy() {
            return why;
        }

        public boolean isOptional() {
            return optional;
        }
    }

    /** Grants needed to provision, observe and tear down agents. */
    public static final List<Grant> CORE = List.of(
            new Grant(
                    "instance",
                    List.of("create", "get", "list", "wait", "destroy"),
                    "Provision an instance per build, poll it to RUNNING, and destroy it afterwards.",
                    false),
            new Grant(
                    "instance/o11y/logs",
                    List.of("get"),
                    "Surface instance-side container logs in the Jenkins agent log.",
                    true));

    /** Additional grants only the SSH launch strategy needs. */
    public static final List<Grant> SSH_ONLY = List.of(
            new Grant("instance", List.of("ssh"), "Open an SSH session to the instance.", false),
            new Grant("ingress", List.of("access"), "Reach the instance through the regional ingress.", false));

    private RequiredGrants() {}

    /** All grants that apply, given whether any template uses the SSH launcher. */
    public static List<Grant> forConfiguration(boolean anyTemplateUsesSsh) {
        if (!anyTemplateUsesSsh) {
            return CORE;
        }
        return java.util.stream.Stream.concat(CORE.stream(), SSH_ONLY.stream()).collect(Collectors.toList());
    }

    /**
     * Renders an {@code nsc token create} command that mints a token with these
     * grants. Shown on the configuration page so the operator does not have to
     * assemble the JSON by hand.
     */
    public static String nscCommand(boolean anyTemplateUsesSsh) {
        StringBuilder sb = new StringBuilder("nsc token create \\\n  --name jenkins \\\n  --expires_in 720h");
        for (Grant g : mergeByResourceType(forConfiguration(anyTemplateUsesSsh))) {
            String actions = g.getActions().stream().map(a -> '"' + a + '"').collect(Collectors.joining(","));
            // resource_id is not optional in practice: actions that are checked
            // against a specific resource (get, wait, destroy) match nothing
            // when it is omitted, while unscoped ones (create, list) still
            // work -- producing a token that creates instances it cannot then
            // wait for or tear down.
            sb.append(" \\\n  --grant '{\"resource_type\":\"")
                    .append(g.getResourceType())
                    .append("\",\"resource_id\":\"*\",\"actions\":[")
                    .append(actions)
                    .append("]}'");
        }
        return sb.toString();
    }

    /**
     * Collapses grants that share a resource type, so {@code instance} appears
     * once with the union of its actions rather than twice.
     */
    public static List<Grant> mergeByResourceType(List<Grant> grants) {
        return grants.stream()
                .collect(Collectors.groupingBy(
                        Grant::getResourceType, java.util.LinkedHashMap::new, Collectors.toList()))
                .entrySet()
                .stream()
                .map(e -> new Grant(
                        e.getKey(),
                        e.getValue().stream()
                                .flatMap(g -> g.getActions().stream())
                                .distinct()
                                .sorted()
                                .collect(Collectors.toList()),
                        e.getValue().stream().map(Grant::getWhy).collect(Collectors.joining(" ")),
                        e.getValue().stream().allMatch(Grant::isOptional)))
                .collect(Collectors.toList());
    }

    /** {@code "instance: create, get, list"} — for display. */
    public static String describe(Grant g) {
        return g.getResourceType() + ": " + String.join(", ", g.getActions()).toLowerCase(Locale.ROOT);
    }
}
