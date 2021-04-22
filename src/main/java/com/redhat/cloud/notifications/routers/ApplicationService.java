package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EventType;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

@Path("/internal/applications")
public class ApplicationService {

    @Inject
    ApplicationResources appResources;

    @GET
    public Uni<List<Application>> getApplications(@QueryParam("bundleName") String bundleName) {
        // Return configured with types?
        if (bundleName == null || bundleName.isBlank()) {
            throw new BadRequestException("There is no bundle name given. Try ?bundleName=xxx");
        }

        return appResources.getApplications(bundleName).collect().asList().onItem().invoke(applications -> {
            if (applications.size() == 0) {
                throw new NotFoundException();
            }
        });
    }

    @POST
    public Uni<Application> addApplication(@NotNull @Valid Application application) {
        return appResources.createApplication(application);
    }

    @PUT
    @Path("/{id}")
    public Uni<Response> updateBundle(@PathParam("id") UUID id, @NotNull @Valid Application bundle) {
        return appResources.updateApplication(id, bundle)
                .onItem().transform(rowCount -> {
                    if (rowCount == 0) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                    } else {
                        return Response.ok().build();
                    }
                });
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
    @Path("/{id}/eventTypes/{eid}")
    public Uni<Boolean> deleteEventTypeById(@PathParam("eid") UUID endTypeId) {
        return appResources.deleteEventTypeById(endTypeId);
    }
}
