package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.WebhookProperties;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

@ApplicationScoped
public class ResourceHelpers {

    public static final String TEST_APP_NAME = "tester";
    public static final String TEST_APP_NAME_2 = "myothertester";
    public static final String TEST_EVENT_TYPE_FORMAT = "eventtype%d";
    public static final String TEST_BUNDLE_NAME = "testbundle";

    @Inject
    EndpointResources endpointResources;

    @Inject
    ApplicationResources appResources;

    @Inject
    BundleResources bundleResources;

    @Inject
    BehaviorGroupResources behaviorGroupResources;

    @Inject
    Mutiny.SessionFactory sessionFactory;

    public Uni<Bundle> createBundle() {
        return createBundle("name", "displayName");
    }

    public Uni<Bundle> createBundle(String name, String displayName) {
        Bundle bundle = new Bundle(name, displayName);
        return bundleResources.createBundle(bundle);
    }

    public Uni<UUID> getBundleId(String bundleName) {
        return bundleResources.getBundle(bundleName)
                .onItem().transform(Bundle::getId);
    }

    public Uni<Application> createApplication(UUID bundleId) {
        return createApplication(bundleId, "name", "displayName");
    }

    public Uni<Application> createApplication(UUID bundleId, String name, String displayName) {
        Application app = new Application();
        app.setBundleId(bundleId);
        app.setName(name);
        app.setDisplayName(displayName);
        return appResources.createApp(app);
    }

    public Uni<UUID> createEventType(String bundleName, String applicationName, String eventTypeName) {
        return appResources.getApplication(bundleName, applicationName)
                .onItem().transformToUni(app -> createEventType(app.getId(), eventTypeName, eventTypeName, "new event type"))
                .onItem().transform(EventType::getId);
    }

    public Uni<EventType> createEventType(UUID applicationId, String name, String displayName, String description) {
        EventType eventType = new EventType();
        eventType.setName(name);
        eventType.setDisplayName(displayName);
        eventType.setDescription(description);
        eventType.setApplicationId(applicationId);
        return appResources.createEventType(eventType);
    }

    public Uni<UUID> createTestAppAndEventTypes() {
        return sessionFactory.withSession(session -> createBundle(TEST_BUNDLE_NAME, "...")
                .call(bundle -> createApplication(bundle.getId(), TEST_APP_NAME, "...")
                        .call(app -> Multi.createFrom().items(() -> IntStream.range(0, 100).boxed())
                                .onItem().transformToUniAndConcatenate(i -> {
                                    String name = String.format(TEST_EVENT_TYPE_FORMAT, i);
                                    String displayName = "... -> " + i;
                                    String description = "Desc .. --> " + i;
                                    return createEventType(app.getId(), name, displayName, description);
                                })
                                .onItem().ignoreAsUni()
                        )
                )
                .call(bundle -> createApplication(bundle.getId(), TEST_APP_NAME_2, "...")
                        .call(app -> Multi.createFrom().items(() -> IntStream.range(0, 100).boxed())
                                .onItem().transformToUniAndConcatenate(i -> {
                                    String name = String.format(TEST_EVENT_TYPE_FORMAT, i);
                                    String displayName = "... -> " + i;
                                    return createEventType(app.getId(), name, displayName, null);
                                })
                                .onItem().ignoreAsUni()
                        )
                )
                .onItem().transform(Bundle::getId)
        );
    }

    public Uni<Endpoint> createEndpoint(String accountId, EndpointType type) {
        return createEndpoint(accountId, type, "name", "description", null, FALSE);
    }

    public Uni<UUID> createWebhookEndpoint(String accountId) {
        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(HttpType.POST);
        properties.setUrl("https://localhost");
        String name = "Endpoint " + UUID.randomUUID();
        return createEndpoint(accountId, WEBHOOK, name, "Automatically generated", properties, TRUE)
                .onItem().transform(Endpoint::getId);
    }

    public Uni<Endpoint> createEndpoint(String accountId, EndpointType type, String name, String description, EndpointProperties properties, Boolean enabled) {
        Endpoint endpoint = new Endpoint();
        endpoint.setAccountId(accountId);
        endpoint.setType(type);
        endpoint.setName(name);
        endpoint.setDescription(description);
        endpoint.setProperties(properties);
        endpoint.setEnabled(enabled);
        return endpointResources.createEndpoint(endpoint, false);
    }

