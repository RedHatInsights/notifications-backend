package com.redhat.cloud.notifications.exports;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.jboss.resteasy.reactive.RestPath;

import java.util.UUID;

@Path("/app/export/v1")
public interface ExportService {

    /**
     * Sends a JSON payload to the export service.
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
        @RestPath UUID exportRequestUuid,
        @RestPath String application,
        @RestPath UUID resourceUuid,
        String exportContents
    );

    /**
     * Sends a CSV payload to the export service.
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
        @RestPath UUID exportRequestUuid,
        @RestPath String application,
        @RestPath UUID resourceUuid,
        String exportContents
    );

    /**
     * Sends an error signal to the export service about a failed export
     * request.
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
        @RestPath UUID exportRequestUuid,
        @RestPath String application,
        @RestPath UUID resourceUuid,
        ExportError exportError
    );
}
