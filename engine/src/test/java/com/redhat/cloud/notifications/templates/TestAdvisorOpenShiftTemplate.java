package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Environment;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.INSTANT;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.NEW_RECOMMENDATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestAdvisorOpenShiftTemplate extends EmailTemplatesInDbHelper {

    @Inject
    AdvisorOpenshift advisorOpenshift;

    @Inject
    Environment environment;

    @Inject
    FeatureFlipper featureFlipper;

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

    @BeforeAll
    static void beforeAll() {
        // TODO Remove this as soon as the daily digest is enabled on prod.
        System.setProperty("rhel.advisor.daily-digest.enabled", "true");
    }

    @AfterEach
    void afterEach() {
        featureFlipper.setAdvisorOpenShiftEmailTemplatesV2Enabled(false);
        migrate();
    }

    @Test
    void shouldNotSupportDailyEmailSubscriptionType() {
        assertFalse(advisorOpenshift.isSupported(NEW_RECOMMENDATION, DAILY));
    }

    @Test
    void shouldSupportNewRecommendations() {
        assertTrue(advisorOpenshift.isSupported(NEW_RECOMMENDATION, INSTANT));
    }

    @Test
    void shouldSupportInstantSubscriptionType() {
        assertTrue(advisorOpenshift.isEmailSubscriptionSupported(INSTANT));
    }

    @Test
    void shouldNotSupportDailySubscriptionType() {
        assertFalse(advisorOpenshift.isEmailSubscriptionSupported(DAILY));
    }

    @Test
    public void testInstantEmailTitleForNewRecommendations() {
        Action action = TestHelpers.createAdvisorAction("123456", NEW_RECOMMENDATION);

        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailSubject(NEW_RECOMMENDATION, action);

            // The date formatting is sensitive to the locale
            String date = DateTimeFormatter.ofPattern("d MMM uuuu").format(action.getTimestamp());
            assertEquals("OpenShift - Advisor Instant Notification - " + date, result);

            featureFlipper.setAdvisorOpenShiftEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailSubject(NEW_RECOMMENDATION, action);
            assertEquals("Instant notification - New recommendation - Advisor - OpenShift", result);
        });
    }

    @Test
    public void testInstantEmailBodyForNewRecommendation() {
        statelessSessionFactory.withSession(statelessSession -> {
            Action action = TestHelpers.createAdvisorAction("123456", NEW_RECOMMENDATION);

            String result = generateEmailBody(NEW_RECOMMENDATION, action);
            checkNewRecommendationsBodyResults(action, result);

            featureFlipper.setAdvisorOpenShiftEmailTemplatesV2Enabled(true);
            action = TestHelpers.createAdvisorAction("123456", NEW_RECOMMENDATION);
            migrate();
            result = generateEmailBody(NEW_RECOMMENDATION, action);
            checkNewRecommendationsBodyResults(action, result);
            assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        });
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
        String result2 = Advisor.Templates.newRecommendationInstantEmailBody()
            .data("action", action)
            .data("environment", environment)
            .render();

        assertTrue(result2.contains("alt=\"Low severity\""), "Body 2 should contain low severity rule image");
        assertFalse(result2.contains("alt=\"Moderate severity\""), "Body 2 should not contain moderate severity rule image");
        assertFalse(result2.contains("alt=\"Important severity\""), "Body 2 should not contain important severity rule image");
        assertFalse(result2.contains("alt=\"Critical severity\""), "Body 2 should not contain critical severity rule image");
    }
}
