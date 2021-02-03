package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.processors.email.aggregators.PoliciesEmailPayloadAggregator;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class PoliciesEmailPayloadAggregatorTest {

    private EmailAggregation createEmailAggregation(String tenant, String bundle, String application, String policyId, String insightsId) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundle(bundle);
        aggregation.setApplication(application);
        aggregation.setAccountId(tenant);

        JsonObject payload = new JsonObject();
        payload.put("policy_id", policyId);
        payload.put("policy_name", "not-used-name");
        payload.put("policy_description", "not-used-desc");
        payload.put("policy_condition", "not-used-condition");
        payload.put("display_name", "not-used-display-name");
        payload.put("insights_id", insightsId);
        payload.put("tags", new JsonArray());

        aggregation.setPayload(payload);

        return aggregation;
    }

    private Integer getUniqueHostForPolicy(PoliciesEmailPayloadAggregator aggregator, String policy) {
        Map<String, Map> policies = (Map<String, Map>) aggregator.getPayload().get("policies");
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
        aggregator.aggregate(createEmailAggregation("tenant", "insights", "policies", "policy-01", "host-01"));
        Assertions.assertEquals("tenant", aggregator.getAccountId());

        // 1 host
        Assertions.assertEquals(1, aggregator.getUniqueHostCount());

        aggregator.aggregate(createEmailAggregation("tenant", "insights", "policies", "policy-02", "host-01"));

        // 1 host (even if two policies)
        Assertions.assertEquals(1, aggregator.getUniqueHostCount());
        Assertions.assertEquals(1, getUniqueHostForPolicy(aggregator, "policy-01"));
        Assertions.assertEquals(1, getUniqueHostForPolicy(aggregator, "policy-02"));

        aggregator.aggregate(createEmailAggregation("tenant", "insights", "policies", "policy-03", "host-02"));
        aggregator.aggregate(createEmailAggregation("tenant", "insights", "policies", "policy-03", "host-03"));

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
            aggregator.aggregate(createEmailAggregation("tenant1", "insights", "policies", "policy-02", "host-01"));
            aggregator.aggregate(createEmailAggregation("tenant2", "insights", "policies", "policy-02", "host-01"));
        });
    }
}
