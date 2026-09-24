package io.jenkins.plugins.namespacecloud.client;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.util.Secret;
import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.Status;
import java.util.concurrent.Executor;

/**
 * Attaches a Namespace tenant token to every outgoing RPC as
 * {@code Authorization: Bearer <token>}.
 *
 * <p>The token is held as a Jenkins {@link Secret} so it is never written to
 * disk or to a log in plaintext.
 */
public final class BearerTokenCredentials extends CallCredentials {

    private static final Metadata.Key<String> AUTHORIZATION =
            Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

    private final Secret token;

    public BearerTokenCredentials(@NonNull Secret token) {
        this.token = token;
    }

    @Override
    public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor, MetadataApplier applier) {
        appExecutor.execute(() -> {
            try {
                String plain = token.getPlainText();
                if (plain == null || plain.isBlank()) {
                    applier.fail(Status.UNAUTHENTICATED.withDescription("Namespace token is empty"));
                    return;
                }
                Metadata headers = new Metadata();
                headers.put(AUTHORIZATION, "Bearer " + plain);
                applier.apply(headers);
            } catch (RuntimeException e) {
                applier.fail(Status.UNAUTHENTICATED.withCause(e).withDescription("Failed to apply Namespace token"));
            }
        });
    }
}
