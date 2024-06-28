package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventAggregationCriteria;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SubscriptionType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EmailAggregationRepositoryTest2 {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final String ORG_ID = "someOrgId";
    private static final String BUNDLE_NAME = "best-bundle";
    private static final String APP_NAME = "awesome-app";
    private static final JsonObject PAYLOAD1 = new JsonObject("{\"foo\":\"bar\"}");
    private static final JsonObject PAYLOAD2 = new JsonObject("{\"hello\":\"world\"}");

    @Inject
    EntityManager entityManager;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EmailAggregationRepository emailAggregationRepository;

    @Test
    void testAllMethods() {
        LocalDateTime start = LocalDateTime.now(UTC).minusHours(1L);
        LocalDateTime end = LocalDateTime.now(UTC).plusHours(1L);
        Application application = resourceHelpers.findOrCreateApplication(BUNDLE_NAME, APP_NAME);
        EventAggregationCriteria key =  new EventAggregationCriteria(ORG_ID, application.getBundleId(), application.getId(), BUNDLE_NAME, APP_NAME);

        clearEvents();
        addEventEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, PAYLOAD1);
        addEventEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, PAYLOAD2);
        addEventEmailAggregation("other-org-id", BUNDLE_NAME, APP_NAME, PAYLOAD2);
        addEventEmailAggregation(ORG_ID, "other-bundle", APP_NAME, PAYLOAD2);
        addEventEmailAggregation(ORG_ID, BUNDLE_NAME, "other-app", PAYLOAD2);

        List<Event> aggregations = emailAggregationRepository.getEmailAggregationBasedOnEvent(key, start, end, 0, 10);
        assertEquals(2, aggregations.size());
        assertTrue(aggregations.stream().map(Event::getOrgId).allMatch(ORG_ID::equals));
        assertTrue(aggregations.stream().map(Event::getBundleId).allMatch(application.getBundleId()::equals));
        assertTrue(aggregations.stream().map(Event::getApplicationId).allMatch(application.getId()::equals));
        assertEquals(1, aggregations.stream().map(Event::getPayload).map(payload -> new JsonObject(payload)).filter(PAYLOAD1::equals).count());
        assertEquals(1, aggregations.stream().map(Event::getPayload).map(payload -> new JsonObject(payload)).filter(PAYLOAD2::equals).count());

        clearEvents();
    }

    @Transactional
    void clearEvents() {
        entityManager.createQuery("DELETE FROM Event")
                .executeUpdate();
    }

    private com.redhat.cloud.notifications.models.Event addEventEmailAggregation(String orgId, String bundleName, String applicationName, JsonObject payload) {
        Application application = resourceHelpers.findOrCreateApplication(bundleName, applicationName);
        EventType eventType = resourceHelpers.findOrCreateEventType(application.getId(), "event_type_test");
        resourceHelpers.findOrCreateEventTypeEmailSubscription(orgId, "obiwan", eventType, SubscriptionType.DAILY);

        com.redhat.cloud.notifications.models.Event event = new com.redhat.cloud.notifications.models.Event();
        event.setId(UUID.randomUUID());
        event.setOrgId(orgId);
        eventType.setApplication(application);
        event.setEventType(eventType);
        event.setPayload(payload.toString());
        event.setCreated(LocalDateTime.now(UTC));

        Event retevent = resourceHelpers.createEvent(event);

        return retevent;
    }
}
