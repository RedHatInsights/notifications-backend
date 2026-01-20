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

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    OAuth2ClientCredentialsCache oauth2ClientCredentialsCache;

    @Inject
    BackendConfig backendConfig;

    // Volatile: these fields are read by multiple request threads and written during channel initialization.
    private volatile KesselInventoryServiceGrpc.KesselInventoryServiceBlockingStub grpcClient;
    private volatile ManagedChannel grpcChannel;

    @PostConstruct
    void postConstruct() {
        initializeChannel("startup");
    }

    private void initializeChannel(String reason) {
        // Capture before overwriting so we can shut it down after.
        ManagedChannel oldGrpcChannel = grpcChannel;

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
        Log.warn("Kessel gRPC channel is unhealthy, recreating");
        initializeChannel("unhealthy_channel");
        return grpcClient;
    }

    @Retry(maxRetries = 3, delay = 100, retryOn = KesselTransientException.class)
    public CheckResponse check(CheckRequest request) {
        try {
            return getClient()
                .withDeadlineAfter(backendConfig.getKesselTimeoutMs(), TimeUnit.MILLISECONDS)
                .check(request);
        } catch (StatusRuntimeException e) {
            throw handleGrpcException(e);
        }
    }

    @Retry(maxRetries = 3, delay = 100, retryOn = KesselTransientException.class)
    public CheckForUpdateResponse checkForUpdate(CheckForUpdateRequest request) {
        try {
            return getClient()
                .withDeadlineAfter(backendConfig.getKesselTimeoutMs(), TimeUnit.MILLISECONDS)
                .checkForUpdate(request);
        } catch (StatusRuntimeException e) {
            throw handleGrpcException(e);
        }
    }

    private RuntimeException handleGrpcException(StatusRuntimeException e) {
        Status.Code code = e.getStatus().getCode();

        Log.errorf("gRPC call to Kessel failed: %s - %s", code, e.getMessage());
        meterRegistry.counter(KESSEL_GRPC_ERROR_COUNTER_NAME, Tags.of(KESSEL_GRPC_ERROR_TAG_ERROR_TYPE, code.name())).increment();

        // UNAUTHENTICATED usually means the OAuth2 token expired. Recreate channel with fresh credentials.
        if (code == UNAUTHENTICATED) {
            Log.warn("Received UNAUTHENTICATED from Kessel, recreating channel with fresh credentials");
            initializeChannel("unauthenticated");
            return new KesselTransientException(e);
        }

        // Other transient failures may resolve on retry.
        if (TRANSIENT_FAILURE_CODES.contains(code)) {
            return new KesselTransientException(e);
        }

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
            if (!grpcChannel.awaitTermination(backendConfig.getKesselTimeoutMs() + 1000, TimeUnit.MILLISECONDS)) {
                Log.warn("Kessel gRPC channel did not terminate gracefully, forcing shutdown");
                grpcChannel.shutdownNow();
            }
        } catch (InterruptedException e) {
            grpcChannel.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
