package com.redhat.cloud.notifications.templates;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.InventoryTestHelpers;
import com.redhat.cloud.notifications.PatchTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.models.Template;
import com.redhat.cloud.notifications.processors.email.EmailPendo;
import com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator;
import com.redhat.cloud.notifications.processors.email.aggregators.ImageBuilderAggregator;
import com.redhat.cloud.notifications.processors.email.aggregators.InventoryEmailAggregator;
import com.redhat.cloud.notifications.processors.email.aggregators.PatchEmailPayloadAggregator;
import com.redhat.cloud.notifications.templates.models.DailyDigestSection;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.AdvisorTestHelpers.createEmailAggregation;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static com.redhat.cloud.notifications.processors.email.EmailPendoResolver.GENERAL_PENDO_MESSAGE;
import static com.redhat.cloud.notifications.processors.email.EmailPendoResolver.GENERAL_PENDO_TITLE;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.DEACTIVATED_RECOMMENDATION;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.NEW_RECOMMENDATION;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.RESOLVED_RECOMMENDATION;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregatorTest.TEST_RULE_1;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregatorTest.TEST_RULE_2;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregatorTest.TEST_RULE_3;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregatorTest.TEST_RULE_4;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregatorTest.TEST_RULE_6;
import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestSingleDailyTemplate extends EmailTemplatesInDbHelper {

    static final List<String> applications = List.of("advisor", "compliance", "inventory",
        "policies", "patch", "resource-optimization", "vulnerability", "image-builder");

    String myCurrentApp;

    @Inject
    Environment environment;

    @Override
    protected String getApp() {
        return myCurrentApp;
    }

    @Override
    @BeforeEach
    protected void initData() {
        Bundle bundle = null;
        try {
            bundle = resourceHelpers.findBundle(getBundle());
        } catch (NoResultException nre) {
            bundle = resourceHelpers.createBundle(getBundle());
        }

        for (String app : applications) {
            try {
                resourceHelpers.findApp(getBundle(), app);
            } catch (NoResultException nre) {
                resourceHelpers.createApp(bundle.getId(), app);
            }
        }

        migrate();
    }

    @Test
    public void testDailyEmailBodyAllApplications() {

        Map<String, DailyDigestSection> dataMap = new HashMap<>();

        generateAggregatedEmailBody(buildPoliciesAggregatedPayload(), "policies", dataMap);

        generateAggregatedEmailBody(buildAdvisorAggregatedPayload(), "advisor", dataMap);

        generateAggregatedEmailBody(buildMapFromAction(TestHelpers.createComplianceAction()), "compliance", dataMap);

        InventoryEmailAggregator aggregator = new InventoryEmailAggregator();
        aggregator.aggregate(InventoryTestHelpers.createEmailAggregation("tenant", "rhel", "inventory", "test event"));
        aggregator.aggregate(InventoryTestHelpers.createMinimalEmailAggregationV2(InventoryEmailAggregator.EVENT_TYPE_SYSTEM_DELETED, UUID.randomUUID(), "host-name"));
        aggregator.aggregate(InventoryTestHelpers.createMinimalEmailAggregationV2(InventoryEmailAggregator.EVENT_TYPE_SYSTEM_DELETED, UUID.randomUUID(), "host-name2"));
        aggregator.aggregate(InventoryTestHelpers.createMinimalEmailAggregationV2(InventoryEmailAggregator.EVENT_TYPE_NEW_SYSTEM_REGISTERED, UUID.randomUUID(), "host-name"));
        aggregator.aggregate(InventoryTestHelpers.createMinimalEmailAggregationV2(InventoryEmailAggregator.EVENT_TYPE_NEW_SYSTEM_REGISTERED, UUID.randomUUID(), "host-name2"));
        aggregator.aggregate(InventoryTestHelpers.createMinimalEmailAggregationV2(InventoryEmailAggregator.EVENT_TYPE_NEW_SYSTEM_REGISTERED, UUID.randomUUID(), "host-name3"));
        aggregator.aggregate(InventoryTestHelpers.createMinimalEmailAggregationV2(InventoryEmailAggregator.EVENT_TYPE_NEW_SYSTEM_REGISTERED, UUID.randomUUID(), "host-name4"));
        aggregator.aggregate(InventoryTestHelpers.createMinimalEmailAggregationV2(InventoryEmailAggregator.EVENT_TYPE_SYSTEM_BECAME_STALE, UUID.randomUUID(), "host-name"));
        aggregator.aggregate(InventoryTestHelpers.createMinimalEmailAggregationV2(InventoryEmailAggregator.EVENT_TYPE_SYSTEM_BECAME_STALE, UUID.randomUUID(), "host-name2"));
        aggregator.aggregate(InventoryTestHelpers.createMinimalEmailAggregationV2(InventoryEmailAggregator.EVENT_TYPE_SYSTEM_BECAME_STALE, UUID.randomUUID(), "host-name3"));

        generateAggregatedEmailBody(aggregator.getContext(), "inventory", dataMap);

        generateAggregatedEmailBody(buildPatchAggregatedPayload(), "patch", dataMap);

        generateAggregatedEmailBody(buildMapFromAction(TestHelpers.createResourceOptimizationAction()), "resource-optimization", dataMap);
        generateAggregatedEmailBody(buildMapFromAction(TestHelpers.createVulnerabilityAction()), "vulnerability", dataMap);

        generateAggregatedEmailBody(buildImageBuilderAggregatedPayload(), "image-builder", dataMap);

        // sort application by name
        List<DailyDigestSection> result = dataMap.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .collect(Collectors.toList());

        Optional<Template> dailyTemplate = templateRepository.findTemplateByName("Common/insightsDailyEmailBody");
        assertTrue(dailyTemplate.isPresent());

        TemplateInstance bodyTemplate = templateService.compileTemplate(dailyTemplate.get().getData(), "singleDailyDigest/dailyDigest");

        assertNotNull(bodyTemplate);
        Map<String, Object> mapData = Map.of("title", "Daily digest - Red Hat Enterprise Linux", "items", result, "orgId", DEFAULT_ORG_ID);

        EmailPendo emailPendo = new EmailPendo(GENERAL_PENDO_TITLE, String.format(GENERAL_PENDO_MESSAGE, environment.url()));

        String templateResult = generateEmailFromContextMap(bodyTemplate, mapData, null);
        templateResultChecks(templateResult);
        assertFalse(templateResult.contains(emailPendo.getPendoTitle()));
        assertFalse(templateResult.contains(emailPendo.getPendoMessage()));

        templateResult = generateEmailFromContextMap(bodyTemplate, mapData, emailPendo);
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

    private void addItem(Map<String, DailyDigestSection> dataMap, String applicationName, String payload) {
        String titleData = payload.split("<!-- Body section -->")[0];
        String[] sections = titleData.split("<!-- next section -->");

        dataMap.put(applicationName, new DailyDigestSection(payload.split("<!-- Body section -->")[1], Arrays.stream(sections).filter(e -> !e.isBlank()).collect(Collectors.toList())));
    }

    protected void generateAggregatedEmailBody(Map<String, Object> context, String app, Map<String, DailyDigestSection> dataMap) {
        context.put("application", app);
        AggregationEmailTemplate emailTemplate = templateRepository.findAggregationEmailTemplate(getBundle(), app, DAILY).get();
        TemplateInstance bodyTemplate = templateService.compileTemplate(emailTemplate.getBodyTemplate().getData(), emailTemplate.getBodyTemplate().getName());
        addItem(dataMap, app, generateEmailFromContextMap(bodyTemplate, context, null));
    }

    private static Map<String, Object> buildMapFromAction(Action action) {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper
            .convertValue(action.getContext(), new TypeReference<Map<String, Object>>() { });
    }

    private static Map<String, Object> buildImageBuilderAggregatedPayload() {

        String LAUNCH_SUCCESS = "launch-success";
        String LAUNCH_FAILURE = "launch-failed";

        ImageBuilderAggregator aggregator = new ImageBuilderAggregator();
        aggregator.aggregate(TestHelpers.createImageBuilderAggregation(LAUNCH_SUCCESS));
        aggregator.aggregate(TestHelpers.createImageBuilderAggregation(LAUNCH_SUCCESS));
        aggregator.aggregate(TestHelpers.createImageBuilderAggregation(LAUNCH_FAILURE));
        return aggregator.getContext();
    }

    private static Map<String, Object> buildPatchAggregatedPayload() {
        String enhancement = "enhancement";
        String bugfix = "bugfix";
        String security = "security";

        PatchEmailPayloadAggregator aggregator = new PatchEmailPayloadAggregator();
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation("rhel", "patch", "advisory_1", "synopsis", security, "host-01"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation("rhel", "patch", "advisory_2", "synopsis", enhancement, "host-01"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation("rhel", "patch", "advisory_3", "synopsis", enhancement, "host-02"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation("rhel", "patch", "advisory_4", "synopsis", bugfix, "host-03"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation("rhel", "patch", "advisory_5", "synopsis", bugfix, "host-04"));

        return aggregator.getContext();
    }

    private static Map<String, Object> buildAdvisorAggregatedPayload() {
        AdvisorEmailAggregator aggregator = new AdvisorEmailAggregator();
        aggregator.aggregate(createEmailAggregation(NEW_RECOMMENDATION, TEST_RULE_1));
        aggregator.aggregate(createEmailAggregation(NEW_RECOMMENDATION, TEST_RULE_6));
        aggregator.aggregate(createEmailAggregation(RESOLVED_RECOMMENDATION, TEST_RULE_2));
        aggregator.aggregate(createEmailAggregation(DEACTIVATED_RECOMMENDATION, TEST_RULE_3));
        aggregator.aggregate(createEmailAggregation(DEACTIVATED_RECOMMENDATION, TEST_RULE_4));

        Map<String, Object> context = aggregator.getContext();
        context.put("start_time", LocalDateTime.now().toString());
        context.put("end_time", LocalDateTime.now().toString());

        return context;
    }

    private static Map<String, Object> buildPoliciesAggregatedPayload() {
        LocalDateTime startTime = LocalDateTime.of(2021, 4, 22, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 4, 22, 14, 15, 33);

        Map<String, Object> policy01 = new HashMap<>();
        policy01.put("policy_id", "policy-01");
        policy01.put("policy_name", "My policy 01");
        policy01.put("unique_system_count", 2);

        Map<String, Object> policy02 = new HashMap<>();
        policy02.put("policy_id", "policy-02");
        policy02.put("policy_name", "My policy 02");
        policy02.put("unique_system_count", 1);

        Map<String, Object> policy03 = new HashMap<>();
        policy03.put("policy_id", "policy-03");
        policy03.put("policy_name", "My policy 03");
        policy03.put("unique_system_count", 1);

        Map<String, Object> policies = new HashMap<>();
        policies.put("policy-01", policy01);
        policies.put("policy-02", policy02);
        policies.put("policy-03", policy03);

        Map<String, Object> payload = new HashMap<>();
        payload.put("start_time", startTime);
        payload.put("end_time", endTime);
        payload.put("policies", policies);
        payload.put("unique_system_count", 3);
        return payload;
    }
}
