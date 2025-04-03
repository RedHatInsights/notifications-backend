package com.redhat.cloud.notifications.templates.drawer;

import com.redhat.cloud.notifications.IntegrationTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.extensions.TimeAgoFormatter;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestEdgeManagementTemplate extends IntegrationTemplatesInDbHelper {

    static final String IMAGE_CREATION = "image-creation";
    static final String UPDATE_DEVICES = "update-devices";
    private static final Action ACTION = TestHelpers.createEdgeManagementAction();

    @Override
    protected String getApp() {
        return "edge-management";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(IMAGE_CREATION, UPDATE_DEVICES);
    }

    @Test
    void testRenderedTemplateImageCreation() {
        TimeAgoFormatter timeFormatter = new TimeAgoFormatter();
        String deltaTme = timeFormatter.format(LocalDateTime.now(UTC), LocalDateTime.from(ACTION.getTimestamp()));
        String result = generateDrawerTemplate(IMAGE_CREATION, ACTION);
        assertEquals(String.format("A new image named **Test name** was created %s.", deltaTme), result);
    }

    @Test
    void testRenderedTemplateUpdateDevice() {
        String result = generateDrawerTemplate(UPDATE_DEVICES, ACTION);
        assertEquals("An Update for the device **DEVICE-9012** started.", result);
    }
}
