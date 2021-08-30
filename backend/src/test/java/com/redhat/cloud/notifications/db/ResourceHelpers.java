package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.WebhookProperties;
import io.vertx.core.json.JsonObject;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static java.lang.Boolean.FALSE;

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
    EndpointEmailSubscriptionResources subscriptionResources;

    @Inject
    EmailAggregationResources emailAggregationResources;

    @Inject
    BehaviorGroupResources behaviorGroupResources;

    public Bundle createBundle() {
        return createBundle("name", "displayName");
    }

    public Bundle createBundle(String name, String displayName) {
        Bundle bundle = new Bundle(name, displayName);
        return bundleResources.createBundle(bundle)
                .await().indefinitely();
    }

    public UUID getBundleId(String bundleName) {
        return bundleResources.getBundle(bundleName)
                .await().indefinitely()
                .getId();
    }

    public Application createApplication(UUID bundleId) {
        return createApplication(bundleId, "name", "displayName");
    }

    public Application createApplication(UUID bundleId, String name, String displayName) {
        Application app = new Application();
        app.setBundleId(bundleId);
        app.setName(name);
        app.setDisplayName(displayName);
        return appResources.createApp(app)
                .await().indefinitely();
    }

    public List<Application> getApplications(String bundleName) {
        return appResources.getApplications(bundleName)
                .await().indefinitely();
    }

    public UUID createEventType(String bundleName, String applicationName, String eventTypeName) {
        Application app = appResources.getApplication(bundleName, applicationName).await().indefinitely();
        return createEventType(app.getId(), eventTypeName, eventTypeName, "new event type");
    }

    public UUID createEventType(UUID applicationId, String name, String displayName, String description) {
        EventType eventType = new EventType();
        eventType.setName(name);
        eventType.setDisplayName(displayName);
        eventType.setDescription(description);
        eventType.setApplicationId(applicationId);
        return appResources.createEventType(eventType)
                .await().indefinitely()
                .getId();
    }

    public UUID createTestAppAndEventTypes() {
        Bundle bundle = createBundle(TEST_BUNDLE_NAME, "...");

        Application app = createApplication(bundle.getId(), TEST_APP_NAME, "...");
        for (int i = 0; i < 100; i++) {
            String name = String.format(TEST_EVENT_TYPE_FORMAT, i);
            String displayName = "... -> " + i;
            String description = "Desc .. --> " + i;
            createEventType(app.getId(), name, displayName, description);
        }

        Application app2 = createApplication(bundle.getId(), TEST_APP_NAME_2, "...");
        for (int i = 0; i < 100; i++) {
            String name = String.format(TEST_EVENT_TYPE_FORMAT, i);
            String displayName = "... -> " + i;
            createEventType(app2.getId(), name, displayName, null);
        }

        return bundle.getId();
    }

    public List<EventType> getEventTypesForApplication(UUID applicationId) {
        return appResources.getEventTypes(applicationId)
                .await().indefinitely();
    }

    public Endpoint createEndpoint(String accountId, EndpointType type) {
        return createEndpoint(accountId, type, "name", "description", null, FALSE);
    }

    public UUID createWebhookEndpoint(String accountId) {
        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(HttpType.POST);
        properties.setUrl("https://localhost");
        String name = "Endpoint " + UUID.randomUUID();
        return createEndpoint(accountId, WEBHOOK, name, "Automatically generated", properties, Boolean.TRUE).getId();
    }

    public Endpoint createEndpoint(String accountId, EndpointType type, String name, String description, EndpointProperties properties, Boolean enabled) {
        Endpoint endpoint = new Endpoint();
        endpoint.setAccountId(accountId);
        endpoint.setType(type);
        endpoint.setName(name);
        endpoint.setDescription(description);
        endpoint.setProperties(properties);
        endpoint.setEnabled(enabled);
        return endpointResources.createEndpoint(endpoint)
                .await().indefinitely();
    }

    public int[] createTestEndpoints(String tenant, int count) {
        int[] statsValues = new int[3];
        statsValues[0] = count;
        for (int i = 0; i < count; i++) {
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
            endpointResources.createEndpoint(ep)
                    .await().indefinitely();
        }
        return statsValues;
    }

    public UUID emailSubscriptionEndpointId(String accountId, EmailSubscriptionProperties properties) {
        return endpointResources.getOrCreateEmailSubscriptionEndpoint(accountId, properties)
                .await().indefinitely().getId();
    }

    public BehaviorGroup createBehaviorGroup(String accountId, String displayName, UUID bundleId) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setDisplayName(displayName);
        behaviorGroup.setBundleId(bundleId);
        return behaviorGroupResources.create(accountId, behaviorGroup)
                .await().indefinitely();
    }

    public List<BehaviorGroup> findBehaviorGroupsByBundleId(String accountId, UUID bundleId) {
        return behaviorGroupResources.findByBundleId(accountId, bundleId)
                .await().indefinitely();
    }

    public List<EventType> findEventTypesByBehaviorGroupId(UUID behaviorGroupId) {
        return behaviorGroupResources.findEventTypesByBehaviorGroupId(DEFAULT_ACCOUNT_ID, behaviorGroupId)
                .await().indefinitely();
    }

    public List<BehaviorGroup> findBehaviorGroupsByEventTypeId(UUID eventTypeId) {
        return behaviorGroupResources.findBehaviorGroupsByEventTypeId(DEFAULT_ACCOUNT_ID, eventTypeId, new Query())
                .await().indefinitely();
    }

    public List<BehaviorGroup> findBehaviorGroupsByEndpointId(UUID endpointId) {
        return behaviorGroupResources.findBehaviorGroupsByEndpointId(DEFAULT_ACCOUNT_ID, endpointId)
                .await().indefinitely();
    }

    public Boolean updateBehaviorGroup(BehaviorGroup behaviorGroup) {
        return behaviorGroupResources.update(DEFAULT_ACCOUNT_ID, behaviorGroup)
                .await().indefinitely();
    }

    public Response.Status updateBehaviorGroupActions(String accountId, UUID behaviorGroupId, List<UUID> endpointIds) {
        return behaviorGroupResources.updateBehaviorGroupActions(accountId, behaviorGroupId, endpointIds)
                .await().indefinitely();
    }

    public Boolean updateEventTypeBehaviors(String accountId, UUID eventTypeId, Set<UUID> behaviorGroupIds) {
        return behaviorGroupResources.updateEventTypeBehaviors(accountId, eventTypeId, behaviorGroupIds)
                .await().indefinitely();
    }

    public Boolean deleteBehaviorGroup(UUID behaviorGroupId) {
        return behaviorGroupResources.delete(DEFAULT_ACCOUNT_ID, behaviorGroupId)
                .await().indefinitely();
    }

    public void subscribe(String tenant, String username, String bundle, String application, EmailSubscriptionType type) {
        subscriptionResources.subscribe(tenant, username, bundle, application, type)
                .await().indefinitely();
    }

    public void unsubscribe(String tenant, String username, String bundle, String application, EmailSubscriptionType type) {
        subscriptionResources.unsubscribe(tenant, username, bundle, application, type)
                .await().indefinitely();
    }

    public EmailSubscription getEmailSubscription(String accountNumber, String username, String bundle, String application, EmailSubscriptionType type) {
        return subscriptionResources.getEmailSubscription(accountNumber, username, bundle, application, type)
                .await().indefinitely();
    }

    public void addEmailAggregation(String tenant, String bundle, String application, String policyId, String insightsId) {
        EmailAggregation aggregation = TestHelpers.createEmailAggregation(tenant, bundle, application, policyId, insightsId);
        emailAggregationResources.addEmailAggregation(aggregation)
                .await().indefinitely();
    }

    public Boolean addEmailAggregation(String accountId, String bundleName, String applicationName, JsonObject payload) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setAccountId(accountId);
        aggregation.setBundleName(bundleName);
        aggregation.setApplicationName(applicationName);
        aggregation.setPayload(payload);
        return emailAggregationResources.addEmailAggregation(aggregation)
                .await().indefinitely();
    }

    public List<EmailAggregation> getEmailAggregation(EmailAggregationKey key, LocalDateTime start, LocalDateTime end) {
        return emailAggregationResources.getEmailAggregation(key, start, end)
                .await().indefinitely();
    }

    public List<EmailAggregationKey> getApplicationsWithPendingAggregation(LocalDateTime start, LocalDateTime end) {
        return emailAggregationResources.getApplicationsWithPendingAggregation(start, end)
                .await().indefinitely();
    }

    public Integer purgeOldAggregation(EmailAggregationKey key, LocalDateTime lastUsedTime) {
        return emailAggregationResources.purgeOldAggregation(key, lastUsedTime)
                .await().indefinitely();
    }
}
