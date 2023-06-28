package com.redhat.cloud.notifications.templates.drawer;

import com.redhat.cloud.notifications.IntegrationTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.List;

import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class TestAdvisorTemplate extends IntegrationTemplatesInDbHelper {

    @Override
    protected String getApp() {
        return "advisor";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(NEW_RECOMMENDATION, RESOLVED_RECOMMENDATION, DEACTIVATED_RECOMMENDATION);
    }

    @Test
    void testRenderedTemplateForResolvedRecommendations() {
        Action action = TestHelpers.createAdvisorAction("123456", RESOLVED_RECOMMENDATION);
        String result = generateDrawerTemplate(RESOLVED_RECOMMENDATION, action);
        assertEquals("<b>My Host</b> has 4 resolved recommendations.", result);
    }

    @Test
    public void testRenderedTemplateForNewRecommendations() {
        Action action = TestHelpers.createAdvisorAction("123456", NEW_RECOMMENDATION);
        String result = generateDrawerTemplate(NEW_RECOMMENDATION, action);
        assertEquals("<b>My Host</b> has 4 new recommendations.", result);
    }

    @Test
    public void testRenderedTemplateForDeactivatedRecommendation() {
        Action action = TestHelpers.createAdvisorAction("123456", DEACTIVATED_RECOMMENDATION);
        String result = generateDrawerTemplate(DEACTIVATED_RECOMMENDATION, action);
        assertEquals("2 recommendations have recently been deactivated by Red Hat Insights and are no longer affecting your systems.", result);
    }
}
