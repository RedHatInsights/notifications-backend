package com.redhat.cloud.notifications.auth.kessel.producer.inventory;

import io.quarkus.logging.Log;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.project_kessel.inventory.client.Config;
import org.project_kessel.inventory.client.InventoryGrpcClientsManager;
import org.project_kessel.inventory.client.NotificationsIntegrationClient;

import java.util.Optional;

/**
 * We have implemented our own producer for the clients because we need our
 * clients to be "@ApplicationScoped" so that we can mock them in our tests.
 * The default producer from the library gives us non-mockable and dependent
 * beans, which we cannot use in our tests.
 */
@ApplicationScoped
@Priority(1)
public class KesselInventoryClientsProducer {
    @Inject
    Config inventoryConfig;

    /**
     * Produces an inventory gRPC clients manager. Useful for being able to
     * safely shut down all the clients when destroying the objects.
     * @return a gRPC clients manager.
     */
    @Alternative
    @ApplicationScoped
    @Produces
    protected InventoryGrpcClientsManager getInventoryGrpcClientsManager() {
        final Optional<Config.AuthenticationConfig> authenticationConfigOptional = this.inventoryConfig.authenticationConfig();

        if (this.inventoryConfig.isSecureClients()) {
            Log.infof("Generated secure inventory gRPC client manager for Kessel with target url \"%s\"", this.inventoryConfig.targetUrl());

            if (authenticationConfigOptional.isPresent()) {
                return InventoryGrpcClientsManager.forSecureClients(this.inventoryConfig.targetUrl(), authenticationConfigOptional.get());
            } else {
                return InventoryGrpcClientsManager.forSecureClients(this.inventoryConfig.targetUrl());
            }
        } else {
            Log.infof("Generated insecure inventory gRPC client manager for Kessel with target url \"%s\"", this.inventoryConfig.targetUrl());

            if (authenticationConfigOptional.isPresent()) {
                return InventoryGrpcClientsManager.forInsecureClients(this.inventoryConfig.targetUrl(), authenticationConfigOptional.get());
            } else {
                return InventoryGrpcClientsManager.forInsecureClients(this.inventoryConfig.targetUrl());
            }
        }
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
