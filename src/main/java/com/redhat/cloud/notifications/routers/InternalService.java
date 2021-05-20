package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.db.BehaviorGroupResources;
import com.redhat.cloud.notifications.db.BundleResources;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
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
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

@Path("/internal")
@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
public class InternalService {

    @Inject
    BundleResources bundleResources;

    @Inject
    BehaviorGroupResources behaviorGroupResources;

    @Inject
    ApplicationResources appResources;

    @POST
    @Path("/bundles")
    public Uni<Bundle> createBundle(@NotNull @Valid Bundle bundle) {
        return bundleResources.createBundle(bundle);
    }

    @GET
    @Path("/bundles")
    public Uni<List<Bundle>> getBundles() {
        // Return configured with types?
        return bundleResources.getBundles();
    }

    @GET
    @Path("/bundles/{bundleId}")
    public Uni<Bundle> getBundle(@PathParam("bundleId") UUID bundleId) {
        return bundleResources.getBundle(bundleId)
                .onItem().ifNull().failWith(new NotFoundException());
    }

    @PUT
    @Path("/bundles/{bundleId}")
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

    @PUT
    @Path("/bundles/{bundleId}/behaviorGroups/{behaviorGroupId}/default")
    @Produces(TEXT_PLAIN)
    public Uni<Response> setDefaultBehaviorGroup(@PathParam("bundleId") UUID bundleId, @PathParam("behaviorGroupId") UUID behaviorGroupId) {
        return behaviorGroupResources.setDefaultBehaviorGroup(bundleId, behaviorGroupId)
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
    public Uni<Boolean> deleteBundle(@PathParam("bundleId") UUID bundleId) {
        return bundleResources.deleteBundle(bundleId);
    }

    @GET
    @Path("/bundles/{bundleId}/applications")
    public Uni<List<Application>> getApplications(@PathParam("bundleId") UUID bundleId) {
        return bundleResources.getApplications(bundleId);
    }

    @POST
    @Path("/applications")
    public Uni<Application> createApplication(@NotNull @Valid Application app) {
        return appResources.createApp(app);
    }

    @GET
    @Path("/applications/{appId}")
    public Uni<Application> getApplication(@PathParam("appId") UUID appId) {
        return appResources.getApplication(appId)
                .onItem().ifNull().failWith(new NotFoundException());
    }

    @PUT
    @Path("/applications/{appId}")
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
    public Uni<Boolean> deleteApplication(@PathParam("appId") UUID appId) {
        return appResources.deleteApplication(appId);
    }

    @GET
    @Path("/applications/{appId}/eventTypes")
    public Uni<List<EventType>> getEventTypes(@PathParam("appId") UUID appId) {
        return appResources.getEventTypes(appId);
    }

    @POST
    @Path("/eventTypes")
    public Uni<EventType> createEventType(@NotNull @Valid EventType eventType) {
        return appResources.createEventType(eventType);
    }

    @DELETE
    @Path("/eventTypes/{eventTypeId}")
    public Uni<Boolean> deleteEventType(@PathParam("eventTypeId") UUID eventTypeId) {
        return appResources.deleteEventTypeById(eventTypeId);
    }
}
