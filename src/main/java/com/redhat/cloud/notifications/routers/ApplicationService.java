package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EventType;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.UUID;

@Path("/internal/applications")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ApplicationService {

    @Inject
    ApplicationResources appResources;

    @GET
    public Multi<Application> getApplications() {
        // Return configured with types?
        return appResources.getApplications();
    }

    @POST
    public Uni<Application> addApplication(@Valid Application application) {
        // We need to ensure that the x-rh-identity isn't present here
        return appResources.createApplication(application);
    }

    @GET
    @Path("/{id}")
    public Uni<Application> getApplication(@PathParam("id") UUID id) {
        return appResources.getApplication(id);
    }

    @POST
    @Path("/{id}/eventTypes")
    public Uni<EventType> addEventType(@PathParam("id") UUID applicationId, @Valid EventType eventType) {
        return appResources.addEventTypeToApplication(applicationId, eventType);
    }

    @GET
    @Path("/{id}/eventTypes")
    public Multi<EventType> getEventTypes(@PathParam("id") UUID applicationId) {
        return appResources.getEventTypes(applicationId);
    }
}
