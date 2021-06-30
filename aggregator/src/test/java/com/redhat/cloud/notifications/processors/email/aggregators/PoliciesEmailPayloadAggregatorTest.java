package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.TestHelpers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PoliciesEmailPayloadAggregatorTest {

    private PoliciesEmailPayloadAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new PoliciesEmailPayloadAggregator();
    }

    @Test
    void emptyAggregatorHasNoAccountId() {
        assertNull(aggregator.getAccountId(), "Empty aggregator has no accountId");
    }

    @Test
    void shouldHaveOneSingleHost() {
        aggregator.aggregate(TestHelpers.createEmailAggregation("tenant", "insights", "policies", "policy-01", "host-01"));
        assertEquals("tenant", aggregator.getAccountId());

        // 1 host
        assertEquals(1, aggregator.getUniqueHostCount());
    }

    @Test
    void shouldHaveOneSingleHostAndTwoDifferentPolicies() {
        aggregator.aggregate(TestHelpers.createEmailAggregation("tenant", "insights", "policies", "policy-01", "host-01"));
        aggregator.aggregate(TestHelpers.createEmailAggregation("tenant", "insights", "policies", "policy-02", "host-01"));

        // 1 host (even if two policies)
        assertEquals(1, aggregator.getUniqueHostCount());
        assertEquals(1, getUniqueHostForPolicy(aggregator, "policy-01"));
        assertEquals(1, getUniqueHostForPolicy(aggregator, "policy-02"));
    }

    @Test
    void shouldHaveUniqueHostsOnly() {
        aggregator.aggregate(TestHelpers.createEmailAggregation("tenant", "insights", "policies", "policy-01", "host-01"));
        aggregator.aggregate(TestHelpers.createEmailAggregation("tenant", "insights", "policies", "policy-02", "host-01"));
        aggregator.aggregate(TestHelpers.createEmailAggregation("tenant", "insights", "policies", "policy-03", "host-02"));
        aggregator.aggregate(TestHelpers.createEmailAggregation("tenant", "insights", "policies", "policy-03", "host-03"));

        // 3 hosts
        assertEquals(3, aggregator.getUniqueHostCount());
    }

    @Test
    void shouldReturnUniqueHostsForPolicies() {
        aggregator.aggregate(TestHelpers.createEmailAggregation("tenant", "insights", "policies", "policy-01", "host-01"));
        aggregator.aggregate(TestHelpers.createEmailAggregation("tenant", "insights", "policies", "policy-02", "host-01"));
        aggregator.aggregate(TestHelpers.createEmailAggregation("tenant", "insights", "policies", "policy-03", "host-02"));
        aggregator.aggregate(TestHelpers.createEmailAggregation("tenant", "insights", "policies", "policy-03", "host-03"));

        assertEquals(1, getUniqueHostForPolicy(aggregator, "policy-01"));
        assertEquals(1, getUniqueHostForPolicy(aggregator, "policy-02"));
        assertEquals(2, getUniqueHostForPolicy(aggregator, "policy-03"));
    }

    @Test
    void emailWithDifferentTenantThrowsError() {
        assertThrows(RuntimeException.class, () -> {
            aggregator.aggregate(TestHelpers.createEmailAggregation("tenant1", "insights", "policies", "policy-02", "host-01"));
            aggregator.aggregate(TestHelpers.createEmailAggregation("tenant2", "insights", "policies", "policy-02", "host-01"));
        });
    }

    private Integer getUniqueHostForPolicy(PoliciesEmailPayloadAggregator aggregator, String policy) {
        Map<String, Map> policies = (Map<String, Map>) aggregator.getContext().get("policies");
        return (Integer) policies.get(policy).get("unique_system_count");
    }
}
