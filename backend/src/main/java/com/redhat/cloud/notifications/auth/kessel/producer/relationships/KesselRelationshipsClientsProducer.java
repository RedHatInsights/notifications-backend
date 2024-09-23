package com.redhat.cloud.notifications.auth.kessel.producer.relationships;

import io.quarkus.logging.Log;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.project_kessel.relations.client.CheckClient;
import org.project_kessel.relations.client.Config;
import org.project_kessel.relations.client.LookupClient;
import org.project_kessel.relations.client.RelationsGrpcClientsManager;

import java.util.Optional;

/**
 * We have implemented our own producer for the clients because we need our
 * clients to be "@ApplicationScoped" so that we can mock them in our tests.
 * The default producer from the library gives us non-mockable and dependent
 * beans, which we cannot use in our tests.
 */
@ApplicationScoped
@Priority(1)
public class KesselRelationshipsClientsProducer {
    @Inject
    Config relationsConfig;

    /**
     * Produces a relations gRPC clients manager. Useful for being able to
     * safely shut down all the clients when destroying the objects.
     * @return a gRPC clients manager.
     */
    @Alternative
    @ApplicationScoped
    @Produces
    protected RelationsGrpcClientsManager getRelationsGrpcClientsManager() {
        final Optional<Config.AuthenticationConfig> authenticationConfigOptional = this.relationsConfig.authenticationConfig();

        if (this.relationsConfig.isSecureClients()) {
            Log.infof("Generated secure relationships gRPC client manager for Kessel with target url \"%s\"", this.relationsConfig.targetUrl());

            if (authenticationConfigOptional.isPresent()) {
                return RelationsGrpcClientsManager.forSecureClients(this.relationsConfig.targetUrl(), authenticationConfigOptional.get());
            } else {
                return RelationsGrpcClientsManager.forSecureClients(this.relationsConfig.targetUrl());
            }
        } else {
            Log.infof("Generated insecure relationships gRPC client manager for Kessel with target url \"%s\"", this.relationsConfig.targetUrl());

            if (authenticationConfigOptional.isPresent()) {
                return RelationsGrpcClientsManager.forInsecureClients(this.relationsConfig.targetUrl(), authenticationConfigOptional.get());
            } else {
                return RelationsGrpcClientsManager.forInsecureClients(this.relationsConfig.targetUrl());
            }
        }
    }

    /**
     * Safely shuts down the relations gRPC clients manager and all the clients
     * it created.
     * @param relationsGrpcClientsManager the relations gRPC clients manager to
     *                                    shut down.
     */
    protected void shutdownClientsManager(@Disposes RelationsGrpcClientsManager relationsGrpcClientsManager) {
        RelationsGrpcClientsManager.shutdownManager(relationsGrpcClientsManager);
    }

    /**
     * Produces a "check client" in order to be able to perform permission
     * checks.
     * @param relationsGrpcClientsManager the relations gRPC manager to create
     *                                    the client from.
     * @return a check client ready to be used for permission checks.
     */
    @Alternative
    @ApplicationScoped
    @Produces
    public CheckClient getCheckClient(final RelationsGrpcClientsManager relationsGrpcClientsManager) {
        return relationsGrpcClientsManager.getCheckClient();
    }

    /**
     * Produces a "lookup client" in order to be able to look up resources.
     * @param relationsGrpcClientsManager the relations gRPC manager to create
     *                                    the client from.
     * @return a "lookup client" ready to be used for looking up resources in
     * Kessel.
     */
    @Alternative
    @ApplicationScoped
    @Produces
    public LookupClient getLookupClient(final RelationsGrpcClientsManager relationsGrpcClientsManager) {
        return relationsGrpcClientsManager.getLookupClient();
    }
}
