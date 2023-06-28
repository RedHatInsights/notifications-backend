package com.redhat.cloud.notifications.templates.drawer;

import com.redhat.cloud.notifications.IntegrationTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class TestAnsibleTemplate extends IntegrationTemplatesInDbHelper {

    static final String REPORT_AVAILABLE_EVENT = "report-available";

    @Override
    protected String getBundle() {
        return "ansible";
    }

    @Override
    protected String getApp() {
        return "reports";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(REPORT_AVAILABLE_EVENT);
    }

    @Test
    void testRenderedTemplateReportAvailable() {
        Action action = TestHelpers.createAnsibleAction(null);
        String result = generateDrawerTemplate(REPORT_AVAILABLE_EVENT, action);
        assertEquals("Ansible report available for download.", result);
    }
}
