package com.redhat.cloud.notifications.templates.drawer;

import com.redhat.cloud.notifications.IntegrationTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestSourcesTemplate extends IntegrationTemplatesInDbHelper {


    static final String AVAILABILITY_STATUS = "availability-status";
    private static final Action ACTION = TestHelpers.createSourcesAction();

    @Override
    protected String getBundle() {
        return "console";
    }

    @Override
    protected String getApp() {
        return "sources";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(AVAILABILITY_STATUS);
    }

    @Test
    void testRenderedTemplateAvailabilityStatus() {
        String result = generateDrawerTemplate(AVAILABILITY_STATUS, ACTION);
        assertEquals("test name 1's availability status was changed from **old status** to **current status**.", result);
    }
}
