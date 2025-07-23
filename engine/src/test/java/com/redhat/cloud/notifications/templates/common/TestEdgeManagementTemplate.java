package com.redhat.cloud.notifications.templates.common;

import com.redhat.cloud.notifications.EmailTemplatesRendererHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestEdgeManagementTemplate extends EmailTemplatesRendererHelper {

    static final String IMAGE_CREATION = "image-creation";
    static final String UPDATE_DEVICES = "update-devices";
    private static final Action ACTION = TestHelpers.createEdgeManagementAction();

    @Override
    protected String getApp() {
        return "edge-management";
    }

    protected String getAppDisplayName() {
        return "Edge Management";
    }

    @Test
    public void testImageCreationEmailTitle() {
        eventTypeDisplayName = "Image Creation";
        String result = generateEmailSubject(IMAGE_CREATION, ACTION);
        assertEquals("Instant notification - Image Creation - Edge Management - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testImageCreationEmailBody() {
        String result = generateEmailBody(IMAGE_CREATION, ACTION);
        assertTrue(result.contains("A new image named"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testUpdateDeviceEmailTitle() {
        eventTypeDisplayName = "Update Devices";
        String result = generateEmailSubject(UPDATE_DEVICES, ACTION);
        assertEquals("Instant notification - Update Devices - Edge Management - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testUpdateDeviceEmailBody() {
        String result = generateEmailBody(UPDATE_DEVICES, ACTION);
        assertTrue(result.contains("An Update for the device"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
