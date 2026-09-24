package io.jenkins.plugins.namespacecloud.client;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.protobuf.StatusProto;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import namespace.cloud.iam.v1beta.Authz;

/**
 * Turns a {@code PERMISSION_DENIED} from the Namespace API into the concrete
 * list of grants the token is missing.
 *
 * <p>Namespace attaches a {@link Authz.PermissionDeniedError} to the gRPC status
 * details, which names each {@code resource_type}/{@code action} pair that was
 * refused. That is far more actionable than the status message alone, and it is
 * the only reliable way to check a scoped token: {@code TenantService.DescribePolicies}
 * requires an <em>admin</em>-scoped token, so calling it from a
 * least-privilege Jenkins token would itself be denied.
 */
public final class PermissionDiagnostics {

    private PermissionDiagnostics() {}

    /**
     * Extracts the refused permissions from a failed RPC.
     *
     * @return the missing accesses, or an empty list if the throwable is not a
     *     permission error or carries no structured details.
     */
    public static List<Authz.Access> missingAccess(Throwable t) {
        com.google.rpc.Status status = StatusProto.fromThrowable(t);
        if (status == null) {
            return List.of();
        }
        List<Authz.Access> out = new ArrayList<>();
        for (Any detail : status.getDetailsList()) {
            if (!detail.is(Authz.PermissionDeniedError.class)) {
                continue;
            }
            try {
                out.addAll(detail.unpack(Authz.PermissionDeniedError.class).getMissingAccessPermissionsList());
            } catch (InvalidProtocolBufferException e) {
                // A detail we cannot parse tells us nothing; the caller still
                // reports the underlying PERMISSION_DENIED.
            }
        }
        return out;
    }

    /** {@code "instance: create, ingress: access"} — for display in a form validation message. */
    public static String format(List<Authz.Access> missing) {
        return missing.stream()
                .map(a -> a.getResourceId().isEmpty()
                        ? a.getResourceType() + ": " + a.getAction()
                        : a.getResourceType() + "[" + a.getResourceId() + "]: " + a.getAction())
                .distinct()
                .collect(Collectors.joining(", "));
    }
}
