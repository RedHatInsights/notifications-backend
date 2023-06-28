package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.DriftTestHelpers;
import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.processors.email.aggregators.DriftEmailPayloadAggregator;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestDriftTemplate extends EmailTemplatesInDbHelper  {

    private DriftEmailPayloadAggregator aggregator;

    private static final String EVENT_TYPE_NAME = "drift-baseline-detected";

    @BeforeEach
    void setUp() {
        aggregator = new DriftEmailPayloadAggregator();
    }

    @Override
    protected String getApp() {
        return "drift";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(EVENT_TYPE_NAME);
    }

    @Test
    public void testInstantEmailTitle() {
        Action action = DriftTestHelpers.createDriftAction("rhel", "drift", "host-01", "Machine 1");
        String result = generateEmailSubject(EVENT_TYPE_NAME, action);
        assertEquals("Instant notification - Drift - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testInstantEmailBody() {
        Action action = DriftTestHelpers.createDriftAction("rhel", "drift", "host-01", "Machine 1");
        String result = generateEmailBody(EVENT_TYPE_NAME, action);
        assertTrue(result.contains("baseline_01"));
        assertTrue(result.contains("Machine 1"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testDailyEmailTitle() {
        LocalDateTime startTime = LocalDateTime.of(2021, 7, 14, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 7, 14, 14, 15, 33);

        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_01", "baseline_1", "host-01", "Machine 1"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_02", "baseline_2", "host-02", "Machine 2"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_03", "baseline_3", "host-01", "Machine 1"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_03", "baseline_3", "host-01", "Machine 1"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_03", "baseline_3", "host-01", "Machine 1"));
        Map<String, Object> drift = aggregator.getContext();
        drift.put("start_time", startTime.toString());
        drift.put("end_time", endTime.toString());

        String result = generateAggregatedEmailSubject(drift);
        assertEquals("Daily digest - Drift - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testDailyEmailBodyOneBaselineAndOneSystem() {

        LocalDateTime startTime = LocalDateTime.of(2021, 4, 22, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 4, 22, 14, 15, 33);

        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_01", "baseline_1", "host-01", "Machine 1"));
        Map<String, Object> drift = aggregator.getContext();
        drift.put("start_time", startTime.toString());
        drift.put("end_time", endTime.toString());

        String result = generateAggregatedEmailBody(drift);
        assertTrue(result.contains("baseline_01"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testDailyEmailBodyMultipleBaselineAndOneSystem() {

        LocalDateTime startTime = LocalDateTime.of(2021, 4, 22, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 4, 22, 14, 15, 33);

        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_01", "baseline_1", "host-01", "Machine 1"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_02", "baseline_2", "host-01", "Machine 1"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_03", "baseline_3", "host-01", "Machine 1"));
        Map<String, Object> drift = aggregator.getContext();
        drift.put("start_time", startTime.toString());
        drift.put("end_time", endTime.toString());

        String result = generateAggregatedEmailBody(drift);
        assertTrue(result.contains("baseline_01"));
        assertTrue(result.contains("baseline_02"));
        assertTrue(result.contains("baseline_03"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testDailyEmailBodyOneBaselineAndMultipleSystem() {

        LocalDateTime startTime = LocalDateTime.of(2021, 7, 14, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 7, 14, 14, 15, 33);

        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_01", "baseline_1", "host-01", "Machine 1"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_01", "baseline_1", "host-02", "Machine 2"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_01", "baseline_1", "host-03", "Machine 3"));
        Map<String, Object> drift = aggregator.getContext();
        drift.put("start_time", startTime.toString());
        drift.put("end_time", endTime.toString());

        String result = generateAggregatedEmailBody(drift);
        assertTrue(result.contains("baseline_01"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testDailyEmailBodyMultipleBaselineAndMultipleSystem() {

        LocalDateTime startTime = LocalDateTime.of(2021, 7, 14, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 7, 14, 14, 15, 33);

        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_01", "baseline_1", "host-01", "Machine 1"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_02", "baseline_2", "host-02", "Machine 2"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_03", "baseline_3", "host-01", "Machine 1"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_03", "baseline_3", "host-01", "Machine 1"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_03", "baseline_3", "host-01", "Machine 1"));
        Map<String, Object> drift = aggregator.getContext();
        drift.put("start_time", startTime.toString());
        drift.put("end_time", endTime.toString());

        String result = generateAggregatedEmailBody(drift);
        assertTrue(result.contains("baseline_01"));
        assertTrue(result.contains("baseline_02"));
        assertTrue(result.contains("baseline_03"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
