package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.ComplianceTestHelpers;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

class ComplianceEmailAggregatorTest {

    private ComplianceEmailAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new ComplianceEmailAggregator();
    }

    @Test
    void emptyAggregatorHasNoAccountIdOrOrgId() {
        Assertions.assertNull(aggregator.getAccountId(), "Empty aggregator has no accountId");
        Assertions.assertNull(aggregator.getOrgId(), "Empty aggregator has no orgId");
    }

    @Test
    void shouldSetAccountNumberAndOrgId() {
        aggregator.aggregate(ComplianceTestHelpers.createEmailAggregation("tenant", "rhel", "compliance", "report-upload-failed", "policyId", "inventoryId"));
        Assertions.assertEquals("tenant", aggregator.getAccountId());
        Assertions.assertEquals(DEFAULT_ORG_ID, aggregator.getOrgId());
    }

    @Test
    void validatePayload() {
        aggregator.aggregate(ComplianceTestHelpers.createEmailAggregation("tenant", "rhel", "compliance", "foo", "policy0", "host0"));
        aggregator.aggregate(ComplianceTestHelpers.createEmailAggregation("tenant", "rhel", "compliance", "report-upload-failed", "policy1", "host1"));
        aggregator.aggregate(ComplianceTestHelpers.createEmailAggregation("tenant", "rhel", "compliance", "report-upload-failed", "policy2", "host2"));
        aggregator.aggregate(ComplianceTestHelpers.createEmailAggregation("tenant", "rhel", "compliance", "compliance-below-threshold", "policy3", "host3"));
        aggregator.aggregate(ComplianceTestHelpers.createEmailAggregation("tenant", "rhel", "compliance", "compliance-below-threshold", "policy4", "host4"));
        // aggregator.aggregate(ComplianceTestHelpers.createEmailAggregation("tenant", "rhel", "compliance", "system-not-reporting", "policy5", "host5"));
        // aggregator.aggregate(ComplianceTestHelpers.createEmailAggregation("tenant", "rhel", "compliance", "system-not-reporting", "policy6", "host6"));

        Map<String, Object> context = aggregator.getContext();
        JsonObject compliance = JsonObject.mapFrom(context).getJsonObject("compliance");
        System.out.println(compliance.toString());

        Assertions.assertFalse(compliance.containsKey("foo"));
        Assertions.assertEquals(compliance.getJsonArray("report-upload-failed").size(), 2);
        Assertions.assertEquals(compliance.getJsonArray("compliance-below-threshold").size(), 2);
        Assertions.assertEquals(compliance.getJsonArray("compliance-below-threshold").size(), 2);
        // Assertions.assertEquals(compliance.getJsonArray("system-not-reporting").size(), 2);
    }
}
