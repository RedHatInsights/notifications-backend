package com.redhat.cloud.notifications.exports;

import com.redhat.cloud.event.apps.exportservice.v1.Format;
import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequest;
import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequestClass;
import com.redhat.cloud.event.parser.ConsoleCloudEvent;
import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.cloud.event.parser.exceptions.ConsoleCloudEventParsingException;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.exports.filters.FilterExtractionException;
import com.redhat.cloud.notifications.exports.transformers.TransformationException;
import com.redhat.cloud.notifications.exports.transformers.UnsupportedFormatException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.apache.http.HttpStatus;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.net.URLEncoder;
import java.util.Optional;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

@ApplicationScoped
public class ExportEventListener {

    public static final String APPLICATION_NAME = "urn:redhat:application:notifications";
    public static final String CE_EXPORT_REQUEST_TYPE = "com.redhat.console.export-service.request";
    public static final String EXPORT_CHANNEL = "exportrequests";
    public static final String EXPORT_SERVICE_URN = "urn:redhat:source:console:app:export-service";
    public static final String RESOURCE_TYPE_EVENTS = "urn:redhat:application:notifications:export:events";

    /**
     * The main counter for tracking failures in the integration.
     */
    protected static final String EXPORTS_SERVICE_FAILURES_COUNTER = "exports.service.failures";
    /**
     * The main counter for tracking successful interactions with the export
     * service.
     */
    protected static final String EXPORTS_SERVICE_SUCCESSES_COUNTER = "exports.service.successes";
    /**
     * Represents a failure caused by the export service responding with a 4xx
     * status code.
     */
    protected static final String FAILURE_CLIENT_ERROR = "client.error";
    /**
     * The key used for specifying the reason of the failure.
     */
    protected static final String FAILURE_KEY = "error";
    /**
     * Represents that a Cloud Event didn't come with the expected body.
     */
    protected static final String FAILURE_EMPTY_BODY = "empty.body";
    /**
     * Represents that the incoming payload wasn't a parseable or valid
     * "resource request" Cloud Event.
     */
    protected static final String FAILURE_NON_PARSEABLE_CE = "non.parseable.cloud.event";
    /**
     * Represents a failure caused by the export service responding with a 5xx
     * status code.
     */
    protected static final String FAILURE_SERVER_ERROR = "server.error";
    /**
     * Represents that the incoming filters in the Cloud Event are not properly
     * specified.
     */
    protected static final String FAILURE_UNABLE_EXTRACT_FILTERS = "unable.extract.filters";
    /**
     * Represents that the data from Notifications could not be transformed to
     * the requested format.
     */
    protected static final String FAILURE_UNABLE_TRANSFORM_DATA = "unable.transform.data";
    /**
     * Represents that the requested export format is not supported by
     * Notifications.
     */
    protected static final String FAILURE_UNSUPPORTED_FORMAT = "unsupported.format";
    /**
     * Represents that the specified resource type to be exported is not
     * supported by Notifications.
     */
    protected static final String FAILURE_UNSUPPORTED_RESOURCE_TYPE = "unsupported.resource.type";

    private final ConsoleCloudEventParser consoleCloudEventParser = new ConsoleCloudEventParser();

    // Failure counters.
    Counter clientErrorFailuresCounter;
    Counter emptyBodyFailuresCounter;
    Counter nonParseableFailuresCounter;
    Counter serverErrorFailuresCounter;
    Counter unableExtractFiltersFailuresCounter;
    Counter unableTransformDataFailuresCounter;
    Counter unsupportedFormatFailuresCounter;
    Counter unsupportedResourceTypeFailuresCounter;

    // Success counter.
    Counter successesCounter;

    @Inject
    EngineConfig engineConfig;

    @Inject
    EventExporterService eventExporterService;

    @Inject
    @RestClient
    ExportServicePsk exportServicePsk;

    @Inject
    @RestClient
    ExportServiceOidc exportServiceOidc;

    /**
     * Returns the appropriate export service client based on the feature toggle.
     * @param orgId the organization ID to check the feature toggle for.
     */
    private ExportService getExportServiceClient(String orgId) {
        if (engineConfig.isExportServiceOidcAuthEnabled(orgId)) {
            Log.debug("Using OIDC Export Service client");
            return exportServiceOidc;
        } else {
            Log.debug("Using PSK Export Service client");
            return exportServicePsk;
        }
    }

    @Inject
    MeterRegistry meterRegistry;

