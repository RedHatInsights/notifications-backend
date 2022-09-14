package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.templates.models.Environment;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestOpenshiftAdvisorTemplate {

    @Inject
    Environment environment;

    @Test
    public void testInstantEmailTitle() {
        Action action = TestHelpers.createAdvisorOpenshiftAction("123456", "new-recommendation");
        String result = AdvisorOpenshift.Templates.newRecommendationInstantEmailTitle()
                .data("action", action)
                .data("environment", environment)
                .render();

        assertTrue(result.contains("OpenShift - Advisor Instant Notification - "));
        assertTrue(result.contains("2021"));
    }

    @Test
    public void testInstantEmailBody() {
        Action action = TestHelpers.createAdvisorOpenshiftAction("123456", "new-recommendation");
        final String result = AdvisorOpenshift.Templates.newRecommendationInstantEmailBody()
                .data("action", action)
                .data("environment", environment)
                .render();

        action.getEvents().forEach(event -> {
            assertTrue(
                    result.contains(event.getPayload().getAdditionalProperties().get("rule_description").toString()),
                    "Body should contain rule description" + event.getPayload().getAdditionalProperties().get("rule_description")
            );
            assertTrue(
                    result.contains(event.getPayload().getAdditionalProperties().get("total_risk").toString()),
                    "Body should contain total_risk" + event.getPayload().getAdditionalProperties().get("total_risk")
            );
            assertTrue(
                    result.contains(event.getPayload().getAdditionalProperties().get("rule_url").toString()),
                    "Body should contain rule_url" + event.getPayload().getAdditionalProperties().get("rule_url")
            );
        });

        assertTrue(result.contains("alt=\"Low severity\""), "Body should contain low severity rule image");
        assertTrue(result.contains("alt=\"Moderate severity\""), "Body should contain moderate severity rule image");
        assertTrue(result.contains("alt=\"Important severity\""), "Body should contain important severity rule image");
        assertTrue(result.contains("alt=\"Critical severity\""), "Body should contain critical severity rule image");

        // Display name
        assertTrue(result.contains("some-cluster-name"), "Body should contain the display_name");

        action.setEvents(action.getEvents().stream().filter(event -> event.getPayload().getAdditionalProperties().get("total_risk").equals("1")).collect(Collectors.toList()));
        String result2 = AdvisorOpenshift.Templates.newRecommendationInstantEmailBody()
                .data("action", action)
                .data("environment", environment)
                .render();

        assertTrue(result2.contains("alt=\"Low severity\""), "Body 2 should contain low severity rule image");
        assertFalse(result2.contains("alt=\"Moderate severity\""), "Body 2 should not contain moderate severity rule image");
        assertFalse(result2.contains("alt=\"Important severity\""), "Body 2 should not contain important severity rule image");
        assertFalse(result2.contains("alt=\"Critical severity\""), "Body 2 should not contain critical severity rule image");
    }
}
