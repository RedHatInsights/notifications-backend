package com.redhat.cloud.notifications.routers.internal;

import com.networknt.schema.ValidationMessage;
import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.cloud.event.parser.exceptions.ConsoleCloudEventValidationException;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.x509CertificateRepository;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.ParsingException;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.X509Certificate;
import com.redhat.cloud.notifications.routers.internal.models.MessageValidationResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.jboss.resteasy.reactive.RestQuery;
import java.util.Optional;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;

@Path(API_INTERNAL + "/validation")
public class ValidationResource {

    private static final String EVENT_TYPE_NOT_FOUND_MSG = "No event type found for [bundle=%s, application=%s, eventType=%s]";
    private static final String CERTIFICATE_NOT_AUTHORIZED_MSG = "This certificate is not authorized for [bundle=%s, application=%s]";

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    x509CertificateRepository x509CertificateRepository;

    ConsoleCloudEventParser consoleCloudEventParser = new ConsoleCloudEventParser();

    @GET
    @Path("/baet")
    @Produces(TEXT_PLAIN)
    @APIResponses({
        @APIResponse(responseCode = "200", description = "The bundle, application and event type triplet is valid"),
        @APIResponse(responseCode = "400", description = "The bundle, application and event type triplet is unknown")
    })
    public Response validate(@RestQuery String bundle, @RestQuery String application, @RestQuery String eventType) {
        EventType foundEventType = applicationRepository.getEventType(bundle, application, eventType);
        if (foundEventType == null) {
            String message = String.format(EVENT_TYPE_NOT_FOUND_MSG, bundle, application, eventType);
            return Response.status(BAD_REQUEST).entity(message).build();
        } else {
            return Response.ok().build();
        }
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @APIResponses({
        @APIResponse(
                content = @Content(
                        mediaType = APPLICATION_JSON,
                        schema = @Schema(
                                implementation = MessageValidationResponse.class,
                                required = true
                        )
                ),
                responseCode = "400"
            ),
        @APIResponse(content = @Content(mediaType = TEXT_PLAIN), responseCode = "200"),
    })
    @Path("/message")
    public Response validateMessage(String action) {
        try {
            Parser.validate(action);
        } catch (ParsingException parsingException) {
            MessageValidationResponse responseMessage = new MessageValidationResponse();
            for (ValidationMessage message : parsingException.getValidationMessages()) {
                responseMessage.addError(message.getPath(), message.getMessage());
            }
            return Response.status(BAD_REQUEST).entity(responseMessage).build();
        }

        return Response.ok().build();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @APIResponses({
        @APIResponse(
            content = @Content(
                mediaType = APPLICATION_JSON,
                schema = @Schema(
                    implementation = MessageValidationResponse.class,
                    required = true
                )
            ),
            responseCode = "400"
            ),
        @APIResponse(content = @Content(mediaType = TEXT_PLAIN), responseCode = "200"),
    })
    @Path("/console-cloud-event")
    public Response validateConsoleCloudEvent(String action) {
        try {
            consoleCloudEventParser.validate(action);
        } catch (ConsoleCloudEventValidationException exception) {
            MessageValidationResponse responseMessage = new MessageValidationResponse();
            for (ValidationMessage message : exception.getValidationMessages()) {
                responseMessage.addError(message.getPath(), message.getMessage());
            }
            return Response.status(BAD_REQUEST).entity(responseMessage).build();
        }

        return Response.ok().build();
    }

    @GET
    @Path("/certificate")
    @Produces(APPLICATION_JSON)
    @APIResponses({
        @APIResponse(responseCode = "200", description = "This certificate is valid this bundle and application"),
        @APIResponse(responseCode = "401", description = "This certificate is not valid this bundle and application")
    })
    public X509Certificate validateCertificateAccordingBundleAndApp(@RestQuery String bundle, @RestQuery String application, @RestQuery String certificateSubjectDn) {
        Optional<X509Certificate> gatewayCertificate = x509CertificateRepository.findCertificate(bundle, application, certificateSubjectDn);
        if (gatewayCertificate.isEmpty()) {
            String message = String.format(CERTIFICATE_NOT_AUTHORIZED_MSG, bundle, application);
            throw new NotAuthorizedException(message, Response.status(UNAUTHORIZED).build());
        } else {
            return gatewayCertificate.get();
        }
    }
}
