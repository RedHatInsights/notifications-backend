package com.redhat.cloud.notifications.auth.kessel.producer.inventory;

import com.redhat.cloud.notifications.config.BackendConfig;
import io.quarkus.logging.Log;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.project_kessel.client.InventoryGrpcClientsManager;
import org.project_kessel.client.NotificationsIntegrationClient;

/**
 * We have implemented our own producer for the clients because we need our
 * clients to be "@ApplicationScoped" so that we can mock them in our tests.
 * The default producer from the library gives us non-mockable and dependent
 * beans, which we cannot use in our tests.
 */
@ApplicationScoped
@Priority(1)
public class KesselInventoryClientsProducer {
    /**
     * Constant for the configuration key that defines the inventory API's
     * target URL.
     */
    private static final String RELATIONSHIPS_API_TARGET_URL = "notifications.kessel.inventory-api.target-url";

    @Inject
    BackendConfig backendConfig;

    /**
     * The target URL the gRPC client will connect to.
     */
    @ConfigProperty(name = RELATIONSHIPS_API_TARGET_URL)
    String targetUrl;

    /**
     * Produces an inventory gRPC clients manager. Useful for being able to
     * safely shut down all the clients when destroying the objects.
     * @return a gRPC clients manager.
     */
    @Alternative
    @ApplicationScoped
    @Produces
    protected InventoryGrpcClientsManager getInventoryGrpcClientsManager() {
        if (this.backendConfig.isKesselUseSecureClientEnabled()) {
            Log.infof("Generated secure inventory gRPC client manager for Kessel with target url \"%s\"", this.targetUrl);

            return InventoryGrpcClientsManager.forSecureClients(this.targetUrl);
        }

        Log.infof("Generated insecure inventory gRPC client manager for Kessel with target url \"%s\"", this.targetUrl);
        return InventoryGrpcClientsManager.forInsecureClients(this.targetUrl);
    }

    /**
     * Safely shuts down the inventory gRPC clients manager and all the clients
     * it created.
     * @param inventoryGrpcClientsManager the inventory gRPC clients manager to
     *                                    shut down.
     */
    protected void shutdownClientsManager(@Disposes InventoryGrpcClientsManager inventoryGrpcClientsManager) {
        InventoryGrpcClientsManager.shutdownManager(inventoryGrpcClientsManager);
    }

    /**
     * Produces a "lookup client" in order to be able to look up resources.
     * @param inventoryGrpcClientsManager the inventory gRPC manager to create
     *                                    the client from.
     * @return a "notifications integration client" ready to be used for
     * managing the integrations in Kessel.
     */
    @Alternative
    @ApplicationScoped
    @Produces
    public NotificationsIntegrationClient getNotificationsIntegrationClient(final InventoryGrpcClientsManager inventoryGrpcClientsManager) {
        return inventoryGrpcClientsManager.getNotificationsIntegrationClient();
    }
}
