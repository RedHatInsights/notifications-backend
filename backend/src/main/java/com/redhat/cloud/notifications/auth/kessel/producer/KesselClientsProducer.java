package com.redhat.cloud.notifications.auth.kessel.producer;

import com.redhat.cloud.notifications.config.BackendConfig;
import io.quarkus.logging.Log;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.project_kessel.relations.client.CheckClient;
import org.project_kessel.relations.client.RelationsGrpcClientsManager;

@ApplicationScoped
@Priority(1)
public class KesselClientsProducer {
    private static final String KESSEL_TARGET_URL = "notifications.kessel.target-url";

    @Inject
    BackendConfig backendConfig;

    /**
     * The target URL the gRPC client will connect to.
     */
    @ConfigProperty(name = KESSEL_TARGET_URL)
    String targetUrl;

    /**
     * Produces a gRPC clients manager. Useful for being able to safely shut
     * down all the clients when destroying the objects.
     * @return a gRPC clients manager.
     */
    @Alternative
    @ApplicationScoped
    @Produces
    protected RelationsGrpcClientsManager getRelationsGrpcClientsManager() {
        if (this.backendConfig.isKesselUseSecureClientEnabled()) {
            Log.infof("Generated secure gRPC client manager with target url \"%s\"", this.targetUrl);

            return RelationsGrpcClientsManager.forSecureClients(this.targetUrl);
        }

        Log.infof("Generated insecure gRPC client manager with target url \"%s\"", this.targetUrl);
        return RelationsGrpcClientsManager.forInsecureClients(this.targetUrl);
    }

    /**
     * Safely shuts down the gRPC clients manager and all the clients it
     * created.
     * @param relationsGrpcClientsManager the gRPC clients to shut down.
     */
    protected void shutdownClientsManager(@Disposes RelationsGrpcClientsManager relationsGrpcClientsManager) {
        RelationsGrpcClientsManager.shutdownManager(relationsGrpcClientsManager);
    }

    /**
     * Produces a "check client" in order to be able to perform permission
     * checks.
     * @param relationsGrpcClientsManager the gRPC manager to create the client
     *                                    from.
     * @return a check client ready to be used for permission checks.
     */
    @Alternative
    @ApplicationScoped
    @Produces
    public CheckClient getCheckClient(final RelationsGrpcClientsManager relationsGrpcClientsManager) {
        return relationsGrpcClientsManager.getCheckClient();
    }
}
