package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.PatchTestHelpers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

public class PatchEmailPayloadAggregatorTest {

    private PatchEmailPayloadAggregator aggregator;
    private final String tenant = "tenant";
    private final String bundle = "rhel";
    private final String application = "patch";
    private final String enhancement = "enhancement";
    private final String bugfix = "bugfix";
    private final String security = "security";

    @BeforeEach
    void setUp() {
        aggregator = new PatchEmailPayloadAggregator();
    }

    @Test
    void emptyAggregatorHasNoAccountIdOrOrgId() {
        Assertions.assertNull(aggregator.getAccountId(), "Empty aggregator has no accountId");
        Assertions.assertNull(aggregator.getOrgId(), "Empty aggregator has no orgId");
    }

    @Test
    void shouldSetAccountNumberAndOrgId() {
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(tenant, bundle, application, "advisory", bugfix, "inventoryId"));
        Assertions.assertEquals("tenant", aggregator.getAccountId());
        Assertions.assertEquals(DEFAULT_ORG_ID, aggregator.getOrgId());
    }

    @Test
    void validatePayload() {
        LocalDateTime startTime = LocalDateTime.of(2021, 4, 22, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 4, 22, 14, 15, 33);

        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(tenant, bundle, application, "advisory_1", security, "host-01"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(tenant, bundle, application, "advisory_2", enhancement, "host-01"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(tenant, bundle, application, "advisory_3", enhancement, "host-02"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(tenant, bundle, application, "advisory_4", bugfix, "host-03"));

        ArrayList<String> secAdvs = PatchTestHelpers.getAdvisoriesByType(aggregator, security);
        Assertions.assertEquals(1, secAdvs.size());
        Assertions.assertTrue(secAdvs.get(0).equals("advisory_1"));

        ArrayList<String> enhAdvs = PatchTestHelpers.getAdvisoriesByType(aggregator, enhancement);
        Assertions.assertEquals(2, enhAdvs.size());
        Assertions.assertTrue(enhAdvs.get(0).equals("advisory_2"));
        Assertions.assertTrue(enhAdvs.get(1).equals("advisory_3"));

        ArrayList<String> fixAdvs = PatchTestHelpers.getAdvisoriesByType(aggregator, bugfix);
        Assertions.assertEquals(1, fixAdvs.size());
        Assertions.assertTrue(fixAdvs.get(0).equals("advisory_4"));

        Map<String, Object> patch = aggregator.getContext();
        patch.put("start_time", startTime.toString());
        patch.put("end_time", endTime.toString());
    }

    @Test
    void validatePayloadMultipleEvents() {
        aggregator.aggregate(PatchTestHelpers.createEmailAggregationMultipleEvents(tenant, bundle, application));
        ArrayList<String> enhAdvs = PatchTestHelpers.getAdvisoriesByType(aggregator, enhancement);
        Assertions.assertTrue(enhAdvs.get(0).equals("RH-1"));
        ArrayList<String> fixAdvs = PatchTestHelpers.getAdvisoriesByType(aggregator, bugfix);
        Assertions.assertTrue(fixAdvs.get(0).equals("RH-2"));
    }
}
