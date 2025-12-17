package email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import email.pojo.DailyDigestSection;
import email.pojo.EmailPendo;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static email.TestAdvisorTemplate.JSON_ADVISOR_DEFAULT_AGGREGATION_CONTEXT;
import static email.TestInventoryTemplate.JSON_INVENTORY_FULL_AGGREGATION_CONTEXT;
import static email.TestPatchTemplate.JSON_PATCH_DEFAULT_AGGREGATION_CONTEXT;
import static email.TestResourceOptimizationTemplate.JSON_RESOURCE_OPTIMIZATION_DEFAULT_AGGREGATION_CONTEXT;
import static email.pojo.EmailPendo.GENERAL_PENDO_MESSAGE;
import static email.pojo.EmailPendo.GENERAL_PENDO_TITLE;
import static helpers.TestHelpers.DEFAULT_ORG_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@QuarkusTest
public class TestRhelDailyTemplate extends EmailTemplatesRendererHelper {

    String myCurrentApp;

    @Override
    protected String getApp() {
        return myCurrentApp;
    }

    @InjectSpy
    TemplateService templateService;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testDailyEmailBodyAllApplications(boolean useBetaTemplate) throws JsonProcessingException {
        String result = commonTestDailyEmailBodyAllApplications(useBetaTemplate);
        assertFalse(result.contains(COMMON_SECURED_LABEL_CHECK));
    }

    @Test
    public void testSecureDailyEmailBodyAllApplications() throws JsonProcessingException {
        when(templateService.isSecuredEmailTemplatesEnabled()).thenReturn(true);
        templateService.init();

        String result = commonTestDailyEmailBodyAllApplications(false);
        assertTrue(result.contains(COMMON_SECURED_LABEL_CHECK));
    }

    public String commonTestDailyEmailBodyAllApplications(boolean useBetaTemplate) throws JsonProcessingException {

        Map<String, DailyDigestSection> dataMap = new HashMap<>();

        generateAggregatedEmailBody(JSON_ADVISOR_DEFAULT_AGGREGATION_CONTEXT, "advisor", dataMap, useBetaTemplate);

        generateAggregatedEmailBody(templateService.convertActionToContextMap(TestHelpers.createComplianceAction()), "compliance", dataMap, useBetaTemplate);

        generateAggregatedEmailBody(JSON_INVENTORY_FULL_AGGREGATION_CONTEXT, "inventory", dataMap, useBetaTemplate);

        generateAggregatedEmailBody(JSON_PATCH_DEFAULT_AGGREGATION_CONTEXT, "patch", dataMap, useBetaTemplate);

        generateAggregatedEmailBody(JSON_RESOURCE_OPTIMIZATION_DEFAULT_AGGREGATION_CONTEXT, "resource-optimization", dataMap, useBetaTemplate);

        generateAggregatedEmailBody(templateService.convertActionToContextMap(TestHelpers.createVulnerabilityAction()), "vulnerability", dataMap, useBetaTemplate);

        // sort application by name
        List<DailyDigestSection> result = dataMap.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .toList();

        TemplateDefinition globalDailyTemplateDefinition = new TemplateDefinition(IntegrationType.EMAIL_DAILY_DIGEST_BUNDLE_AGGREGATION_BODY, null, null, null, useBetaTemplate);
        TemplateDefinition globalDailyTitleTemplateDefinition = new TemplateDefinition(IntegrationType.EMAIL_DAILY_DIGEST_BUNDLE_AGGREGATION_TITLE, null, null, null);

        EmailPendo emailPendo = new EmailPendo(GENERAL_PENDO_TITLE, String.format(GENERAL_PENDO_MESSAGE, environment.url()));

        Map<String, Object> mapDataTitle = Map.of("source", Map.of("bundle", Map.of("display_name", "Red Hat Enterprise Linux")));

        String templateTitleResult = templateService.renderTemplateWithCustomDataMap(globalDailyTitleTemplateDefinition, mapDataTitle);
        assertEquals("Daily Digest - Red Hat Enterprise Linux", templateTitleResult);

        Map<String, Object> mapData = Map.of("title", templateTitleResult, "items", result, "orgId", DEFAULT_ORG_ID);

        String templateResult = generateEmailFromContextMap(globalDailyTemplateDefinition, mapData, null);
        templateResultChecks(templateResult);
        assertFalse(templateResult.contains(emailPendo.getPendoTitle()));
        assertFalse(templateResult.contains(emailPendo.getPendoMessage()));

        templateResult = generateEmailFromContextMap(globalDailyTemplateDefinition, mapData, emailPendo);
        templateResultChecks(templateResult);
        assertTrue(templateResult.contains(emailPendo.getPendoTitle()));
        assertTrue(templateResult.contains(emailPendo.getPendoMessage()));
        return templateResult;
    }

    private static void templateResultChecks(String templateResult) {
        assertTrue(templateResult.contains("\"#advisor-section1\""));
        assertTrue(templateResult.contains("\"#compliance-section1\""));
        assertTrue(templateResult.contains("\"#inventory-section1\""));
        assertTrue(templateResult.contains("\"#patch-section1\""));
        assertTrue(templateResult.contains("\"#resource-optimization-section1\""));
        assertTrue(templateResult.contains("\"#vulnerability-section1\""));

        assertTrue(templateResult.contains("\"advisor-section1\""));
        assertTrue(templateResult.contains("\"compliance-section1\""));
        assertTrue(templateResult.contains("\"inventory-section1\""));
        assertTrue(templateResult.contains("\"patch-section1\""));
        assertTrue(templateResult.contains("\"resource-optimization-section1\""));
        assertTrue(templateResult.contains("\"vulnerability-section1\""));

        // Query parameters in URLs
        assertTrue(templateResult.contains("/insights/patch/advisories/advisory_3?from=notifications&integration=daily_digest\">advisory_3</a>"));
    }
}
