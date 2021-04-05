package com.redhat.cloud.notifications.migration.policynotification;

import io.smallrye.mutiny.Uni;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/internal/policies-notifications")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PoliciesMigrationService {

    @Path("/migrate")
    @GET
    public Uni<Void> migrate() {
        // The migration should be over before the behavior groups are merged into master.
        return Uni.createFrom().voidItem();
    }
}
