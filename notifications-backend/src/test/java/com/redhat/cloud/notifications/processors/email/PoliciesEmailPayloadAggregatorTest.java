package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.processors.email.aggregators.PoliciesEmailPayloadAggregator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class PoliciesEmailPayloadAggregatorTest {

    private Integer getUniqueHostForPolicy(PoliciesEmailPayloadAggregator aggregator, String policy) {
        Map<String, Map> policies = (Map<String, Map>) aggregator.getContext().get("policies");
        return (Integer) policies.get(policy).get("unique_system_count");
    }

    @Test
    void emptyAggregatorHasNoAccountId() {
        PoliciesEmailPayloadAggregator aggregator = new PoliciesEmailPayloadAggregator();
        Assertions.assertEquals(null, aggregator.getAccountId(), "Empty aggregator has no accountId");
    }

    @Test
    void aggregatorTests() {
        PoliciesEmailPayloadAggregator aggregator = new PoliciesEmailPayloadAggregator();
        aggregator.aggregate(TestHelpers.createEmailAggregation("tenant", "insights", "policies", "policy-01", "host-01"));
        Assertions.assertEquals("tenant", aggregator.getAccountId());

        // 1 host
        Assertions.assertEquals(1, aggregator.getUniqueHostCount());

        aggregator.aggregate(TestHelpers.createEmailAggregation("tenant", "insights", "policies", "policy-02", "host-01"));

        // 1 host (even if two policies)
        Assertions.assertEquals(1, aggregator.getUniqueHostCount());
        Assertions.assertEquals(1, getUniqueHostForPolicy(aggregator, "policy-01"));
        Assertions.assertEquals(1, getUniqueHostForPolicy(aggregator, "policy-02"));

        aggregator.aggregate(TestHelpers.createEmailAggregation("tenant", "insights", "policies", "policy-03", "host-02"));
        aggregator.aggregate(TestHelpers.createEmailAggregation("tenant", "insights", "policies", "policy-03", "host-03"));

        // 3 hosts
        Assertions.assertEquals(3, aggregator.getUniqueHostCount());
        Assertions.assertEquals(1, getUniqueHostForPolicy(aggregator, "policy-01"));
        Assertions.assertEquals(1, getUniqueHostForPolicy(aggregator, "policy-02"));
        Assertions.assertEquals(2, getUniqueHostForPolicy(aggregator, "policy-03"));
    }

    @Test
    void emailWithDifferentTenantThrowsError() {
        PoliciesEmailPayloadAggregator aggregator = new PoliciesEmailPayloadAggregator();

        Assertions.assertThrows(RuntimeException.class, () -> {
            aggregator.aggregate(TestHelpers.createEmailAggregation("tenant1", "insights", "policies", "policy-02", "host-01"));
            aggregator.aggregate(TestHelpers.createEmailAggregation("tenant2", "insights", "policies", "policy-02", "host-01"));
        });
    }
}
