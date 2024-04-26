package com.redhat.cloud.notifications.templates.drawer;

import com.redhat.cloud.notifications.IntegrationTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestComplianceTemplate extends IntegrationTemplatesInDbHelper {

    static final String COMPLIANCE_BELOW_THRESHOLD = "compliance-below-threshold";
    static final String REPORT_UPLOAD_FAILED = "report-upload-failed";
    private static final Action ACTION = TestHelpers.createComplianceAction();

    @Override
    protected String getApp() {
        return "compliance";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(COMPLIANCE_BELOW_THRESHOLD, REPORT_UPLOAD_FAILED);
    }

    @Test
    void testRenderedTemplateBelowThreshold() {

        String result = generateDrawerTemplate(COMPLIANCE_BELOW_THRESHOLD, ACTION);
        assertEquals("Your system, **My test machine**, assigned to policy **Tested name**, has been marked as non-compliant because its compliance score 20% dropped below the configured compliance threshold of 25% for this policy.", result);
    }

    @Test
    void testRenderedTemplateUploadFailed() {
        String result = generateDrawerTemplate(REPORT_UPLOAD_FAILED, ACTION);
        assertEquals("Your system **My test machine** failed to upload a new compliance report. The error message returned by our system for the request *12345* was: *Kernel panic (test)*", result);
    }
}
