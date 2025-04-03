package drawer;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestComplianceTemplate {

    static final String COMPLIANCE_BELOW_THRESHOLD = "compliance-below-threshold";
    static final String REPORT_UPLOAD_FAILED = "report-upload-failed";
    private static final Action ACTION = TestHelpers.createComplianceAction();

    @Inject
    TemplateService templateService;

    @Test
    void testRenderedTemplateBelowThreshold() {
        String result = renderTemplate(COMPLIANCE_BELOW_THRESHOLD, ACTION);
        assertEquals("Your system, **My test machine**, assigned to policy **Tested name**, has been marked as non-compliant because its compliance score 20% dropped below the configured compliance threshold of 25% for this policy.", result);
    }

    @Test
    void testRenderedTemplateUploadFailed() {
        String result = renderTemplate(REPORT_UPLOAD_FAILED, ACTION);
        assertEquals("Your system **My test machine** failed to upload a new compliance report. The error message returned by our system for the request *12345* was: *Kernel panic (test)*", result);
    }

    String renderTemplate(final String eventType, final Action action) {
        TemplateDefinition templateConfig = new TemplateDefinition(IntegrationType.DRAWER, "rhel", "compliance", eventType);
        return templateService.renderTemplate(templateConfig, action);
    }
}
