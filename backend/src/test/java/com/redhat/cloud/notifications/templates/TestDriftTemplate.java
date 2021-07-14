package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.DriftTestHelpers;
import com.redhat.cloud.notifications.processors.email.aggregators.DriftEmailPayloadAggregator;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

@QuarkusTest
public class TestDriftTemplate {
    private DriftEmailPayloadAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new DriftEmailPayloadAggregator();
    }

    @Test
    public void testDailyEmailBodyOneBaselineAndOneSystem() {

        LocalDateTime startTime = LocalDateTime.of(2021, 4, 22, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 4, 22, 14, 15, 33);

        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_01", "host-01"));
        Map<String, Object> drift = aggregator.getContext();
        drift.put("start_time", startTime.toString());
        drift.put("end_time", endTime.toString());
        String result = Drift.Templates.dailyEmailBody()
                .data("action", Map.of("context", drift))
                .render();
        //System.out.println(result);
    }

    @Test
    public void testDailyEmailBodyMultipleBaselineAndOneSystem() {

        LocalDateTime startTime = LocalDateTime.of(2021, 4, 22, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 4, 22, 14, 15, 33);

        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_01", "host-01"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_02", "host-01"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_03", "host-01"));
        Map<String, Object> drift = aggregator.getContext();
        drift.put("start_time", startTime.toString());
        drift.put("end_time", endTime.toString());
        String result = Drift.Templates.dailyEmailBody()
                .data("action", Map.of("context", drift))
                .render();
        //System.out.println(result);
    }

    @Test
    public void testDailyEmailBodyOneBaselineAndMultipleSystem() {

        LocalDateTime startTime = LocalDateTime.of(2021, 4, 22, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 4, 22, 14, 15, 33);

        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_01", "host-01"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_01", "host-02"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_01", "host-03"));
        Map<String, Object> drift = aggregator.getContext();
        drift.put("start_time", startTime.toString());
        drift.put("end_time", endTime.toString());
        String result = Drift.Templates.dailyEmailBody()
                .data("action", Map.of("context", drift))
                .render();
        System.out.println(result);
    }
}