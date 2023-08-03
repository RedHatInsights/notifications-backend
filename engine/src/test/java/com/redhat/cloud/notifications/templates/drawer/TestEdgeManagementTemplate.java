package com.redhat.cloud.notifications.templates.drawer;

import com.redhat.cloud.notifications.IntegrationTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class TestEdgeManagementTemplate extends IntegrationTemplatesInDbHelper {

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
        long nbMonths = ChronoUnit.MONTHS.between(
            LocalDate.from(ACTION.getTimestamp()),
            LocalDate.now(UTC));

        String result = generateDrawerTemplate(IMAGE_CREATION, ACTION);
        assertEquals(String.format("A new image named <b>Test name</b> was created %d months ago.", nbMonths), result);
    }

    @Test
    public void testRenderedTemplateUpdateDevice() {
        String result = generateDrawerTemplate(UPDATE_DEVICES, ACTION);
        assertEquals("An Update for the device <b>DEVICE-9012</b> started.", result);
    }
}
