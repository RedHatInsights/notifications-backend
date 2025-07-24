package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.DailyEmailAggregationJob;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.AggregationOrgConfig;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventAggregationCriterion;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

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

    @Inject
    DailyEmailAggregationJob dailyEmailAggregationJob;

    void configureTimePref(LocalDateTime localDateTime) {
        final AggregationOrgConfig orgPrefDef = new AggregationOrgConfig(ORG_ID,
            localDateTime.toLocalTime(),
            LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        resourceHelpers.addAggregationOrgConfig(orgPrefDef);
        orgPrefDef.setOrgId("other-org-id");
        resourceHelpers.addAggregationOrgConfig(orgPrefDef);
    }

    @AfterEach
    void afterEach() {
        resourceHelpers.purgeAggregationOrgConfig();
    }

    @Test
    void testApplicationsWithPendingAggregationAccordingOrgPref() {
        configureTimePref(dailyEmailAggregationJob.computeScheduleExecutionTime());
        Event event1 = resourceHelpers.addEventEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, dailyEmailAggregationJob.computeScheduleExecutionTime().minusMinutes(5), PAYLOAD1.toString());
        Event event2 = resourceHelpers.addEventEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, dailyEmailAggregationJob.computeScheduleExecutionTime().minusMinutes(5), PAYLOAD2.toString());
        resourceHelpers.addEventEmailAggregation("other-org-id", BUNDLE_NAME, APP_NAME, dailyEmailAggregationJob.computeScheduleExecutionTime().minusMinutes(5), PAYLOAD2.toString());
        resourceHelpers.addEventEmailAggregation(ORG_ID, "other-bundle", APP_NAME, dailyEmailAggregationJob.computeScheduleExecutionTime().minusMinutes(5), PAYLOAD2.toString());
        resourceHelpers.addEventEmailAggregation(ORG_ID, BUNDLE_NAME, "other-app", dailyEmailAggregationJob.computeScheduleExecutionTime().minusMinutes(5), PAYLOAD2.toString());

        List<AggregationCommand> keys = emailAggregationResources.getApplicationsWithPendingAggregationAccordingOrgPref(dailyEmailAggregationJob.computeScheduleExecutionTime());
        assertEquals(4, keys.size());
        Application application = resourceHelpers.findApp(BUNDLE_NAME, APP_NAME);

        List<AggregationCommand> matchedKeys = keys.stream().filter(k -> ORG_ID.equals(k.getOrgId())).filter(k -> (((EventAggregationCriterion) k.getAggregationKey()).getApplicationId().equals(application.getId()))).collect(Collectors.toList());
        assertEquals(1, matchedKeys.size());
        assertEquals(BUNDLE_NAME,  matchedKeys.get(0).getAggregationKey().getBundle());
        assertEquals(APP_NAME, matchedKeys.get(0).getAggregationKey().getApplication());

        resourceHelpers.deleteEvent(event1);
        resourceHelpers.deleteEvent(event2);

        keys = emailAggregationResources.getApplicationsWithPendingAggregationAccordingOrgPref(dailyEmailAggregationJob.computeScheduleExecutionTime());
        assertEquals(3, keys.size());
        matchedKeys = keys.stream().filter(k -> ORG_ID.equals(k.getOrgId())).filter(k -> (((EventAggregationCriterion) k.getAggregationKey()).getApplicationId().equals(application.getId()))).collect(Collectors.toList());
        assertEquals(0, matchedKeys.size());
    }
}
