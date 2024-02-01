package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.SourcesSecretable;
import com.redhat.cloud.notifications.routers.sources.SecretUtils;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

    /**
     * A map of processed endpoints to be kept in case the secrets need to be
     * deleted from Sources if any error occurs on our side.
     */
    final Map<UUID, Endpoint> processedEndpoints = new HashMap<>();

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
    public void migrateEndpointSecretsSources() {
        final Map<UUID, String> eligibleEndpoints = this.endpointRepository.findEndpointWithPropertiesWithStoredSecrets();

        int processedCounter = 0;
        int errorCounter = 0;
        for (final Map.Entry<UUID, String> eligibleEndpoint : eligibleEndpoints.entrySet()) {
            // On failure, don't stop processing the endpoints and keep going,
            // since this is not a destructive operation.
            try {
                migrateEndpoint(eligibleEndpoint);
            } catch (final Exception e) {
                if (this.extractConstraintViolationException(e) instanceof ConstraintViolationException cve) {
                    Log.errorf("[endpoint_id: %s] error when migrating the endpoint secrets: a constraint violation exception was raised: %s. Constraints list: %s", eligibleEndpoint.getKey(), cve.getMessage(), cve.getConstraintViolations());

                    // Delete the secrets for the processed endpoint, since an
                    // error occurred.
                    final Endpoint endpoint = this.processedEndpoints.get(eligibleEndpoint.getKey());
                    final EndpointProperties endpointProperties = endpoint.getProperties();
                    if (endpointProperties instanceof SourcesSecretable properties) {
                        try {
                            this.secretUtils.deleteSecretsForEndpoint(endpoint);
                        } catch (final Exception deleteException) {
                            Log.errorf("[endpoint_id: %s][basic_auth_sources_secret_id: %s][secret_token_sources_secret_id: %s] unable to remove the endpoint secrets from Sources after hitting an error while updating the endpoint: %s", endpoint.getId(), properties.getBasicAuthenticationSourcesId(), properties.getSecretTokenSourcesId(), e.getMessage());
                        }
                    } else {
                        Log.errorf("[endpoint_id: %s] the properties for the endpoint don't seem to be Sources secretable", endpoint.getId());
                    }
                } else if (e instanceof NotFoundException) {
                    Log.errorf("[endpoint_id: %s] error when migrating the endpoint secrets: unable to find endpoint in the database", eligibleEndpoint.getKey());
                    // The endpoint was not found, so we need to continue the
                    // loop since there is nothing to delete.
                    continue;
                } else if (e instanceof WebApplicationException wae) {
                    Log.errorf("[endpoint_id: %s] error when migrating the endpoint secrets: unable to create the secrets for the endpoint: %s", eligibleEndpoint.getKey(), wae.getResponse().getEntity());
                } else {
                    Log.errorf("[endpoint_id: %s] error when migrating the endpoint secrets: unable to create the secrets for the endpoint: %s", eligibleEndpoint.getKey(), e.getMessage());
                }

                this.endpointRepository.disableEndpoint(eligibleEndpoint.getValue(), eligibleEndpoint.getKey());
                Log.infof("[endpoint_id: %s] endpoint disabled");

                errorCounter++;

                continue;
            }

            Log.infof("[endpoint_id: %s] migrated the endpoint's secrets to Sources", eligibleEndpoint.getKey());

            this.processedEndpoints.remove(eligibleEndpoint.getKey());

            processedCounter++;
        }

        Log.infof("[migrated: %s][errors: %s] migrated endpoints' secrets to Sources", processedCounter, errorCounter);
    }

    /**
     * Fetches the given endpoint and creates the corresponding secrets in
     * Sources.
     * @param eligibleEndpoint the target endpoint to migrate.
     */
    @Transactional
    public void migrateEndpoint(final Map.Entry<UUID, String> eligibleEndpoint) {
        final Endpoint endpoint = this.endpointRepository.getEndpoint(eligibleEndpoint.getValue(), eligibleEndpoint.getKey());
        if (endpoint == null) {
            throw new NotFoundException();
        }

        // Pick up errors to delete the secrets
        this.secretUtils.createSecretsForEndpointLegacy(endpoint);

        this.processedEndpoints.put(endpoint.getId(), endpoint);
    }

    // There are three levels deep of exceptions until we reach the
    // ConstraintViolation one:

    //
    // That is why we attempt to catch them all, just in case...

    /**
     * Extracts the constraint violation exception up to three levels deep in
     * the chain. The reason is that There are three levels deep of exceptions
     * until we reach the target:
     * ┌─ io.quarkus.arc.ArcUndeclaredThrowableException: Error invoking subclass method
     * ├───── javax.transaction.RollbackException: ARJUNA016053: Could not commit transaction.
     * └───────── javax.validation.ConstraintViolationException
     * This is why we attempt to get the CVE on each level.
     * @param e the exception to attempt to get the Constraint Violation
     *          Exception from.
     * @return a {@link ConstraintViolationException} if the given exception is
     * of that type, or the original exception if it wasn't.
     */
    private Exception extractConstraintViolationException(final Exception e) {
        if (e instanceof ConstraintViolationException cve) {
            return cve;
        } else if (e.getCause() instanceof ConstraintViolationException cve) {
            return cve;
        } else if (e.getCause().getCause() instanceof ConstraintViolationException cve) {
            return cve;
        } else {
            return e;
        }
    }
}
