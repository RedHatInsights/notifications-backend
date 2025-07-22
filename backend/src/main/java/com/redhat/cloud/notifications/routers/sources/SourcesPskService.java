package com.redhat.cloud.notifications.routers.sources;

import com.redhat.cloud.notifications.Constants;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestPath;

/**
 * <p>PSK-based REST Client for the Sources API. The OpenAPI spec is available at:</p>
 *
 * <ul>
 *  <li><a href="https://console.stage.redhat.com/docs/api/sources/v3.1">OpenApi v3.1 in the stage environment.</a></li>
 *  <li><a href="https://console.redhat.com/docs/api/sources/v3.1">OpenApi v3.1 in the production environment.</a></li>
 *  <li><a href="https://github.com/RedHatInsights/sources-api-go/blob/main/public/openapi-3-v3.1.json">OpenApi v3.1 JSON file on GitHub.</a></li>
 * </ul>
 *
 * <p>Please be aware of the following:</p>
 *
 * <ul>
 *     <li>If sources is using a database backend, only the {@link Secret#password} field will get encrypted.</li>
 *     <li>On the other hand, if sources is using the AWS Secrets Manager, then the whole secret will get encrypted.</li>
 * </ul>
 *
 * <p>The authentication to Sources works by using a service-to-service PSK that will be sent in the header that
 * Sources expects. At the same time, the organization id will be sent so that Sources knows to which tenants belongs
 * the operation that is going to be performed.</p>
 */
@RegisterRestClient(configKey = "sources")
public interface SourcesPskService {

    /**
     * Get a single secret from Sources. In this case we need to hit the internal endpoint —which is only available for
     * requests coming from inside the cluster— to be able to get the password of these secrets.
     * @param xRhSourcesOrgId the organization id related to this operation for the tenant identification.
     * @param xRhSourcesPsk the sources PSK required for the authorization.
     * @param id the secret id.
     * @return a {@link Secret} instance.
     */
    @GET
    @Path("/internal/v2.0/secrets/{id}")
    @Retry(maxRetries = 3)
    Secret getById(
        @HeaderParam(Constants.X_RH_SOURCES_ORG_ID) @NotBlank String xRhSourcesOrgId,
        @HeaderParam(Constants.X_RH_SOURCES_PSK) @NotBlank String xRhSourcesPsk,
        @RestPath long id
    );

    /**
     * Create a secret on the Sources backend.
     * @param xRhSourcesOrgId the organization id related to this operation for the tenant identification.
     * @param xRhSourcesPsk the sources PSK required for the authorization.
     * @param secret the {@link Secret} to be created.
     * @return the created secret.
     */
    @Path("/api/sources/v3.1/secrets")
    @POST
    @Retry(maxRetries = 3)
    Secret create(
        @HeaderParam(Constants.X_RH_SOURCES_ORG_ID) @NotBlank String xRhSourcesOrgId,
        @HeaderParam(Constants.X_RH_SOURCES_PSK) @NotBlank String xRhSourcesPsk,
        Secret secret
    );

    /**
     * Update a secret on the Sources backend.
     * @param xRhSourcesOrgId the organization id related to this operation for the tenant identification.
     * @param xRhSourcesPsk the sources PSK required for the authorization.
     * @param secret the {@link Secret} to be updated.
     * @return the updated secret.
     */
    @Path("/api/sources/v3.1/secrets/{id}")
    @PATCH
    @Retry(maxRetries = 3)
    Secret update(
        @HeaderParam(Constants.X_RH_SOURCES_ORG_ID) @NotBlank String xRhSourcesOrgId,
        @HeaderParam(Constants.X_RH_SOURCES_PSK) String xRhSourcesPsk,
        @RestPath long id,
        Secret secret
    );

    /**
     * Delete a secret on the Sources backend.
     * @param xRhSourcesOrgId the organization id related to this operation for the tenant identification.
     * @param xRhSourcesPsk the sources PSK required for the authorization.
     * @param id the id of the {@link Secret} to be deleted.
     */
    @DELETE
    @Path("/api/sources/v3.1/secrets/{id}")
    @Retry(maxRetries = 3)
    void delete(
        @HeaderParam(Constants.X_RH_SOURCES_ORG_ID) @NotBlank String xRhSourcesOrgId,
        @HeaderParam(Constants.X_RH_SOURCES_PSK) String xRhSourcesPsk,
        @RestPath long id
    );

    /**
     * Throws a runtime exception with the client's response for an easier debugging.
     * @param response the received response from Sources.
     * @return the {@link RuntimeException} to be thrown.
     */
    @ClientExceptionMapper
    static RuntimeException toException(final Response response) {
        final var errMessage = String.format("Sources responded with a %s status: %s", response.getStatus(), response.readEntity(String.class));

        throw new WebApplicationException(errMessage, response);
    }
}
