package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.db.BundleResources;
import com.redhat.cloud.notifications.db.StatusResources;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.CurrentStatus;
import com.redhat.cloud.notifications.models.EventType;
import io.smallrye.mutiny.Uni;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.RedirectionException;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.INTERNAL;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

@Path(INTERNAL)
public class InternalService {

    @Inject
    BundleResources bundleResources;

    @Inject
    ApplicationResources appResources;

    @Inject
    StatusResources statusResources;

    @GET
    @Path("/")
    public void httpRoot() {
        throw new RedirectionException(Response.Status.OK, URI.create("index.html"));
    }

    @POST
    @Path("/bundles")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Uni<Bundle> createBundle(@NotNull @Valid Bundle bundle) {
        return bundleResources.createBundle(bundle);
    }

    @GET
    @Path("/bundles")
    @Produces(APPLICATION_JSON)
    public Uni<List<Bundle>> getBundles() {
        // Return configured with types?
        return bundleResources.getBundles();
    }

    @GET
    @Path("/bundles/{bundleId}")
    @Produces(APPLICATION_JSON)
    public Uni<Bundle> getBundle(@PathParam("bundleId") UUID bundleId) {
        return bundleResources.getBundle(bundleId)
                .onItem().ifNull().failWith(new NotFoundException());
    }

    @PUT
    @Path("/bundles/{bundleId}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    public Uni<Response> updateBundle(@PathParam("bundleId") UUID bundleId, @NotNull @Valid Bundle bundle) {
        return bundleResources.updateBundle(bundleId, bundle)
                .onItem().transform(rowCount -> {
                    if (rowCount == 0) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                    } else {
                        return Response.ok().build();
                    }
                });
    }

    @DELETE
    @Path("/bundles/{bundleId}")
    @Produces(APPLICATION_JSON)
    public Uni<Boolean> deleteBundle(@PathParam("bundleId") UUID bundleId) {
        return bundleResources.deleteBundle(bundleId);
    }

    @GET
    @Path("/bundles/{bundleId}/applications")
    @Produces(APPLICATION_JSON)
    public Uni<List<Application>> getApplications(@PathParam("bundleId") UUID bundleId) {
        return bundleResources.getApplications(bundleId);
    }

    @POST
    @Path("/applications")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Uni<Application> createApplication(@NotNull @Valid Application app) {
        return appResources.createApp(app);
    }

    @GET
    @Path("/applications/{appId}")
    @Produces(APPLICATION_JSON)
    public Uni<Application> getApplication(@PathParam("appId") UUID appId) {
        return appResources.getApplication(appId)
                .onItem().ifNull().failWith(new NotFoundException());
    }

    @PUT
    @Path("/applications/{appId}")
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    public Uni<Response> updateApplication(@PathParam("appId") UUID appId, @NotNull @Valid Application app) {
        return appResources.updateApplication(appId, app)
                .onItem().transform(rowCount -> {
                    if (rowCount == 0) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                    } else {
                        return Response.ok().build();
                    }
                });
    }

    @DELETE
    @Path("/applications/{appId}")
    @Produces(APPLICATION_JSON)
    public Uni<Boolean> deleteApplication(@PathParam("appId") UUID appId) {
        return appResources.deleteApplication(appId);
    }

    @GET
    @Path("/applications/{appId}/eventTypes")
    @Produces(APPLICATION_JSON)
    public Uni<List<EventType>> getEventTypes(@PathParam("appId") UUID appId) {
        return appResources.getEventTypes(appId);
    }

    @POST
    @Path("/eventTypes")
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public Uni<EventType> createEventType(@NotNull @Valid EventType eventType) {
        return appResources.createEventType(eventType);
    }

    @DELETE
    @Path("/eventTypes/{eventTypeId}")
    @Produces(APPLICATION_JSON)
    public Uni<Boolean> deleteEventType(@PathParam("eventTypeId") UUID eventTypeId) {
        return appResources.deleteEventTypeById(eventTypeId);
    }

    @PUT
    @Path("/status")
    @Consumes(APPLICATION_JSON)
    public Uni<Void> setCurrentStatus(@NotNull @Valid CurrentStatus status) {
        return statusResources.setCurrentStatus(status);
    }
}
