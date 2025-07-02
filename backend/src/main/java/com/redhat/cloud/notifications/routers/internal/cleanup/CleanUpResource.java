package com.redhat.cloud.notifications.routers.internal.cleanup;

import com.redhat.cloud.notifications.auth.ConsoleIdentityProvider;
import com.redhat.cloud.notifications.db.repositories.DrawerNotificationRepository;
import com.redhat.cloud.notifications.db.repositories.EventRepository;
import com.redhat.cloud.notifications.models.Environment;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;

@RolesAllowed(ConsoleIdentityProvider.RBAC_INTERNAL_ADMIN)
@Path(API_INTERNAL + "/cleanup")
public class CleanUpResource {

    @Inject
    DrawerNotificationRepository drawerNotificationRepository;

    @Inject
    EventRepository eventRepository;

    @Inject
    Environment environment;

    @POST
    @Path("/drawer")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response cleanUp(int limit) {
        if (environment.isStage() || environment.isLocal()) {
            drawerNotificationRepository.cleanupIntegrations(limit);
            return Response.ok().build();
        }
        return Response.status(Response.Status.FORBIDDEN).build();
    }

    @POST
    @Path("/inventory_events")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response cleanUpInventoryEvent(int limit) {
        if (environment.isStage() || environment.isLocal()) {
            eventRepository.cleanupInventoryEvents(limit);
            return Response.ok().build();
        }
        return Response.status(Response.Status.FORBIDDEN).build();
    }
}
