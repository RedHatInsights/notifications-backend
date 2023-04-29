package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.PatchTestHelpers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

public class PatchEmailPayloadAggregatorTest {

    private PatchEmailPayloadAggregator aggregator;
    private final String bundle = "rhel";
    private final String application = "patch";
    private final String enhancement = "enhancement";
    private final String bugfix = "bugfix";
    private final String security = "security";
    private final String unspecified = "unspecified";
    private final String other = "other";

    @BeforeEach
    void setUp() {
        aggregator = new PatchEmailPayloadAggregator();
    }

    @Test
    void emptyAggregatorHasNoOrgId() {
        Assertions.assertNull(aggregator.getOrgId(), "Empty aggregator has no orgId");
    }

    @Test
    void shouldSetOrgId() {
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(bundle, application, "advisory", "test synopsis", bugfix, "inventoryId"));
        Assertions.assertEquals(DEFAULT_ORG_ID, aggregator.getOrgId());
    }

    @Test
    void validatePayload() {
        LocalDateTime startTime = LocalDateTime.of(2021, 4, 22, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 4, 22, 14, 15, 33);

        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(bundle, application, "advisory_1", "test synopsis", security, "host-01"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(bundle, application, "advisory_2", "test synopsis", enhancement, "host-01"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(bundle, application, "advisory_3", "test synopsis", enhancement, "host-02"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(bundle, application, "advisory_4", "test synopsis", bugfix, "host-03"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(bundle, application, "advisory_5", "test synopsis", unspecified, "host-04"));

        ArrayList<LinkedHashMap<String, String>> secAdvs = PatchTestHelpers.getAdvisoriesByType(aggregator, security);
        Assertions.assertEquals(1, secAdvs.size());
        Assertions.assertTrue(secAdvs.get(0).get("name").equals("advisory_1"));
        Assertions.assertTrue(secAdvs.get(0).get("synopsis").equals("test synopsis"));

        ArrayList<LinkedHashMap<String, String>> enhAdvs = PatchTestHelpers.getAdvisoriesByType(aggregator, enhancement);
        Assertions.assertEquals(2, enhAdvs.size());
        Assertions.assertTrue(enhAdvs.get(0).get("name").equals("advisory_2"));
        Assertions.assertTrue(enhAdvs.get(0).get("synopsis").equals("test synopsis"));
        Assertions.assertTrue(enhAdvs.get(1).get("name").equals("advisory_3"));
        Assertions.assertTrue(enhAdvs.get(1).get("synopsis").equals("test synopsis"));

        ArrayList<LinkedHashMap<String, String>> fixAdvs = PatchTestHelpers.getAdvisoriesByType(aggregator, bugfix);
        Assertions.assertEquals(1, fixAdvs.size());
        Assertions.assertTrue(fixAdvs.get(0).get("name").equals("advisory_4"));
        Assertions.assertTrue(fixAdvs.get(0).get("synopsis").equals("test synopsis"));

        ArrayList<LinkedHashMap<String, String>> otherAdvs = PatchTestHelpers.getAdvisoriesByType(aggregator, other);
        Assertions.assertEquals(1, otherAdvs.size());
        Assertions.assertTrue(otherAdvs.get(0).get("name").equals("advisory_5"));
        Assertions.assertTrue(otherAdvs.get(0).get("synopsis").equals("test synopsis"));

        Map<String, Object> patch = aggregator.getContext();
        patch.put("start_time", startTime.toString());
        patch.put("end_time", endTime.toString());

        //System.out.println(JsonObject.mapFrom(patch).toString());
    }

    @Test
    void validatePayloadMultipleEvents() {
        aggregator.aggregate(PatchTestHelpers.createEmailAggregationMultipleEvents(bundle, application));
        ArrayList<LinkedHashMap<String, String>> enhAdvs = PatchTestHelpers.getAdvisoriesByType(aggregator, enhancement);
        Assertions.assertTrue(enhAdvs.get(0).get("name").equals("RH-1"));
        Assertions.assertTrue(enhAdvs.get(0).get("synopsis").equals("synopsis"));
        ArrayList<LinkedHashMap<String, String>> fixAdvs = PatchTestHelpers.getAdvisoriesByType(aggregator, bugfix);
        Assertions.assertTrue(fixAdvs.get(0).get("name").equals("RH-2"));
        Assertions.assertTrue(fixAdvs.get(0).get("synopsis").equals("synopsis"));
    }
}
