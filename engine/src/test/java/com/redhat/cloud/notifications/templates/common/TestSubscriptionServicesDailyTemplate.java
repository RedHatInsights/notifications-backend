package com.redhat.cloud.notifications.templates.common;

import com.redhat.cloud.notifications.EmailTemplatesRendererHelper;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.processors.email.EmailPendo;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.mapping.SubscriptionServices;
import com.redhat.cloud.notifications.templates.models.DailyDigestSection;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.ErrataTestHelpers.buildErrataAggregatedPayload;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.processors.email.EmailPendoResolver.GENERAL_PENDO_MESSAGE;
import static com.redhat.cloud.notifications.processors.email.EmailPendoResolver.GENERAL_PENDO_TITLE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TestSubscriptionServicesDailyTemplate extends EmailTemplatesRendererHelper {

    String myCurrentApp;

    @Inject
    Environment environment;

    @Override
    protected String getApp() {
        return myCurrentApp;
    }

    @Override
    protected String getBundle() {
        return SubscriptionServices.BUNDLE_NAME;
    }

    @Test
    void testDailyEmailBodyAllApplications() {

        Map<String, DailyDigestSection> dataMap = new HashMap<>();

        generateAggregatedEmailBody(buildErrataAggregatedPayload(), SubscriptionServices.ERRATA_APP_NAME, dataMap);

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

    private void addItem(Map<String, DailyDigestSection> dataMap, String applicationName, String payload) {
        String titleData = payload.split("<!-- Body section -->")[0];
        String[] sections = titleData.split("<!-- next section -->");

        dataMap.put(applicationName,
            new DailyDigestSection(payload.split("<!-- Body section -->")[1], Arrays.stream(sections)
                .filter(e -> !e.isBlank()).toList()));
    }

    protected void generateAggregatedEmailBody(Map<String, Object> context, String app, Map<String, DailyDigestSection> dataMap) {
        context.put("application", app);
        TemplateDefinition templateDefinition = new TemplateDefinition(IntegrationType.EMAIL_DAILY_DIGEST_BODY, getBundle(), app, null, true);

        addItem(dataMap, app, generateEmailFromContextMap(templateDefinition, context, null));
    }
}
