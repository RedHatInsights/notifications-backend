package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.INSTANT;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class TestAdvisorTemplate {

    private final Advisor advisor = new Advisor();

    @Test
    void shouldNotSupportDailyEmailSubscriptionType() {
        assertFalse(advisor.isSupported("some-recommendation", DAILY));
    }

    @ValueSource(strings = {"new-recommendation", "resolved-recommendation", "deactivated-recommendation" })
    @ParameterizedTest
    void shouldSupportNewResolvedAndDeactivatedRecommendations(String eventType) {
        assertTrue(advisor.isSupported(eventType, INSTANT));
    }

    @Test
    void shouldThrowUnsupportedOperationExceptionWhenEmailSubscriptionTypeIsNotInstantWhenGettingTitle() {
        Exception exception = assertThrows(UnsupportedOperationException.class, () -> {
            advisor.getTitle("some-eventtype", DAILY);
        });
        assertEquals("No email title template for Advisor event_type: some-eventtype and EmailSubscription: DAILY found.", exception.getMessage());
    }

    @Test
    void shouldThrowUnsupportedOperationExceptionWhenEmailSubscriptionTypeIsNotInstantWhenGettingBody() {
        Exception exception = assertThrows(UnsupportedOperationException.class, () -> {
            advisor.getBody("some-eventtype", DAILY);
        });
        assertEquals("No email body template for Advisor event_type: some-eventtype and EmailSubscription: DAILY found.", exception.getMessage());
    }

    @Test
    void testInstantEmailTitleForResolvedRecommendations() {
        Action action = TestHelpers.createAdvisorAction("123456", "resolved-recommendation");
        String result = Advisor.Templates.resolvedRecommendationInstantEmailTitle()
                .data("action", action)
                .render();

        assertEquals("Red Hat Enterprise Linux - Advisor Instant Notification - 03 Oct 2020 15:22 UTC - 4 resolved recommendations\n", result, "Title contains the number of reports created");

        // Action with only 1 event
        action.setEvents(List.of(action.getEvents().get(0)));
        result = Advisor.Templates.resolvedRecommendationInstantEmailTitle()
                .data("action", action)
                .render();
        assertEquals("Red Hat Enterprise Linux - Advisor Instant Notification - 03 Oct 2020 15:22 UTC - 1 resolved recommendation\n", result, "Title contains the number of reports created");
    }

    @Test
    void shouldSupportInstantSubscriptionType() {
        assertTrue(advisor.isEmailSubscriptionSupported(INSTANT));
    }

    @Test
    void shouldNotSupportDailyubscriptionType() {
        assertFalse(advisor.isEmailSubscriptionSupported(DAILY));
    }

    @Test
    public void testInstantEmailTitleForNewRecommendations() {
        Action action = TestHelpers.createAdvisorAction("123456", "new-recommendation");
        String result = Advisor.Templates.newRecommendationInstantEmailTitle()
                .data("action", action)
                .render();

        assertEquals("Red Hat Enterprise Linux - Advisor Instant Notification - 03 Oct 2020 15:22 UTC - 4 new recommendations\n", result, "Title contains the number of reports created");

        // Action with only 1 event
        action.setEvents(List.of(action.getEvents().get(0)));
        result = Advisor.Templates.newRecommendationInstantEmailTitle()
                .data("action", action)
                .render();
        assertEquals("Red Hat Enterprise Linux - Advisor Instant Notification - 03 Oct 2020 15:22 UTC - 1 new recommendation\n", result, "Title contains the number of reports created");
    }

    @Test
    public void testInstantEmailBodyForNewRecommendation() {
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

    @Test
    public void testInstantEmailTitleForDeactivatedRecommendation() {
        Action action = TestHelpers.createAdvisorAction("123456", "deactivated-recommendation");
        String result = Advisor.Templates.deactivatedRecommendationInstantEmailTitle()
                .data("action", action)
                .render();

        assertEquals("Red Hat Enterprise Linux - Advisor Instant Notification - 03 Oct 2020 15:22 UTC - 2 deactivated recommendations\n", result, "Title contains the number of reports created");

        // Action with only 1 event
        action.setEvents(List.of(action.getEvents().get(0)));
        result = Advisor.Templates.deactivatedRecommendationInstantEmailTitle()
                .data("action", action)
                .render();
        assertEquals("Red Hat Enterprise Linux - Advisor Instant Notification - 03 Oct 2020 15:22 UTC - 1 deactivated recommendation\n", result, "Title contains the number of reports created");
    }

    @Test
    public void testInstantEmailBodyForDeactivatedRecommendation() {
        Action action = TestHelpers.createAdvisorAction("123456", "deactivated-recommendation");
        final String result = Advisor.Templates.deactivatedRecommendationInstantEmailBody()
                .data("action", action).data("user", Map.of("firstName", "Testing"))
                .render();

        action.getEvents().forEach(event -> {
            assertTrue(result.contains(event.getPayload().get("rule_description").toString()),
                       "Body should contain rule description" + event.getPayload().get("rule_description"));
            assertTrue(result.contains(event.getPayload().get("affected_systems").toString()),
                       "Body should contain affected systems" + event.getPayload().get("affected_systems"));
            assertTrue(result.contains(event.getPayload().get("deactivation_reason").toString()),
                       "Body should contain deactivation reason" + event.getPayload().get("deactivation_reason"));
        });

        assertTrue(result.contains("<span class=\"rh-metric__count\">2</span>"),
                   "Body should contain the number of deactivated recommendations");
        assertTrue(result.contains("alt=\"Low severity\""), "Body should contain low severity rule image");
        assertTrue(result.contains("alt=\"Moderate severity\""), "Body should contain moderate severity rule image");
        assertTrue(result.contains("Hi Testing,"), "Body should contain user's first name");
    }
}
