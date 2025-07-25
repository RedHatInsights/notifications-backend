package email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.mapping.SubscriptionServices;
import email.pojo.DailyDigestSection;
import email.pojo.EmailPendo;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static email.TestErrataTemplate.JSON_ERRATA_DEFAULT_AGGREGATION_CONTEXT;
import static email.pojo.EmailPendo.GENERAL_PENDO_MESSAGE;
import static email.pojo.EmailPendo.GENERAL_PENDO_TITLE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TestSubscriptionServicesDailyTemplate extends EmailTemplatesRendererHelper {

    String myCurrentApp;

    @Override
    protected String getApp() {
        return myCurrentApp;
    }

    @Override
    protected String getBundle() {
        return SubscriptionServices.BUNDLE_NAME;
    }

    @Test
    void testDailyEmailBodyAllApplications() throws JsonProcessingException {

        Map<String, DailyDigestSection> dataMap = new HashMap<>();

        generateAggregatedEmailBody(JSON_ERRATA_DEFAULT_AGGREGATION_CONTEXT, SubscriptionServices.ERRATA_APP_NAME, dataMap);

        // sort application by name
        List<DailyDigestSection> result = dataMap.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .toList();

        TemplateDefinition globalDailyTemplateDefinition = new TemplateDefinition(IntegrationType.EMAIL_DAILY_DIGEST_BUNDLE_AGGREGATION_BODY, null, null, null);

        Map<String, Object> mapData = Map.of("title", "Daily digest - Subscription Services", "items", result, "orgId", DEFAULT_ORG_ID);

        EmailPendo emailPendo = new EmailPendo(GENERAL_PENDO_TITLE, String.format(GENERAL_PENDO_MESSAGE, environment.url()));

        String templateResult = generateEmailFromContextMap(globalDailyTemplateDefinition, mapData, null);
        templateResultChecks(templateResult);
        assertFalse(templateResult.contains(emailPendo.getPendoTitle()));
        assertFalse(templateResult.contains(emailPendo.getPendoMessage()));

        templateResult = generateEmailFromContextMap(globalDailyTemplateDefinition, mapData, emailPendo);
        templateResultChecks(templateResult);
        assertTrue(templateResult.contains(emailPendo.getPendoTitle()));
        assertTrue(templateResult.contains(emailPendo.getPendoMessage()));
    }

    private static void templateResultChecks(String templateResult) {
        assertTrue(templateResult.contains("\"#errata-notifications-section1-1\""));
        assertTrue(templateResult.contains("\"#errata-notifications-section1-2\""));
        assertTrue(templateResult.contains("\"#errata-notifications-section1-3\""));
    }


}
