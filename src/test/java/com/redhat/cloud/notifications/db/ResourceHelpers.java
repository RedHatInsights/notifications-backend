package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.WebhookAttributes;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ResourceHelpers {

    public static final String TEST_APP_NAME = "tester";
    public static final String TEST_APP_NAME_2 = "myothertester";
    public static final String TEST_EVENT_TYPE_FORMAT = "eventtype%d";
    public static final String TEST_BUNDLE_NAME = "testbundle";

    @Inject
    EndpointResources resources;

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

    public List<Application> getApplications(String bundleName) {
        return appResources.getApplications(bundleName).collect().asList().await().indefinitely();
    }

    public EmailSubscription getSubscription(String accountNumber, String username, String bundle, String application, EmailSubscriptionType type) {
        return subscriptionResources.getEmailSubscription(accountNumber, username, bundle, application, type).await().indefinitely();
    }

    public UUID createTestAppAndEventTypes() {
        Bundle bundle = new Bundle(TEST_BUNDLE_NAME, "...");
        Bundle b = bundleResources.createBundle(bundle).await().indefinitely();

        Application app = new Application();
        app.setName(TEST_APP_NAME);
        app.setDisplayName("...");
        app.setBundleId(b.getId());
        Application added = appResources.createApplication(app).await().indefinitely();

        for (int i = 0; i < 100; i++) {
            EventType eventType = new EventType();
            eventType.setName(String.format(TEST_EVENT_TYPE_FORMAT, i));
            eventType.setDisplayName("... -> " + i);
            eventType.setDescription("Desc .. --> " + i);
            appResources.addEventTypeToApplication(added.getId(), eventType).await().indefinitely();
        }

        Application app2 = new Application();
        app2.setName(TEST_APP_NAME_2);
        app2.setDisplayName("...");
        app2.setBundleId(b.getId());
        Application added2 = appResources.createApplication(app2).await().indefinitely();

        for (int i = 0; i < 100; i++) {
            EventType eventType = new EventType();
            eventType.setName(String.format(TEST_EVENT_TYPE_FORMAT, i));
            eventType.setDisplayName("... -> " + i);
            appResources.addEventTypeToApplication(added2.getId(), eventType).await().indefinitely();
        }

        return b.getId();
    }

    public int[] createTestEndpoints(String tenant, int count) {
        int[] statsValues = new int[3];
        statsValues[0] = count;
        for (int i = 0; i < count; i++) {
            // Add new endpoints
            WebhookAttributes webAttr = new WebhookAttributes();
            webAttr.setMethod(HttpType.POST);
            webAttr.setUrl("https://localhost");

            Endpoint ep = new Endpoint();
            ep.setType(EndpointType.WEBHOOK);
            ep.setName(String.format("Endpoint %d", count - i));
            ep.setDescription("Automatically generated");
            boolean enabled = (i % (count / 5)) != 0;
            if (!enabled) {
                statsValues[1]++;
            }
            ep.setEnabled(enabled);
            if (i > 0) {
                statsValues[2]++;
                ep.setProperties(webAttr);
            }

            ep.setAccountId(tenant);
            resources.createEndpoint(ep).await().indefinitely();
        }
        return statsValues;
    }

    public UUID createWebhookEndpoint(String tenant) {
        WebhookAttributes webAttr = new WebhookAttributes();
        webAttr.setMethod(HttpType.POST);
        webAttr.setUrl("https://localhost");
        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.WEBHOOK);
        ep.setName(String.format("Endpoint %s", UUID.randomUUID().toString()));
        ep.setDescription("Automatically generated");
        ep.setEnabled(true);
        ep.setAccountId(tenant);
        return resources.createEndpoint(ep).await().indefinitely().getId();
    }


    public void createSubscription(String tenant, String username, String bundle, String application, EmailSubscriptionType type) {
        subscriptionResources.subscribe(tenant, username, bundle, application, type).await().indefinitely();
    }

    public void removeSubscription(String tenant, String username, String bundle, String application, EmailSubscriptionType type) {
        subscriptionResources.unsubscribe(tenant, username, bundle, application, type).await().indefinitely();
    }

    public void addEmailAggregation(String tenant, String bundle, String application, String policyId, String insightsId) {
        EmailAggregation aggregation = TestHelpers.createEmailAggregation(tenant, bundle, application, policyId, insightsId);
        emailAggregationResources.addEmailAggregation(aggregation).await().indefinitely();
    }

    public List<EventType> getEventTypesForApplication(UUID applicationId) {
        return appResources.getEventTypes(applicationId).collect().asList().await().indefinitely();
    }

    public UUID createBehaviorGroup(String accountId, String name, UUID bundleId) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setName(name);
        behaviorGroup.setDisplayName("Behavior group");
        behaviorGroup.setBundleId(bundleId);
        return behaviorGroupResources.create(accountId, behaviorGroup).await().indefinitely().getId();
    }

    public void linkBehaviorGroupToEventType(String accountId, UUID eventTypeId, UUID behaviorGroupId) {
        behaviorGroupResources.addEventTypeBehavior(accountId, eventTypeId, behaviorGroupId).await().indefinitely();
    }

    public void addBehaviorGroupAction(String accountId, UUID behaviorGroupId, UUID endpointId) {
        behaviorGroupResources.addBehaviorGroupAction(accountId, behaviorGroupId, endpointId).await().indefinitely();
    }
}
