package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.DriftTestHelpers;
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
        assertTrue(result.contains("<b>1 baseline</b> drifted on <b>1 system</b>"));
        //writeEmailTemplate(result, "driftEmailOneOne.html");
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
        assertTrue(result.contains("<b>3 baselines</b> drifted on <b>1 system</b>"));
        //writeEmailTemplate(result, "drfitEmailMultOne.html");
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
        assertTrue(result.contains("<b>1 baseline</b> drifted on <b>3 unique systems</b>"));
        //writeEmailTemplate(result, "driftEmailOneMult.html");
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
        assertTrue(result.contains("<b>3 baselines</b> drifted on <b>2 unique systems</b>"));
        //writeEmailTemplate(result, "driftEmailMultMult.html");
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
