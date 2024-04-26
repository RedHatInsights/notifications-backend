package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.AggregationOrgConfig;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class EmailAggregationRepositoryTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final String ORG_ID = "987654321";
    private static final String BUNDLE_NAME = "best-bundle";
    private static final String APP_NAME = "awesome-app";
    private static final JsonObject PAYLOAD1 = new JsonObject("{\"foo\":\"bar\"}");
    private static final JsonObject PAYLOAD2 = new JsonObject("{\"hello\":\"world\"}");

    @Inject
    EmailAggregationRepository emailAggregationResources;

    @Inject
    ResourceHelpers resourceHelpers;

    @Test
    void testApplicationsWithPendingAggregationAccordinfOrgPref() {
        final AggregationOrgConfig orgPrefDef = new AggregationOrgConfig(ORG_ID,
            LocalTime.of(LocalTime.now(ZoneOffset.UTC).getHour(), LocalTime.now(ZoneOffset.UTC).getMinute()),
            LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        resourceHelpers.addAggregationOrgConfig(orgPrefDef);
        orgPrefDef.setOrgId("other-org-id");
        resourceHelpers.addAggregationOrgConfig(orgPrefDef);

        LocalDateTime end = LocalDateTime.now(UTC).plusMinutes(10);

        addEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, PAYLOAD1);
        addEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, PAYLOAD2);
        addEmailAggregation("other-org-id", BUNDLE_NAME, APP_NAME, PAYLOAD2);
        addEmailAggregation(ORG_ID, "other-bundle", APP_NAME, PAYLOAD2);
        addEmailAggregation(ORG_ID, BUNDLE_NAME, "other-app", PAYLOAD2);

        EmailAggregationKey key = new EmailAggregationKey(ORG_ID, BUNDLE_NAME, APP_NAME);

        List<AggregationCommand> keys = emailAggregationResources.getApplicationsWithPendingAggregationAccordinfOrgPref(end);
        assertEquals(4, keys.size());
        assertEquals(ORG_ID, keys.get(0).getOrgId());
        assertEquals(BUNDLE_NAME, keys.get(0).getAggregationKey().getBundle());
        assertEquals(APP_NAME, keys.get(0).getAggregationKey().getApplication());

        Integer purged = resourceHelpers.purgeOldAggregation(key, end);
        assertEquals(2, purged);

        keys = emailAggregationResources.getApplicationsWithPendingAggregationAccordinfOrgPref(end);
        assertEquals(3, keys.size());
    }

    private void addEmailAggregation(String orgId, String bundleName, String applicationName, JsonObject payload) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setOrgId(orgId);
        aggregation.setBundleName(bundleName);
        aggregation.setApplicationName(applicationName);
        aggregation.setPayload(payload);

        resourceHelpers.addEmailAggregation(aggregation);
    }
}
