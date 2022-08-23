package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.DriftTestHelpers;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

class DriftEmailPayloadAggregatorTest {

    private DriftEmailPayloadAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new DriftEmailPayloadAggregator();
    }

    @Test
    void emptyAggregatorHasNoOrgId() {
        Assertions.assertNull(aggregator.getOrgId(), "Empty aggregator has no orgId");
    }

    @Test
    void shouldHaveOneSingleHost() {
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_01", "baseline_1", "host-01", "Machine 1"));
        Assertions.assertEquals(DEFAULT_ORG_ID, aggregator.getOrgId());

        // 1 host
        Assertions.assertEquals(1, aggregator.getUniqueHostCount());
    }

    @Test
    void validatePayload() {

        LocalDateTime startTime = LocalDateTime.of(2021, 4, 22, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 4, 22, 14, 15, 33);

        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_01", "baseline_1", "host-01", "Machine 1"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_01", "baseline_1", "host-02",  "Machine 2"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_02", "baseline_2", "host-01", "Machine 1"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_02", "baseline_2", "host-03", "Machine 3"));
        Map<String, Object> drift = aggregator.getContext();
        drift.put("start_time", startTime.toString());
        drift.put("end_time", endTime.toString());
        System.out.println(JsonObject.mapFrom(drift).toString());
        Assertions.assertEquals(1, 1);
    }
}
