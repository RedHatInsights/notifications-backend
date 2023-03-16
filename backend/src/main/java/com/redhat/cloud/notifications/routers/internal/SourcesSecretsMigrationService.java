package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.routers.sources.SecretUtils;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import java.util.List;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.auth.ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN;

/**
 * A migration helper class whose goal is to migrate the secrets that we have
 * stored in our database to Sources.
 */
@Deprecated(forRemoval = true)
@Path(API_INTERNAL)
@RolesAllowed(RBAC_INTERNAL_ADMIN)
public class SourcesSecretsMigrationService {

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    SecretUtils secretUtils;

    /**
     * Grabs the endpoints that contain secrets stored in the database, creates
     * the corresponding secrets in Sources, and updates the endpoints.
     */
    @APIResponse(responseCode = "204", description = "No Content")
    @Deprecated(forRemoval = true)
    @POST
    @Path("/sources-migration")
    @Transactional
    public void migrateEndpointSecretsSources() {
        final List<Endpoint> endpoints = this.endpointRepository.findEndpointWithPropertiesWithStoredSecrets();

        int processedCounter = 0;
        int errorCounter = 0;
        for (final Endpoint endpoint : endpoints) {
            // On failure, don't stop processing the endpoints and keep going,
            // since this is not a destructive operation.
            try {
                this.secretUtils.createSecretsForEndpoint(endpoint);
            } catch (final WebApplicationException e) {
                Log.errorf("[endpoint_id: %s] error when migrating the endpoint secrets: unable to create the secrets for the endpoint: %s", e.getResponse().getEntity());
                errorCounter++;

                continue;
            }

            Log.infof("[endpoint_id: %s] migrated the endpoint's secrets to Sources", endpoint.getId());

            processedCounter++;
        }

        Log.infof("[migrated: %s][errors: %s] migrated endpoints' secrets to Sources", processedCounter, errorCounter);
    }
}
