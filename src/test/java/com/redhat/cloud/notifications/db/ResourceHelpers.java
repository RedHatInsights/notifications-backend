package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.EmailSubscription.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Endpoint.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.WebhookAttributes;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ResourceHelpers {

    public static final String TEST_APP_NAME = "Tester";
    public static final String TEST_APP_NAME_2 = "MyOtherTester";
    public static final String TEST_EVENT_TYPE_FORMAT = "EventType%d";
    private static final String TEST_BUNDLE_NAME = "TestBundle";

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

    public List<Application> getApplications() {
        return appResources.getApplications().collectItems().asList().await().indefinitely();
    }

    public EmailSubscription getSubscription(String accountNumber, String username, EmailSubscription.EmailSubscriptionType type) {
        return subscriptionResources.getEmailSubscription(accountNumber, username, type).await().indefinitely();
    }

    public void createTestAppAndEventTypes() {
        Bundle bundle = new Bundle(TEST_BUNDLE_NAME, "...");
        Bundle b = bundleResources.createBundle(bundle).await().indefinitely();

        Application app = new Application();
        app.setName(TEST_APP_NAME);
        app.setDisplay_name("...");
        app.setBundleId(b.getId());
        Application added = appResources.createApplication(app).await().indefinitely();

        for (int i = 0; i < 100; i++) {
            EventType eventType = new EventType();
            eventType.setName(String.format(TEST_EVENT_TYPE_FORMAT, i));
            eventType.setDisplay_name("... -> " + i);
            appResources.addEventTypeToApplication(added.getId(), eventType).await().indefinitely();
        }

        Application app2 = new Application();
        app2.setName(TEST_APP_NAME_2);
        app2.setDisplay_name("...");
        app2.setBundleId(b.getId());
        Application added2 = appResources.createApplication(app2).await().indefinitely();

        for (int i = 0; i < 100; i++) {
            EventType eventType = new EventType();
            eventType.setName(String.format(TEST_EVENT_TYPE_FORMAT, i));
            eventType.setDisplay_name("... -> " + i);
            appResources.addEventTypeToApplication(added2.getId(), eventType).await().indefinitely();
        }
    }

    public int[] createTestEndpoints(String tenant, int count) {
        int[] statsValues = new int[3];
        statsValues[0] = count;
        for (int i = 0; i < count; i++) {
            // Add new endpoints
            WebhookAttributes webAttr = new WebhookAttributes();
            webAttr.setMethod(WebhookAttributes.HttpType.POST);
            webAttr.setUrl("https://localhost");

            Endpoint ep = new Endpoint();
            if (i > 0) {
                ep.setType(Endpoint.EndpointType.WEBHOOK);
                ep.setName(String.format("Endpoint %d", count - i));
            } else {
                ep.setType(Endpoint.EndpointType.DEFAULT);
                ep.setName("Default endpoint");
            }
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

            ep.setTenant(tenant);
            resources.createEndpoint(ep).await().indefinitely();
        }
        return statsValues;
    }

    public UUID createWebhookEndpoint(String tenant) {
        WebhookAttributes webAttr = new WebhookAttributes();
        webAttr.setMethod(WebhookAttributes.HttpType.POST);
        webAttr.setUrl("https://localhost");
        Endpoint ep = new Endpoint();
        ep.setType(Endpoint.EndpointType.WEBHOOK);
        ep.setName(String.format("Endpoint %s", UUID.randomUUID().toString()));
        ep.setDescription("Automatically generated");
        ep.setEnabled(true);
        ep.setTenant(tenant);
        return resources.createEndpoint(ep).await().indefinitely().getId();
    }

    public void assignEndpointToEventType(String tenant, UUID endpointId, long eventTypeId) {
        resources.linkEndpoint(tenant, endpointId, eventTypeId).await().indefinitely();
    }

    public UUID getDefaultEndpointId(String tenant) {
        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.DEFAULT);
        ep.setName(String.format("Endpoint %s", UUID.randomUUID().toString()));
        ep.setDescription("Automatically generated");
        ep.setEnabled(true);
        ep.setTenant(tenant);

        return resources.createEndpoint(ep).await().indefinitely().getId();
    }

    public void assignEndpointToDefault(String tenant, UUID endpointId) {
        resources.addEndpointToDefaults(tenant, endpointId).await().indefinitely();
    }

    public void createSubscription(String tenant, String username, EmailSubscriptionType type) {
        subscriptionResources.subscribe(tenant, username, type).await().indefinitely();
    }

    public void removeSubscription(String tenant, String username, EmailSubscriptionType type) {
        subscriptionResources.unsubscribe(tenant, username, type).await().indefinitely();
    }

    public void addEmailAggregation(String tenant, String application, String policyId, String insightsId) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setApplication(application);
        aggregation.setAccountId(tenant);

        JsonObject payload = new JsonObject();
        payload.put("policy_id", policyId);
        payload.put("policy_name", "not-used-name");
        payload.put("policy_description", "not-used-desc");
        payload.put("policy_condition", "not-used-condition");
        payload.put("display_name", "not-used-display-name");
        payload.put("insights_id", insightsId);
        payload.put("tags", new JsonArray());

        aggregation.setPayload(payload);

        emailAggregationResources.addEmailAggregation(aggregation).await().indefinitely();
    }

    public List<EventType> getEventTypesForApplication(UUID applicationId) {
        return appResources.getEventTypes(applicationId).collectItems().asList().await().indefinitely();
    }
}
