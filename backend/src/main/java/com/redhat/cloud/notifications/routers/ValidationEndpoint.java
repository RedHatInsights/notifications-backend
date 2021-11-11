package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.models.EventType;
import io.smallrye.mutiny.Uni;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

@Path("/validation")
public class ValidationEndpoint {

    @Inject
    ApplicationResources appResources;

    @GET
    public Uni<Response> validate(@PathParam("bundle") String bundleName, @PathParam("application") String applicationName, @PathParam("eventtype") String eventType) {
        return appResources.getEventType(bundleName, applicationName, eventType)
                .onItem()
                .transform(this::isValid)
                .onFailure().recoverWithItem(throwable -> Response.status(404).build());
    }

    private Response isValid(EventType e) {
        if (e == null) {
            return Response.ok().build();
        } else {
            return Response.status(404).build();
        }
    }
}
