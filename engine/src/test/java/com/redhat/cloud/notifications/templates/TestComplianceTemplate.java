package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.templates.models.Environment;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestComplianceTemplate {

    private static final Action ACTION = TestHelpers.createComplianceAction();

    @Inject
    Environment environment;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    Compliance compliance;

    @AfterEach
    void afterEach() {
        featureFlipper.setComplianceEmailTemplatesV2Enabled(false);
    }

    @Test
    public void testInstantComplianceBelowThresholdEmailBody() {
        String result = generateEmail(compliance.getBody(Compliance.COMPLIANCE_BELOW_THRESHOLD, EmailSubscriptionType.INSTANT));
        assertTrue(result.contains(ACTION.getEvents().get(0).getPayload().getAdditionalProperties().get("policy_id").toString()));

        // test template V2
        featureFlipper.setComplianceEmailTemplatesV2Enabled(true);
        result = generateEmail(compliance.getBody(Compliance.COMPLIANCE_BELOW_THRESHOLD, EmailSubscriptionType.INSTANT));
        assertTrue(result.contains(ACTION.getEvents().get(0).getPayload().getAdditionalProperties().get("policy_id").toString()));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantComplianceBelowThresholdEmailTitle() {
        String result = generateEmail(compliance.getTitle(Compliance.COMPLIANCE_BELOW_THRESHOLD, EmailSubscriptionType.INSTANT));
        assertTrue(result.contains("is non-compliant with policy"));

        // test template V2
        featureFlipper.setComplianceEmailTemplatesV2Enabled(true);
        result = generateEmail(compliance.getTitle(Compliance.COMPLIANCE_BELOW_THRESHOLD, EmailSubscriptionType.INSTANT));
        assertEquals("Instant notification - Compliance - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testInstantReportUploadFailedEmailBody() {
        String result = generateEmail(compliance.getBody(Compliance.REPORT_UPLOAD_FAILED, EmailSubscriptionType.INSTANT));
        assertTrue(result.contains(ACTION.getEvents().get(0).getPayload().getAdditionalProperties().get("error").toString()));

        // test template V2
        featureFlipper.setComplianceEmailTemplatesV2Enabled(true);
        result = generateEmail(compliance.getBody(Compliance.REPORT_UPLOAD_FAILED, EmailSubscriptionType.INSTANT));
        assertTrue(result.contains(ACTION.getEvents().get(0).getPayload().getAdditionalProperties().get("error").toString()));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantReportUploadFailedEmailTitle() {
        String result =  generateEmail(compliance.getTitle(Compliance.REPORT_UPLOAD_FAILED, EmailSubscriptionType.INSTANT));
        assertTrue(result.contains("Failed to upload report from system"));

        // test template V2
        featureFlipper.setComplianceEmailTemplatesV2Enabled(true);
        result = generateEmail(compliance.getTitle(Compliance.REPORT_UPLOAD_FAILED, EmailSubscriptionType.INSTANT));
        assertEquals("Instant notification - Compliance - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testDailyReportEmailBody() {
        String result = generateEmail(compliance.getBody(null, EmailSubscriptionType.DAILY));
        assertTrue(result.contains("Red Hat Insights has identified one or more systems"));

        // test template V2
        featureFlipper.setComplianceEmailTemplatesV2Enabled(true);
        result = generateEmail(compliance.getBody(null, EmailSubscriptionType.DAILY));
        assertTrue(result.contains("Red Hat Insights has identified one or more systems"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testDailyReportEmailTitle() {
        String result = generateEmail(compliance.getTitle(null, EmailSubscriptionType.DAILY));
        assertTrue(result.contains("Insights Compliance findings that require your attention"));

        // test template V2
        featureFlipper.setComplianceEmailTemplatesV2Enabled(true);
        result = generateEmail(compliance.getTitle(null, EmailSubscriptionType.DAILY));
        assertEquals("Daily digest - Compliance - Red Hat Enterprise Linux", result);
    }

    private String generateEmail(TemplateInstance template) {
        return template
            .data("action", ACTION)
            .data("environment", environment)
            .render();
    }
}
