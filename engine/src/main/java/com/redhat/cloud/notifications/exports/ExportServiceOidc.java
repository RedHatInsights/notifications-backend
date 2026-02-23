package com.redhat.cloud.notifications.exports;

import io.quarkus.oidc.client.filter.OidcClientFilter;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * <p>OIDC-enabled REST Client for the Export Service API.</p>
 *
 * <p>The authentication to the Export Service works by using OIDC client credentials to obtain
 * a bearer token that will be sent in the Authorization header.</p>
 */
@RegisterRestClient(configKey = "export-service-oidc")
@OidcClientFilter
public interface ExportServiceOidc extends ExportService {

    @ClientExceptionMapper
    static RuntimeException toException(final Response response) {
        final String errMessage = String.format("The export service responded with a %s status: %s", response.getStatus(), response.readEntity(String.class));

        throw new WebApplicationException(errMessage, response);
    }
}
