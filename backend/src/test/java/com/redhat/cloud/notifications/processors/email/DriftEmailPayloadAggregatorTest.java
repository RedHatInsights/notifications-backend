package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.DriftTestHelpers;
import com.redhat.cloud.notifications.processors.email.aggregators.DriftEmailPayloadAggregator;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DriftEmailPayloadAggregatorTest {

    private DriftEmailPayloadAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new DriftEmailPayloadAggregator();
    }

    @Test
    void emptyAggregatorHasNoAccountId() {
        Assertions.assertNull(aggregator.getAccountId(), "Empty aggregator has no accountId");
    }

    @Test
    void shouldHaveOneSingleHost() {
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_01", "host-01"));
        Assertions.assertEquals("tenant", aggregator.getAccountId());

        // 1 host
        Assertions.assertEquals(1, aggregator.getUniqueHostCount());
    }

    @Test
    void validatePayload() {
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_01", "host-01"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_01", "host-02"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_02", "host-01"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_02", "host-03"));
        System.out.println(JsonObject.mapFrom(aggregator.getContext()).toString());
        Assertions.assertEquals(1, 1);
    }
    /*private Integer getUniqueHostForPolicy(PoliciesEmailPayloadAggregator aggregator, String policy) {
        Map<String, Map> policies = (Map<String, Map>) aggregator.getContext().get("policies");
        return (Integer) policies.get(policy).get("unique_system_count");
    }*/
}
