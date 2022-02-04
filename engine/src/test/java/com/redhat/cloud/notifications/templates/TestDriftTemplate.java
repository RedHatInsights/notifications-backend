package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.DriftTestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.processors.email.aggregators.DriftEmailPayloadAggregator;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestDriftTemplate {
    private DriftEmailPayloadAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new DriftEmailPayloadAggregator();
    }

    public void testInstantEmailTitle() {
        Action action = DriftTestHelpers.createDriftAction("tenant", "rhel", "drift", "host-01", "Machine 1");
        String result = Drift.Templates.newBaselineDriftInstantEmailTitle()
                .data("action", action)
                .render();
        assertTrue(result.contains("2 drifts from baseline detected on Machine 1"));
    }

    @Test
    public void testInstantEmailBody() {
        Action action = DriftTestHelpers.createDriftAction("tenant", "rhel", "drift", "host-01", "Machine 1");
        String result = Drift.Templates.newBaselineDriftInstantEmailBody()
                .data("action", action)
                .render();
        assertTrue(result.contains("baseline_01"));
        assertTrue(result.contains("Machine 1"));
        //writeEmailTemplate(result, "instantEmail.html");
    }

    @Test
    public void testDailyEmailTitle() {
        LocalDateTime startTime = LocalDateTime.of(2021, 7, 14, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 7, 14, 14, 15, 33);

        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_01", "baseline_1", "host-01", "Machine 1"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_02", "baseline_2", "host-02", "Machine 2"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_03", "baseline_3", "host-01", "Machine 1"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_03", "baseline_3", "host-01", "Machine 1"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_03", "baseline_3", "host-01", "Machine 1"));
        Map<String, Object> drift = aggregator.getContext();
        drift.put("start_time", startTime.toString());
        drift.put("end_time", endTime.toString());
        String result = Drift.Templates.dailyEmailTitle()
                .data("action", Map.of("context", drift))
                .render();
        //writeEmailTemplate(result, "driftEmailMultMult.html");
        assertTrue(result.contains("3 drifts from baseline detected on 2 unique system"));
    }

    @Test
    public void testDailyEmailBodyOneBaselineAndOneSystem() {

        LocalDateTime startTime = LocalDateTime.of(2021, 4, 22, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 4, 22, 14, 15, 33);

        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_01", "baseline_1", "host-01", "Machine 1"));
        Map<String, Object> drift = aggregator.getContext();
        drift.put("start_time", startTime.toString());
        drift.put("end_time", endTime.toString());
        String result = Drift.Templates.dailyEmailBody()
                .data("action", Map.of("context", drift))
                .render();
        //writeEmailTemplate(result, "driftEmailOneOne.html");
        assertTrue(result.contains("baseline_01"));
        //System.out.println(result);
    }

    @Test
    public void testDailyEmailBodyMultipleBaselineAndOneSystem() {

        LocalDateTime startTime = LocalDateTime.of(2021, 4, 22, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 4, 22, 14, 15, 33);

        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_01", "baseline_1", "host-01", "Machine 1"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_02", "baseline_2", "host-01", "Machine 1"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_03", "baseline_3", "host-01", "Machine 1"));
        Map<String, Object> drift = aggregator.getContext();
        drift.put("start_time", startTime.toString());
        drift.put("end_time", endTime.toString());
        String result = Drift.Templates.dailyEmailBody()
                .data("action", Map.of("context", drift))
                .render();
        //writeEmailTemplate(result, "drfitEmailMultOne.html");
        assertTrue(result.contains("baseline_01"));
        assertTrue(result.contains("baseline_02"));
        assertTrue(result.contains("baseline_03"));
        //System.out.println(result);
    }

    @Test
    public void testDailyEmailBodyOneBaselineAndMultipleSystem() {

        LocalDateTime startTime = LocalDateTime.of(2021, 7, 14, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 7, 14, 14, 15, 33);

        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_01", "baseline_1", "host-01", "Machine 1"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_01", "baseline_1", "host-02", "Machine 2"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_01", "baseline_1", "host-03", "Machine 3"));
        Map<String, Object> drift = aggregator.getContext();
        drift.put("start_time", startTime.toString());
        drift.put("end_time", endTime.toString());
        String result = Drift.Templates.dailyEmailBody()
                .data("action", Map.of("context", drift))
                .render();
        //writeEmailTemplate(result, "driftEmailOneMult.html");
        assertTrue(result.contains("baseline_01"));
        //System.out.println(result);
    }

    @Test
    public void testDailyEmailBodyMultipleBaselineAndMultipleSystem() {

        LocalDateTime startTime = LocalDateTime.of(2021, 7, 14, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 7, 14, 14, 15, 33);

        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_01", "baseline_1", "host-01", "Machine 1"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_02", "baseline_2", "host-02", "Machine 2"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_03", "baseline_3", "host-01", "Machine 1"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_03", "baseline_3", "host-01", "Machine 1"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("tenant", "rhel", "drift", "baseline_03", "baseline_3", "host-01", "Machine 1"));
        Map<String, Object> drift = aggregator.getContext();
        drift.put("start_time", startTime.toString());
        drift.put("end_time", endTime.toString());
        String result = Drift.Templates.dailyEmailBody()
                .data("action", Map.of("context", drift))
                .render();
        //writeEmailTemplate(result, "driftEmailMultMult.html");
        assertTrue(result.contains("baseline_01"));
        assertTrue(result.contains("baseline_02"));
        assertTrue(result.contains("baseline_03"));
    }

    public void writeEmailTemplate(String result, String fileName) {
        try {
            FileWriter writerObj = new FileWriter(fileName);
            writerObj.write(result);
            writerObj.close();
        } catch (IOException e) {
            System.out.println("An error occurred");
            e.printStackTrace();
        }
    }
}
