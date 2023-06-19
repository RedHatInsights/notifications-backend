package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestComplianceTemplate extends EmailTemplatesInDbHelper {

    static final String COMPLIANCE_BELOW_THRESHOLD = "compliance-below-threshold";
    static final String REPORT_UPLOAD_FAILED = "report-upload-failed";
    private static final Action ACTION = TestHelpers.createComplianceAction();

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    EntityManager entityManager;

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
        return List.of(COMPLIANCE_BELOW_THRESHOLD, REPORT_UPLOAD_FAILED);
    }

    @Test
    public void testInstantComplianceBelowThresholdEmailBody() {
        String result = generateEmailBody(COMPLIANCE_BELOW_THRESHOLD, ACTION);
        assertTrue(result.contains(ACTION.getEvents().get(0).getPayload().getAdditionalProperties().get("policy_id").toString()));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setComplianceEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailBody(COMPLIANCE_BELOW_THRESHOLD, ACTION);
        assertTrue(result.contains(ACTION.getEvents().get(0).getPayload().getAdditionalProperties().get("policy_id").toString()));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantComplianceBelowThresholdEmailTitle() {
        String result = generateEmailSubject(COMPLIANCE_BELOW_THRESHOLD, ACTION);
        assertTrue(result.contains("is non-compliant with policy"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setComplianceEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailSubject(COMPLIANCE_BELOW_THRESHOLD, ACTION);
        assertEquals("Instant notification - Compliance - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testInstantReportUploadFailedEmailBody() {
        String result = generateEmailBody(REPORT_UPLOAD_FAILED, ACTION);
        assertTrue(result.contains(ACTION.getEvents().get(0).getPayload().getAdditionalProperties().get("error").toString()));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setComplianceEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailBody(REPORT_UPLOAD_FAILED, ACTION);
        assertTrue(result.contains(ACTION.getEvents().get(0).getPayload().getAdditionalProperties().get("error").toString()));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantReportUploadFailedEmailTitle() {
        String result = generateEmailSubject(REPORT_UPLOAD_FAILED, ACTION);
        assertTrue(result.contains("Failed to upload report from system"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setComplianceEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailSubject(REPORT_UPLOAD_FAILED, ACTION);
        assertEquals("Instant notification - Compliance - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testDailyReportEmailBody() {
        String result = generateAggregatedEmailBody(Map.of());
        assertTrue(result.contains("Red Hat Insights has identified one or more systems"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setComplianceEmailTemplatesV2Enabled(true);
        migrate();
        result = generateAggregatedEmailBody(Map.of());
        assertTrue(result.contains("Red Hat Insights has identified one or more systems"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testDailyReportEmailTitle() {
        String result = generateAggregatedEmailSubject(Map.of());
        assertTrue(result.contains("Insights Compliance findings that require your attention"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setComplianceEmailTemplatesV2Enabled(true);
        migrate();
        result = generateAggregatedEmailSubject(Map.of());
        assertEquals("Daily digest - Compliance - Red Hat Enterprise Linux", result);
    }
}
