package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventAggregationCriteria;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EmailAggregationRepositoryBasedOnEventTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final String ORG_ID = "someOrgId";
    private static final String BUNDLE_NAME = "best-bundle-email-aggregation-repository-based-on-event-test";
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

        resourceHelpers.clearEvents();
        resourceHelpers.addEventEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, PAYLOAD1);
        resourceHelpers.addEventEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, PAYLOAD2);
        resourceHelpers.addEventEmailAggregation("other-org-id", BUNDLE_NAME, APP_NAME, PAYLOAD2);
        resourceHelpers.addEventEmailAggregation(ORG_ID, "other-bundle", APP_NAME, PAYLOAD2);
        resourceHelpers.addEventEmailAggregation(ORG_ID, BUNDLE_NAME, "other-app", PAYLOAD2);

        List<Event> aggregations = emailAggregationRepository.getEmailAggregationBasedOnEvent(key, start, end, 0, 10);
        assertEquals(2, aggregations.size());
        assertTrue(aggregations.stream().map(Event::getOrgId).allMatch(ORG_ID::equals));
        assertTrue(aggregations.stream().map(Event::getBundleId).allMatch(application.getBundleId()::equals));
        assertTrue(aggregations.stream().map(Event::getApplicationId).allMatch(application.getId()::equals));
        assertEquals(1, aggregations.stream().map(Event::getPayload).map(payload -> new JsonObject(payload)).filter(PAYLOAD1::equals).count());
        assertEquals(1, aggregations.stream().map(Event::getPayload).map(payload -> new JsonObject(payload)).filter(PAYLOAD2::equals).count());

        resourceHelpers.clearEvents();
    }


}
