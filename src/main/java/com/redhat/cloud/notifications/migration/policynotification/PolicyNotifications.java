package com.redhat.cloud.notifications.migration.policynotification;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.List;

@Path("/")
@RegisterRestClient(configKey = "policynotifications")
@ApplicationScoped
public interface PolicyNotifications {

    @GET
    @Path("/endpoints/email/subscriptions")
    @Consumes("application/json")
    @Produces("application/json")
    Uni<List<PoliciesEmailSubscription>> getSubscriptions();
}
