package io.jenkins.plugins.namespacecloud.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.StatusProto;
import java.util.List;
import namespace.cloud.iam.v1beta.Authz;
import org.junit.jupiter.api.Test;

class RequiredGrantsTest {

    @Test
    void everyGrantCarriesAWildcardResourceId() {
        // Omitting resource_id yields a token that can create and list but not
        // get, wait on, or destroy an instance: those actions are checked
        // against a specific resource and match nothing without it.
        String cmd = RequiredGrants.nscCommand(true);
        long grants = cmd.lines().filter(l -> l.contains("--grant")).count();
        long withId = cmd.lines()
                .filter(l -> l.contains("--grant"))
                .filter(l -> l.contains("\"resource_id\":\"*\""))
                .count();
        assertEquals(grants, withId, "every --grant must carry resource_id");
        assertTrue(grants > 0);
    }

    @Test
    void coreGrantsDoNotIncludeSshUnlessAsked() {
        String core = RequiredGrants.nscCommand(false);
        assertTrue(core.contains("\"resource_type\":\"instance\""));
        assertTrue(core.contains("\"create\""), "provisioning needs instance:create");
        assertTrue(core.contains("\"destroy\""), "teardown needs instance:destroy");
        assertFalse(core.contains("\"ssh\""), "SSH must not be requested unless a profile uses it");
        assertFalse(core.contains("ingress"), "ingress access is SSH-only");
    }

    @Test
    void sshConfigurationAddsSshAndIngress() {
        String ssh = RequiredGrants.nscCommand(true);
        assertTrue(ssh.contains("\"ssh\""));
        assertTrue(ssh.contains("\"resource_type\":\"ingress\""));
        assertTrue(ssh.contains("\"access\""));
    }

    @Test
    void grantsForOneResourceTypeAreMergedIntoASingleFlag() {
        // "instance" appears in both CORE and SSH_ONLY; the emitted command must
        // not pass --grant twice for it, which would be confusing to read and
        // may not union the way an operator expects.
        List<RequiredGrants.Grant> merged = RequiredGrants.mergeByResourceType(RequiredGrants.forConfiguration(true));
        long instanceEntries = merged.stream()
                .filter(g -> "instance".equals(g.getResourceType()))
                .count();
        assertEquals(1, instanceEntries);

        RequiredGrants.Grant instance = merged.stream()
                .filter(g -> "instance".equals(g.getResourceType()))
                .findFirst()
                .orElseThrow();
        assertTrue(instance.getActions().containsAll(List.of("create", "destroy", "get", "list", "ssh", "wait")));
    }

    @Test
    void missingPermissionsAreExtractedFromTheGrpcErrorDetails() {
        // This is what makes "Test connection" actionable: the server names the
        // refused grants rather than just saying PERMISSION_DENIED.
        Authz.PermissionDeniedError denied = Authz.PermissionDeniedError.newBuilder()
                .addMissingAccessPermissions(Authz.Access.newBuilder()
                        .setResourceType("instance")
                        .setAction("create")
                        .build())
                .addMissingAccessPermissions(Authz.Access.newBuilder()
                        .setResourceType("ingress")
                        .setAction("access")
                        .build())
                .build();

        com.google.rpc.Status status = com.google.rpc.Status.newBuilder()
                .setCode(Status.Code.PERMISSION_DENIED.value())
                .setMessage("permission denied")
                .addDetails(com.google.protobuf.Any.pack(denied))
                .build();

        StatusRuntimeException e = StatusProto.toStatusRuntimeException(status);

        List<Authz.Access> missing = PermissionDiagnostics.missingAccess(e);
        assertEquals(2, missing.size());
        assertEquals("instance: create, ingress: access", PermissionDiagnostics.format(missing));
    }

    @Test
    void anErrorWithoutDetailsYieldsNoFalseClaims() {
        StatusRuntimeException plain =
                new StatusRuntimeException(Status.PERMISSION_DENIED.withDescription("nope"), new Metadata());
        assertTrue(PermissionDiagnostics.missingAccess(plain).isEmpty());
    }
}
