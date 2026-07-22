package com.redhat.cloud.notifications.recipients.resolver.kessel;

import com.nimbusds.jose.util.Pair;
import com.redhat.cloud.notifications.recipients.config.RecipientsResolverConfig;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.project_kessel.api.auth.OAuth2ClientCredentials;
import org.project_kessel.api.inventory.v1beta2.ClientBuilder;
import org.project_kessel.api.inventory.v1beta2.KesselInventoryServiceGrpc;
import org.project_kessel.api.inventory.v1beta2.StreamedListSubjectsRequest;
import org.project_kessel.api.inventory.v1beta2.StreamedListSubjectsResponse;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.grpc.Status.Code.ABORTED;
import static io.grpc.Status.Code.DEADLINE_EXCEEDED;
import static io.grpc.Status.Code.RESOURCE_EXHAUSTED;
import static io.grpc.Status.Code.UNAUTHENTICATED;
import static io.grpc.Status.Code.UNAVAILABLE;

/**
 * gRPC client for Kessel's {@code StreamedListSubjects} Inventory API operation.
 *
 * <p>Resilience features:
 * <ul>
 *   <li>Configurable timeout on the gRPC call (notifications.kessel.timeout-ms)</li>
 *   <li>Automatic channel recreation on UNAUTHENTICATED errors (token expiry)</li>
 *   <li>Automatic channel recreation if channel enters SHUTDOWN state</li>
 * </ul>
 *
 * <p>Unlike a unary call, {@link #streamedListSubjects(StreamedListSubjectsRequest)} returns a
 * streaming {@link Iterator}: a {@link StatusRuntimeException} can surface mid-stream, after some
 * responses were already consumed. This class does not retry on its own -- the caller owns
 * retrying the whole request/iteration from scratch.
 */
@ApplicationScoped
public class KesselInventoryClient {

    public static final String KESSEL_CHANNEL_INIT_COUNTER_NAME = "notifications.kessel.channel.init";
    public static final String KESSEL_CHANNEL_INIT_TAG_REASON = "reason";
    public static final String KESSEL_GRPC_ERROR_COUNTER_NAME = "notifications.kessel.grpc.error";
    public static final String KESSEL_GRPC_ERROR_TAG_ERROR_TYPE = "error_type";

    private static final List<Status.Code> TRANSIENT_FAILURE_CODES = List.of(UNAVAILABLE, DEADLINE_EXCEEDED, RESOURCE_EXHAUSTED, ABORTED);
    private static final long CHANNEL_SHUTDOWN_TIMEOUT_SECONDS = 30;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    OAuth2ClientCredentialsCache oauth2ClientCredentialsCache;

    @Inject
    RecipientsResolverConfig recipientsResolverConfig;

    // Volatile: these fields are read by multiple request threads and written during channel initialization.
    private volatile KesselInventoryServiceGrpc.KesselInventoryServiceBlockingStub grpcClient;
    private volatile ManagedChannel grpcChannel;

    @PostConstruct
    void postConstruct() {
        initializeChannel("startup", false);
    }

    // Synchronized so concurrent callers (e.g. racing UNAUTHENTICATED errors) don't shut down a
    // channel that another thread just published and grabbed a stub reference to.
    private synchronized void initializeChannel(String reason, boolean forceCredentialsRefresh) {
        // Capture before overwriting so we can shut it down after.
        ManagedChannel oldGrpcChannel = grpcChannel;

        Pair<KesselInventoryServiceGrpc.KesselInventoryServiceBlockingStub, ManagedChannel> clientAndChannel;
        /*
         * OAuth2 authentication and TLS verification are currently disabled in Kessel, so the insecure mode is the only option.
         * TLS verification requires a CA cert which should be provided through the Clowder config soon. When the CA cert is
         * available, we'll have to update our code and use it, then switch to the secure mode with OAuth2 and TLS.
         */
        if (recipientsResolverConfig.isKesselInsecureClientEnabled()) {
            Log.warn("Initializing insecure client for Kessel: OAuth2 authentication and TLS verification will be disabled");
            clientAndChannel = new ClientBuilder(recipientsResolverConfig.getKesselUrl())
                .insecure()
                .build();
        } else {
            // Only force a cache clear after an UNAUTHENTICATED error: startup/unhealthy-channel
            // reinit can reuse a still-valid cached token instead of forcing a fresh OIDC round-trip.
            if (forceCredentialsRefresh) {
                oauth2ClientCredentialsCache.clearCache();
            }
            OAuth2ClientCredentials oAuth2ClientCredentials = oauth2ClientCredentialsCache.getCredentials();
            clientAndChannel = new ClientBuilder(recipientsResolverConfig.getKesselUrl())
                .oauth2ClientAuthenticated(oAuth2ClientCredentials)
                .build();
        }

        grpcClient = clientAndChannel.getLeft();
        grpcChannel = clientAndChannel.getRight();

        Log.debugf("Kessel gRPC channel initialized: %s", reason);
        meterRegistry.counter(KESSEL_CHANNEL_INIT_COUNTER_NAME, Tags.of(KESSEL_CHANNEL_INIT_TAG_REASON, reason)).increment();

        // Shutdown old gRPC channel without waiting (let in-flight requests drain in background).
        if (oldGrpcChannel != null) {
            oldGrpcChannel.shutdown();
        }
    }

