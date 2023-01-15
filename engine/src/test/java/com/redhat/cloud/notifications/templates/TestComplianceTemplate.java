package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.templates.models.Environment;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestComplianceTemplate {

    @Inject
    Environment environment;

    @Test
    public void testInstantComplianceBelowThresholdEmailBody() {
        Action action = TestHelpers.createComplianceAction();
        String result = Compliance.Templates.complianceBelowThresholdEmailBody()
                .data("action", action)
                .data("environment", environment)
                .render();
        assertTrue(result.contains(action.getEvents().get(0).getPayload().getAdditionalProperties().get("policy_id").toString()));
    }

    @Test
    public void testInstantComplianceBelowThresholdEmailTitle() {
        Action action = TestHelpers.createComplianceAction();
        String result = Compliance.Templates.complianceBelowThresholdEmailTitle()
            .data("action", action)
            .data("environment", environment)
            .render();
        assertTrue(result.contains("is non-compliant with policy"));
    }

    @Test
    public void testInstantComplianceBelowThresholdEmailBodyV2() {
        Action action = TestHelpers.createComplianceAction();
        String result = Compliance.Templates.complianceBelowThresholdEmailBodyV2()
            .data("action", action)
            .data("environment", environment)
            .render();
        assertTrue(result.contains(action.getEvents().get(0).getPayload().getAdditionalProperties().get("policy_id").toString()));
    }

    @Test
    public void testInstantComplianceBelowThresholdEmailTitleV2() {
        Action action = TestHelpers.createComplianceAction();
        String result = Compliance.Templates.complianceBelowThresholdEmailTitleV2()
            .data("action", action)
            .data("environment", environment)
            .render();
        assertEquals("Instant notification - Compliance - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testInstantReportUploadFailedEmailBody() {
        Action action = TestHelpers.createComplianceAction();
        String result = Compliance.Templates.reportUploadFailedEmailBody()
            .data("action", action)
            .data("environment", environment)
            .render();
        assertTrue(result.contains(action.getEvents().get(0).getPayload().getAdditionalProperties().get("error").toString()));
    }

    @Test
    public void testInstantReportUploadFailedEmailTitle() {
        Action action = TestHelpers.createComplianceAction();
        String result = Compliance.Templates.reportUploadFailedEmailTitle()
            .data("action", action)
            .data("environment", environment)
            .render();
        assertTrue(result.contains("Failed to upload report from system"));
    }

    @Test
    public void testInstantReportUploadFailedEmailBodyV2() {
        Action action = TestHelpers.createComplianceAction();
        String result = Compliance.Templates.reportUploadFailedEmailBodyV2()
            .data("action", action)
            .data("environment", environment)
            .render();
        assertTrue(result.contains(action.getEvents().get(0).getPayload().getAdditionalProperties().get("error").toString()));
    }

    @Test
    public void testInstantReportUploadFailedEmailTitleV2() {
        Action action = TestHelpers.createComplianceAction();
        String result = Compliance.Templates.reportUploadFailedEmailTitleV2()
            .data("action", action)
            .data("environment", environment)
            .render();
        assertEquals("Instant notification - Compliance - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testDailyReportEmailBody() {
        Action action = TestHelpers.createComplianceAction();
        String result = Compliance.Templates.dailyEmailBody()
            .data("action", action)
            .data("environment", environment)
            .render();
        assertTrue(result.contains("Red Hat Insights has identified one or more systems"));
    }

    @Test
    public void testDailyReportEmailTitle() {
        Action action = TestHelpers.createComplianceAction();
        String result = Compliance.Templates.dailyEmailTitle()
            .data("action", action)
            .data("environment", environment)
            .render();
        assertTrue(result.contains("Insights Compliance findings that require your attention"));
    }

    @Test
    public void testDailyReportEmailBodyV2() {
        Action action = TestHelpers.createComplianceAction();
        String result = Compliance.Templates.dailyEmailBodyV2()
            .data("action", action)
            .data("environment", environment)
            .render();
        assertTrue(result.contains("Red Hat Insights has identified one or more systems"));
    }

    @Test
    public void testDailyReportEmailTitleV2() {
        Action action = TestHelpers.createComplianceAction();
        String result = Compliance.Templates.dailyEmailTitleV2()
            .data("action", action)
            .data("environment", environment)
            .render();
        assertEquals("Daily digest - Compliance - Red Hat Enterprise Linux", result);
    }
}
