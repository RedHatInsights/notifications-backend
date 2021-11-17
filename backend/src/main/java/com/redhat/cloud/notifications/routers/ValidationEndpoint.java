package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.models.EventType;
import io.smallrye.mutiny.Uni;
import org.jboss.resteasy.reactive.RestQuery;

import javax.inject.Inject;
import javax.persistence.NoResultException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static com.redhat.cloud.notifications.Constants.INTERNAL;

@Path(INTERNAL + "/validation")
public class ValidationEndpoint {

    private static final String EVENT_TYPE_NOT_FOUND_MSG = "No event type found for [bundle=%s, application=%s, eventType=%s]";

    @Inject
    ApplicationResources appResources;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/baet")
    public Uni<Response> validate(@RestQuery String bundle, @RestQuery String application, @RestQuery String eventType) {
        return appResources.getEventType(bundle, application, eventType)
                .onItem()
                .transform(this::convertToOkayResponse)
                .onFailure(NoResultException.class).recoverWithItem(t -> convertToNotFoundResponse(bundle, application, eventType));
    }

    private Response convertToNotFoundResponse(String bundle, String application, String eventType) {
        return Response.status(404).entity(String.format(EVENT_TYPE_NOT_FOUND_MSG, bundle, application, eventType)).build();
    }

    private Response convertToOkayResponse(EventType e) {
        return Response.ok().build();
    }
}