    private KesselInventoryServiceGrpc.KesselInventoryServiceBlockingStub getClient() {
        // SHUTDOWN state is terminal - channel cannot recover and must be recreated.
        if (grpcChannel != null && grpcChannel.getState(false) != ConnectivityState.SHUTDOWN) {
            return grpcClient;
        }
        synchronized (this) {
            // Re-check after acquiring the lock: another thread may have already recreated the
            // channel while this one was waiting.
            if (grpcChannel != null && grpcChannel.getState(false) != ConnectivityState.SHUTDOWN) {
                return grpcClient;
            }
            Log.warn("Kessel gRPC channel is unhealthy, recreating");
            initializeChannel("unhealthy_channel", false);
        }
        return grpcClient;
    }

    /**
     * Issues the request and returns the raw response stream. This method does not catch
     * {@link StatusRuntimeException}: a mid-stream failure can surface from the returned
     * iterator's {@code hasNext()}/{@code next()} just as easily as from this initial call, so
     * the caller must wrap the whole iteration and call {@link #handleGrpcException} itself.
     */
    public Iterator<StreamedListSubjectsResponse> streamedListSubjects(StreamedListSubjectsRequest request) {
        return getClient()
            .withDeadlineAfter(recipientsResolverConfig.getKesselTimeoutMs(), TimeUnit.MILLISECONDS)
            .streamedListSubjects(request);
    }

    /**
     * Maps a {@link StatusRuntimeException} raised anywhere during the request/iteration to a
     * {@link KesselTransientException} if it's retryable, recreating the gRPC channel first if the
     * failure was caused by an expired OAuth2 token. Non-transient errors are returned as-is.
     */
    public RuntimeException handleGrpcException(StatusRuntimeException e) {
        Status.Code code = e.getStatus().getCode();

        meterRegistry.counter(KESSEL_GRPC_ERROR_COUNTER_NAME, Tags.of(KESSEL_GRPC_ERROR_TAG_ERROR_TYPE, code.name())).increment();

        // UNAUTHENTICATED usually means the OAuth2 token expired. Recreate channel with fresh credentials.
        if (code == UNAUTHENTICATED) {
            Log.warnf("Transient gRPC error from Kessel (may retry): %s - %s. Recreating channel with fresh credentials.", code, e.getMessage());
            initializeChannel("unauthenticated", true);
            return new KesselTransientException(e);
        }

        // Other transient failures may resolve on retry.
        if (TRANSIENT_FAILURE_CODES.contains(code)) {
            Log.warnf("Transient gRPC error from Kessel (may retry): %s - %s", code, e.getMessage());
            return new KesselTransientException(e);
        }

        // Only non-transient errors are logged at error level.
        Log.errorf("gRPC call to Kessel failed: %s - %s", code, e.getMessage());

        // Non-transient errors (PERMISSION_DENIED, NOT_FOUND, etc.) are not retried.
        return e;
    }

    @PreDestroy
    void preDestroy() {
        if (grpcChannel == null) {
            return;
        }
        grpcChannel.shutdown();
        try {
            if (!grpcChannel.awaitTermination(CHANNEL_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Log.warn("Kessel gRPC channel did not terminate gracefully, forcing shutdown");
                grpcChannel.shutdownNow();
            }
        } catch (InterruptedException e) {
            grpcChannel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
