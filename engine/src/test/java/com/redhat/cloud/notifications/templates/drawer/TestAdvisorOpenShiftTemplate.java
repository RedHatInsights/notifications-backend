package com.redhat.cloud.notifications.templates.drawer;

import com.redhat.cloud.notifications.IntegrationTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestAdvisorOpenShiftTemplate extends IntegrationTemplatesInDbHelper {

    static final String NEW_RECOMMENDATION = "new-recommendation";

    @Override
    protected String getApp() {
        return "advisor";
    }

    @Override
    protected String getBundle() {
        return "openshift";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(NEW_RECOMMENDATION);
    }

    @Test
    void testRenderedTemplateForNewRecommendations() {
        Action action = TestHelpers.createAdvisorAction("123456", NEW_RECOMMENDATION);
        String result = generateDrawerTemplate(NEW_RECOMMENDATION, action);
        assertEquals("<b>My Host</b> has 4 new recommendations.", result);
    }
}
