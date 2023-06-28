package com.redhat.cloud.notifications.templates.drawer;

import com.redhat.cloud.notifications.DriftTestHelpers;
import com.redhat.cloud.notifications.IntegrationTemplatesInDbHelper;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class TestDriftTemplate extends IntegrationTemplatesInDbHelper {

    static final String EVENT_TYPE_NAME = "drift-baseline-detected";

    @Override
    protected String getApp() {
        return "drift";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(EVENT_TYPE_NAME);
    }

    @Test
    void testRenderedTemplateDrift() {
        Action action = DriftTestHelpers.createDriftAction("rhel", "drift", "host-01", "Machine 1");
        String result = generateDrawerTemplate(EVENT_TYPE_NAME, action);
        assertEquals("<b>Machine 1</b> has drifted from 2 baselines.", result);
    }
}
