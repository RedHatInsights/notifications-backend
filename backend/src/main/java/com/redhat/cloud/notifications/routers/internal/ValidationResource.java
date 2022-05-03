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
import javax.persistence.NoResultException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;

@Path(API_INTERNAL + "/validation")
public class ValidationResource {

    private static final String EVENT_TYPE_NOT_FOUND_MSG = "No event type found for [bundle=%s, application=%s, eventType=%s]";

    @Inject
    ApplicationRepository applicationRepository;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/baet")
    public Response validate(@RestQuery String bundle, @RestQuery String application, @RestQuery String eventType) {
        try {
            return convertToOkayResponse(applicationRepository.getEventType(bundle, application, eventType));
        } catch (NoResultException e) {
            return convertToNotFoundResponse(bundle, application, eventType);
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @APIResponses({
            @APIResponse(
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(
                                    implementation = MessageValidationResponse.class,
                                    required = true
                            )
                    ),
                    responseCode = "400"
            ),
            @APIResponse(content = @Content(mediaType = MediaType.TEXT_PLAIN), responseCode = "200"),
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
            return Response.status(400).entity(responseMessage).build();
        }

        return Response.ok().build();
    }

    private Response convertToNotFoundResponse(String bundle, String application, String eventType) {
        return Response.status(404).entity(String.format(EVENT_TYPE_NOT_FOUND_MSG, bundle, application, eventType)).build();
    }

    private Response convertToOkayResponse(EventType e) {
        return Response.ok().build();
    }
}
