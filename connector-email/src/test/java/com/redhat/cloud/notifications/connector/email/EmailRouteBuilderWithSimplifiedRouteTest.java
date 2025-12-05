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
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import email.TestPoliciesTemplate;
import helpers.TestHelpers;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.MediaType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.connector.ConnectorRoutesTest.KAFKA_SOURCE_MOCK;
import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.CONNECTOR_TO_ENGINE;
import static com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder.ENGINE_TO_CONNECTOR;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventFilter.X_RH_NOTIFICATIONS_CONNECTOR_HEADER;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventProcessor.*;
import static com.redhat.cloud.notifications.connector.email.CloudEventHistoryBuilder.TOTAL_RECIPIENTS_KEY;
import static email.TestAdvisorTemplate.JSON_ADVISOR_DEFAULT_AGGREGATION_CONTEXT;
import static email.TestImageBuilderTemplate.JSON_IMAGE_BUILDER_DEFAULT_AGGREGATION_CONTEXT;
import static email.TestInventoryTemplate.JSON_INVENTORY_DEFAULT_AGGREGATION_CONTEXT;
import static email.TestPatchTemplate.JSON_PATCH_DEFAULT_AGGREGATION_CONTEXT;
import static email.TestResourceOptimizationTemplate.JSON_RESOURCE_OPTIMIZATION_DEFAULT_AGGREGATION_CONTEXT;
import static org.apache.camel.builder.AdviceWith.adviceWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockserver.model.HttpResponse.response;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class EmailRouteBuilderWithSimplifiedRouteTest extends CamelQuarkusTestSupport {
    private static final String PATCH_TEST_EVENT = "{\"account_id\":\"\",\"application\":\"patch\",\"bundle\":\"rhel\",\"context\":{\"system_check_in\":\"2022-08-03T15:22:42.199046\",\"start_time\":\"2022-08-03T15:22:42.199046\",\"patch\":{\"Alpha\":[\"advA\",\"advB\",\"advC\"],\"Roman\":[\"advI\",\"advII\",\"advIII\"],\"Numerical\":[\"adv1\",\"adv2\"]}},\"event_type\":\"new-advisory\",\"events\":[{\"metadata\":{},\"payload\":{\"advisory_name\":\"name 1\",\"synopsis\":\"synopsis 1\"}},{\"metadata\":{},\"payload\":{\"advisory_name\":\"name 2\",\"synopsis\":\"synopsis 2\"}}],\"orgId\":\"default-org-id\",\"timestamp\":\"2022-10-03T15:22:13.000000025\",\"severity\":\"MODERATE\",\"source\":{\"application\":{\"display_name\":\"Patch\"},\"bundle\":{\"display_name\":\"Red Hat Enterprise Linux\"},\"event_type\":{\"display_name\":\"New Advisory\"}},\"environment\":{\"url\":\"https://localhost\",\"ocmUrl\":\"https://localhost\"},\"pendo_message\":null,\"ignore_user_preferences\":true}";

    @InjectSpy
    EmailConnectorConfig emailConnectorConfig;

    @Inject
    ObjectMapper objectMapper;

    static boolean camelRoutesInitialised = false;

    static MockEndpoint kafkaConnectorToEngine;
    static MockEndpoint kafkaEngineToConnector;

    @InjectSpy
    BOPManager bopManager;

    @Inject
    TemplateService templateService;

    Map<String, Object> eventData;
    boolean isDailyDigest = false;

    @BeforeEach
    void beforeEach() {
        when(emailConnectorConfig.useSimplifiedEmailRoute(anyString())).thenReturn(true);
        eventData = buildInstantEmailContext();
        isDailyDigest = false;
    }

    void initCamelRoutes() throws Exception {

        adviceWith(emailConnectorConfig.getConnectorName(), context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpoints(
                    "direct:" + CONNECTOR_TO_ENGINE
                );
                mockEndpointsAndSkip("kafka:" + emailConnectorConfig.getOutgoingKafkaTopic());
            }
        });

        adviceWith(CONNECTOR_TO_ENGINE, context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpointsAndSkip("kafka:" + emailConnectorConfig.getOutgoingKafkaTopic());
            }
        });

        adviceWith(ENGINE_TO_CONNECTOR, context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                replaceFromWith(KAFKA_SOURCE_MOCK);
            }
        });

        kafkaConnectorToEngine = getMockEndpoint("mock:kafka:" + emailConnectorConfig.getOutgoingKafkaTopic());
        kafkaEngineToConnector = getMockEndpoint("mock:" + KAFKA_SOURCE_MOCK);
    }

    void initMocks(ExpectationResponseCallback verifyEmptyRequest, ExpectationResponseCallback bopResponse) throws Exception {

        MockServerLifecycleManager.getClient().reset();
        getMockHttpRequest("/internal/recipients-resolver", "PUT", verifyEmptyRequest);
        getMockHttpRequest("/v1/sendEmails", "POST", bopResponse);
        if (!camelRoutesInitialised) {
            initCamelRoutes();
            camelRoutesInitialised = true;
        }
    }

    @Test
    void testEmptyRecipients() throws Exception {

        ExpectationResponseCallback recipientsResolverResponse = req -> response().withBody("[]").withStatusCode(200);
        ExpectationResponseCallback bopResponse = req -> response().withStatusCode(200);
        initMocks(recipientsResolverResponse, bopResponse);

        kafkaConnectorToEngine.expectedMessageCount(1);

        buildCloudEventAndSendIt(null);

        kafkaConnectorToEngine.assertIsSatisfied();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testWithRecipients(boolean emailsInternalOnlyEnabled) throws Exception {
        try {
            emailConnectorConfig.setEmailsInternalOnlyEnabled(emailsInternalOnlyEnabled);
            Set<User> users = TestUtils.createUsers("user-1", "user-2", "user-3", "user-4", "user-5", "user-6", "user-7");
            String strUsers = objectMapper.writeValueAsString(users);
            ExpectationResponseCallback recipientsResolverResponse = req -> response().withContentType(MediaType.APPLICATION_JSON).withBody(strUsers).withStatusCode(200);
            ExpectationResponseCallback bopResponse = req -> response().withContentType(MediaType.APPLICATION_JSON).withStatusCode(200);
            initMocks(recipientsResolverResponse, bopResponse);

            Set<String> additionalEmails = Set.of("redhat_user@redhat.com", "external_user@noway.com");
            int usersAndRecipientsTotalNumber = users.size() + additionalEmails.size();

            kafkaConnectorToEngine.expectedMessageCount(1);

            buildCloudEventAndSendIt(additionalEmails);

            kafkaConnectorToEngine.assertIsSatisfied(2000);

            final ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass((Class) List.class);
            verify(bopManager, times(3))
                .sendToBop(listCaptor.capture(), anyString(), anyString(), anyString());

            checkRecipientsAndHistory(usersAndRecipientsTotalNumber, listCaptor.getAllValues(), kafkaConnectorToEngine, emailsInternalOnlyEnabled, "external_user@noway.com");
        } finally {
            emailConnectorConfig.setEmailsInternalOnlyEnabled(false);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testWithRecipientsForDailyDigest(boolean emailsInternalOnlyEnabled) throws Exception {
        eventData = buildRhelDailyDigestEmailContext();
        isDailyDigest = true;
        try {
            emailConnectorConfig.setEmailsInternalOnlyEnabled(emailsInternalOnlyEnabled);
            Set<User> users = TestUtils.createUsers("user-1", "user-2", "user-3", "user-4", "user-5", "user-6", "user-7");
            String strUsers = objectMapper.writeValueAsString(users);
            ExpectationResponseCallback recipientsResolverResponse = req -> response().withContentType(MediaType.APPLICATION_JSON).withBody(strUsers).withStatusCode(200);
            ExpectationResponseCallback bopResponse = req -> response().withContentType(MediaType.APPLICATION_JSON).withStatusCode(200);
            initMocks(recipientsResolverResponse, bopResponse);

            Set<String> additionalEmails = Set.of("redhat_user@redhat.com", "external_user@noway.com");
            int usersAndRecipientsTotalNumber = users.size() + additionalEmails.size();

            kafkaConnectorToEngine.expectedMessageCount(1);

            buildCloudEventAndSendIt(additionalEmails);

            kafkaConnectorToEngine.assertIsSatisfied(2000);

            final ArgumentCaptor<List<String>> listCaptor = ArgumentCaptor.forClass((Class) List.class);
            verify(bopManager, times(3))
                .sendToBop(listCaptor.capture(), anyString(), anyString(), anyString());

            checkRecipientsAndHistory(usersAndRecipientsTotalNumber, listCaptor.getAllValues(), kafkaConnectorToEngine, emailsInternalOnlyEnabled, "external_user@noway.com");
        } finally {
            emailConnectorConfig.setEmailsInternalOnlyEnabled(false);
        }
    }

    @Test
    void testFailureFetchingRecipientsInternalError() throws Exception {

        ExpectationResponseCallback recipientsResolverResponse = req -> response().withContentType(MediaType.APPLICATION_JSON).withStatusCode(500);
        initMocks(recipientsResolverResponse, null);

        kafkaConnectorToEngine.expectedMessageCount(1);

        buildCloudEventAndSendIt(null);

        kafkaConnectorToEngine.assertIsSatisfied();
        List<JsonObject> responseDetails = checkRecipientsAndHistoryFailure(kafkaConnectorToEngine, 0, true);
        for (JsonObject responseDetail : responseDetails) {
            assertEquals(500, responseDetail.getJsonObject("error").getInteger("http_status_code"));
            assertEquals("HTTP_5XX", responseDetail.getJsonObject("error").getString("error_type"));
        }
    }

    @Test
    void testFailureFetchingRecipientsTimeout() throws Exception {
        initMocks(null, null);

        kafkaConnectorToEngine.expectedMessageCount(1);

        buildCloudEventAndSendIt(null);

        kafkaConnectorToEngine.assertIsSatisfied();
        checkRecipientsAndHistoryFailure(kafkaConnectorToEngine, 0, false);
    }

    @Test
    void testFailureBopInternalError() throws Exception {

        Set<User> users = TestUtils.createUsers("user-1", "user-2", "user-3", "user-4", "user-5", "user-6", "user-7");
        String strUsers = objectMapper.writeValueAsString(users);
        ExpectationResponseCallback recipientsResolverResponse = req -> response().withBody(strUsers).withContentType(MediaType.APPLICATION_JSON).withStatusCode(200);
        ExpectationResponseCallback bopInternalError = req -> response().withContentType(MediaType.APPLICATION_JSON).withStatusCode(500);
        initMocks(recipientsResolverResponse, bopInternalError);

        kafkaConnectorToEngine.expectedMessageCount(1);
        buildCloudEventAndSendIt(null);

        kafkaConnectorToEngine.assertIsSatisfied();

        List<JsonObject> responseDetails = checkRecipientsAndHistoryFailure(kafkaConnectorToEngine, 7, true);
        for (JsonObject responseDetail : responseDetails) {
            assertEquals(500, responseDetail.getJsonObject("error").getInteger("http_status_code"));
            assertEquals("HTTP_5XX", responseDetail.getJsonObject("error").getString("error_type"));
        }
    }

    @Test
    void testFailureBopRecipientsTimeout() throws Exception {

        Set<User> users = TestUtils.createUsers("user-1", "user-2", "user-3", "user-4", "user-5", "user-6", "user-7");
        String strUsers = objectMapper.writeValueAsString(users);
        ExpectationResponseCallback recipientsResolverResponse = req -> response().withContentType(MediaType.APPLICATION_JSON).withBody(strUsers).withStatusCode(200);

        initMocks(recipientsResolverResponse, null);

        kafkaConnectorToEngine.expectedMessageCount(1);
        buildCloudEventAndSendIt(null);

        kafkaConnectorToEngine.assertIsSatisfied();

        checkRecipientsAndHistoryFailure(kafkaConnectorToEngine, 7, false);
    }

    private List<JsonObject> checkRecipientsAndHistoryFailure(MockEndpoint kafkaEndpoint, int expectedRecipientNumber, boolean errorDetailsExpected) {

        int expectedBopRequests = expectedRecipientNumber > 0 ? 1 : 0;
        verify(bopManager, times(expectedBopRequests))
            .sendToBop(anyList(), anyString(), anyString(), anyString());

        List<JsonObject> dataToReturn = new ArrayList<>();
        // check metrics sent to engine
        for (Exchange kafkaMessage : kafkaEndpoint.getReceivedExchanges()) {
            JsonObject payload = new JsonObject(kafkaMessage.getIn().getBody(String.class));
            JsonObject data = new JsonObject(payload.getString("data"));

            assertFalse(data.getBoolean("successful"));
            assertTrue(data.containsKey("details"));
            assertEquals(expectedRecipientNumber, data.getJsonObject("details").getInteger(TOTAL_RECIPIENTS_KEY));
            assertFalse(data.getJsonObject("details").getString("outcome").isBlank());
            if (errorDetailsExpected) {
                assertTrue(data.containsKey("error"));
                assertFalse(data.getJsonObject("error").getString("error_type").isBlank());
            }
            dataToReturn.add(data);
        }
        return dataToReturn;
    }

    private static void checkRecipientsAndHistory(int usersAndRecipientsTotalNumber, List<List<String>> recipientsSentToBop, MockEndpoint kafkaEndpoint, boolean emailsInternalOnlyEnabled, String filteredRecipient) {

        // check recipients sent to bop
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

        // check metrics sent to engine
        Exchange kafkaMessage  = kafkaEndpoint.getReceivedExchanges().stream().findFirst().get();
        JsonObject payload = new JsonObject(kafkaMessage.getIn().getBody(String.class));
        JsonObject data = new JsonObject(payload.getString("data"));

        assertTrue(data.getBoolean("successful"));

        if (emailsInternalOnlyEnabled) {
            assertEquals(usersAndRecipientsTotalNumber - 1, data.getJsonObject("details").getInteger(TOTAL_RECIPIENTS_KEY));
        } else {
            assertEquals(usersAndRecipientsTotalNumber, data.getJsonObject("details").getInteger(TOTAL_RECIPIENTS_KEY));
        }
    }

    private HttpRequest getMockHttpRequest(String path, String method, ExpectationResponseCallback expectationResponseCallback) {
        HttpRequest postReq = new HttpRequest()
            .withPath(path)
            .withMethod(method);

        Delay responseDelay = Delay.milliseconds(0);
        if (expectationResponseCallback == null) {
            responseDelay = Delay.seconds(1);
        }
        MockServerLifecycleManager.getClient()
            .withSecure(false)
            .when(postReq)
            .respond(expectationResponseCallback, responseDelay);
        return postReq;
    }

    private void buildCloudEventAndSendIt(Set<String> emailRecipients) {
        final JsonObject cloudEvent = generateIncomingCloudEvent(emailRecipients);

        final Map<String, Object> headers = new HashMap<>();
        headers.put(X_RH_NOTIFICATIONS_CONNECTOR_HEADER, emailConnectorConfig.getConnectorName());
        template.sendBodyAndHeaders(KAFKA_SOURCE_MOCK, cloudEvent.encode(), headers);
    }

    private JsonObject generateIncomingCloudEvent(Set<String> emailRecipients) {
        RecipientSettings recipientSettings = new RecipientSettings(false, false, null, null, emailRecipients);

        final EmailNotification emailNotification = new EmailNotification(
            "test email body",
            "[MODERATE] test email subject",
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
        final JsonObject payload = JsonObject.mapFrom(emailNotification);

        final String cloudEventId = UUID.randomUUID().toString();

        final JsonObject cloudEvent = new JsonObject();
        cloudEvent.put(CLOUD_EVENT_ID, cloudEventId);
        cloudEvent.put(CLOUD_EVENT_TYPE, "com.redhat.console.notification.toCamel." + emailConnectorConfig.getConnectorName());
        cloudEvent.put(CLOUD_EVENT_DATA, JsonObject.mapFrom(payload));
        return cloudEvent;
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
                "Compliance",
                templateService.convertActionToContextMap(TestHelpers.createComplianceAction())
            ));
            applicationAggregatedDataList.add(new ApplicationAggregatedData(
                "image-builder",
                objectMapper.readValue(JSON_IMAGE_BUILDER_DEFAULT_AGGREGATION_CONTEXT, typeRef)
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
                "policies",
                TestPoliciesTemplate.buildPoliciesAggregatedPayload()
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

    private Map<String, Object> buildInstantEmailContext() {
        TypeReference<HashMap<String, Object>> typeRef = new TypeReference<>() { };
        try {
            return objectMapper.readValue(PATCH_TEST_EVENT, typeRef);
        } catch (JsonProcessingException e) {
            fail(e);
            return null;
        }
    }
}
