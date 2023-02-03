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
public class TestEdgeManagementTemplate {

    private static final boolean SHOULD_WRITE_ON_FILE_FOR_DEBUG = false;

    private static final Action ACTION = TestHelpers.createEdgeManagementAction();

    @Inject
    Environment environment;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    EdgeManagement edgeManagement;

    @AfterEach
    void afterEach() {
        featureFlipper.setEdgeManagementEmailTemplatesV2Enabled(false);
    }

    @Test
    public void testImageCreationEmailTitle() {
        String result =  generateEmail(edgeManagement.getTitle(EdgeManagement.IMAGE_CREATION, EmailSubscriptionType.INSTANT));
        writeEmailTemplate(result, edgeManagement.getTitle(EdgeManagement.IMAGE_CREATION, EmailSubscriptionType.INSTANT).getTemplate().getId());
        assertTrue(result.startsWith("Edge Management - Image Creation Started"));

        // test template V2
        featureFlipper.setEdgeManagementEmailTemplatesV2Enabled(true);
        result = generateEmail(edgeManagement.getTitle(EdgeManagement.IMAGE_CREATION, EmailSubscriptionType.INSTANT));
        writeEmailTemplate(result, edgeManagement.getTitle(EdgeManagement.IMAGE_CREATION, EmailSubscriptionType.INSTANT).getTemplate().getId());
        assertEquals("Instant notification - Image creation started - Edge Management - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testImageCreationEmailBody() {
        String result =  generateEmail(edgeManagement.getBody(EdgeManagement.IMAGE_CREATION, EmailSubscriptionType.INSTANT));
        writeEmailTemplate(result, edgeManagement.getBody(EdgeManagement.IMAGE_CREATION, EmailSubscriptionType.INSTANT).getTemplate().getId());
        assertTrue(result.contains("A new image was created"));

        // test template V2
        featureFlipper.setEdgeManagementEmailTemplatesV2Enabled(true);
        result = generateEmail(edgeManagement.getBody(EdgeManagement.IMAGE_CREATION, EmailSubscriptionType.INSTANT));
        writeEmailTemplate(result, edgeManagement.getBody(EdgeManagement.IMAGE_CREATION, EmailSubscriptionType.INSTANT).getTemplate().getId());
        assertTrue(result.contains("A new image named"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testUpdateDeviceEmailTitle() {
        String result =  generateEmail(edgeManagement.getTitle(EdgeManagement.UPDATE_DEVICES, EmailSubscriptionType.INSTANT));
        writeEmailTemplate(result, edgeManagement.getTitle(EdgeManagement.UPDATE_DEVICES, EmailSubscriptionType.INSTANT).getTemplate().getId());
        assertTrue(result.startsWith("Update Device Started"));

        // test template V2
        featureFlipper.setEdgeManagementEmailTemplatesV2Enabled(true);
        result = generateEmail(edgeManagement.getTitle(EdgeManagement.UPDATE_DEVICES, EmailSubscriptionType.INSTANT));
        writeEmailTemplate(result, edgeManagement.getTitle(EdgeManagement.UPDATE_DEVICES, EmailSubscriptionType.INSTANT).getTemplate().getId());
        assertEquals("Instant notification - Update device started - Edge Management - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testUpdateDeviceEmailBody() {
        String result =  generateEmail(edgeManagement.getBody(EdgeManagement.UPDATE_DEVICES, EmailSubscriptionType.INSTANT));
        writeEmailTemplate(result, edgeManagement.getBody(EdgeManagement.UPDATE_DEVICES, EmailSubscriptionType.INSTANT).getTemplate().getId());
        assertTrue(result.contains("An Update for the device"));

        // test template V2
        featureFlipper.setEdgeManagementEmailTemplatesV2Enabled(true);
        result = generateEmail(edgeManagement.getBody(EdgeManagement.UPDATE_DEVICES, EmailSubscriptionType.INSTANT));
        writeEmailTemplate(result, edgeManagement.getBody(EdgeManagement.UPDATE_DEVICES, EmailSubscriptionType.INSTANT).getTemplate().getId());
        assertTrue(result.contains("An Update for the device"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    private String generateEmail(TemplateInstance template) {
        return template
            .data("action", ACTION)
            .data("environment", environment)
            .render();
    }

    public void writeEmailTemplate(String result, String fileName) {
        if (SHOULD_WRITE_ON_FILE_FOR_DEBUG) {
            TestHelpers.writeEmailTemplate(result, fileName);
        }
    }
}
