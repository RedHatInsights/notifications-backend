package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class EmailAggregationRepositoryTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final String ORG_ID = "987654321";
    private static final String BUNDLE_NAME = "best-bundle";
    private static final String APP_NAME = "awesome-app";

    private static final String EVENT_TYPE = "amazing-event-type";
    private static final JsonObject PAYLOAD1 = new JsonObject("{\"foo\":\"bar\"}");
    private static final JsonObject PAYLOAD2 = new JsonObject("{\"hello\":\"world\"}");

    @Inject
    EmailAggregationRepository emailAggregationResources;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    FeatureFlipper featureFlipper;

    @BeforeEach
    void setUp() {
        featureFlipper.setUseEventTypeForAggregationEnabled(false);
    }

    @Test
    void testAllMethods() {

        LocalDateTime start = LocalDateTime.now(UTC).minusHours(1L);
        LocalDateTime end = LocalDateTime.now(UTC).plusHours(1L);

        addEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, PAYLOAD1);
        addEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, PAYLOAD2);
        addEmailAggregation("other-org-id", BUNDLE_NAME, APP_NAME, PAYLOAD2);
        addEmailAggregation(ORG_ID, "other-bundle", APP_NAME, PAYLOAD2);
        addEmailAggregation(ORG_ID, BUNDLE_NAME, "other-app", PAYLOAD2);

        EmailAggregationKey key = new EmailAggregationKey(ORG_ID, BUNDLE_NAME, APP_NAME, EVENT_TYPE);

        List<EmailAggregationKey> keys = emailAggregationResources.getApplicationsWithPendingAggregation(start, end);
        assertEquals(4, keys.size());
        assertEquals(ORG_ID, keys.get(0).getOrgId());
        assertEquals(BUNDLE_NAME, keys.get(0).getBundle());
        assertEquals(APP_NAME, keys.get(0).getApplication());

        Integer purged = resourceHelpers.purgeOldAggregation(key, end);
        assertEquals(2, purged);

        keys = emailAggregationResources.getApplicationsWithPendingAggregation(start, end);
        assertEquals(3, keys.size());
    }

    @Test
    void testAllMethodsWithEventTypeAndFeatureFlipDisabled() {

        LocalDateTime start = LocalDateTime.now(UTC).minusHours(1L);
        LocalDateTime end = LocalDateTime.now(UTC).plusHours(1L);

        addEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, EVENT_TYPE, PAYLOAD1);
        addEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, EVENT_TYPE, PAYLOAD2);
        addEmailAggregation("other-org-id", BUNDLE_NAME, APP_NAME, EVENT_TYPE, PAYLOAD2);
        addEmailAggregation(ORG_ID, "other-bundle", APP_NAME, EVENT_TYPE, PAYLOAD2);
        addEmailAggregation(ORG_ID, BUNDLE_NAME, "other-app", EVENT_TYPE, PAYLOAD2);

        EmailAggregationKey key = new EmailAggregationKey(ORG_ID, BUNDLE_NAME, APP_NAME, EVENT_TYPE);

        List<EmailAggregationKey> keys = emailAggregationResources.getApplicationsWithPendingAggregation(start, end);
        assertEquals(4, keys.size());
        assertEquals(ORG_ID, keys.get(0).getOrgId());
        assertEquals(BUNDLE_NAME, keys.get(0).getBundle());
        assertEquals(APP_NAME, keys.get(0).getApplication());

        Integer purged = resourceHelpers.purgeOldAggregation(key, end);
        assertEquals(2, purged);

        keys = emailAggregationResources.getApplicationsWithPendingAggregation(start, end);
        assertEquals(3, keys.size());
    }

    @Test
    void testAllMethodsFiltredByEventType() {
        featureFlipper.setUseEventTypeForAggregationEnabled(true);

        LocalDateTime start = LocalDateTime.now(UTC).minusHours(1L);
        LocalDateTime end = LocalDateTime.now(UTC).plusHours(1L);

        addEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, EVENT_TYPE, PAYLOAD1);
        addEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, EVENT_TYPE, PAYLOAD2);
        addEmailAggregation("other-org-id", BUNDLE_NAME, APP_NAME, EVENT_TYPE, PAYLOAD2);
        addEmailAggregation(ORG_ID, "other-bundle", APP_NAME, EVENT_TYPE, PAYLOAD2);
        addEmailAggregation(ORG_ID, BUNDLE_NAME, "other-app", EVENT_TYPE, PAYLOAD2);
        addEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, "other-event-type", PAYLOAD2);

        EmailAggregationKey key = new EmailAggregationKey(ORG_ID, BUNDLE_NAME, APP_NAME, EVENT_TYPE);

        List<EmailAggregationKey> keys = emailAggregationResources.getApplicationsWithPendingAggregation(start, end);
        assertEquals(5, keys.size());
        assertEquals(ORG_ID, keys.get(0).getOrgId());
        assertEquals(BUNDLE_NAME, keys.get(0).getBundle());
        assertEquals(APP_NAME, keys.get(0).getApplication());

        Integer purged = resourceHelpers.purgeOldAggregationWithEventType(key, end);
        assertEquals(2, purged);

        keys = emailAggregationResources.getApplicationsWithPendingAggregation(start, end);
        assertEquals(4, keys.size());
        resourceHelpers.purgeOldAggregation(key, end);
    }

    private void addEmailAggregation(String orgId, String bundleName, String applicationName, JsonObject payload) {
        addEmailAggregation(orgId, bundleName, applicationName, null, payload);
    }

    private void addEmailAggregation(String orgId, String bundleName, String applicationName, String eventType, JsonObject payload) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setOrgId(orgId);
        aggregation.setBundleName(bundleName);
        aggregation.setApplicationName(applicationName);
        aggregation.setEventType(eventType);
        aggregation.setPayload(payload);

        resourceHelpers.addEmailAggregation(aggregation);
    }

}
