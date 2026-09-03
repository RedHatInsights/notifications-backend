package com.redhat.cloud.notifications.auth.kessel;

import com.nimbusds.jose.util.Pair;
import com.redhat.cloud.notifications.config.BackendConfig;
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
import org.eclipse.microprofile.faulttolerance.Retry;
import org.project_kessel.api.auth.OAuth2ClientCredentials;
import org.project_kessel.api.auth.OAuth2Exception;
import org.project_kessel.api.inventory.v1beta2.CheckForUpdateRequest;
import org.project_kessel.api.inventory.v1beta2.CheckForUpdateResponse;
import org.project_kessel.api.inventory.v1beta2.CheckRequest;
import org.project_kessel.api.inventory.v1beta2.CheckResponse;
import org.project_kessel.api.inventory.v1beta2.ClientBuilder;
import org.project_kessel.api.inventory.v1beta2.KesselInventoryServiceGrpc;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.grpc.Status.Code.ABORTED;
import static io.grpc.Status.Code.DEADLINE_EXCEEDED;
import static io.grpc.Status.Code.RESOURCE_EXHAUSTED;
import static io.grpc.Status.Code.UNAUTHENTICATED;
import static io.grpc.Status.Code.UNAVAILABLE;

/**
 * gRPC client for Kessel permission checks.
 *
 * <p>Resilience features:
 * <ul>
 *   <li>Configurable timeout on all gRPC calls (notifications.kessel.timeout-ms)</li>
 *   <li>Automatic retry (3 attempts) on transient failures (UNAVAILABLE, DEADLINE_EXCEEDED, etc.)</li>
 *   <li>Automatic channel recreation on UNAUTHENTICATED errors (token expiry)</li>
 *   <li>Automatic channel recreation if channel enters SHUTDOWN state</li>
 * </ul>
 */
@ApplicationScoped
public class KesselCheckClient {

    public static final String KESSEL_CHANNEL_INIT_COUNTER_NAME = "notifications.kessel.channel.init";
    public static final String KESSEL_CHANNEL_INIT_TAG_REASON = "reason";
    public static final String KESSEL_GRPC_ERROR_COUNTER_NAME = "notifications.kessel.grpc.error";
    public static final String KESSEL_GRPC_ERROR_TAG_ERROR_TYPE = "error_type";

    // These codes indicate temporary issues that may resolve on retry.
    private static final List<Status.Code> TRANSIENT_FAILURE_CODES = List.of(UNAVAILABLE, DEADLINE_EXCEEDED, RESOURCE_EXHAUSTED, ABORTED);
    private static final long CHANNEL_SHUTDOWN_TIMEOUT_SECONDS = 30;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    OAuth2ClientCredentialsCache oauth2ClientCredentialsCache;

    @Inject
    BackendConfig backendConfig;

    /**
     * Immutable pairing of a gRPC stub and the channel that backs it. Holding both in a single
     * volatile reference lets request threads read a consistent (stub, channel) pair with one
     * volatile read, so a concurrent channel reinitialization can never leave a thread with a stub
     * from one channel and a reference to a different channel.
     */
    record ChannelHolder(KesselInventoryServiceGrpc.KesselInventoryServiceBlockingStub stub, ManagedChannel channel) { }

    // Volatile: read by multiple request threads and written during channel initialization.
    private volatile ChannelHolder channelHolder;

    @PostConstruct
    void postConstruct() {
        initializeChannel("startup");
    }

    private void initializeChannel(String reason) {
        // Capture before overwriting so we can shut it down after.
        ChannelHolder oldHolder = channelHolder;

        Pair<KesselInventoryServiceGrpc.KesselInventoryServiceBlockingStub, ManagedChannel> clientAndChannel;
        /*
         * OAuth2 authentication and TLS verification are currently disabled in Kessel, so the insecure mode is the only option.
         * TLS verification requires a CA cert which should be provided through the Clowder config soon. When the CA cert is
         * available, we'll have to update our code and use it, then switch to the secure mode with OAuth2 and TLS.
         */
        if (backendConfig.isKesselInsecureClientEnabled()) {
            Log.warn("Initializing insecure client for Kessel: OAuth2 authentication and TLS verification will be disabled");
            clientAndChannel = new ClientBuilder(backendConfig.getKesselUrl())
                .insecure()
                .build();
        } else {
            // Clear cache to get fresh credentials (important after UNAUTHENTICATED errors).
            oauth2ClientCredentialsCache.clearCache();
            OAuth2ClientCredentials oAuth2ClientCredentials = oauth2ClientCredentialsCache.getCredentials();
            clientAndChannel = new ClientBuilder(backendConfig.getKesselUrl())
                .oauth2ClientAuthenticated(oAuth2ClientCredentials)
                .build();
        }

        channelHolder = new ChannelHolder(clientAndChannel.getLeft(), clientAndChannel.getRight());

        Log.debugf("Kessel gRPC channel initialized: %s", reason);
        meterRegistry.counter(KESSEL_CHANNEL_INIT_COUNTER_NAME, Tags.of(KESSEL_CHANNEL_INIT_TAG_REASON, reason)).increment();

        // Shutdown old gRPC channel without waiting (let in-flight requests drain in background).
        if (oldHolder != null && oldHolder.channel() != null) {
            oldHolder.channel().shutdown();
        }
    }

