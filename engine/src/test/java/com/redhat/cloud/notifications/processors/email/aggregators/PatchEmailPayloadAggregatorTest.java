package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.PatchTestHelpers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

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
    void emptyAggregatorHasNoAccountId() {
        Assertions.assertNull(aggregator.getAccountId(), "Empty aggregator has no accountId");
    }

    @Test
    void shouldSetAccountNumber() {
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(tenant, bundle, application, "advisory", bugfix, "inventoryId"));
        Assertions.assertEquals("tenant", aggregator.getAccountId());
    }

    @Test
    void validatePayload() {
        LocalDateTime startTime = LocalDateTime.of(2021, 4, 22, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 4, 22, 14, 15, 33);

        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(tenant, bundle, application, "advisory_1", security, "host-01"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(tenant, bundle, application, "advisory_1", security, "host-01"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(tenant, bundle, application, "advisory_2", enhancement, "host-01"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(tenant, bundle, application, "advisory_3", enhancement, "host-02"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(tenant, bundle, application, "advisory_4", bugfix, "host-03"));
        Assertions.assertEquals(PatchTestHelpers.getUniqueHostForAdvisoryType(aggregator, security), 1);
        Assertions.assertEquals(PatchTestHelpers.getUniqueHostForAdvisoryType(aggregator, enhancement), 2);
        Assertions.assertEquals(PatchTestHelpers.getUniqueHostForAdvisoryType(aggregator, bugfix), 1);
        Assertions.assertEquals(PatchTestHelpers.getUniqueHost(aggregator), 3);

        Set<String> secAdvs = PatchTestHelpers.getAdvisoriesByType(aggregator, security);
        Assertions.assertEquals(secAdvs.size(), 1);
        Assertions.assertTrue(secAdvs.iterator().next().equals("advisory_1"));

        Set<String> enhAdvs = PatchTestHelpers.getAdvisoriesByType(aggregator, enhancement);
        Assertions.assertEquals(enhAdvs.size(), 2);
        Assertions.assertTrue(enhAdvs.iterator().next().equals("advisory_2"));

        Set<String> fixAdvs = PatchTestHelpers.getAdvisoriesByType(aggregator, bugfix);
        Assertions.assertEquals(fixAdvs.size(), 1);
        Assertions.assertTrue(fixAdvs.iterator().next().equals("advisory_4"));

        Map<String, Object> patch = aggregator.getContext();
        patch.put("start_time", startTime.toString());
        patch.put("end_time", endTime.toString());
    }

    @Test
    void validatePayloadMultipleEvents() {
        aggregator.aggregate(PatchTestHelpers.createEmailAggregationMultipleEvents(tenant, bundle, application));
        Set<String> enhAdvs = PatchTestHelpers.getAdvisoriesByType(aggregator, enhancement);
        Assertions.assertTrue(enhAdvs.iterator().next().equals("RH-1"));
        Set<String> fixAdvs = PatchTestHelpers.getAdvisoriesByType(aggregator, bugfix);
        Assertions.assertTrue(fixAdvs.iterator().next().equals("RH-2"));
    }
}
