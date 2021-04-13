package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class TestAdvisorTemplate {

    @Test
    public void testInstantEmailTitle() {
        Action action = TestHelpers.createAdvisorAction("123456", "new-recommendation");
        String result = Advisor.Templates.newRecommendationInstantEmailTitle()
                .data("action", action)
                .render();

        assertEquals("Advisor instant notification - 03 Oct 2020 15:22 UTC - 4 recommendations", result, "Title contains the number of reports created");

        // Action with only 1 event
        action.setEvents(List.of(action.getEvents().get(0)));
        result = Advisor.Templates.newRecommendationInstantEmailTitle()
                .data("action", action)
                .render();
        assertEquals("Advisor instant notification - 03 Oct 2020 15:22 UTC - 1 recommendation", result, "Title contains the number of reports created");
    }

    @Test
    public void testInstantEmailBody() {
        Action action = TestHelpers.createAdvisorAction("123456", "new-recommendation");
        final String result = Advisor.Templates.newRecommendationInstantEmailBody()
                .data("action", action)
                .render();

        action.getEvents().forEach(event -> {
            assertTrue(
                    result.contains(event.getPayload().get("rule_id").toString()),
                    "Body should contain rule id" + event.getPayload().get("rule_id")
            );
            assertTrue(
                    result.contains(event.getPayload().get("rule_description").toString()),
                    "Body should contain rule description" + event.getPayload().get("rule_description")
            );
        });

        assertTrue(result.contains("alt=\"Low severity\""), "Body should contain low severity rule image");
        assertTrue(result.contains("alt=\"Moderate severity\""), "Body should contain moderate severity rule image");
        assertTrue(result.contains("alt=\"Important severity\""), "Body should contain important severity rule image");
        assertTrue(result.contains("alt=\"Critical severity\""), "Body should contain critical severity rule image");

        // Display name
        assertTrue(result.contains("My Host"), "Body should contain the display_name");

        action.setEvents(action.getEvents().stream().filter(event -> event.getPayload().get("total_risk").equals("1")).collect(Collectors.toList()));
        String result2 = Advisor.Templates.newRecommendationInstantEmailBody()
                .data("action", action)
                .render();

        assertTrue(result2.contains("alt=\"Low severity\""), "Body 2 should contain low severity rule image");
        assertFalse(result2.contains("alt=\"Moderate severity\""), "Body 2 should not contain moderate severity rule image");
        assertFalse(result2.contains("alt=\"Important severity\""), "Body 2 should not contain important severity rule image");
        assertFalse(result2.contains("alt=\"Critical severity\""), "Body 2 should not contain critical severity rule image");
    }
}
