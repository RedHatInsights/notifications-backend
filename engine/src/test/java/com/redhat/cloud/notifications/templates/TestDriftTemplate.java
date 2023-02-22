package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.DriftTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.processors.email.aggregators.DriftEmailPayloadAggregator;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestDriftTemplate {

    private static final boolean SHOULD_WRITE_ON_FILE_FOR_DEBUG = false;

    @Inject
    Environment environment;

    private DriftEmailPayloadAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new DriftEmailPayloadAggregator();
    }

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    Drift driftBean;

    @BeforeEach
    void beforeEach() {
        featureFlipper.setDriftEmailTemplatesV2Enabled(false);
    }

    @Test
    public void testInstantEmailTitle() {
        Action action = DriftTestHelpers.createDriftAction("rhel", "drift", "host-01", "Machine 1");
        String result = generateFromTemplate(driftBean.getTitle(null, EmailSubscriptionType.INSTANT), action);
        assertTrue(result.contains("2 drifts from baseline detected on 'Machine 1'"));

        featureFlipper.setDriftEmailTemplatesV2Enabled(true);
        result = generateFromTemplate(driftBean.getTitle(null, EmailSubscriptionType.INSTANT), action);
        assertEquals("Instant notification - Drift - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testInstantEmailBody() {
        Action action = DriftTestHelpers.createDriftAction("rhel", "drift", "host-01", "Machine 1");
        String result = generateFromTemplate(driftBean.getBody(null, EmailSubscriptionType.INSTANT), action);

        assertTrue(result.contains("baseline_01"));
        assertTrue(result.contains("Machine 1"));
        writeEmailTemplate(result, "instantEmail.html");

        // test template V2
        featureFlipper.setDriftEmailTemplatesV2Enabled(true);
        result = generateFromTemplate(driftBean.getBody(null, EmailSubscriptionType.INSTANT), action);
        assertTrue(result.contains("baseline_01"));
        assertTrue(result.contains("Machine 1"));
        writeEmailTemplate(result, "instantEmailV2.html");
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
        String result = generateFromTemplate(driftBean.getTitle(null, EmailSubscriptionType.DAILY), drift);

        writeEmailTemplate(result, "driftEmailMultMult.html");
        assertTrue(result.contains("3 drifts from baseline detected on 2 unique system"));

        // test template V2
        featureFlipper.setDriftEmailTemplatesV2Enabled(true);
        result = generateFromTemplate(driftBean.getTitle(null, EmailSubscriptionType.DAILY), drift);
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
        String result = generateFromTemplate(driftBean.getBody(null, EmailSubscriptionType.DAILY), drift);

        writeEmailTemplate(result, "driftEmailOneOne.html");
        assertTrue(result.contains("baseline_01"));

        // test template V2
        featureFlipper.setDriftEmailTemplatesV2Enabled(true);
        result = generateFromTemplate(driftBean.getBody(null, EmailSubscriptionType.DAILY), drift);

        writeEmailTemplate(result, "driftEmailOneOneV2.html");
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
        String result = generateFromTemplate(driftBean.getBody(null, EmailSubscriptionType.DAILY), drift);

        writeEmailTemplate(result, "drfitEmailMultOne.html");
        assertTrue(result.contains("baseline_01"));
        assertTrue(result.contains("baseline_02"));
        assertTrue(result.contains("baseline_03"));

        // test template V2
        featureFlipper.setDriftEmailTemplatesV2Enabled(true);
        result = generateFromTemplate(driftBean.getBody(null, EmailSubscriptionType.DAILY), drift);

        writeEmailTemplate(result, "drfitEmailMultOneV2.html");
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
        String result = generateFromTemplate(driftBean.getBody(null, EmailSubscriptionType.DAILY), drift);

        writeEmailTemplate(result, "driftEmailOneMult.html");
        assertTrue(result.contains("baseline_01"));

        // test template V2
        featureFlipper.setDriftEmailTemplatesV2Enabled(true);
        result = generateFromTemplate(driftBean.getBody(null, EmailSubscriptionType.DAILY), drift);
        writeEmailTemplate(result, "driftEmailOneMultV2.html");
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
        String result = generateFromTemplate(driftBean.getBody(null, EmailSubscriptionType.DAILY), drift);

        writeEmailTemplate(result, "driftEmailMultMult.html");
        assertTrue(result.contains("baseline_01"));
        assertTrue(result.contains("baseline_02"));
        assertTrue(result.contains("baseline_03"));

        // test template V2
        featureFlipper.setDriftEmailTemplatesV2Enabled(true);
        result = generateFromTemplate(driftBean.getBody(null, EmailSubscriptionType.DAILY), drift);
        writeEmailTemplate(result, "driftEmailMultMultV2.html");
        assertTrue(result.contains("baseline_01"));
        assertTrue(result.contains("baseline_02"));
        assertTrue(result.contains("baseline_03"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    private String generateFromTemplate(TemplateInstance templateInstance, Action action) {
        return templateInstance
            .data("action", action)
            .data("environment", environment)
            .data("user", Map.of("firstName", "Drift User", "lastName", "RHEL"))
            .render();
    }

    private String generateFromTemplate(TemplateInstance templateInstance, Map<String, Object> drift) {
        return templateInstance
            .data("action", Map.of("context", drift, "bundle", "rhel"))
            .data("environment", environment)
            .data("user", Map.of("firstName", "Drift User", "lastName", "RHEL"))
            .render();
    }

    public void writeEmailTemplate(String result, String fileName) {
        if (SHOULD_WRITE_ON_FILE_FOR_DEBUG) {
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
}
