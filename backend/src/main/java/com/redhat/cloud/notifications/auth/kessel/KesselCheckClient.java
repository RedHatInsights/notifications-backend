package com.redhat.cloud.notifications.auth.kessel;

import com.nimbusds.jose.util.Pair;
import com.redhat.cloud.notifications.config.BackendConfig;
import io.grpc.ManagedChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.project_kessel.api.auth.ClientConfigAuth;
import org.project_kessel.api.auth.OAuth2ClientCredentials;
import org.project_kessel.api.auth.OIDCDiscovery;
import org.project_kessel.api.auth.OIDCDiscoveryMetadata;
import org.project_kessel.api.inventory.v1beta2.CheckForUpdateRequest;
import org.project_kessel.api.inventory.v1beta2.CheckForUpdateResponse;
import org.project_kessel.api.inventory.v1beta2.CheckRequest;
import org.project_kessel.api.inventory.v1beta2.CheckResponse;
import org.project_kessel.api.inventory.v1beta2.ClientBuilder;
import org.project_kessel.api.inventory.v1beta2.KesselInventoryServiceGrpc;

@ApplicationScoped
public class KesselCheckClient {

    @Inject
    BackendConfig backendConfig;

    private KesselInventoryServiceGrpc.KesselInventoryServiceBlockingStub grpcClient;
    private ManagedChannel grpcChannel;

    @PostConstruct
    void postConstruct() {

        OIDCDiscoveryMetadata oidcDiscovery = OIDCDiscovery.fetchOIDCDiscovery(backendConfig.getOidcIssuer());
        ClientConfigAuth authConfig = new ClientConfigAuth(backendConfig.getOidcClientId(), backendConfig.getOidcSecret(), oidcDiscovery.tokenEndpoint());
        OAuth2ClientCredentials credentials = new OAuth2ClientCredentials(authConfig);

        Pair<KesselInventoryServiceGrpc.KesselInventoryServiceBlockingStub, ManagedChannel> clientAndChannel = new ClientBuilder(backendConfig.getKesselUrl())
            .oauth2ClientAuthenticated(credentials)
            .build();
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
