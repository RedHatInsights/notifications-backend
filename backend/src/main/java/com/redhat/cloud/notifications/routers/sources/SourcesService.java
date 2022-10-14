package com.redhat.cloud.notifications.routers.sources;

import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestPath;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PATCH;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

/**
 * <p>REST Client for the Sources API. The OpenAPI spec is available at:</p>
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
 */
@RegisterClientHeaders(SourcesXRHIDHeaderFactory.class)
@RegisterRestClient(configKey = "sources")
public interface SourcesService {

    /**
     * Get a single secret from Sources. In this case we need to hit the internal endpoint —which is only available for
     * requests coming from inside the cluster— to be able to get the password of these secrets.
     * @param id the secret id.
     * @return a {@link Secret} instance.
     */
    @GET
    @Path("/internal/v2.0/secrets/{id}")
    Secret getById(@RestPath long id);

    /**
     * Create a secret on the Sources backend.
     * @param secret the {@link Secret} to be created.
     * @return the created secret.
     */
    @Path("/api/sources/v3.1/secrets")
    @POST
    Secret create(Secret secret);

    /**
     * Update a secret on the Sources backend.
     * @param secret the {@link Secret} to be updated.
     * @return the updated secret.
     */
    @Path("/api/sources/v3.1/secrets/{id}")
    @PATCH
    Secret update(@RestPath long id, Secret secret);

    /**
     * Delete a secret on the Sources backend.
     * @param id the id of the {@link Secret} to be deleted.
     */
    @DELETE
    @Path("/api/sources/v3.1/secrets/{id}")
    void delete(@RestPath long id);

    /**
     * Throws a runtime exception with the client's response for an easier debugging.
     * @param response the received response from Sources.
     * @return the {@link RuntimeException} to be thrown.
     */
    @ClientExceptionMapper
    static RuntimeException toException(final Response response) {
        final var errMessage = String.format("Sources responded with a %s status: %s", response.getStatus(), response.readEntity(String.class));

        throw new RuntimeException(errMessage);
    }
}
