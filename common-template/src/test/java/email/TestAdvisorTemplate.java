package email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.redhat.cloud.notifications.ingress.Action;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.ADVISOR_DEACTIVATED_RECOMMENDATION;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.ADVISOR_RESOLVED_RECOMMENDATION;
import static email.TestAdvisorOpenShiftTemplate.NEW_RECOMMENDATION;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class TestAdvisorTemplate extends EmailTemplatesRendererHelper {

    @Override
    protected String getApp() {
        return "advisor";
    }

    @Override
    protected String getAppDisplayName() {
        return "Advisor";
    }

    public static final String JSON_ADVISOR_DEFAULT_AGGREGATION_CONTEXT = "{" +
        "   \"advisor\":{" +
        "      \"total_incident\":2," +
        "      \"total_recommendation\":5," +
        "      \"new_recommendations\":{" +
        "         \"test|Active_rule_1\":{" +
        "            \"total_risk\":\"3\"," +
        "            \"rule_description\":\"Active rule 1\"," +
        "            \"rule_url\":\"https://console.redhat.com/insights/advisor/recommendations/test|Active_rule_1\"," +
        "            \"has_incident\":\"true\"," +
        "            \"systems\":1" +
        "         }," +
        "         \"test|Active_rule_6\":{" +
        "            \"total_risk\":\"2\"," +
        "            \"rule_description\":\"Active rule 6 with a very long text to validate the carriage return\"," +
        "            \"rule_url\":\"https://console.redhat.com/insights/advisor/recommendations/test|Active_rule_6\"," +
        "            \"has_incident\":\"false\"," +
        "            \"systems\":1" +
        "         }" +
        "      }," +
        "      \"resolved_recommendations\":{" +
        "         \"test|Active_rule_2\":{" +
        "            \"total_risk\":\"1\"," +
        "            \"rule_description\":\"Active rule 2\"," +
        "            \"rule_url\":\"https://console.redhat.com/insights/advisor/recommendations/test|Active_rule_2\"," +
        "            \"has_incident\":\"false\"," +
        "            \"systems\":1" +
        "         }" +
        "      }," +
        "      \"deactivated_recommendations\":{" +
        "         \"test|Active_rule_3\":{" +
        "            \"total_risk\":\"4\"," +
        "            \"rule_description\":\"Active rule 3\"," +
        "            \"has_incident\":\"false\"," +
        "            \"rule_url\":\"https://console.redhat.com/insights/advisor/recommendations/test|Active_rule_3\"" +
        "         }," +
        "         \"test|Active_rule_4\":{" +
        "            \"total_risk\":\"4\"," +
        "            \"rule_description\":\"Active rule 4\"," +
        "            \"has_incident\":\"true\"," +
        "            \"rule_url\":\"https://console.redhat.com/insights/advisor/recommendations/test|Active_rule_4\"" +
        "         }" +
        "      }" +
        "   }," +
        "   \"start_time\":\"2025-07-25T13:38:38.379873993\"," +
        "   \"end_time\":\"2025-07-25T13:38:38.379899748\"" +
        "}";

    @Test
    public void testDailyEmailBody() throws JsonProcessingException {
        final String result = generateAggregatedEmailBody(JSON_ADVISOR_DEFAULT_AGGREGATION_CONTEXT);
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
    public void testDailyEmailBodyWithEmptyRuleValues() throws JsonProcessingException {

        final String jsonContext = "{" +
            "   \"advisor\":{" +
            "      \"total_incident\":0," +
            "      \"total_recommendation\":3," +
            "      \"new_recommendations\":{" +
            "         \"\":{" +
            "            \"rule_description\":\"\"," +
            "            \"total_risk\":\"\"," +
            "            \"has_incident\":\"\"," +
            "            \"rule_url\":\"\"," +
            "            \"systems\":2" +
            "         }" +
            "      }," +
            "      \"resolved_recommendations\":{" +
            "         \"\":{" +
            "            \"rule_description\":\"\"," +
            "            \"total_risk\":\"\"," +
            "            \"has_incident\":\"\"," +
            "            \"rule_url\":\"\"," +
            "            \"systems\":1" +
            "         }" +
            "      }," +
            "      \"deactivated_recommendations\":{" +
            "         \"\":{" +
            "            \"rule_description\":\"\"," +
            "            \"total_risk\":\"\"," +
            "            \"has_incident\":\"\"," +
            "            \"rule_url\":\"\"" +
            "         }" +
            "      }" +
            "   }," +
            "   \"start_time\":\"2025-07-25T13:51:20.657571343\"," +
            "   \"end_time\":\"2025-07-25T13:51:20.657602689\"" +
            "}";

        final String result = generateAggregatedEmailBody(jsonContext);
        // check that template is able to render sections, even if they are empty
        assertTrue(result.contains("New Recommendation"));
        assertTrue(result.contains("Resolved Recommendation"));
        assertTrue(result.contains("Deactivated Recommendation"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testDailyEmailBodyResolvedOnly() throws JsonProcessingException {
        final String jsonContext = "{" +
            "   \"advisor\":{" +
            "      \"total_incident\":0," +
            "      \"total_recommendation\":1," +
            "      \"resolved_recommendations\":{" +
            "         \"test|Active_rule_2\":{" +
            "            \"rule_description\":\"Active rule 2\"," +
            "            \"total_risk\":\"1\"," +
            "            \"has_incident\":\"false\"," +
            "            \"rule_url\":\"https://console.redhat.com/insights/advisor/recommendations/test|Active_rule_2\"," +
            "            \"systems\":1" +
            "         }" +
            "      }" +
            "   }," +
            "   \"start_time\":\"2025-07-25T13:56:28.213058072\"," +
            "   \"end_time\":\"2025-07-25T13:56:28.213081719\"" +
            "}";

        final String result = generateAggregatedEmailBody(jsonContext);
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
        eventTypeDisplayName = "Resolved recommendation";
        Action action = TestHelpers.createAdvisorAction("123456", ADVISOR_RESOLVED_RECOMMENDATION);

        String result = generateEmailSubject(ADVISOR_RESOLVED_RECOMMENDATION, action);
        assertEquals("Instant notification - Resolved recommendation - Advisor - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testInstantEmailTitleForNewRecommendations() {
        eventTypeDisplayName = "New recommendation";
        Action action = TestHelpers.createAdvisorAction("123456", NEW_RECOMMENDATION);
        String result = generateEmailSubject(NEW_RECOMMENDATION, action);
        assertEquals("Instant notification - New recommendation - Advisor - Red Hat Enterprise Linux", result);
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

        action.setEvents(action.getEvents().stream().filter(event -> event.getPayload().getAdditionalProperties().get("total_risk").equals("1")).toList());
    }

    @Test
    public void testInstantEmailTitleForDeactivatedRecommendation() {
        eventTypeDisplayName = "Deactivated recommendation";
        Action action = TestHelpers.createAdvisorAction("123456", ADVISOR_DEACTIVATED_RECOMMENDATION);
        String result = generateEmailSubject(ADVISOR_DEACTIVATED_RECOMMENDATION, action);
        assertEquals("Instant notification - Deactivated recommendation - Advisor - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testInstantEmailBodyForDeactivatedRecommendation() {
        Action action = TestHelpers.createAdvisorAction("123456", ADVISOR_DEACTIVATED_RECOMMENDATION);
        String result = generateEmailBody(ADVISOR_DEACTIVATED_RECOMMENDATION, action);
        checkDeactivatedRecommendationResults(action, result);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantEmailBodyForResolvedRecommendation() {
        Action action = TestHelpers.createAdvisorAction("123456", ADVISOR_RESOLVED_RECOMMENDATION);
        String result = generateEmailBody(ADVISOR_RESOLVED_RECOMMENDATION, action);
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
