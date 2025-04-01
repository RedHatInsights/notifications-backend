package com.redhat.cloud.notifications.connector.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.MockServerLifecycleManager;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.model.EmailNotification;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import com.redhat.cloud.notifications.connector.email.model.settings.User;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
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
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
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
import static com.redhat.cloud.notifications.connector.IncomingCloudEventProcessor.CLOUD_EVENT_DATA;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventProcessor.CLOUD_EVENT_ID;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventProcessor.CLOUD_EVENT_TYPE;
import static com.redhat.cloud.notifications.connector.email.CloudEventHistoryBuilder.TOTAL_RECIPIENTS_KEY;
import static com.redhat.cloud.notifications.connector.email.constants.Routes.SEND_EMAIL_BOP;
import static com.redhat.cloud.notifications.connector.email.constants.Routes.SPLIT_AND_SEND;
import static org.apache.camel.builder.AdviceWith.adviceWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockserver.model.HttpResponse.response;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EmailRouteBuilderTest extends CamelQuarkusTestSupport {
    @InjectSpy
    EmailConnectorConfig emailConnectorConfig;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    static boolean camelRoutesInitialised = false;

    static MockEndpoint splitRoute;
    static MockEndpoint bopRoute;
    static MockEndpoint kafkaConnectorToEngine;
    static MockEndpoint kafkaEngineToConnector;

    void initCamelRoutes() throws Exception {

        adviceWith(emailConnectorConfig.getConnectorName(), context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpoints(
                    "direct:" + CONNECTOR_TO_ENGINE,
                    "direct:" + SPLIT_AND_SEND
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

        adviceWith(SPLIT_AND_SEND, context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpoints("direct:" + SEND_EMAIL_BOP);
            }
        });

        adviceWith(ENGINE_TO_CONNECTOR, context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                replaceFromWith(KAFKA_SOURCE_MOCK);
            }
        });

        splitRoute = getMockEndpoint("mock:direct:" + SPLIT_AND_SEND);
        bopRoute = getMockEndpoint("mock:direct:" + SEND_EMAIL_BOP);
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

    @BeforeEach
    void beforeEach() {
        when(emailConnectorConfig.useSimplifiedEmailRoute(anyString())).thenReturn(false);
    }

    @Test
    void testEmptyRecipients() throws Exception {

        ExpectationResponseCallback recipientsResolverResponse = req -> response().withBody("[]").withStatusCode(200);
        ExpectationResponseCallback bopResponse = req -> response().withStatusCode(200);
        initMocks(recipientsResolverResponse, bopResponse);

        splitRoute.expectedMessageCount(0);
        kafkaConnectorToEngine.expectedMessageCount(1);

        buildCloudEventAndSendIt(null);

        splitRoute.assertIsSatisfied();
        kafkaConnectorToEngine.assertIsSatisfied();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testWithRecipients(boolean emailsInternalOnlyEnabled) throws Exception {
        try {
            emailConnectorConfig.setEmailsInternalOnlyEnabled(emailsInternalOnlyEnabled);
            Set<User> users = TestUtils.createUsers("user-1", "user-2", "user-3", "user-4", "user-5", "user-6", "user-7");
            String strUsers = objectMapper.writeValueAsString(users);
            ExpectationResponseCallback recipientsResolverResponse = req -> response().withBody(strUsers).withStatusCode(200);
            ExpectationResponseCallback bopResponse = req -> response().withStatusCode(200);
            initMocks(recipientsResolverResponse, bopResponse);

            Set<String> additionalEmails = Set.of("redhat_user@redhat.com", "external_user@noway.com");
            int usersAndRecipientsTotalNumber = users.size() + additionalEmails.size();

            splitRoute.expectedMessageCount(1);
            bopRoute.expectedMessageCount(3);
            kafkaConnectorToEngine.expectedMessageCount(1);

            buildCloudEventAndSendIt(additionalEmails);

            splitRoute.assertIsSatisfied();
            bopRoute.assertIsSatisfied();
            kafkaConnectorToEngine.assertIsSatisfied(2000);

            checkRecipientsAndHistory(usersAndRecipientsTotalNumber, usersAndRecipientsTotalNumber, bopRoute, kafkaConnectorToEngine, emailsInternalOnlyEnabled, "external_user@noway.com");
        } finally {
            emailConnectorConfig.setEmailsInternalOnlyEnabled(false);
        }
    }

    @Test
    void testFailureFetchingRecipientsInternalError() throws Exception {

        ExpectationResponseCallback recipientsResolverResponse = req -> response().withStatusCode(500);
        initMocks(recipientsResolverResponse, null);

        splitRoute.expectedMessageCount(0);
        kafkaConnectorToEngine.expectedMessageCount(1);

        buildCloudEventAndSendIt(null);

        kafkaConnectorToEngine.assertIsSatisfied();
        splitRoute.assertIsSatisfied();
        List<JsonObject> responseDetails = checkRecipientsAndHistory(false, bopRoute, kafkaConnectorToEngine, 0);
        for (JsonObject responseDetail : responseDetails) {
            assertEquals(500, responseDetail.getJsonObject("error").getInteger("http_status_code"));
            assertEquals("HTTP_5XX", responseDetail.getJsonObject("error").getString("error_type"));
        }
    }

    @Test
    void testFailureFetchingRecipientsTimeout() throws Exception {
        initMocks(null, null);

        splitRoute.expectedMessageCount(0);
        kafkaConnectorToEngine.expectedMessageCount(1);

        buildCloudEventAndSendIt(null);

        kafkaConnectorToEngine.assertIsSatisfied();
        splitRoute.assertIsSatisfied();
        List<JsonObject> responseDetails = checkRecipientsAndHistory(false, bopRoute, kafkaConnectorToEngine, 0);
        for (JsonObject responseDetail : responseDetails) {
            assertFalse(responseDetail.getJsonObject("error").containsKey("http_status_code"));
            assertEquals("SOCKET_TIMEOUT", responseDetail.getJsonObject("error").getString("error_type"));
        }
    }

    @Test
    void testFailureBopInternalError() throws Exception {

        Set<User> users = TestUtils.createUsers("user-1", "user-2", "user-3", "user-4", "user-5", "user-6", "user-7");
        String strUsers = objectMapper.writeValueAsString(users);
        ExpectationResponseCallback recipientsResolverResponse = req -> response().withBody(strUsers).withStatusCode(200);
        ExpectationResponseCallback bopInternalError = req -> response().withStatusCode(500);
        initMocks(recipientsResolverResponse, bopInternalError);

        // The split route should result of 3 iterations:
        // 7 recipients / (4 as max recipients per email - 1 for the default recipient no-reply@redhat.com = 3) = 3 iterations
        // each iteration create its own route, since in case of bop server issue, each single iteration will send its own error message to engine.
        // when all iterations are completed, the main route continue, triggering a success message
        splitRoute.expectedMessageCount(1);
        kafkaConnectorToEngine.expectedMessageCount(4);
        buildCloudEventAndSendIt(null);

        splitRoute.assertIsSatisfied();
        kafkaConnectorToEngine.assertIsSatisfied();
        checkErrorResultsInSplitLoop(1, 3, "HTTP_5XX", 500);
    }

    @Test
    void testFailureBopRecipientsTimeout() throws Exception {

        Set<User> users = TestUtils.createUsers("user-1", "user-2", "user-3", "user-4", "user-5", "user-6", "user-7");
        String strUsers = objectMapper.writeValueAsString(users);
        ExpectationResponseCallback recipientsResolverResponse = req -> response().withBody(strUsers).withStatusCode(200);

        initMocks(recipientsResolverResponse, null);

        // The split route should result of 3 iterations:
        // 7 recipients / (4 as max recipients per email - 1 for the default recipient no-reply@redhat.com = 3) = 3 iterations
        // each iteration create its own route, since in case of bop server issue, each single iteration will send its own error message to engine.
        // when all iterations are completed, the main route continue, triggering a success message
        splitRoute.expectedMessageCount(1);
        kafkaConnectorToEngine.expectedMessageCount(4);
        buildCloudEventAndSendIt(null);

        splitRoute.assertIsSatisfied();
        kafkaConnectorToEngine.assertIsSatisfied();

        checkErrorResultsInSplitLoop(1, 3, "SOCKET_TIMEOUT", null);
    }

    private static void checkErrorResultsInSplitLoop(int expectedSuccess, int expectedFailure, String errorType, Integer httpStatusCode) {
        int successfullMessages = 0;
        int failureMessages = 0;
        for (Exchange kafkaMessage : kafkaConnectorToEngine.getReceivedExchanges()) {
            JsonObject payload = new JsonObject(kafkaMessage.getIn().getBody(String.class));
            JsonObject data = new JsonObject(payload.getString("data"));

            if (data.getBoolean("successful")) {
                successfullMessages++;
                assertTrue(data.getBoolean("successful"));
                assertTrue(data.containsKey("details"));
                assertEquals(7, data.getJsonObject("details").getInteger(TOTAL_RECIPIENTS_KEY));
            } else {
                failureMessages++;
                assertEquals(false, data.getBoolean("successful"));
                assertTrue(data.containsKey("details"));
                assertEquals(7, data.getJsonObject("details").getInteger(TOTAL_RECIPIENTS_KEY));
                assertFalse(data.getJsonObject("details").getString("outcome").isBlank());
                assertTrue(data.containsKey("error"));
                assertFalse(data.getJsonObject("error").getString("error_type").isBlank());

                if (null == httpStatusCode) {
                    assertFalse(data.getJsonObject("error").containsKey("http_status_code"));
                } else {
                    assertEquals(500, data.getJsonObject("error").getInteger("http_status_code"));
                }
                assertEquals(errorType, data.getJsonObject("error").getString("error_type"));
            }
        }

        assertEquals(expectedSuccess, successfullMessages);
        assertEquals(expectedFailure, failureMessages);
    }

    private static List<JsonObject> checkRecipientsAndHistory(boolean success, MockEndpoint bopRoute, MockEndpoint kafkaEndpoint, int expectedRecipientNumber) {
        // check recipients sent to bop
        List<Exchange> receivedExchanges  = bopRoute.getReceivedExchanges();
        Set<String> receivedEmails = new HashSet<>();
        for (Exchange  receivedExchange : receivedExchanges) {
            Set<String> receivedEmailsOnExchangeMsg = receivedExchange.getIn().getBody(Set.class);
            assertTrue(receivedEmailsOnExchangeMsg.size() <= 3);
            receivedEmails.addAll(receivedEmailsOnExchangeMsg);
        }

        List<JsonObject> dataToReturn = new ArrayList<>();
        // check metrics sent to engine
        for (Exchange kafkaMessage : kafkaEndpoint.getReceivedExchanges()) {
            JsonObject payload = new JsonObject(kafkaMessage.getIn().getBody(String.class));
            JsonObject data = new JsonObject(payload.getString("data"));

            assertEquals(success, data.getBoolean("successful"));
            assertTrue(data.containsKey("details"));
            assertEquals(expectedRecipientNumber, data.getJsonObject("details").getInteger(TOTAL_RECIPIENTS_KEY));
            assertFalse(data.getJsonObject("details").getString("outcome").isBlank());
            assertTrue(data.containsKey("error"));
            assertFalse(data.getJsonObject("error").getString("error_type").isBlank());
            dataToReturn.add(data);
        }
        return dataToReturn;
    }

    private static void checkRecipientsAndHistory(int usersAndRecipientsTotalNumber, int recipientsReceivedByBopTotalNumber, MockEndpoint bopRoute, MockEndpoint kafkaEndpoint, boolean emailsInternalOnlyEnabled, String filteredRecipient) {
        // check recipients sent to bop
        List<Exchange> receivedExchanges  = bopRoute.getReceivedExchanges();
        Set<String> receivedEmails = new HashSet<>();
        for (Exchange  receivedExchange : receivedExchanges) {
            Set<String> receivedEmailsOnExchangeMsg = receivedExchange.getIn().getBody(Set.class);
            assertTrue(receivedEmailsOnExchangeMsg.size() <= 3);
            receivedEmails.addAll(receivedEmailsOnExchangeMsg);
        }

        if (emailsInternalOnlyEnabled) {
            assertFalse(receivedEmails.contains(filteredRecipient));
            assertEquals(recipientsReceivedByBopTotalNumber - 1, receivedEmails.size());
        } else {
            assertTrue(receivedEmails.contains(filteredRecipient));
            assertEquals(recipientsReceivedByBopTotalNumber, receivedEmails.size());
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
        MockServerLifecycleManager.getClient()
            .withSecure(false)
            .when(postReq)
            .respond(expectationResponseCallback);
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
            "test email subject",
            "Not used",
            "123456",
            List.of(recipientSettings),
            new ArrayList<>(),
            new ArrayList<>(),
            false,
            null
        );
        final JsonObject payload = JsonObject.mapFrom(emailNotification);

        final String cloudEventId = UUID.randomUUID().toString();

        final JsonObject cloudEvent = new JsonObject();
        cloudEvent.put(CLOUD_EVENT_ID, cloudEventId);
        cloudEvent.put(CLOUD_EVENT_TYPE, "com.redhat.console.notification.toCamel." + emailConnectorConfig.getConnectorName());
        cloudEvent.put(CLOUD_EVENT_DATA, JsonObject.mapFrom(payload));
        return cloudEvent;
    }
}
