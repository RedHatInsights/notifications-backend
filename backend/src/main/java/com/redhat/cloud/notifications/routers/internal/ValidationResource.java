package com.redhat.cloud.notifications.routers.internal;

import com.networknt.schema.ValidationMessage;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.ParsingException;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.routers.internal.models.MessageValidationResponse;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.jboss.resteasy.reactive.RestQuery;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

@Path(API_INTERNAL + "/validation")
public class ValidationResource {

    private static final String EVENT_TYPE_NOT_FOUND_MSG = "No event type found for [bundle=%s, application=%s, eventType=%s]";

    @Inject
    ApplicationRepository applicationRepository;

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
}
