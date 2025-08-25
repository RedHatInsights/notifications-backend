package email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import email.pojo.DailyDigestSection;
import email.pojo.EmailPendo;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static email.TestAdvisorTemplate.JSON_ADVISOR_DEFAULT_AGGREGATION_CONTEXT;
import static email.TestImageBuilderTemplate.JSON_IMAGE_BUILDER_DEFAULT_AGGREGATION_CONTEXT;
import static email.TestInventoryTemplate.JSON_INVENTORY_DEFAULT_AGGREGATION_CONTEXT;
import static email.TestPatchTemplate.JSON_PATCH_DEFAULT_AGGREGATION_CONTEXT;
import static email.TestResourceOptimizationTemplate.JSON_RESOURCE_OPTIMIZATION_DEFAULT_AGGREGATION_CONTEXT;
import static email.pojo.EmailPendo.GENERAL_PENDO_MESSAGE;
import static email.pojo.EmailPendo.GENERAL_PENDO_TITLE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestRhelDailyTemplate extends EmailTemplatesRendererHelper {

    String myCurrentApp;

    @Override
    protected String getApp() {
        return myCurrentApp;
    }

    @Inject
    TemplateService templateService;

    @Test
    public void testDailyEmailBodyAllApplications() throws JsonProcessingException {

        Map<String, DailyDigestSection> dataMap = new HashMap<>();

        generateAggregatedEmailBody(TestPoliciesTemplate.buildPoliciesAggregatedPayload(), "policies", dataMap);

        generateAggregatedEmailBody(JSON_ADVISOR_DEFAULT_AGGREGATION_CONTEXT, "advisor", dataMap);

        generateAggregatedEmailBody(templateService.convertActionToContextMap(TestHelpers.createComplianceAction()), "compliance", dataMap);

        generateAggregatedEmailBody(JSON_INVENTORY_DEFAULT_AGGREGATION_CONTEXT, "inventory", dataMap);

        generateAggregatedEmailBody(JSON_PATCH_DEFAULT_AGGREGATION_CONTEXT, "patch", dataMap);

        generateAggregatedEmailBody(JSON_RESOURCE_OPTIMIZATION_DEFAULT_AGGREGATION_CONTEXT, "resource-optimization", dataMap);

        generateAggregatedEmailBody(templateService.convertActionToContextMap(TestHelpers.createVulnerabilityAction()), "vulnerability", dataMap);

        generateAggregatedEmailBody(JSON_IMAGE_BUILDER_DEFAULT_AGGREGATION_CONTEXT, "image-builder", dataMap);

        // sort application by name
        List<DailyDigestSection> result = dataMap.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .toList();

        TemplateDefinition globalDailyTemplateDefinition = new TemplateDefinition(IntegrationType.EMAIL_DAILY_DIGEST_BUNDLE_AGGREGATION_BODY, null, null, null);

        Map<String, Object> mapData = Map.of("title", "Daily digest - Red Hat Enterprise Linux", "items", result, "orgId", DEFAULT_ORG_ID);

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
        assertTrue(templateResult.contains("\"#advisor-section1\""));
        assertTrue(templateResult.contains("\"#compliance-section1\""));
        assertTrue(templateResult.contains("\"#image-builder-section1\""));
        assertTrue(templateResult.contains("\"#image-builder-section2\""));
        assertTrue(templateResult.contains("\"#inventory-section1\""));
        assertTrue(templateResult.contains("\"#patch-section1\""));
        assertTrue(templateResult.contains("\"#policies-section1\""));
        assertTrue(templateResult.contains("\"#resource-optimization-section1\""));
        assertTrue(templateResult.contains("\"#vulnerability-section1\""));

        assertTrue(templateResult.contains("\"advisor-section1\""));
        assertTrue(templateResult.contains("\"compliance-section1\""));
        assertTrue(templateResult.contains("\"image-builder-section1\""));
        assertTrue(templateResult.contains("\"image-builder-section2\""));
        assertTrue(templateResult.contains("\"inventory-section1\""));
        assertTrue(templateResult.contains("\"patch-section1\""));
        assertTrue(templateResult.contains("\"policies-section1\""));
        assertTrue(templateResult.contains("\"resource-optimization-section1\""));
        assertTrue(templateResult.contains("\"vulnerability-section1\""));

        // Query parameters in URLs
        assertTrue(templateResult.contains("/insights/patch/advisories/advisory_3?from=notifications&integration=daily_digest\">advisory_3</a>"));
    }
}
