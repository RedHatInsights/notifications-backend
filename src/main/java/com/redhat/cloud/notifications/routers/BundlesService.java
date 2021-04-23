package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.db.BehaviorGroupResources;
import com.redhat.cloud.notifications.db.BundleResources;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import java.util.UUID;

/**
 * Deal with bundles
 */
@Path("/internal/bundles")
public class BundlesService {

    @Inject
    BundleResources bundleResources;

    @Inject
    BehaviorGroupResources behaviorGroupResources;

    @GET
    public Multi<Bundle> getBundles() {
        // Return configured with types?
        return bundleResources.getBundles();
    }

    @POST
    public Uni<Bundle> addBundle(@NotNull @Valid Bundle bundle) {
        return bundleResources.createBundle(bundle);
    }

    @PUT
    @Path("/{id}")
    public Uni<Response> updateBundle(@PathParam("id") UUID id, @NotNull @Valid Bundle bundle) {
        return bundleResources.updateBundle(id, bundle)
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
    public Uni<Boolean> deleteBundle(@PathParam("id") UUID id) {
        return bundleResources.deleteBundle(id);
    }

    @GET
    @Path("/{id}")
    public Uni<Bundle> getBundle(@PathParam("id") UUID id) {
        Uni<Bundle> bundleUni = bundleResources.getBundle(id);

        return bundleUni.onItem().ifNull().failWith(new NotFoundException(id.toString()));
    }

    @POST
    @Path("/{id}/applications")
    public Uni<Application> addApplication(@PathParam("id") UUID bundleId, @Valid Application application) {
        return bundleResources.addApplicationToBundle(bundleId, application);
    }

    @GET
    @Path("/{id}/applications")
    public Multi<Application> getApplications(@PathParam("id") UUID bundleId) {
        return bundleResources.getApplications(bundleId);
    }

    @PUT
    @Path("/{id}/behaviorGroups/{behaviorGroupId}/default")
    public Uni<Boolean> setDefaultBehaviorGroup(@PathParam("id") UUID bundleId, @PathParam("behaviorGroupId") UUID behaviorGroupId) {
        return behaviorGroupResources.setDefaultBehaviorGroup(bundleId, behaviorGroupId);
    }
}
