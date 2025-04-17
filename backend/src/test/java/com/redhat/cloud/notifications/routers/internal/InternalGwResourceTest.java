package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class InternalGwResourceTest extends DbIsolatedTest {

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Inject
    EntityManager em;

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
