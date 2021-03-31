package com.redhat.cloud.notifications.migration.policynotification;

import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.models.EmailSubscriptionAttributes;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

@Path("/internal/policies-notifications")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PoliciesMigrationService {

    static final String BUNDLE = "rhel";
    static final String APPLICATION = "policies";
    static final String EVENT_TYPE = "policy-triggered";
    static final String DAILY_EMAIL_TYPE = "policies-daily-mail";
    static final String INSTANT_EMAIL_TYPE = "policies-instant-mail";

    @Inject
    @RestClient
    PolicyNotifications policyNotifications;

    @Inject
    EndpointEmailSubscriptionResources emailSubscriptionResources;

    @Inject
    EndpointResources endpointResources;

    @Inject
    ApplicationResources applicationResources;

    static class MigrateResponse {
        public AtomicInteger eventTypesMigrated = new AtomicInteger();
        public AtomicInteger accountsMigrated = new AtomicInteger();
    }

    @Path("/migrate")
    @GET
    public Uni<MigrateResponse> migrate() {
        final MigrateResponse response = new MigrateResponse();
        return applicationResources.getEventType(BUNDLE, APPLICATION, EVENT_TYPE)
        .onItem().transformToMulti(eventType -> {
            return policyNotifications.getSubscriptions()
                    .onItem().transformToMulti(policiesEmailSubscriptions -> Multi.createFrom().iterable(policiesEmailSubscriptions))
                    .onItem().transformToMulti(pes -> emailSubscriptionResources.subscribe(
                    pes.accountId,
                    pes.userId,
                    BUNDLE,
                    APPLICATION,
                    fromPoliciesEventType(pes.eventType.toLowerCase())
            )
                    .invoke(() -> response.eventTypesMigrated.incrementAndGet())
                    .onItem().transform(subscribed -> pes.accountId).toMulti())
                    .concatenate().collectItems().in(HashSet::new, HashSet::add)
                    .onItem().transformToMulti(accountIds -> Multi.createFrom().iterable(accountIds))
                    .onItem().castTo(String.class)
                    .onItem().transformToMultiAndConcatenate(accountId -> {
                        response.accountsMigrated.incrementAndGet();
                        // Remove existing endpoints
                        return endpointResources.getLinkedEndpoints(accountId, eventType.getId(), new Query())
                        .onItem().transformToMultiAndConcatenate(endpoint -> endpointResources.unlinkEndpoint(accountId, endpoint.getId(), eventType.getId()).toMulti())
                        // Create EmailAction (Endpoint) and add it to the eventType
                        .collectItems().asList()
                        .onItem().transformToUni(_removed -> {
                            Endpoint endpoint = new Endpoint();
                            endpoint.setAccountId(accountId);
                            endpoint.setEnabled(true);
                            endpoint.setDescription("");
                            endpoint.setName("email-endpoint");
                            endpoint.setType(EndpointType.EMAIL_SUBSCRIPTION);
                            endpoint.setProperties(new EmailSubscriptionAttributes());
                            return endpointResources.createEndpoint(endpoint);
                        })
                        .onItem().transformToUni(endpoint -> endpointResources.linkEndpoint(accountId, endpoint.getId(), eventType.getId())).toMulti();
                    });
        }).collectItems().asList().onItem().transform(objects -> response);
    }

    private EmailSubscriptionType fromPoliciesEventType(String eventType) {
        if (eventType.equals(DAILY_EMAIL_TYPE)) {
            return EmailSubscriptionType.DAILY;
        } else if (eventType.equals(INSTANT_EMAIL_TYPE)) {
            return EmailSubscriptionType.INSTANT;
        }
        throw new IllegalArgumentException("Unknown policies event type: " + eventType);
    }
}
