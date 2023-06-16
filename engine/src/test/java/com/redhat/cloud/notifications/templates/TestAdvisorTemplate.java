package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.AdvisorTestHelpers.createEmailAggregation;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.DEACTIVATED_RECOMMENDATION;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.NEW_RECOMMENDATION;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.RESOLVED_RECOMMENDATION;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregatorTest.TEST_RULE_1;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregatorTest.TEST_RULE_2;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregatorTest.TEST_RULE_3;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregatorTest.TEST_RULE_4;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregatorTest.TEST_RULE_6;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestAdvisorTemplate extends EmailTemplatesInDbHelper {

    @Inject
    Environment environment;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    EntityManager entityManager;

    @Override
    protected String getApp() {
        return "advisor";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(NEW_RECOMMENDATION, RESOLVED_RECOMMENDATION, DEACTIVATED_RECOMMENDATION);
    }

    @AfterEach
    void afterEach() {
        featureFlipper.setAdvisorEmailTemplatesV2Enabled(false);
        featureFlipper.setRhelAdvisorDailyDigestEnabled(false);
        migrate();
    }

    @BeforeEach
    void beforeEach() {
        featureFlipper.setRhelAdvisorDailyDigestEnabled(true);
        migrate();
    }

    @Test
    public void testDailyEmailTitle() {
        AdvisorEmailAggregator aggregator = new AdvisorEmailAggregator();
        aggregator.aggregate(createEmailAggregation(NEW_RECOMMENDATION, TEST_RULE_1));

        Map<String, Object> context = aggregator.getContext();
        context.put("start_time", LocalDateTime.now().toString());
        context.put("end_time", LocalDateTime.now().toString());

        String result = generateAggregatedEmailSubject(context);
        assertTrue(result.startsWith("Insights Advisor daily summary report"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setAdvisorEmailTemplatesV2Enabled(true);
        migrate();
        result = generateAggregatedEmailSubject(context);
        assertEquals("Daily digest - Advisor - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testDailyEmailBody() {
        AdvisorEmailAggregator aggregator = new AdvisorEmailAggregator();
        aggregator.aggregate(createEmailAggregation(NEW_RECOMMENDATION, TEST_RULE_1));
        aggregator.aggregate(createEmailAggregation(NEW_RECOMMENDATION, TEST_RULE_6));
        aggregator.aggregate(createEmailAggregation(RESOLVED_RECOMMENDATION, TEST_RULE_2));
        aggregator.aggregate(createEmailAggregation(DEACTIVATED_RECOMMENDATION, TEST_RULE_3));
        aggregator.aggregate(createEmailAggregation(DEACTIVATED_RECOMMENDATION, TEST_RULE_4));

        Map<String, Object> context = aggregator.getContext();
        context.put("start_time", LocalDateTime.now().toString());
        context.put("end_time", LocalDateTime.now().toString());

        String result = generateAggregatedEmailBody(context);
        assertTrue(result.contains("Hi John,"));
        assertTrue(result.contains("New recommendations"));
        assertTrue(result.contains("/insights/advisor/recommendations/test|Active_rule_1"));
        assertTrue(result.contains("Active rule 1</a>"));
        assertTrue(result.contains("<span class=\"rh-incident\">Incident</span>"));
        assertTrue(result.contains("/apps/frontend-assets/email-assets/img_important.png"));
        assertTrue(result.contains("Resolved recommendations"));
        assertTrue(result.contains("/insights/advisor/recommendations/test|Active_rule_2"));
        assertTrue(result.contains("Active rule 2</a>"));
        assertTrue(result.contains("/apps/frontend-assets/email-assets/img_low.png"));
        assertTrue(result.contains("Deactivated recommendations"));
        assertTrue(result.contains("/insights/advisor/recommendations/test|Active_rule_3"));
        assertTrue(result.contains("Active rule 3</a>"));
        assertTrue(result.contains("/apps/frontend-assets/email-assets/img_critical.png"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setAdvisorEmailTemplatesV2Enabled(true);
        migrate();
        result = generateAggregatedEmailBody(context);
        assertTrue(result.contains("New Recommendations"));
        assertTrue(result.contains("/insights/advisor/recommendations/test|Active_rule_1"));
        assertTrue(result.contains("Active rule 1</a>"));
        assertTrue(result.contains("https://console.redhat.com/apps/frontend-assets/email-assets/img_incident.png"));
        assertTrue(result.contains("/apps/frontend-assets/email-assets/img_important.png"));
        assertTrue(result.contains("Resolved Recommendation"));
        assertTrue(result.contains("/insights/advisor/recommendations/test|Active_rule_2"));
        assertTrue(result.contains("Active rule 2</a>"));
        assertTrue(result.contains("/apps/frontend-assets/email-assets/img_low.png"));
        assertTrue(result.contains("Deactivated Recommendations"));
        assertTrue(result.contains("/insights/advisor/recommendations/test|Active_rule_3"));
        assertTrue(result.contains("Active rule 3</a>"));
        assertTrue(result.contains("/apps/frontend-assets/email-assets/img_critical.png"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testDailyEmailBodyResolvedOnly() {
        AdvisorEmailAggregator aggregator = new AdvisorEmailAggregator();
        aggregator.aggregate(createEmailAggregation(RESOLVED_RECOMMENDATION, TEST_RULE_2));

        Map<String, Object> context = aggregator.getContext();
        context.put("start_time", LocalDateTime.now().toString());
        context.put("end_time", LocalDateTime.now().toString());

        String result = generateAggregatedEmailBody(context);
        assertTrue(result.contains("Hi John,"));
        assertTrue(result.contains("Resolved recommendations"));
        assertTrue(result.contains("/insights/advisor/recommendations/test|Active_rule_2"));
        assertTrue(result.contains("Active rule 2</a>"));
        assertTrue(result.contains("/apps/frontend-assets/email-assets/img_low.png"));
        assertFalse(result.contains("New recommendations"));
        assertFalse(result.contains("Deactivated recommendations"));
        assertFalse(result.contains("/apps/frontend-assets/email-assets/img_critical.png"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setAdvisorEmailTemplatesV2Enabled(true);
        migrate();
        result = generateAggregatedEmailBody(context);
        assertTrue(result.contains("Resolved Recommendation"));
        assertTrue(result.contains("/insights/advisor/recommendations/test|Active_rule_2"));
        assertTrue(result.contains("Active rule 2</a>"));
        assertTrue(result.contains("/apps/frontend-assets/email-assets/img_low.png"));
        assertFalse(result.contains("New Recommendation"));
        assertFalse(result.contains("Deactivated Recommendation"));
        assertFalse(result.contains("/apps/frontend-assets/email-assets/img_critical.png"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    void testInstantEmailTitleForResolvedRecommendations() {
        Action action = TestHelpers.createAdvisorAction("123456", RESOLVED_RECOMMENDATION);

        String result = generateEmailSubject(RESOLVED_RECOMMENDATION, action);
        assertEquals("Red Hat Enterprise Linux - Advisor Instant Notification - 03 Oct 2020 15:22 UTC - 4 resolved recommendations\n", result, "Title contains the number of reports created");

        // Action with only 1 event
        action.setEvents(List.of(action.getEvents().get(0)));
        result = generateEmailSubject(RESOLVED_RECOMMENDATION, action);
        assertEquals("Red Hat Enterprise Linux - Advisor Instant Notification - 03 Oct 2020 15:22 UTC - 1 resolved recommendation\n", result, "Title contains the number of reports created");

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setAdvisorEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailSubject(RESOLVED_RECOMMENDATION, action);
        assertEquals("Instant notification - Resolved recommendation - Advisor - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testInstantEmailTitleForNewRecommendations() {
        Action action = TestHelpers.createAdvisorAction("123456", NEW_RECOMMENDATION);
        String result = generateEmailSubject(NEW_RECOMMENDATION, action);
        assertEquals("Red Hat Enterprise Linux - Advisor Instant Notification - 03 Oct 2020 15:22 UTC - 4 new recommendations\n", result, "Title contains the number of reports created");

        // Action with only 1 event
        action.setEvents(List.of(action.getEvents().get(0)));
        result = generateEmailSubject(NEW_RECOMMENDATION, action);
        assertEquals("Red Hat Enterprise Linux - Advisor Instant Notification - 03 Oct 2020 15:22 UTC - 1 new recommendation\n", result, "Title contains the number of reports created");

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setAdvisorEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailSubject(NEW_RECOMMENDATION, action);
        assertEquals("Instant notification - New recommendation - Advisor - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testInstantEmailBodyForNewRecommendation() {
        Action action = TestHelpers.createAdvisorAction("123456", NEW_RECOMMENDATION);

        String result = generateEmailBody(NEW_RECOMMENDATION, action);
        checkNewRecommendationsBodyResults(action, result);

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setAdvisorEmailTemplatesV2Enabled(true);
        action = TestHelpers.createAdvisorAction("123456", NEW_RECOMMENDATION);
        migrate();
        result = generateEmailBody(NEW_RECOMMENDATION, action);
        checkNewRecommendationsBodyResults(action, result);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    private void checkNewRecommendationsBodyResults(Action action, final String result) {
        action.getEvents().forEach(event -> {
            assertTrue(
                result.contains(event.getPayload().getAdditionalProperties().get("rule_id").toString()),
                "Body should contain rule id" + event.getPayload().getAdditionalProperties().get("rule_id")
            );
            assertTrue(
                result.contains(event.getPayload().getAdditionalProperties().get("rule_description").toString()),
                "Body should contain rule description" + event.getPayload().getAdditionalProperties().get("rule_description")
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

    @Test
    public void testInstantEmailTitleForDeactivatedRecommendation() {
        Action action = TestHelpers.createAdvisorAction("123456", DEACTIVATED_RECOMMENDATION);
        String result = generateEmailSubject(DEACTIVATED_RECOMMENDATION, action);
        assertEquals("Red Hat Enterprise Linux - Advisor Instant Notification - 03 Oct 2020 15:22 UTC - 2 deactivated recommendations\n", result, "Title contains the number of reports created");

        // Action with only 1 event
        action.setEvents(List.of(action.getEvents().get(0)));
        result = generateEmailSubject(DEACTIVATED_RECOMMENDATION, action);
        assertEquals("Red Hat Enterprise Linux - Advisor Instant Notification - 03 Oct 2020 15:22 UTC - 1 deactivated recommendation\n", result, "Title contains the number of reports created");

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setAdvisorEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailSubject(DEACTIVATED_RECOMMENDATION, action);
        assertEquals("Instant notification - Deactivated recommendation - Advisor - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testInstantEmailBodyForDeactivatedRecommendation() {
        Action action = TestHelpers.createAdvisorAction("123456", DEACTIVATED_RECOMMENDATION);
        String result = generateEmailBody(DEACTIVATED_RECOMMENDATION, action);
        checkDeactivatedRecommendationResults(action, result);
        assertTrue(result.contains("<span class=\"rh-metric__count\">2</span>"),
            "Body should contain the number of deactivated recommendations");
        assertTrue(result.contains("Hi John,"), "Body should contain user's first name");

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setAdvisorEmailTemplatesV2Enabled(true);
        action = TestHelpers.createAdvisorAction("123456", DEACTIVATED_RECOMMENDATION);
        migrate();
        result = generateEmailBody(DEACTIVATED_RECOMMENDATION, action);
        checkDeactivatedRecommendationResults(action, result);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantEmailBodyForResolvedRecommendation() {
        Action action = TestHelpers.createAdvisorAction("123456", RESOLVED_RECOMMENDATION);
        String result = generateEmailBody(RESOLVED_RECOMMENDATION, action);
        checkNewRecommendationsBodyResults(action, result);

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setAdvisorEmailTemplatesV2Enabled(true);
        action = TestHelpers.createAdvisorAction("123456", RESOLVED_RECOMMENDATION);
        migrate();
        result = generateEmailBody(RESOLVED_RECOMMENDATION, action);
        checkNewRecommendationsBodyResults(action, result);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    private void checkDeactivatedRecommendationResults(Action action, final String result) {
        action.getEvents().forEach(event -> {
            assertTrue(result.contains(event.getPayload().getAdditionalProperties().get("rule_description").toString()),
                "Body should contain rule description" + event.getPayload().getAdditionalProperties().get("rule_description"));
            assertTrue(result.contains(event.getPayload().getAdditionalProperties().get("affected_systems").toString()),
                "Body should contain affected systems" + event.getPayload().getAdditionalProperties().get("affected_systems"));
            assertTrue(result.contains(event.getPayload().getAdditionalProperties().get("deactivation_reason").toString()),
                "Body should contain deactivation reason" + event.getPayload().getAdditionalProperties().get("deactivation_reason"));
        });

        assertTrue(result.contains("alt=\"Low severity\""), "Body should contain low severity rule image");
        assertTrue(result.contains("alt=\"Moderate severity\""), "Body should contain moderate severity rule image");
    }
}
