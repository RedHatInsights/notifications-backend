package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class TestOpenshiftAdvisorTemplate {

    @Test
    public void testInstantEmailTitle() {
        Action action = TestHelpers.createAdvisorOpenshiftAction("123456", "new-recommendation");
        String result = AdvisorOpenshift.Templates.newRecommendationInstantEmailTitle()
                .data("action", action)
                .render();

        assertEquals("Openshift - Advisor Instant Notification - 20 May 2021", result, "Title is the expected.");
    }

    @Test
    public void testInstantEmailBody() {
        Action action = TestHelpers.createAdvisorOpenshiftAction("123456", "new-recommendation");
        final String result = AdvisorOpenshift.Templates.newRecommendationInstantEmailBody()
                .data("action", action)
                .render();

        action.getEvents().forEach(event -> {
            assertTrue(
                    result.contains(event.getPayload().get("rule_description").toString()),
                    "Body should contain rule description" + event.getPayload().get("rule_description")
            );
            assertTrue(
                    result.contains(event.getPayload().get("total_risk").toString()),
                    "Body should contain total_risk" + event.getPayload().get("total_risk")
            );
            assertTrue(
                    result.contains(event.getPayload().get("rule_url").toString()),
                    "Body should contain rule_url" + event.getPayload().get("rule_url")
            );
        });

        assertTrue(result.contains("alt=\"Low severity\""), "Body should contain low severity rule image");
        assertTrue(result.contains("alt=\"Moderate severity\""), "Body should contain moderate severity rule image");
        assertTrue(result.contains("alt=\"Important severity\""), "Body should contain important severity rule image");
        assertTrue(result.contains("alt=\"Critical severity\""), "Body should contain critical severity rule image");

        // Display name
        assertTrue(result.contains("some-cluster-name"), "Body should contain the display_name");

        action.setEvents(action.getEvents().stream().filter(event -> event.getPayload().get("total_risk").equals("1")).collect(Collectors.toList()));
        String result2 = AdvisorRHEL.Templates.resolvedRecommendationInstantEmailBody()
                .data("action", action)
                .render();

        assertTrue(result2.contains("alt=\"Low severity\""), "Body 2 should contain low severity rule image");
        assertFalse(result2.contains("alt=\"Moderate severity\""), "Body 2 should not contain moderate severity rule image");
        assertFalse(result2.contains("alt=\"Important severity\""), "Body 2 should not contain important severity rule image");
        assertFalse(result2.contains("alt=\"Critical severity\""), "Body 2 should not contain critical severity rule image");
    }

    @Test
    public void testDigestEmailTitle() {
        Action action = TestHelpers.createAdvisorOpenshiftAction("123456", "weekly-digest");
        String result = AdvisorOpenshift.Templates.weeklyDigestEmailTitle()
                .data("action", action)
                .render();

        assertEquals("Openshift - Advisor Weekly Report - 20 May 2021", result, "Title is the expected.");
    }

    @Test
    public void testDigestEmailBody() {
        Action action = TestHelpers.createAdvisorOpenshiftAction("123456", "weekly-digest");
        final String result = AdvisorOpenshift.Templates.weeklyDigestEmailBody()
                .data("action", action)
                .render();

        assertTrue(action.getEvents().size() == 1, "weekly digest should only contain one event");

        action.getEvents().forEach(event -> {
            assertTrue(
                    result.contains(event.getPayload().get("total_clusters").toString()),
                    "Body should contain total_clusters" + event.getPayload().get("total_clusters")
            );
            assertTrue(
                    result.contains(event.getPayload().get("total_recommendations").toString()),
                    "Body should contain total_recommendations" + event.getPayload().get("total_recommendations")
            );
            assertTrue(
                    result.contains(event.getPayload().get("total_incidents").toString()),
                    "Body should contain total_incidents" + event.getPayload().get("total_incidents")
            );
            assertTrue(
                    result.contains(event.getPayload().get("total_critical").toString()),
                    "Body should contain total_critical" + event.getPayload().get("total_critical")
            );
            assertTrue(
                    result.contains(event.getPayload().get("total_important").toString()),
                    "Body should contain total_important" + event.getPayload().get("total_important")
            );        });

        assertTrue(result.contains("alt=\"Important severity\""), "Body should contain important severity rule image");
        assertTrue(result.contains("alt=\"Critical severity\""), "Body should contain critical severity rule image");
    }
}
