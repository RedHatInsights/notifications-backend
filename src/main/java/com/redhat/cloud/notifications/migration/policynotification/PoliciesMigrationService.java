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

    private final String BUNDLE = "insights";
    private final String APPLICATION = "policies";
    private final String EVENT_TYPE = "policy-triggered";

    @Inject
    @RestClient
    PolicyNotifications policyNotifications;

    @Inject
    EndpointEmailSubscriptionResources emailSubscriptionResources;

    @Inject
    EndpointResources endpointResources;

    @Inject
    ApplicationResources applicationResources;

    class MigrateResponse {
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
                    .merge().collectItems().in(HashSet::new, HashSet::add)
                    .onItem().transformToMulti(accountIds -> Multi.createFrom().iterable(accountIds))
                    .onItem().castTo(String.class)
                    .onItem().transformToMulti(accountId -> {
                        response.accountsMigrated.incrementAndGet();
                        // Remove existing endpoints
                        return endpointResources.getLinkedEndpoints(accountId, eventType.getId(), new Query())
                        .onItem().transform(endpoint -> endpointResources.unlinkEndpoint(accountId, endpoint.getId(), eventType.getId()))
                        .collectItems().asList()
                        // Create EmailAction (Endpoint) and add it to the eventType
                        .onItem().transformToMulti(_removed -> {
                            Endpoint endpoint = new Endpoint();
                            endpoint.setAccountId(accountId);
                            endpoint.setEnabled(true);
                            endpoint.setDescription("");
                            endpoint.setName("email-endpoint");
                            endpoint.setType(EndpointType.EMAIL_SUBSCRIPTION);
                            endpoint.setProperties(new EmailSubscriptionAttributes());
                            return endpointResources.createEndpoint(endpoint).toMulti();
                        })
                        .onItem().transform(endpoint -> endpointResources.linkEndpoint(accountId, endpoint.getId(), eventType.getId()));
                    }).merge();
        }).collectItems().asList().onItem().transform(objects -> response);
    }

    private EmailSubscriptionType fromPoliciesEventType(String eventType) {
        if (eventType.equals("policies-daily-mail")) {
            return EmailSubscriptionType.DAILY;
        } else if (eventType.equals("policies-instant-mail")) {
            return EmailSubscriptionType.INSTANT;
        }
        throw new IllegalArgumentException("Unknown policies event type: " + eventType);
    }
}
