package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestComplianceTemplate extends EmailTemplatesInDbHelper {

    private static final Action ACTION = TestHelpers.createComplianceAction();

    @Inject
    FeatureFlipper featureFlipper;

    @AfterEach
    void afterEach() {
        featureFlipper.setComplianceEmailTemplatesV2Enabled(false);
        migrate();
    }

    @Override
    protected String getApp() {
        return "compliance";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(Compliance.COMPLIANCE_BELOW_THRESHOLD, Compliance.REPORT_UPLOAD_FAILED);
    }

    @Test
    public void testInstantComplianceBelowThresholdEmailBody() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailBody(Compliance.COMPLIANCE_BELOW_THRESHOLD, ACTION);
            assertTrue(result.contains(ACTION.getEvents().get(0).getPayload().getAdditionalProperties().get("policy_id").toString()));

            featureFlipper.setComplianceEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailBody(Compliance.COMPLIANCE_BELOW_THRESHOLD, ACTION);
            assertTrue(result.contains(ACTION.getEvents().get(0).getPayload().getAdditionalProperties().get("policy_id").toString()));
            assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        });
    }

    @Test
    public void testInstantComplianceBelowThresholdEmailTitle() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailSubject(Compliance.COMPLIANCE_BELOW_THRESHOLD, ACTION);
            assertTrue(result.contains("is non-compliant with policy"));

            featureFlipper.setComplianceEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailSubject(Compliance.COMPLIANCE_BELOW_THRESHOLD, ACTION);
            assertEquals("Instant notification - Compliance - Red Hat Enterprise Linux", result);
        });
    }

    @Test
    public void testInstantReportUploadFailedEmailBody() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailBody(Compliance.REPORT_UPLOAD_FAILED, ACTION);
            assertTrue(result.contains(ACTION.getEvents().get(0).getPayload().getAdditionalProperties().get("error").toString()));

            featureFlipper.setComplianceEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailBody(Compliance.REPORT_UPLOAD_FAILED, ACTION);
            assertTrue(result.contains(ACTION.getEvents().get(0).getPayload().getAdditionalProperties().get("error").toString()));
            assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        });
    }

    @Test
    public void testInstantReportUploadFailedEmailTitle() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailSubject(Compliance.REPORT_UPLOAD_FAILED, ACTION);
            assertTrue(result.contains("Failed to upload report from system"));

            featureFlipper.setComplianceEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailSubject(Compliance.REPORT_UPLOAD_FAILED, ACTION);
            assertEquals("Instant notification - Compliance - Red Hat Enterprise Linux", result);
        });
    }

    @Test
    public void testDailyReportEmailBody() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateAggregatedEmailBody(Map.of());
            assertTrue(result.contains("Red Hat Insights has identified one or more systems"));

            featureFlipper.setComplianceEmailTemplatesV2Enabled(true);
            migrate();
            result = generateAggregatedEmailBody(Map.of());
            assertTrue(result.contains("Red Hat Insights has identified one or more systems"));
            assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        });
    }

    @Test
    public void testDailyReportEmailTitle() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateAggregatedEmailSubject(Map.of());
            assertTrue(result.contains("Insights Compliance findings that require your attention"));

            featureFlipper.setComplianceEmailTemplatesV2Enabled(true);
            migrate();
            result = generateAggregatedEmailSubject(Map.of());
            assertEquals("Daily digest - Compliance - Red Hat Enterprise Linux", result);
        });
    }
}
