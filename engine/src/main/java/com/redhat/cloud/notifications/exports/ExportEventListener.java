package com.redhat.cloud.notifications.exports;

import com.redhat.cloud.event.apps.exportservice.v1.ExportRequest;
import com.redhat.cloud.event.apps.exportservice.v1.ExportRequestClass;
import com.redhat.cloud.event.apps.exportservice.v1.Format;
import com.redhat.cloud.event.parser.ConsoleCloudEvent;
import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.cloud.event.parser.exceptions.ConsoleCloudEventParsingException;
import com.redhat.cloud.notifications.exports.filters.FilterExtractionException;
import com.redhat.cloud.notifications.exports.transformers.TransformationException;
import com.redhat.cloud.notifications.exports.transformers.UnsupportedFormatException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.Blocking;
import org.apache.http.HttpStatus;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class ExportEventListener {

    public static final String APPLICATION_NAME = "urn:redhat:application:notifications";
    public static final String CE_EXPORT_REQUEST_TYPE = "com.redhat.console.export-service.request";
    public static final String EXPORT_CHANNEL = "export-requests";
    public static final String EXPORT_SERVICE_URN = "urn:redhat:source:console:app:export-service";
    public static final String RESOURCE_TYPE_EVENTS = "urn:redhat:application:notifications:export:events";

    protected static final String EXPORTS_SERVICE_FAILURES_COUNTER = "exports.service.failures";
    protected static final String EXPORTS_SERVICE_SUCCESSES_COUNTER = "exports.service.successes";

    private final ConsoleCloudEventParser consoleCloudEventParser = new ConsoleCloudEventParser();
    private final Pattern subjectUuidExtractPattern = Pattern.compile("^urn:redhat:subject:export-service:request:(?<uuid>.+)$");

    Counter failuresCounter;
    Counter successesCounter;

    @Inject
    EventExporterService eventExporterService;

    @RestClient
    ExportService exportService;

    @Inject
    MeterRegistry meterRegistry;

    @ConfigProperty(name = "export-service.psk")
    String exportServicePsk;

    @PostConstruct
    void postConstruct() {
        this.failuresCounter = this.meterRegistry.counter(EXPORTS_SERVICE_FAILURES_COUNTER);
        this.successesCounter = this.meterRegistry.counter(EXPORTS_SERVICE_SUCCESSES_COUNTER);
    }

    /**
     * Listens to the exports channel, extracts the request's data, and if
     * Notifications is the target application, and the requested resource and
     * formats are supported, the corresponding payload is sent to the export
     * service.
     * @param payload the incoming payload from the channel.
     */
    @Blocking
    @Incoming(EXPORT_CHANNEL)
    public void eventListener(final String payload) {
        // Attempt deserializing the received message as a Cloud Event.
        final ConsoleCloudEvent receivedEvent;
        try {
            receivedEvent = this.consoleCloudEventParser.fromJsonString(payload);
        } catch (final ConsoleCloudEventParsingException e) {
            Log.error("the received payload from the 'export-requests' topic is not a parseable Cloud Event", e);

            this.failuresCounter.increment();

            return;
        }

        // Extract the export request's UUID from the subject.
        final UUID exportRequestUuid;
        try {
            exportRequestUuid = this.extractExportUuidFromSubject(receivedEvent.getSubject());
        } catch (final IllegalArgumentException | IllegalStateException e) {
            Log.errorf(e, "unable to extract the export request's UUID from the subject '%s'. Original Cloud Event: %s", receivedEvent.getSubject(), payload);

            this.failuresCounter.increment();

            return;
        }

        // Make sure that we are attempting to handle an export request.
        if (!this.isAnExportRequest(receivedEvent)) {
            Log.debugf("[export_request_uuid: %s] ignoring received event from the 'export-requests' topic since either it doesn't come from the 'export-service' or it is not of the 'request-export' type: %s", exportRequestUuid, payload);
            return;
        }

        // Also, make sure that it contains the expected payload's structure.
        final Optional<ExportRequest> requestMaybe = receivedEvent.getData(ExportRequest.class);
        if (requestMaybe.isEmpty()) {
            Log.errorf("[export_request_uuid: %s] unable to process the export request: the cloud event's data is empty. Original cloud event: %s", exportRequestUuid, payload);

            this.failuresCounter.increment();

            return;
        }

        // Extract a few bits of information that will be reused over and over.
        final ExportRequest request = requestMaybe.get();
        final ExportRequestClass exportRequest = request.getExportRequest();
        final String application = exportRequest.getApplication();
        final UUID resourceUuid = exportRequest.getUUID();

        // If the application target isn't Notifications, then we can simply
        // skip the payload.
        if (!APPLICATION_NAME.equals(application)) {
            Log.debugf("[export_request_uuid: %s][resource_uuid: %s] export request ignored for Cloud Event since the target application is '%s': %s", exportRequestUuid, resourceUuid, application, payload);
            return;
        }

        final String resource = exportRequest.getResource();

        // Check that we support the requested resource type to export.
        if (!this.isValidResourceType(resource)) {
            Log.errorf("[export_request_uuid: %s][resource_uuid: %s] export request could not be fulfilled: the requested resource type '%s' is not handled. Original cloud event: %s", exportRequestUuid, resourceUuid, resource, payload);

            this.failuresCounter.increment();

            final ExportError exportError = new ExportError(HttpStatus.SC_BAD_REQUEST, "the specified resource type is unsupported by this application");
            this.exportService.notifyErrorExport(this.exportServicePsk, exportRequestUuid, APPLICATION_NAME, resourceUuid, exportError);

            return;
        }

        final Format format = exportRequest.getFormat();
        final String orgId = receivedEvent.getOrgId();

        // Handle exporting the requested resource type.
        final String exportedContents;
        try {
            exportedContents = this.eventExporterService.exportEvents(exportRequest, orgId);
        } catch (FilterExtractionException e) {
            this.failuresCounter.increment();

            final ExportError exportError = new ExportError(HttpStatus.SC_BAD_REQUEST, e.getMessage());
            this.exportService.notifyErrorExport(this.exportServicePsk, exportRequestUuid, APPLICATION_NAME, resourceUuid, exportError);

            return;
        } catch (TransformationException e) {
            Log.errorf(e, "[export_request_uuid: %s][resource_uuid: %s][requested_format: %s] unable to transform events to the requested format: %s", exportRequestUuid, resourceUuid, format, e.getCause().getMessage());

            this.failuresCounter.increment();

            final ExportError exportError = new ExportError(HttpStatus.SC_INTERNAL_SERVER_ERROR, "unable to serialize payload in the correct format");
            this.exportService.notifyErrorExport(this.exportServicePsk, exportRequestUuid, APPLICATION_NAME, resourceUuid, exportError);

            return;
        } catch (UnsupportedFormatException e) {
            Log.debugf("[export_request_uuid: %s][resource_uuid: %s][requested_format: %s] unsupported format", exportRequestUuid, resourceUuid, format);

            final ExportError exportError = new ExportError(
                HttpStatus.SC_BAD_REQUEST,
                String.format("the specified format '%s' is unsupported for the request", format)
            );
            this.exportService.notifyErrorExport(this.exportServicePsk, exportRequestUuid, APPLICATION_NAME, resourceUuid, exportError);

            return;
        }

        // Send the contents to the export service.
        switch (format) {
            case CSV -> this.exportService.uploadCSVExport(this.exportServicePsk, exportRequestUuid, APPLICATION_NAME, resourceUuid, exportedContents);
            case JSON -> this.exportService.uploadJSONExport(this.exportServicePsk, exportRequestUuid, APPLICATION_NAME, resourceUuid, exportedContents);
            default -> {
                Log.debugf("[export_request_uuid: %s][resource_uuid: %s][requested_format: %s] unsupported format", exportRequestUuid, resourceUuid, format);

                final ExportError exportError = new ExportError(
                    HttpStatus.SC_BAD_REQUEST,
                    String.format("the specified format '%s' is unsupported for the request", format)
                );

                this.exportService.notifyErrorExport(this.exportServicePsk, exportRequestUuid, APPLICATION_NAME, resourceUuid, exportError);
            }
        }

        this.successesCounter.increment();
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

    /**
     * Extracts the export request's {@link UUID}, which comes in the Cloud
     * Event's subject. Beware that the Cloud Event's {@link UUID} is not the
     * same as the export request's {@link UUID}, or the resource's
     * {@link UUID}.
     * @param subject the received subject of the Cloud Event.
     * @return the extracted {@link UUID} from the subject.
     */
    UUID extractExportUuidFromSubject(final String subject) {
        final Matcher matcher = this.subjectUuidExtractPattern.matcher(subject);

        // Attempt to find the expected UUID in the subject.
        matcher.find();

        // Attempt to build the UUID. In any unsuccessful case, exceptions will
        // be thrown which have to be
        return UUID.fromString(matcher.group("uuid"));
    }
}
