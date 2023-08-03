package com.redhat.cloud.notifications.templates.drawer;

import com.redhat.cloud.notifications.IntegrationTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestPoliciesTemplate extends IntegrationTemplatesInDbHelper {


    private static final String EVENT_TYPE_NAME = "policy-triggered";

    @Override
    protected String getApp() {
        return "policies";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(EVENT_TYPE_NAME);
    }

    @Test
    void testRenderedTemplateNewAdvisory() {
        Action action = TestHelpers.createPoliciesAction("", "", "", "FooMachine");
        String result = generateDrawerTemplate(EVENT_TYPE_NAME, action);
        assertEquals("<b>FooMachine</b> triggered 2 policies.", result);
    }
}
