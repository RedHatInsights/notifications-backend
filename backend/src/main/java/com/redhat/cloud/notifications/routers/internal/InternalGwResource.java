package com.redhat.cloud.notifications.routers.internal;


import com.redhat.cloud.notifications.db.repositories.SubscriptionRepository;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import org.jboss.resteasy.reactive.RestQuery;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(API_INTERNAL + "/gw")
public class InternalGwResource {

    final SubscriptionRepository subscriptionRepository;

    public InternalGwResource(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * Search org ids grouped by even type when at least one subscriber to instant email Notifications
     *
     * @param bundleName Application bundle name
     * @param applicationName Application name
     * @param eventTypeNames Event types
     * @return list of org id grouped by event types
     */
    @GET
    @Path("/subscriptions/{bundleName}/{applicationName}")
    @Produces(APPLICATION_JSON)
    public Map<String, List<String>> getOrgSubscriptions(@PathParam("bundleName") String bundleName, @PathParam("applicationName") String applicationName, @RestQuery List<String> eventTypeNames) {
        return subscriptionRepository.getOrgSubscriptionsPerEventType(bundleName, applicationName, eventTypeNames);
    }
}
