package com.redhat.cloud.notifications.templates.common;

import com.redhat.cloud.notifications.EmailTemplatesRendererHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestAdvisorOpenShiftTemplate extends EmailTemplatesRendererHelper {

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
    protected String getBundleDisplayName() {
        return "OpenShift";
    }

    @Override
    protected String getAppDisplayName() {
        return "Advisor";
    }

    @Test
    public void testInstantEmailTitleForNewRecommendations() {
        eventTypeDisplayName = "New Recommendation";
        Action action = TestHelpers.createAdvisorAction("123456", NEW_RECOMMENDATION);

        String result = generateEmailSubject(NEW_RECOMMENDATION, action);
        assertEquals("Instant notification - New Recommendation - Advisor - OpenShift", result);
    }

    @Test
    public void testInstantEmailBodyForNewRecommendation() {
        Action action = TestHelpers.createAdvisorAction("123456", NEW_RECOMMENDATION);

        String result = generateEmailBody(NEW_RECOMMENDATION, action);
        checkNewRecommendationsBodyResults(action, result);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    private void checkNewRecommendationsBodyResults(Action action, final String result) {
        action.getEvents().forEach(event -> {
            assertTrue(
                result.contains(event.getPayload().getAdditionalProperties().get("rule_id").toString()),
                "Body should contain rule id " + event.getPayload().getAdditionalProperties().get("rule_id")
            );
            assertTrue(
                result.contains(event.getPayload().getAdditionalProperties().get("rule_description").toString()),
                "Body should contain rule description " + event.getPayload().getAdditionalProperties().get("rule_description")
            );
        });

        assertTrue(result.contains("alt=\"Low severity\""), "Body should contain low severity rule image");
        assertTrue(result.contains("alt=\"Moderate severity\""), "Body should contain moderate severity rule image");
        assertTrue(result.contains("alt=\"Important severity\""), "Body should contain important severity rule image");
        assertTrue(result.contains("alt=\"Critical severity\""), "Body should contain critical severity rule image");

        // Display name
        assertTrue(result.contains("My Host"), "Body should contain the display_name");

        action.setEvents(action.getEvents().stream().filter(event -> event.getPayload().getAdditionalProperties().get("total_risk").equals("1")).collect(Collectors.toList()));
    }
}
