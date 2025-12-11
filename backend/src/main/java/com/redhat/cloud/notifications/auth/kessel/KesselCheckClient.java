package com.redhat.cloud.notifications.auth.kessel;

import com.nimbusds.jose.util.Pair;
import com.redhat.cloud.notifications.config.BackendConfig;
import io.grpc.ManagedChannel;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.project_kessel.api.auth.OAuth2ClientCredentials;
import org.project_kessel.api.inventory.v1beta2.CheckForUpdateRequest;
import org.project_kessel.api.inventory.v1beta2.CheckForUpdateResponse;
import org.project_kessel.api.inventory.v1beta2.CheckRequest;
import org.project_kessel.api.inventory.v1beta2.CheckResponse;
import org.project_kessel.api.inventory.v1beta2.ClientBuilder;
import org.project_kessel.api.inventory.v1beta2.KesselInventoryServiceGrpc;

@ApplicationScoped
public class KesselCheckClient {

    @Inject
    OAuth2ClientCredentialsCache oauth2ClientCredentialsCache;

    @Inject
    BackendConfig backendConfig;

    private KesselInventoryServiceGrpc.KesselInventoryServiceBlockingStub grpcClient;
    private ManagedChannel grpcChannel;

    @PostConstruct
    void postConstruct() {

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
            OAuth2ClientCredentials oAuth2ClientCredentials = oauth2ClientCredentialsCache.getCredentials();
            clientAndChannel = new ClientBuilder(backendConfig.getKesselUrl())
                .oauth2ClientAuthenticated(oAuth2ClientCredentials)
                .build();
        }

        grpcClient = clientAndChannel.getLeft();
        grpcChannel = clientAndChannel.getRight();
    }

    @PreDestroy
    void preDestroy() {
        grpcChannel.shutdown();
    }

    public CheckResponse check(CheckRequest request) {
        return grpcClient.check(request);
    }

    public CheckForUpdateResponse checkForUpdate(CheckForUpdateRequest request) {
        return grpcClient.checkForUpdate(request);
    }
}