    @PostConstruct
    void postConstruct() {
        // Different failures counters.
        this.clientErrorFailuresCounter             = this.meterRegistry.counter(EXPORTS_SERVICE_FAILURES_COUNTER, Tags.of(FAILURE_KEY, FAILURE_CLIENT_ERROR));
        this.emptyBodyFailuresCounter               = this.meterRegistry.counter(EXPORTS_SERVICE_FAILURES_COUNTER, Tags.of(FAILURE_KEY, FAILURE_EMPTY_BODY));
        this.unsupportedFormatFailuresCounter       = this.meterRegistry.counter(EXPORTS_SERVICE_FAILURES_COUNTER, Tags.of(FAILURE_KEY, FAILURE_UNSUPPORTED_FORMAT));
        this.nonParseableFailuresCounter            = this.meterRegistry.counter(EXPORTS_SERVICE_FAILURES_COUNTER, Tags.of(FAILURE_KEY, FAILURE_NON_PARSEABLE_CE));
        this.serverErrorFailuresCounter             = this.meterRegistry.counter(EXPORTS_SERVICE_FAILURES_COUNTER, Tags.of(FAILURE_KEY, FAILURE_SERVER_ERROR));
        this.unableExtractFiltersFailuresCounter    = this.meterRegistry.counter(EXPORTS_SERVICE_FAILURES_COUNTER, Tags.of(FAILURE_KEY, FAILURE_UNABLE_EXTRACT_FILTERS));
        this.unableTransformDataFailuresCounter     = this.meterRegistry.counter(EXPORTS_SERVICE_FAILURES_COUNTER, Tags.of(FAILURE_KEY, FAILURE_UNABLE_TRANSFORM_DATA));
        this.unsupportedResourceTypeFailuresCounter = this.meterRegistry.counter(EXPORTS_SERVICE_FAILURES_COUNTER, Tags.of(FAILURE_KEY, FAILURE_UNSUPPORTED_RESOURCE_TYPE));

        // Success counter.
        this.successesCounter = this.meterRegistry.counter(EXPORTS_SERVICE_SUCCESSES_COUNTER);
    }

