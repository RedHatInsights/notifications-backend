package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.models.EventType;
import io.smallrye.mutiny.Uni;
import org.jboss.resteasy.reactive.RestQuery;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@Path("/validation")
public class ValidationEndpoint {

    @Inject
    ApplicationResources appResources;

    @GET
    public Uni<Response> validate(@RestQuery String bundle, @RestQuery String application, @RestQuery String eventType) {
        return appResources.getEventType(bundle, application, eventType)
                .onItem()
                .transform(this::isValid);
    }

    private Response isValid(EventType e) {
        if (e != null) {
            return Response.ok().build();
        } else {
            return Response.status(404).entity("did not find triple of bundle").build();
        }
    }
}
