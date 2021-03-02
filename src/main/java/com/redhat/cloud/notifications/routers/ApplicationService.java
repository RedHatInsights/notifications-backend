package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EventType;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.UUID;

@Path("/internal/applications")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ApplicationService {

    @Inject
    ApplicationResources appResources;

    @GET
    public Uni<Response> getApplications(@QueryParam("bundleName") String bundleName) {
        // Return configured with types?
        if (bundleName == null || bundleName.isBlank()) {
            return Uni.createFrom().failure(new IllegalArgumentException("there is no bundle name given. Try ?bundleName=xxx"));
        }
        // TODO how can we find out there is nothing to send and return a 404 ?
        return appResources.getApplications(bundleName).collectItems().asList();
    }

    @POST
    public Uni<Application> addApplication(@Valid Application application) {
        // We need to ensure that the x-rh-identity isn't present here
        return appResources.createApplication(application);
    }

    @DELETE
    @Path("/{id}")
    public Uni<Boolean> deleteApplication(@PathParam("id") UUID id) {
        return appResources.deleteApplication(id);
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

    @DELETE
    @Path("/{id}/eventType")
    public Uni<Boolean> deleteEventTypeById(@PathParam("eid") UUID endTypeId) {
        return appResources.deleteEventTypeById(endTypeId);
    }
}