    /**
     * Listens to the exports channel, extracts the request's data, and if
     * Notifications is the target application, and the requested resource and
     * formats are supported, the corresponding payload is sent to the export
     * service.
     * @param payload the incoming payload from the channel.
     */
    @ActivateRequestContext
    @Blocking
    @Incoming(EXPORT_CHANNEL)
    public void eventListener(final String payload) {
        try {
            // Attempt deserializing the received message as a Cloud Event.
            final ConsoleCloudEvent receivedEvent;
            try {
                receivedEvent = this.consoleCloudEventParser.fromJsonString(payload);
            } catch (final ConsoleCloudEventParsingException e) {
                Log.error("the received payload from the 'exportrequests' topic is not a parseable Cloud Event", e);

                this.nonParseableFailuresCounter.increment();

                return;
            }

            // Make sure that we are attempting to handle an export request.
            if (!this.isAnExportRequest(receivedEvent)) {
                Log.debugf("ignoring received event from the 'exportrequests' topic since either it doesn't come from the 'export-service' or it is not of the 'request-export' type: %s", payload);
                return;
            }

            // Also, make sure that it contains the expected payload's structure.
            final Optional<ResourceRequest> requestMaybe = receivedEvent.getData(ResourceRequest.class);
            if (requestMaybe.isEmpty()) {
                Log.errorf("unable to process the export request: the cloud event's data is empty. Original cloud event: %s", payload);

                this.emptyBodyFailuresCounter.increment();

                return;
            }

            // Extract a few bits of information that will be reused over and over.
            final ResourceRequest request = requestMaybe.get();
            final ResourceRequestClass resourceRequest = request.getResourceRequest();
            final String application = resourceRequest.getApplication();
            final UUID exportRequestUuid = resourceRequest.getExportRequestUUID();
            final UUID resourceUuid = resourceRequest.getUUID();
            final String orgId = receivedEvent.getOrgId();

            // If the application target isn't Notifications, then we can simply
            // skip the payload.
            if (!APPLICATION_NAME.equals(application)) {
                Log.debugf("[export_request_uuid: %s][resource_uuid: %s] export request ignored for Cloud Event since the target application is '%s': %s", exportRequestUuid, resourceUuid, application, payload);
                return;
            }

            final String resource = resourceRequest.getResource();

            // Check that we support the requested resource type to export.
            if (!this.isValidResourceType(resource)) {
                Log.errorf("[export_request_uuid: %s][resource_uuid: %s] export request could not be fulfilled: the requested resource type '%s' is not handled. Original cloud event: %s", exportRequestUuid, resourceUuid, resource, payload);

                this.unsupportedResourceTypeFailuresCounter.increment();

                final ExportError exportError = new ExportError(HttpStatus.SC_BAD_REQUEST, "the specified resource type is unsupported by this application");
                getExportServiceClient(orgId).notifyErrorExport(exportRequestUuid, APPLICATION_NAME, resourceUuid, exportError);

                return;
            }

            final Format format = resourceRequest.getFormat();

            // Handle exporting the requested resource type.
            final String exportedContents;
            try {
                exportedContents = this.eventExporterService.exportEvents(resourceRequest, orgId);
            } catch (FilterExtractionException e) {
                this.unableExtractFiltersFailuresCounter.increment();

                final ExportError exportError = new ExportError(HttpStatus.SC_BAD_REQUEST, e.getMessage());
                getExportServiceClient(orgId).notifyErrorExport(exportRequestUuid, APPLICATION_NAME, resourceUuid, exportError);

                return;
            } catch (TransformationException e) {
                Log.errorf(e, "[export_request_uuid: %s][resource_uuid: %s][requested_format: %s] unable to transform events to the requested format: %s", exportRequestUuid, resourceUuid, format, e.getCause().getMessage());

                this.unableTransformDataFailuresCounter.increment();

                final ExportError exportError = new ExportError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "unable to serialize payload in the correct format");
                getExportServiceClient(orgId).notifyErrorExport(exportRequestUuid, APPLICATION_NAME, resourceUuid, exportError);

                return;
            } catch (UnsupportedFormatException e) {
                Log.debugf("[export_request_uuid: %s][resource_uuid: %s][requested_format: %s] unsupported format", exportRequestUuid, resourceUuid, format);

                this.unsupportedFormatFailuresCounter.increment();

                final ExportError exportError = new ExportError(
                    HttpStatus.SC_BAD_REQUEST,
                    String.format("the specified format '%s' is unsupported for the request", format)
                );
                getExportServiceClient(orgId).notifyErrorExport(exportRequestUuid, APPLICATION_NAME, resourceUuid, exportError);

                return;
            }

            String encodedAppName = URLEncoder.encode(APPLICATION_NAME, UTF_8);

            // Send the contents to the export service.
            switch (format) {
                case CSV -> getExportServiceClient(orgId).uploadCSVExport(exportRequestUuid, encodedAppName, resourceUuid, exportedContents);
                case JSON -> getExportServiceClient(orgId).uploadJSONExport(exportRequestUuid, encodedAppName, resourceUuid, exportedContents);
                default -> {
                    Log.debugf("[export_request_uuid: %s][resource_uuid: %s][requested_format: %s] unsupported format", exportRequestUuid, resourceUuid, format);

                    final ExportError exportError = new ExportError(
                        HttpStatus.SC_BAD_REQUEST,
                        String.format("the specified format '%s' is unsupported for the request", format)
                    );

                    getExportServiceClient(orgId).notifyErrorExport(exportRequestUuid, APPLICATION_NAME, resourceUuid, exportError);

                    return;
                }
            }

            this.successesCounter.increment();
        } catch (final Exception e) {
            if (e instanceof final WebApplicationException wae) {
                final int statusCode = wae.getResponse().getStatus();
                final Response.Status.Family responseFamily = Response.Status.Family.familyOf(statusCode);

                if (responseFamily == Response.Status.Family.CLIENT_ERROR) {
                    this.clientErrorFailuresCounter.increment();
                }

                if (responseFamily == Response.Status.Family.SERVER_ERROR) {
                    this.serverErrorFailuresCounter.increment();
                }
            }

            Log.errorf(e, "something went wrong when handling a resource request from the export service. Received payload: %s", payload);
        }
    }

    /**
     * Checks if the provided resource type is handleable.
     * @param resourceType the resource type to be checked.
     * @return true if the provided resource type is handleable.
     */
    boolean isValidResourceType(final String resourceType) {
        return RESOURCE_TYPE_EVENTS.equals(resourceType);
    }

    /**
     * Checks if the provided Cloud Event comes from the "export-service" and
     * if the event type is a proper export request.
     * @param cloudEvent the cloud event to check.
     * @return true if the cloud event comes from the export service, and it is
     * of the "export request" type.
     */
    boolean isAnExportRequest(final ConsoleCloudEvent cloudEvent) {
        return EXPORT_SERVICE_URN.equals(cloudEvent.getSource())
            && CE_EXPORT_REQUEST_TYPE.equals(cloudEvent.getType());
    }
}
