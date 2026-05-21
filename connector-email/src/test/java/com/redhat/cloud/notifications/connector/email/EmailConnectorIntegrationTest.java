package com.redhat.cloud.notifications.connector.email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.MockServerLifecycleManager;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.model.EmailNotification;
import com.redhat.cloud.notifications.connector.email.model.aggregation.ApplicationAggregatedData;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import com.redhat.cloud.notifications.connector.email.model.settings.User;
import com.redhat.cloud.notifications.connector.email.processors.bop.BOPManager;
import com.redhat.cloud.notifications.connector.v2.BaseConnectorIntegrationTest;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import helpers.TestHelpers;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.redhat.cloud.notifications.connector.email.CloudEventHistoryBuilder.TOTAL_RECIPIENTS_KEY;
import static email.TestAdvisorTemplate.JSON_ADVISOR_DEFAULT_AGGREGATION_CONTEXT;
import static email.TestInventoryTemplate.JSON_INVENTORY_DEFAULT_AGGREGATION_CONTEXT;
import static email.TestPatchTemplate.JSON_PATCH_DEFAULT_AGGREGATION_CONTEXT;
import static email.TestResourceOptimizationTemplate.JSON_RESOURCE_OPTIMIZATION_DEFAULT_AGGREGATION_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class EmailConnectorIntegrationTest extends BaseConnectorIntegrationTest {

    private static final String PATCH_TEST_EVENT = "{\"account_id\":\"\",\"application\":\"patch\",\"bundle\":\"rhel\",\"context\":{\"system_check_in\":\"2022-08-03T15:22:42.199046\",\"start_time\":\"2022-08-03T15:22:42.199046\",\"patch\":{\"Alpha\":[\"advA\",\"advB\",\"advC\"],\"Roman\":[\"advI\",\"advII\",\"advIII\"],\"Numerical\":[\"adv1\",\"adv2\"]}},\"event_type\":\"new-advisory\",\"events\":[{\"metadata\":{},\"payload\":{\"advisory_name\":\"name 1\",\"synopsis\":\"synopsis 1\"}},{\"metadata\":{},\"payload\":{\"advisory_name\":\"name 2\",\"synopsis\":\"synopsis 2\"}}],\"orgId\":\"default-org-id\",\"timestamp\":\"2022-10-03T15:22:13.000000025\",\"severity\":\"MODERATE\",\"source\":{\"application\":{\"display_name\":\"Patch\"},\"bundle\":{\"display_name\":\"Red Hat Enterprise Linux\"},\"event_type\":{\"display_name\":\"New Advisory\"}},\"environment\":{\"url\":\"https://localhost\",\"ocmUrl\":\"https://localhost\"},\"pendo_message\":null,\"ignore_user_preferences\":true}";

    @InjectSpy
    EmailConnectorConfig emailConnectorConfig;

    @Inject
    ObjectMapper objectMapper;

    @InjectSpy
    BOPManager bopManager;

    @Inject
    TemplateService templateService;

    @Override
    protected JsonObject buildIncomingPayload(String targetUrl) {
        return buildEmailPayload(buildInstantEmailContext(), false, null);
    }

    @Override
    protected String getConnectorSpecificTargetUrl() {
        return getMockServerUrl();
    }

    private void setupMocks(Integer recipientsStatus, String recipientsBody, Integer bopStatus, Integer delayMs) {
        MockServerLifecycleManager.getClient().resetAll();

        if (recipientsStatus != null) {
            var responseBuilder = aResponse()
                .withStatus(recipientsStatus)
                .withHeader("Content-Type", "application/json")
                .withBody(recipientsBody != null ? recipientsBody : "");

            if (delayMs != null && delayMs > 0) {
                responseBuilder.withFixedDelay(delayMs);
            }

            MockServerLifecycleManager.getClient().stubFor(
                put(urlEqualTo("/internal/recipients-resolver"))
                    .willReturn(responseBuilder)
            );
        }

        if (bopStatus != null) {
            var responseBuilder = aResponse()
                .withStatus(bopStatus)
                .withHeader("Content-Type", "application/json");

            if (delayMs != null && delayMs > 0) {
                responseBuilder.withFixedDelay(delayMs);
            }

            MockServerLifecycleManager.getClient().stubFor(
                post(urlEqualTo("/v1/sendEmails"))
                    .willReturn(responseBuilder)
            );
        }
    }

    @Test
    void testEmptyRecipients() {
        setupMocks(200, "[]", 200, null);

        JsonObject payload = buildEmailPayload(buildInstantEmailContext(), false, null);
        String cloudEventId = sendCloudEventMessage(payload);

        JsonObject data = waitForOutgoingMessage(cloudEventId);
        assertTrue(data.getBoolean("successful"));
        assertEquals(0, data.getJsonObject("details").getInteger(TOTAL_RECIPIENTS_KEY));

        assertMetricsIncrement(1, 0);
        assertHandlerDurationTimerRecorded(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testWithRecipients(boolean emailsInternalOnlyEnabled) throws Exception {
        try {
            emailConnectorConfig.setEmailsInternalOnlyEnabled(emailsInternalOnlyEnabled);
            Set<User> users = TestUtils.createUsers("user-1", "user-2", "user-3", "user-4", "user-5", "user-6", "user-7");
            String strUsers = objectMapper.writeValueAsString(users);
            setupMocks(200, strUsers, 200, null);

            Set<String> additionalEmails = Set.of("redhat_user@redhat.com", "external_user@noway.com");
            int usersAndRecipientsTotalNumber = users.size() + additionalEmails.size();

            JsonObject payload = buildEmailPayload(buildInstantEmailContext(), false, additionalEmails);
            String cloudEventId = sendCloudEventMessage(payload);

            JsonObject data = waitForOutgoingMessage(cloudEventId);
            assertTrue(data.getBoolean("successful"));

            @SuppressWarnings("unchecked")
            final ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass((Class) List.class);
            verify(bopManager, times(3))
                .sendToBop(listCaptor.capture(), anyString(), anyString(), anyString());

            checkRecipientsAndHistory(usersAndRecipientsTotalNumber, listCaptor.getAllValues(), data, emailsInternalOnlyEnabled, "external_user@noway.com");

            assertMetricsIncrement(1, 0);
            assertHandlerDurationTimerRecorded(1);
        } finally {
            emailConnectorConfig.setEmailsInternalOnlyEnabled(false);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testWithRecipientsForDailyDigest(boolean emailsInternalOnlyEnabled) throws Exception {
        try {
            emailConnectorConfig.setEmailsInternalOnlyEnabled(emailsInternalOnlyEnabled);
            Set<User> users = TestUtils.createUsers("user-1", "user-2", "user-3", "user-4", "user-5", "user-6", "user-7");
            String strUsers = objectMapper.writeValueAsString(users);
            setupMocks(200, strUsers, 200, null);

            Set<String> additionalEmails = Set.of("redhat_user@redhat.com", "external_user@noway.com");
            int usersAndRecipientsTotalNumber = users.size() + additionalEmails.size();

            JsonObject payload = buildEmailPayload(buildRhelDailyDigestEmailContext(), true, additionalEmails);
            String cloudEventId = sendCloudEventMessage(payload);

            JsonObject data = waitForOutgoingMessage(cloudEventId);
            assertTrue(data.getBoolean("successful"));

            @SuppressWarnings("unchecked")
            final ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass((Class) List.class);
            verify(bopManager, times(3))
                .sendToBop(listCaptor.capture(), anyString(), anyString(), anyString());

            checkRecipientsAndHistory(usersAndRecipientsTotalNumber, listCaptor.getAllValues(), data, emailsInternalOnlyEnabled, "external_user@noway.com");

            assertMetricsIncrement(1, 0);
            assertHandlerDurationTimerRecorded(1);
        } finally {
            emailConnectorConfig.setEmailsInternalOnlyEnabled(false);
        }
    }

    @Test
    void testFailureFetchingRecipientsInternalError() {
        setupMocks(500, "", null, null);

        JsonObject payload = buildEmailPayload(buildInstantEmailContext(), false, null);
        String cloudEventId = sendCloudEventMessage(payload);

        JsonObject data = waitForOutgoingMessage(cloudEventId);
        assertFalse(data.getBoolean("successful"));
        assertEquals(0, data.getJsonObject("details").getInteger(TOTAL_RECIPIENTS_KEY));

        assertMetricsIncrement(0, 1);
        assertHandlerDurationTimerRecorded(1);
    }

    @Test
    void testFailureFetchingRecipientsTimeout() {
        setupMocks(200, "[]", null, 5000);

        JsonObject payload = buildEmailPayload(buildInstantEmailContext(), false, null);
        String cloudEventId = sendCloudEventMessage(payload);

        JsonObject data = waitForOutgoingMessage(cloudEventId);
        assertFalse(data.getBoolean("successful"));

        assertMetricsIncrement(0, 1);
        assertHandlerDurationTimerRecorded(1);
    }

    @Test
    void testFailureBopInternalError() throws Exception {
        Set<User> users = TestUtils.createUsers("user-1", "user-2", "user-3", "user-4", "user-5", "user-6", "user-7");
        String strUsers = objectMapper.writeValueAsString(users);
        setupMocks(200, strUsers, 500, null);

        JsonObject payload = buildEmailPayload(buildInstantEmailContext(), false, null);
        String cloudEventId = sendCloudEventMessage(payload);

        JsonObject data = waitForOutgoingMessage(cloudEventId);
        assertFalse(data.getBoolean("successful"));

        assertMetricsIncrement(0, 1);
        assertHandlerDurationTimerRecorded(1);
    }

    @Test
    void testFailureBopTimeout() throws Exception {
        Set<User> users = TestUtils.createUsers("user-1", "user-2", "user-3", "user-4", "user-5", "user-6", "user-7");
        String strUsers = objectMapper.writeValueAsString(users);

        MockServerLifecycleManager.getClient().resetAll();
        MockServerLifecycleManager.getClient().stubFor(
            put(urlEqualTo("/internal/recipients-resolver"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(strUsers))
        );
        MockServerLifecycleManager.getClient().stubFor(
            post(urlEqualTo("/v1/sendEmails"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withFixedDelay(5000))
        );

        JsonObject payload = buildEmailPayload(buildInstantEmailContext(), false, null);
        String cloudEventId = sendCloudEventMessage(payload);

        JsonObject data = waitForOutgoingMessage(cloudEventId);
        assertFalse(data.getBoolean("successful"));

        assertMetricsIncrement(0, 1);
        assertHandlerDurationTimerRecorded(1);
    }

    // --- Helper methods ---

    private JsonObject buildEmailPayload(Map<String, Object> eventData, boolean isDailyDigest, Set<String> emailRecipients) {
        RecipientSettings recipientSettings = new RecipientSettings(false, false, null, null, emailRecipients);

        EmailNotification emailNotification = new EmailNotification(
            "Not used",
            "123456",
            "123456",
            List.of(recipientSettings),
            new ArrayList<>(),
            new ArrayList<>(),
            false,
            null,
            eventData,
            isDailyDigest
        );

        return JsonObject.mapFrom(emailNotification);
    }

    private static void checkRecipientsAndHistory(int usersAndRecipientsTotalNumber, List<List<String>> recipientsSentToBop,
                                                  JsonObject data, boolean emailsInternalOnlyEnabled, String filteredRecipient) {
        Set<String> receivedEmails = new HashSet<>();
        for (List<String> recipientsList : recipientsSentToBop) {
            assertTrue(recipientsList.size() <= 3);
            receivedEmails.addAll(recipientsList);
        }

        if (emailsInternalOnlyEnabled) {
            assertFalse(receivedEmails.contains(filteredRecipient));
            assertEquals(usersAndRecipientsTotalNumber - 1, receivedEmails.size());
        } else {
            assertTrue(receivedEmails.contains(filteredRecipient));
            assertEquals(usersAndRecipientsTotalNumber, receivedEmails.size());
        }

        assertTrue(data.getBoolean("successful"));

        if (emailsInternalOnlyEnabled) {
            assertEquals(usersAndRecipientsTotalNumber - 1, data.getJsonObject("details").getInteger(TOTAL_RECIPIENTS_KEY));
        } else {
            assertEquals(usersAndRecipientsTotalNumber, data.getJsonObject("details").getInteger(TOTAL_RECIPIENTS_KEY));
        }
    }

    private Map<String, Object> buildInstantEmailContext() {
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<>() { };
        try {
            return objectMapper.readValue(PATCH_TEST_EVENT, typeRef);
        } catch (JsonProcessingException e) {
            fail(e);
            return null;
        }
    }

    private Map<String, Object> buildRhelDailyDigestEmailContext() {
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<>() { };

        List<ApplicationAggregatedData> applicationAggregatedDataList = new ArrayList<>();
        try {
            applicationAggregatedDataList.add(new ApplicationAggregatedData(
                "advisor",
                objectMapper.readValue(JSON_ADVISOR_DEFAULT_AGGREGATION_CONTEXT, typeRef)
            ));
            applicationAggregatedDataList.add(new ApplicationAggregatedData(
                "compliance",
                templateService.convertActionToContextMap(TestHelpers.createComplianceAction())
            ));
            applicationAggregatedDataList.add(new ApplicationAggregatedData(
                "inventory",
                objectMapper.readValue(JSON_INVENTORY_DEFAULT_AGGREGATION_CONTEXT, typeRef)
            ));
            applicationAggregatedDataList.add(new ApplicationAggregatedData(
                "patch",
                objectMapper.readValue(JSON_PATCH_DEFAULT_AGGREGATION_CONTEXT, typeRef)
            ));
            applicationAggregatedDataList.add(new ApplicationAggregatedData(
                "resource-optimization",
                objectMapper.readValue(JSON_RESOURCE_OPTIMIZATION_DEFAULT_AGGREGATION_CONTEXT, typeRef)
            ));
            applicationAggregatedDataList.add(new ApplicationAggregatedData(
                "vulnerability",
                templateService.convertActionToContextMap(TestHelpers.createVulnerabilityAction())
            ));
        } catch (JsonProcessingException e) {
            fail(e);
        }

        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("environment", Map.of("url", "https://root.env.url.com"));
        additionalContext.put("bundle_name", "rhel");
        additionalContext.put("bundle_display_name", "Red Hat Enterprise Linux");
        additionalContext.put("pendo_message", null);
        additionalContext.put("application_aggregated_data_list", new JsonArray(objectMapper.convertValue(applicationAggregatedDataList, List.class)));

        return additionalContext;
    }
}