    public Uni<int[]> createTestEndpoints(String tenant, int count) {
        int[] statsValues = new int[3];
        statsValues[0] = count;
        return Multi.createFrom().items(() -> IntStream.range(0, count).boxed())
                .onItem().transformToUniAndConcatenate(i -> {
                    // Add new endpoints
                    WebhookProperties properties = new WebhookProperties();
                    properties.setMethod(HttpType.POST);
                    properties.setUrl("https://localhost");

                    Endpoint ep = new Endpoint();
                    ep.setType(WEBHOOK);
                    ep.setName(String.format("Endpoint %d", count - i));
                    ep.setDescription("Automatically generated");
                    boolean enabled = (i % (count / 5)) != 0;
                    if (!enabled) {
                        statsValues[1]++;
                    }
                    ep.setEnabled(enabled);
                    if (i > 0) {
                        statsValues[2]++;
                        ep.setProperties(properties);
                    }

                    ep.setAccountId(tenant);
                    return endpointResources.createEndpoint(ep, false);
                })
                .onItem().ignoreAsUni()
                .replaceWith(statsValues);
    }

    public Uni<NotificationHistory> createNotificationHistory(Event event, Endpoint endpoint, Boolean invocationResult) {
        NotificationHistory history = new NotificationHistory();
        history.setId(UUID.randomUUID());
        history.setInvocationTime(1L);
        history.setInvocationResult(invocationResult);
        history.setEvent(event);
        history.setEndpoint(endpoint);
        history.setEndpointType(endpoint.getType());
        history.prePersist();
        return sessionFactory.withStatelessSession(statelessSession -> {
            return statelessSession.insert(history)
                    .replaceWith(history);
        });
    }

    public Uni<UUID> emailSubscriptionEndpointId(String accountId, EmailSubscriptionProperties properties) {
        return endpointResources.getOrCreateEmailSubscriptionEndpoint(accountId, properties, false)
                .onItem().transform(Endpoint::getId);
    }

    public Uni<BehaviorGroup> createBehaviorGroup(String accountId, String displayName, UUID bundleId) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setDisplayName(displayName);
        behaviorGroup.setBundleId(bundleId);
        return behaviorGroupResources.create(accountId, behaviorGroup);
    }

    public Uni<BehaviorGroup> createDefaultBehaviorGroup(String displayName, UUID bundleId) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setDisplayName(displayName);
        behaviorGroup.setBundleId(bundleId);
        return behaviorGroupResources.createDefault(behaviorGroup);
    }

    public Uni<List<EventType>> findEventTypesByBehaviorGroupId(UUID behaviorGroupId) {
        return behaviorGroupResources.findEventTypesByBehaviorGroupId(DEFAULT_ACCOUNT_ID, behaviorGroupId);
    }

    public Uni<List<BehaviorGroup>> findBehaviorGroupsByEventTypeId(UUID eventTypeId) {
        return behaviorGroupResources.findBehaviorGroupsByEventTypeId(DEFAULT_ACCOUNT_ID, eventTypeId, new Query());
    }

    public Uni<List<BehaviorGroup>> findBehaviorGroupsByEndpointId(UUID endpointId) {
        return behaviorGroupResources.findBehaviorGroupsByEndpointId(DEFAULT_ACCOUNT_ID, endpointId);
    }

    public Uni<Boolean> updateBehaviorGroup(BehaviorGroup behaviorGroup) {
        return behaviorGroupResources.update(DEFAULT_ACCOUNT_ID, behaviorGroup);
    }

    public Uni<Boolean> deleteBehaviorGroup(UUID behaviorGroupId) {
        return behaviorGroupResources.delete(DEFAULT_ACCOUNT_ID, behaviorGroupId);
    }

    public Uni<Boolean> updateDefaultBehaviorGroup(BehaviorGroup behaviorGroup) {
        return behaviorGroupResources.updateDefault(behaviorGroup);
    }

    public Uni<Boolean> deleteDefaultBehaviorGroup(UUID behaviorGroupId) {
        return behaviorGroupResources.deleteDefault(behaviorGroupId);
    }
}
