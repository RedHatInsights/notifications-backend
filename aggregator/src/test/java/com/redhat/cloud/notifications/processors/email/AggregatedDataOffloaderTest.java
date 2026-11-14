package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EventAggregationCriterion;
import com.redhat.cloud.notifications.processors.payload.PayloadDetails;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class AggregatedDataOffloaderTest {

    private static final String ORG_ID = "offloader-test-org";
    private static final String BUNDLE = "rhel";
    private static final String APP = "compliance";

    @Inject
    AggregatedDataOffloader aggregatedDataOffloader;

    @Inject
    ResourceHelpers resourceHelpers;

    @AfterEach
    void afterEach() {
        resourceHelpers.purgeEventAggregations();
        resourceHelpers.purgeEndpoints();
    }

    @Test
    void shouldNotExceedLimitForASmallPayload() {
        assertFalse(aggregatedDataOffloader.exceedsMaxRequestSize("{}"));
    }

    @Test
    void shouldExceedLimitForAPayloadBiggerThanTheConfiguredLimit() {
        String huge = "x".repeat(2 * 1024 * 1024);
        assertTrue(aggregatedDataOffloader.exceedsMaxRequestSize(huge));
    }

    @Test
    void shouldSaveCombinedDataAnchoredToAnAlreadyPersistedEvent() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        resourceHelpers.addEventEmailAggregation(ORG_ID, BUNDLE, APP, now.minusHours(1), "{}", false);

        EventAggregationCriterion criterion = criterionFor(APP);
        Map<String, Map<String, Object>> combined = Map.of(APP, Map.of("compliance", Map.of("report-upload-failed", "data")));

        UUID payloadDetailsId = aggregatedDataOffloader.offload(combined, criterion, now.minusDays(1), now.plusMinutes(1));

        PayloadDetails saved = resourceHelpers.findPayloadDetailsById(payloadDetailsId);
        assertEquals(JsonObject.mapFrom(combined).encode(), saved.getContents());
    }

    @Test
    void shouldThrowWhenNoEventExistsToAnchorTo() {
        EventAggregationCriterion criterion = criterionFor(APP);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        assertThrows(IllegalStateException.class,
            () -> aggregatedDataOffloader.offload(Map.of(APP, Map.of()), criterion, now.minusDays(1), now));
    }

    private EventAggregationCriterion criterionFor(String applicationName) {
        Application application = resourceHelpers.findOrCreateApplication(BUNDLE, applicationName);
        return new EventAggregationCriterion(ORG_ID, application.getBundleId(), application.getId(), BUNDLE, applicationName);
    }
}