    private ChannelHolder getClient() {
        ChannelHolder holder = channelHolder;
        // SHUTDOWN state is terminal - channel cannot recover and must be recreated.
        if (holder != null && holder.channel() != null && holder.channel().getState(false) != ConnectivityState.SHUTDOWN) {
            return holder;
        }
        Log.warn("Kessel gRPC channel is unhealthy, recreating");
        initializeChannel("unhealthy_channel");
        return channelHolder;
    }

    @Retry(maxRetries = 3, delay = 100, retryOn = KesselTransientException.class)
    public CheckResponse check(CheckRequest request) {
        // getClient() is inside the try so an OAuth2Exception thrown while lazily reinitializing the channel is
        // wrapped in KesselTransientException and retried, instead of propagating past the @Retry interceptor.
        // The holder gives us a consistent (stub, channel) snapshot for this call from a single volatile read.
        ChannelHolder holder = null;
        try {
            holder = getClient();
            return holder.stub()
                .withDeadlineAfter(backendConfig.getKesselTimeoutMs(), TimeUnit.MILLISECONDS)
                .check(request);
        } catch (StatusRuntimeException e) {
            throw handleGrpcException(e, holder);
        } catch (OAuth2Exception e) {
            Log.warnf("Transient error fetching Kessel OAuth2 credentials (may retry): %s", e.getMessage());
            throw new KesselTransientException(e);
        }
    }

    @Retry(maxRetries = 3, delay = 100, retryOn = KesselTransientException.class)
    public CheckForUpdateResponse checkForUpdate(CheckForUpdateRequest request) {
        // getClient() is inside the try so an OAuth2Exception thrown while lazily reinitializing the channel is
        // wrapped in KesselTransientException and retried, instead of propagating past the @Retry interceptor.
        // The holder gives us a consistent (stub, channel) snapshot for this call from a single volatile read.
        ChannelHolder holder = null;
        try {
            holder = getClient();
            return holder.stub()
                .withDeadlineAfter(backendConfig.getKesselTimeoutMs(), TimeUnit.MILLISECONDS)
                .checkForUpdate(request);
        } catch (StatusRuntimeException e) {
            throw handleGrpcException(e, holder);
        } catch (OAuth2Exception e) {
            Log.warnf("Transient error fetching Kessel OAuth2 credentials (may retry): %s", e.getMessage());
            throw new KesselTransientException(e);
        }
    }

    private RuntimeException handleGrpcException(StatusRuntimeException e, ChannelHolder failedHolder) {
        Status.Code code = e.getStatus().getCode();

        meterRegistry.counter(KESSEL_GRPC_ERROR_COUNTER_NAME, Tags.of(KESSEL_GRPC_ERROR_TAG_ERROR_TYPE, code.name())).increment();

        // UNAUTHENTICATED usually means the OAuth2 token expired. Recreate channel with fresh credentials.
        if (code == UNAUTHENTICATED) {
            Log.warnf("Transient gRPC error from Kessel (may retry): %s - %s. Recreating channel with fresh credentials.", code, e.getMessage());
            try {
                initializeChannel("unauthenticated");
            } catch (OAuth2Exception oauthEx) {
                Log.warnf("Failed to refresh OAuth2 credentials after UNAUTHENTICATED error: %s", oauthEx.getMessage());
                // Shut down only the exact channel that produced this failed call, so a channel another thread
                // concurrently reinitialized to a healthy state is never torn down here.
                if (failedHolder != null && failedHolder.channel() != null) {
                    failedHolder.channel().shutdown();
                }
                return new KesselTransientException(oauthEx);
            }
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
        ChannelHolder holder = channelHolder;
        if (holder == null || holder.channel() == null) {
            return;
        }
        ManagedChannel channel = holder.channel();
        channel.shutdown();
        try {
            if (!channel.awaitTermination(CHANNEL_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                Log.warn("Kessel gRPC channel did not terminate gracefully, forcing shutdown");
                channel.shutdownNow();
            }
        } catch (InterruptedException e) {
            channel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
