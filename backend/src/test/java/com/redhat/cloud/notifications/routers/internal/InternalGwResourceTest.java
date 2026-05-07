package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.Header;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.CrudTestHelpers.*;
import static com.redhat.cloud.notifications.models.SubscriptionType.INSTANT;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class InternalGwResourceTest extends DbIsolatedTest {

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Inject
    EntityManager em;

    @Inject
    ResourceHelpers resourceHelpers;

    @Test
    void testSubscriptionsList() {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);

        final String bundleName = "bundle-name-baet1";
        final String applicationName = "application-name-baet1";
        String bundleId = createBundle(adminIdentity, bundleName, RandomStringUtils.secure().nextAlphanumeric(10), HttpStatus.SC_OK).get();
        String applicationId = createApp(adminIdentity, bundleId, applicationName, RandomStringUtils.secure().nextAlphanumeric(10), null, HttpStatus.SC_OK).get();
        EventType eventType = new EventType();
        eventType.setDescription(RandomStringUtils.secure().nextAlphanumeric(10));
        eventType.setName("event1");
        eventType.setDisplayName("Event 1");
        eventType.setApplicationId(UUID.fromString(applicationId));
        String eventId1 = createEventType(adminIdentity, eventType, HttpStatus.SC_OK).get();

        eventType.setName("event2");
        eventType.setDisplayName("Event 2");
        String eventId2 = createEventType(adminIdentity, eventType, HttpStatus.SC_OK).get();

        // Create enabled EMAIL_SUBSCRIPTION endpoints linked to event types for orgs 123456 and 654321
        Endpoint emailEp1 = resourceHelpers.createEndpoint("account", "123456", EndpointType.EMAIL_SUBSCRIPTION, null, "email-ep-123456", "desc", null, true);
        linkEndpointToEventType(emailEp1.getId(), UUID.fromString(eventId1));
        linkEndpointToEventType(emailEp1.getId(), UUID.fromString(eventId2));
        Endpoint emailEp2 = resourceHelpers.createEndpoint("account", "654321", EndpointType.EMAIL_SUBSCRIPTION, null, "email-ep-654321", "desc", null, true);
        linkEndpointToEventType(emailEp2.getId(), UUID.fromString(eventId1));
        linkEndpointToEventType(emailEp2.getId(), UUID.fromString(eventId2));

        // no subscriber yet
        Map<String, List<String>> mapOrgsByEventTypes = getSubscribedOrgs(adminIdentity, bundleName, applicationName, List.of("event1", "event2"));
        assertTrue(mapOrgsByEventTypes.isEmpty());

        // add one subscriber to event type 2
        createUserSubscription(UUID.fromString(eventId2), "123456");
        mapOrgsByEventTypes = getSubscribedOrgs(adminIdentity, bundleName, applicationName, List.of("event1", "event2"));
        assertEquals(1, mapOrgsByEventTypes.size());
        assertTrue(mapOrgsByEventTypes.containsKey("event2"));
        assertTrue(mapOrgsByEventTypes.get("event2").contains("123456"));

        // add one subscriber to event type 1
        createUserSubscription(UUID.fromString(eventId1), "654321");
        mapOrgsByEventTypes = getSubscribedOrgs(adminIdentity, bundleName, applicationName, List.of("event1", "event2"));
        assertEquals(2, mapOrgsByEventTypes.size());
        assertTrue(mapOrgsByEventTypes.containsKey("event1"));
        assertTrue(mapOrgsByEventTypes.get("event1").contains("654321"));
        assertTrue(mapOrgsByEventTypes.containsKey("event2"));
        assertTrue(mapOrgsByEventTypes.get("event2").contains("123456"));

        // add one subscriber to event type 2
        createUserSubscription(UUID.fromString(eventId2), "654321");
        mapOrgsByEventTypes = getSubscribedOrgs(adminIdentity, bundleName, applicationName, List.of("event1", "event2"));
        assertEquals(2, mapOrgsByEventTypes.size());
        assertTrue(mapOrgsByEventTypes.containsKey("event1"));
        assertTrue(mapOrgsByEventTypes.get("event1").contains("654321"));
        assertTrue(mapOrgsByEventTypes.containsKey("event2"));
        assertTrue(mapOrgsByEventTypes.get("event2").contains("123456"));
        assertTrue(mapOrgsByEventTypes.get("event2").contains("654321"));

        // filter on event type 1 only
        createUserSubscription(UUID.fromString(eventId1), "654321");
        mapOrgsByEventTypes = getSubscribedOrgs(adminIdentity, bundleName, applicationName, List.of("event1"));
        assertEquals(1, mapOrgsByEventTypes.size());
        assertTrue(mapOrgsByEventTypes.containsKey("event1"));
        assertTrue(mapOrgsByEventTypes.get("event1").contains("654321"));

        // test with unknown even type
        mapOrgsByEventTypes = getSubscribedOrgs(adminIdentity, bundleName, applicationName, List.of("not-exist"));
        assertEquals(0, mapOrgsByEventTypes.size());
    }

    @Test
    void testMachineToMachineEndpointReturnsOrg() {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);

        final String bundleName = "bundle-name-m2m";
        final String applicationName = "application-name-m2m";
        final String orgId = "org-m2m-1";
        String bundleId = createBundle(adminIdentity, bundleName, RandomStringUtils.secure().nextAlphanumeric(10), HttpStatus.SC_OK).get();
        String applicationId = createApp(adminIdentity, bundleId, applicationName, RandomStringUtils.secure().nextAlphanumeric(10), null, HttpStatus.SC_OK).get();
        EventType eventType = new EventType();
        eventType.setDescription(RandomStringUtils.secure().nextAlphanumeric(10));
        eventType.setName("event-m2m");
        eventType.setDisplayName("Event M2M");
        eventType.setApplicationId(UUID.fromString(applicationId));
        String eventId = createEventType(adminIdentity, eventType, HttpStatus.SC_OK).get();

        // No endpoints yet — should be empty
        Map<String, List<String>> result = getSubscribedOrgs(adminIdentity, bundleName, applicationName, List.of("event-m2m"));
        assertTrue(result.isEmpty());

        // Create an enabled WEBHOOK endpoint and link it to the event type
        Endpoint webhookEndpoint = resourceHelpers.createEndpoint("account", orgId, EndpointType.WEBHOOK, null, "webhook-ep", "desc", null, true);
        linkEndpointToEventType(webhookEndpoint.getId(), UUID.fromString(eventId));

        // Org should now appear (machine-to-machine, no subscription needed)
        result = getSubscribedOrgs(adminIdentity, bundleName, applicationName, List.of("event-m2m"));
        assertEquals(1, result.size());
        assertTrue(result.containsKey("event-m2m"));
        assertTrue(result.get("event-m2m").contains(orgId));
    }

    @Test
    void testRecipientEndpointWithoutSubscriptionDoesNotReturnOrg() {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);

        final String bundleName = "bundle-name-recip";
        final String applicationName = "application-name-recip";
        final String orgId = "org-recip-1";
        String bundleId = createBundle(adminIdentity, bundleName, RandomStringUtils.secure().nextAlphanumeric(10), HttpStatus.SC_OK).get();
        String applicationId = createApp(adminIdentity, bundleId, applicationName, RandomStringUtils.secure().nextAlphanumeric(10), null, HttpStatus.SC_OK).get();
        EventType eventType = new EventType();
        eventType.setDescription(RandomStringUtils.secure().nextAlphanumeric(10));
        eventType.setName("event-recip");
        eventType.setDisplayName("Event Recip");
        eventType.setApplicationId(UUID.fromString(applicationId));
        String eventId = createEventType(adminIdentity, eventType, HttpStatus.SC_OK).get();

        // Create an enabled EMAIL_SUBSCRIPTION endpoint linked to the event type, but NO user subscription
        Endpoint emailEndpoint = resourceHelpers.createEndpoint("account", orgId, EndpointType.EMAIL_SUBSCRIPTION, null, "email-ep", "desc", null, true);
        linkEndpointToEventType(emailEndpoint.getId(), UUID.fromString(eventId));

        // Org should NOT appear (recipient-based, no active subscription)
        Map<String, List<String>> result = getSubscribedOrgs(adminIdentity, bundleName, applicationName, List.of("event-recip"));
        assertFalse(result.containsKey("event-recip"));

        // Now add a user subscription — org should appear
        createUserSubscription(UUID.fromString(eventId), orgId);
        result = getSubscribedOrgs(adminIdentity, bundleName, applicationName, List.of("event-recip"));
        assertEquals(1, result.size());
        assertTrue(result.containsKey("event-recip"));
        assertTrue(result.get("event-recip").contains(orgId));
    }

    @Test
    void testSeveritySubscriptionReturnsOrg() {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);

        final String bundleName = "bundle-name-sev";
        final String applicationName = "application-name-sev";
        final String orgId = "org-sev-1";
        String bundleId = createBundle(adminIdentity, bundleName, RandomStringUtils.secure().nextAlphanumeric(10), HttpStatus.SC_OK).get();
        String applicationId = createApp(adminIdentity, bundleId, applicationName, RandomStringUtils.secure().nextAlphanumeric(10), null, HttpStatus.SC_OK).get();
        EventType eventType = new EventType();
        eventType.setDescription(RandomStringUtils.secure().nextAlphanumeric(10));
        eventType.setName("event-sev");
        eventType.setDisplayName("Event Severity");
        eventType.setApplicationId(UUID.fromString(applicationId));
        String eventId = createEventType(adminIdentity, eventType, HttpStatus.SC_OK).get();

        // Create an enabled DRAWER endpoint linked to the event type
        Endpoint drawerEndpoint = resourceHelpers.createEndpoint("account", orgId, EndpointType.DRAWER, null, "drawer-ep", "desc", null, true);
        linkEndpointToEventType(drawerEndpoint.getId(), UUID.fromString(eventId));

        // Add a severity-based subscription (subscribed=false but severities has true)
        createSeveritySubscription(UUID.fromString(eventId), orgId);

        // Org should appear (severity-based subscription satisfies the condition)
        Map<String, List<String>> result = getSubscribedOrgs(adminIdentity, bundleName, applicationName, List.of("event-sev"));
        assertEquals(1, result.size());
        assertTrue(result.containsKey("event-sev"));
        assertTrue(result.get("event-sev").contains(orgId));
    }

    @Test
    void testDisabledEndpointDoesNotReturnOrg() {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);

        final String bundleName = "bundle-name-dis";
        final String applicationName = "application-name-dis";
        final String orgId = "org-dis-1";
        String bundleId = createBundle(adminIdentity, bundleName, RandomStringUtils.secure().nextAlphanumeric(10), HttpStatus.SC_OK).get();
        String applicationId = createApp(adminIdentity, bundleId, applicationName, RandomStringUtils.secure().nextAlphanumeric(10), null, HttpStatus.SC_OK).get();
        EventType eventType = new EventType();
        eventType.setDescription(RandomStringUtils.secure().nextAlphanumeric(10));
        eventType.setName("event-dis");
        eventType.setDisplayName("Event Disabled");
        eventType.setApplicationId(UUID.fromString(applicationId));
        String eventId = createEventType(adminIdentity, eventType, HttpStatus.SC_OK).get();

        // Create a DISABLED WEBHOOK endpoint linked to the event type
        Endpoint webhookEndpoint = resourceHelpers.createEndpoint("account", orgId, EndpointType.WEBHOOK, null, "webhook-disabled", "desc", null, false);
        linkEndpointToEventType(webhookEndpoint.getId(), UUID.fromString(eventId));

        // Org should NOT appear (endpoint is disabled)
        Map<String, List<String>> result = getSubscribedOrgs(adminIdentity, bundleName, applicationName, List.of("event-dis"));
        assertFalse(result.containsKey("event-dis"));
    }

    private static Map<String, List<String>> getSubscribedOrgs(final Header adminIdentity, final String bundleName, final String applicationName, final List<String> event1) {
        return given()
            .contentType(JSON)
            .header(adminIdentity)
            .pathParam("bundle", bundleName)
            .pathParam("application", applicationName)
            .param("eventTypeNames", event1)
            .when()
            .get("/internal/gw/subscriptions/{bundle}/{application}")
            .then()
            .statusCode(HttpStatus.SC_OK).extract().as(new TypeRef<>() {
            });
    }

    @Transactional
    protected void linkEndpointToEventType(UUID endpointId, UUID eventTypeId) {
        em.createNativeQuery("INSERT INTO endpoint_event_type(endpoint_id, event_type_id) VALUES (:endpointId, :eventTypeId)")
            .setParameter("endpointId", endpointId)
            .setParameter("eventTypeId", eventTypeId)
            .executeUpdate();
        em.flush();
    }

    @Transactional
    protected void createSeveritySubscription(UUID eventTypeId, String orgId) {
        String sql = "INSERT INTO email_subscriptions(org_id, user_id, event_type_id, subscription_type, subscribed, severities) " +
            "VALUES (:orgId, :userId, :eventTypeId, :subscriptionType, :subscribed, CAST(:severities AS jsonb)) " +
            "ON CONFLICT (org_id, user_id, event_type_id, subscription_type) DO UPDATE SET subscribed = :subscribed, severities = CAST(:severities AS jsonb)";

        em.createNativeQuery(sql)
            .setParameter("orgId", orgId)
            .setParameter("userId", "username")
            .setParameter("eventTypeId", eventTypeId)
            .setParameter("subscriptionType", INSTANT.name())
            .setParameter("subscribed", false)
            .setParameter("severities", "{\"CRITICAL\": true}")
            .executeUpdate();
        em.flush();
    }

    @Transactional
    protected void createUserSubscription(UUID eventId2, String orgId) {
        // We're performing an upsert to update the user subscription.
        String sql = "INSERT INTO email_subscriptions(org_id, user_id, event_type_id, subscription_type, subscribed) " +
            "VALUES (:orgId, :userId, :eventTypeId, :subscriptionType, :subscribed) " +
            "ON CONFLICT (org_id, user_id, event_type_id, subscription_type) DO UPDATE SET subscribed = :subscribed";

        em.createNativeQuery(sql)
            .setParameter("orgId", orgId)
            .setParameter("userId", "username")
            .setParameter("eventTypeId", eventId2)
            .setParameter("subscriptionType", INSTANT.name())
            .setParameter("subscribed", true)
            .executeUpdate();

        em.flush();
    }
}
