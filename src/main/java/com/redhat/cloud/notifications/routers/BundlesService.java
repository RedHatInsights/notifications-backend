package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.db.BundleResources;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.UUID;

/**
 * Deal with bundles
 */
@Path("/internal/bundles")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class BundlesService {

    @Inject
    BundleResources bundleResources;

    @GET
    public Multi<Bundle> getBundles() {
        // Return configured with types?
        return bundleResources.getBundles();
    }

    @POST
    public Uni<Bundle> addBundle(@Valid Bundle bundle) {
        // We need to ensure that the x-rh-identity isn't present here
        return bundleResources.createBundle(bundle);
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

}
