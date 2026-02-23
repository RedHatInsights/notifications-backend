package com.redhat.cloud.notifications.exports;

import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * <p>PSK-based REST Client for the Export Service API.</p>
 *
 * <p>The authentication to the Export Service works by using a pre-shared key (PSK) that is sent
 * in the {@code x-rh-exports-psk} header. The PSK header is automatically added by
 * {@link ExportServicePskRequestFilter}.</p>
 */
@RegisterRestClient(configKey = "export-service")
@RegisterProvider(ExportServicePskRequestFilter.class)
public interface ExportServicePsk extends ExportService {

    @ClientExceptionMapper
    static RuntimeException toException(final Response response) {
        final String errMessage = String.format("The export service responded with a %s status: %s", response.getStatus(), response.readEntity(String.class));

        throw new WebApplicationException(errMessage, response);
    }
}
