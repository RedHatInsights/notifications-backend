package com.redhat.cloud.notifications.exports;

import com.redhat.cloud.notifications.Constants;
import io.quarkus.rest.client.reactive.ClientExceptionMapper;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestPath;

import java.util.UUID;

@Path("/app/export/v1")
@RegisterRestClient(configKey = "export-service")
public interface ExportService {

    /**
     * Sends a JSON payload to the export service.
     * @param xRhExportServicePsk the export service PSK required for the authorization.
     * @param exportRequestUuid the {@link UUID} of the export request.
     * @param application the application the export request got requested to.
     * @param resourceUuid the {@link UUID} of the requested resource.
     * @param exportContents the payload of the request.
     */
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{exportRequestUuid}/{application}/{resourceUuid}/upload")
    @POST
    @Retry(maxRetries = 3)
    void uploadJSONExport(
        @HeaderParam(Constants.X_RH_EXPORT_SERVICE_PSK) String xRhExportServicePsk,
        @RestPath UUID exportRequestUuid,
        @RestPath String application,
        @RestPath UUID resourceUuid,
        String exportContents
    );

    /**
     * Sends a CSV payload to the export service.
     * @param xRhExportServicePsk the export service PSK required for the authorization.
     * @param exportRequestUuid the {@link UUID} of the export request.
     * @param application the application the export request got requested to.
     * @param resourceUuid the {@link UUID} of the requested resource.
     * @param exportContents the payload of the request.
     */
    @Consumes("text/csv")
    @Path("/{exportRequestUuid}/{application}/{resourceUuid}/upload")
    @POST
    @Retry(maxRetries = 3)
    void uploadCSVExport(
        @HeaderParam(Constants.X_RH_EXPORT_SERVICE_PSK) String xRhExportServicePsk,
        @RestPath UUID exportRequestUuid,
        @RestPath String application,
        @RestPath UUID resourceUuid,
        String exportContents
    );

    /**
     * Sends an error signal to the export service about a failed export
     * request.
     * @param xRhExportServicePsk the export service PSK required for the authorization.
     * @param exportRequestUuid the {@link UUID} of the export request.
     * @param application the application the export request got requested to.
     * @param resourceUuid the {@link UUID} of the requested resource.
     * @param exportError the error payload.
     */
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{exportRequestUuid}/{application}/{resourceUuid}/error")
    @POST
    @Retry(maxRetries = 3)
    void notifyErrorExport(
        @HeaderParam(Constants.X_RH_EXPORT_SERVICE_PSK) String xRhExportServicePsk,
        @RestPath UUID exportRequestUuid,
        @RestPath String application,
        @RestPath UUID resourceUuid,
        ExportError exportError
    );

    /**
     * Throws a runtime exception with the client's response for an easier
     * debugging.
     * @param response the received response from the Export Service.
     * @return the {@link RuntimeException} to be thrown.
     */
    @ClientExceptionMapper
    static RuntimeException toException(final Response response) {
        final String errMessage = String.format("The export service responded with a %s status: %s", response.getStatus(), response.readEntity(String.class));

        throw new WebApplicationException(errMessage, response);
    }
}
