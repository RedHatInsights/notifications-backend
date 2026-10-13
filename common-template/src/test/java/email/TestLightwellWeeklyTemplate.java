package email;

import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.mapping.Lightwell;
import email.pojo.DailyDigestSection;
import email.pojo.EmailPendo;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static email.pojo.EmailPendo.GENERAL_PENDO_MESSAGE;
import static email.pojo.EmailPendo.GENERAL_PENDO_TITLE;
import static helpers.TestHelpers.DEFAULT_ORG_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TestLightwellWeeklyTemplate extends EmailTemplatesRendererHelper {

    String myCurrentApp;

    @Override
    protected String getApp() {
        return myCurrentApp;
    }

    @Override
    protected String getBundle() {
        return Lightwell.BUNDLE_NAME;
    }

    @Override
    protected String getBundleDisplayName() {
        return "Lightwell";
    }

    @Override
    protected String getAppDisplayName() {
        return "Lightwell";
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testWeekEmailBody(boolean useBetaVersion) {

        Map<String, DailyDigestSection> dataMap = new HashMap<>();

        // Create test context for Lightwell application
        Map<String, Object> lightwellContext = createLightwellWeeklyDigestContext();

        generateWeeklyAggregatedEmailBody(lightwellContext, Lightwell.LIGHTWELL_APP_NAME, dataMap, useBetaVersion);

        // Sort application by name
        List<DailyDigestSection> result = dataMap.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .toList();

        TemplateDefinition globalWeeklyTemplateDefinition = new TemplateDefinition(IntegrationType.EMAIL_WEEKLY_DIGEST_BUNDLE_AGGREGATION_BODY, null, null, null, useBetaVersion);
        TemplateDefinition globalWeeklyTitleTemplateDefinition = new TemplateDefinition(IntegrationType.EMAIL_WEEKLY_DIGEST_BUNDLE_AGGREGATION_TITLE, null, null, null);

        Map<String, Object> mapDataTitle = Map.of("source", Map.of("bundle", Map.of("display_name", "Lightwell")));

        String templateTitleResult = templateService.renderTemplateWithCustomDataMap(globalWeeklyTitleTemplateDefinition, mapDataTitle);
        assertEquals("Weekly Digest - Lightwell", templateTitleResult);

        Map<String, Object> mapData = Map.of("title", templateTitleResult, "items", result, "orgId", DEFAULT_ORG_ID, "useLightwellBranding", true);

        EmailPendo emailPendo = new EmailPendo(GENERAL_PENDO_TITLE, String.format(GENERAL_PENDO_MESSAGE, environment.url()));

        String templateResult = generateEmailFromContextMap(globalWeeklyTemplateDefinition, mapData, null);
        templateResultChecks(templateResult);
        assertFalse(templateResult.contains(emailPendo.getPendoTitle()));
        assertFalse(templateResult.contains(emailPendo.getPendoMessage()));

        templateResult = generateEmailFromContextMap(globalWeeklyTemplateDefinition, mapData, emailPendo);
        templateResultChecks(templateResult);
        assertTrue(templateResult.contains(emailPendo.getPendoTitle()));
        assertTrue(templateResult.contains(emailPendo.getPendoMessage()));
    }

    private static void templateResultChecks(String templateResult) {
        // Check for Lightwell logo
        assertTrue(templateResult.contains("Logo_lightwell_dark.png"));

        // Check for section anchors
        assertTrue(templateResult.contains("\"#lightwell-java-remediated\""));
        assertTrue(templateResult.contains("\"#lightwell-python-remediated\""));
        assertTrue(templateResult.contains("\"lightwell-java-remediated\""));
        assertTrue(templateResult.contains("\"lightwell-python-remediated\""));

        // Check for Coverage Updates summary
        assertTrue(templateResult.contains("Coverage Updates"));
        assertTrue(templateResult.contains("Repositories"));
        assertTrue(templateResult.contains("Packages"));

        // Check for event type section titles
        assertTrue(templateResult.contains("New Packages Available for Java Remediated"));
        assertTrue(templateResult.contains("New Packages Available for Python Remediated"));

        // Check for intro text
        assertTrue(templateResult.contains("The following packages were fixed by Lightwell"));

        // Check for package links
        assertTrue(templateResult.contains("org.glassfish.jaxb:codemodel"));
        assertTrue(templateResult.contains("requests"));

        // Check for CTA button
        assertTrue(templateResult.contains("Go to Lightwell"));

        // Check for query parameters in URLs
        assertTrue(templateResult.contains("from=notifications&integration=weekly_digest"));
    }

    private Map<String, Object> createLightwellWeeklyDigestContext() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("total_advisories", 5);
        context.put("total_packages", 3);

        Map<String, Map<String, Object>> packagesByEventType = new LinkedHashMap<>();

        // Java Remediated event type
        packagesByEventType.put("java-remediated", createEventTypePackages("Java Remediated", List.of(
            createPackageInfo("org.glassfish.jaxb:codemodel", "https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:codemodel", 3),
            createPackageInfo("org.glassfish.jaxb:jaxb-core", "https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:jaxb-core", 1)
        )));

        // Python Remediated event type
        packagesByEventType.put("python-remediated", createEventTypePackages("Python Remediated", List.of(
            createPackageInfo("requests", "https://console.redhat.com/lightwell/packages/requests", 2)
        )));

        context.put("total_repositories", packagesByEventType.size());
        context.put("packages_by_event_type", packagesByEventType);
        context.put("application", "lightwell");
        context.put("useLightwellBranding", true);

        return context;
    }

    private Map<String, Object> createEventTypePackages(String eventTypeDisplayName, List<Map<String, Object>> packages) {
        Map<String, Object> eventTypeData = new LinkedHashMap<>();
        eventTypeData.put("event_type_display_name", eventTypeDisplayName);
        eventTypeData.put("packages", packages);
        return eventTypeData;
    }

    private Map<String, Object> createPackageInfo(String packageName, String packageLink, int advisoryCount) {
        Map<String, Object> packageInfo = new LinkedHashMap<>();
        packageInfo.put("package_name", packageName);
        packageInfo.put("package_link", packageLink);
        packageInfo.put("advisory_count", advisoryCount);
        return packageInfo;
    }
}
