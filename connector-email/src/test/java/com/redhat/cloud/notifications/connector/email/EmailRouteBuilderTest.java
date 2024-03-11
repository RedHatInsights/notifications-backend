package com.redhat.cloud.notifications.connector.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.MockServerLifecycleManager;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.model.EmailNotification;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
import com.redhat.cloud.notifications.connector.email.model.settings.User;
import io.quarkus.logging.Log;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.redhat.cloud.notifications.connector.ConnectorRoutesTest.KAFKA_SOURCE_MOCK;
import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.CONNECTOR_TO_ENGINE;
import static com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder.ENGINE_TO_CONNECTOR;
import static com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder.KAFKA_REINJECTION;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventFilter.X_RH_NOTIFICATIONS_CONNECTOR_HEADER;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventProcessor.CLOUD_EVENT_DATA;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventProcessor.CLOUD_EVENT_ID;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventProcessor.CLOUD_EVENT_TYPE;
import static com.redhat.cloud.notifications.connector.email.CloudEventHistoryBuilder.TOTAL_FAILURE_RECIPIENTS_KEY;
import static com.redhat.cloud.notifications.connector.email.CloudEventHistoryBuilder.TOTAL_RECIPIENTS_KEY;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.RECIPIENTS_WITH_EMAIL_ERROR;
import static com.redhat.cloud.notifications.connector.email.constants.Routes.SEND_EMAIL_BOP;
import static com.redhat.cloud.notifications.connector.email.constants.Routes.SPLIT_AND_SEND;
import static org.apache.camel.builder.AdviceWith.adviceWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockserver.model.HttpResponse.response;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EmailRouteBuilderTest extends CamelQuarkusTestSupport {
    @Inject
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
        adviceWith(SPLIT_AND_SEND, context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpoints(
                    "direct:" + SEND_EMAIL_BOP
                );
            }
        });

        adviceWith(emailConnectorConfig.getConnectorName(), context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpoints(
                    "direct:" + CONNECTOR_TO_ENGINE,
                    "direct:" + SPLIT_AND_SEND
                );
            }
        });

        adviceWith(CONNECTOR_TO_ENGINE, context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpoints("kafka:" + emailConnectorConfig.getOutgoingKafkaTopic());
            }
        });

        adviceWith(SPLIT_AND_SEND, context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpointsAndSkip(
                    "direct:" + SEND_EMAIL_BOP
                );
            }
        });

        adviceWith(ENGINE_TO_CONNECTOR, context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                replaceFromWith(KAFKA_SOURCE_MOCK);
            }
        });

        adviceWith(ENGINE_TO_CONNECTOR, context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpoints(KAFKA_SOURCE_MOCK);
            }
        });

        adviceWith(KAFKA_REINJECTION, context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                // because we will change kafka endpoint to an internal endpoint, exchange properties will be sent as well to this endpoint
                // we have to remove them to be sure that properties will be loaded from kafka header and message only in case of re-injection
                weaveByToUri("kafka:*").before().removeProperties("*", "camel*");
                weaveByToUri("kafka:*").replace().to(KAFKA_SOURCE_MOCK);
            }
        });

        splitRoute = getMockEndpoint("mock:direct:" + SPLIT_AND_SEND);
        bopRoute = getMockEndpoint("mock:direct:" + SEND_EMAIL_BOP);
        kafkaConnectorToEngine = getMockEndpoint("mock:kafka:" + emailConnectorConfig.getOutgoingKafkaTopic());
        kafkaEngineToConnector = getMockEndpoint("mock:" + KAFKA_SOURCE_MOCK);
    }

    void initMocks(ExpectationResponseCallback verifyEmptyRequest, ExpectationResponseCallback bopResponse) throws Exception {
        if (bopResponse == null) {
            bopResponse = req -> response().withStatusCode(200);
        }

        MockServerLifecycleManager.getClient().reset();
        getMockHttpRequest("/internal/recipients-resolver", "PUT", verifyEmptyRequest);
        getMockBOPRequest("/v1/sendEmails", "POST", bopResponse);
        if (!camelRoutesInitialised) {
            initCamelRoutes();
            camelRoutesInitialised = true;
        }

        splitRoute.reset();
        bopRoute.reset();
        kafkaConnectorToEngine.reset();
        kafkaEngineToConnector.reset();
    }

    @Test
    void testEmptyRecipients() throws Exception {

        ExpectationResponseCallback verifyEmptyRequest = req -> response().withBody("[]").withStatusCode(200);

        initMocks(verifyEmptyRequest, null);

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
            ExpectationResponseCallback verifyEmptyRequest = req -> response().withBody(strUsers).withStatusCode(200);

            initMocks(verifyEmptyRequest, null);

            Set<String> additionalEmails = Set.of("redhat_user@redhat.com", "external_user@noway.com");
            int usersAndRecipientsTotalNumber = users.size() + additionalEmails.size();

            splitRoute.expectedMessageCount(1);
            bopRoute.expectedMessageCount(3);
            kafkaConnectorToEngine.expectedMessageCount(1);

            buildCloudEventAndSendIt(additionalEmails);

            splitRoute.assertIsSatisfied();
            bopRoute.assertIsSatisfied();
            kafkaConnectorToEngine.assertIsSatisfied(2000);

            checkRecipientsAndHistory(usersAndRecipientsTotalNumber, usersAndRecipientsTotalNumber, bopRoute, kafkaConnectorToEngine, 0, emailsInternalOnlyEnabled, "external_user@noway.com");
        } finally {
            emailConnectorConfig.setEmailsInternalOnlyEnabled(false);
        }
    }

    @Test
    void testReInjectionWithBopError() throws Exception {
        Set<User> users = TestUtils.createUsers("user-1", "user-2", "user-3", "user-4", "user-5", "user-6", "user-7");
        String strUsers = objectMapper.writeValueAsString(users);
        ExpectationResponseCallback verifyEmptyRequest = req -> response().withBody(strUsers).withStatusCode(200);

        ExpectationResponseCallback simulateBopErrorOnUser3 = req -> {
            JsonObject jsobj = new JsonObject(req.getBodyAsString());
            Map ob = (Map) jsobj.getJsonArray("emails").getList().get(0);

            List<String> bccList = (List<String>) ob.get("bccList");
            Log.info("message received " + bccList);

            if (bccList.contains("user-3-email")) {
                return response().withStatusCode(500);
            } else {
                return response().withStatusCode(200);
            }
        };

        initMocks(verifyEmptyRequest, simulateBopErrorOnUser3);

        splitRoute.expectedMessageCount(4); // initial message + 3 re-injections
        bopRoute.expectedMessageCount(6); // initial attempt (3 emails to send) + only one email re-injection (*3)
        kafkaConnectorToEngine.expectedMessageCount(1);
        kafkaConnectorToEngine.setResultWaitTime(25000);
        kafkaEngineToConnector.expectedMessageCount(4); // initial message + 3 re-injections

        buildCloudEventAndSendIt(null);

        kafkaConnectorToEngine.assertIsSatisfied();
        splitRoute.assertIsSatisfied();
        bopRoute.assertIsSatisfied();
        kafkaEngineToConnector.assertIsSatisfied();

        checkReInjection(bopRoute, kafkaEngineToConnector);
        checkRecipientsAndHistory(users.size(), users.size(), bopRoute, kafkaConnectorToEngine, 1, false, "user-3-email");
    }

    @Test
    void testReInjectionWithBopErrorsThenSucceed() throws Exception {
        Set<User> users = TestUtils.createUsers("user-1", "user-2", "user-3", "user-4", "user-5", "user-6", "user-7");
        String strUsers = objectMapper.writeValueAsString(users);
        ExpectationResponseCallback verifyEmptyRequest = req -> response().withBody(strUsers).withStatusCode(200);

        final AtomicInteger bopRequestedTimeOnErrorUser = new AtomicInteger(0);
        ExpectationResponseCallback simulateBopErrorOnUser3 = req -> {
            JsonObject jsobj = new JsonObject(req.getBodyAsString());
            Map ob = (Map) jsobj.getJsonArray("emails").getList().get(0);

            List<String> bccList = (List<String>) ob.get("bccList");
            Log.info("message received " + bccList);

            if (bopRequestedTimeOnErrorUser.get() <= 3 && bccList.contains("user-3-email")) {
                bopRequestedTimeOnErrorUser.incrementAndGet();
                return response().withStatusCode(500);
            } else {
                return response().withStatusCode(200);
            }
        };

        initMocks(verifyEmptyRequest, simulateBopErrorOnUser3);

        splitRoute.expectedMessageCount(2); // initial message + 1 re-injections
        bopRoute.expectedMessageCount(4); // initial attempt (3 emails to send) + only one email re-injection (*3)
        kafkaConnectorToEngine.expectedMessageCount(1);
        kafkaConnectorToEngine.setResultWaitTime(25000);
        kafkaEngineToConnector.expectedMessageCount(2); // initial message + 1 re-injections

        buildCloudEventAndSendIt(null);

        kafkaConnectorToEngine.assertIsSatisfied();
        splitRoute.assertIsSatisfied();
        bopRoute.assertIsSatisfied();
        kafkaEngineToConnector.assertIsSatisfied();

        checkReInjection(bopRoute, kafkaEngineToConnector);
        checkRecipientsAndHistory(users.size(), users.size(), bopRoute, kafkaConnectorToEngine, 0, false, "user-3-email");
    }

    private static void checkReInjection(MockEndpoint bopRoute, MockEndpoint kafkaReInjection) {
        // check recipients sent to bop
        List<Exchange> receivedExchanges  = bopRoute.getReceivedExchanges();
        Set<String> receivedEmails = new HashSet<>();
        for (Exchange  receivedExchange : receivedExchanges) {
            Set<String> receivedEmailsOnExchangeMsg = receivedExchange.getIn().getBody(Set.class);
            assertTrue(receivedEmailsOnExchangeMsg.size() <= 3);
            receivedEmails.addAll(receivedEmailsOnExchangeMsg);
        }

        // check injected kafka messages
        if (kafkaReInjection.getReceivedExchanges().size() > 0) {
            assertNull(getRecipientsWithError(kafkaReInjection.getReceivedExchanges(), 0)); // initial message form engine
        }
        if (kafkaReInjection.getReceivedExchanges().size() > 1) {
            assertTrue(getRecipientsWithError(kafkaReInjection.getReceivedExchanges(), 1).contains("user-3-email")); // first re-injection
        }
        if (kafkaReInjection.getReceivedExchanges().size() > 2) {
            assertTrue(getRecipientsWithError(kafkaReInjection.getReceivedExchanges(), 2).contains("user-3-email")); // second re-injection
        }
        if (kafkaReInjection.getReceivedExchanges().size() > 3) {
            assertTrue(getRecipientsWithError(kafkaReInjection.getReceivedExchanges(), 3).contains("user-3-email")); // third re-injection
        }
    }

    private static Set<String> getRecipientsWithError(List<Exchange> messages, int index) {
        Optional<String> recipientsWithError = Optional.ofNullable(messages.get(index).getMessage().getHeader(RECIPIENTS_WITH_EMAIL_ERROR, String.class));
        if (recipientsWithError.isPresent()) {
            return new HashSet<>(Arrays.asList(recipientsWithError.get().split(",")));
        }
        return null;
    }

    private static void checkRecipientsAndHistory(int usersAndRecipientsTotalNumber, int recipientsReceivedByBopTotalNumber, MockEndpoint bopRoute, MockEndpoint kafkaEndpoint, int totalRecipientsFailure, boolean emailsInternalOnlyEnabled, String filteredRecipient) {
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
        if (totalRecipientsFailure == 0) {
            assertTrue(data.getBoolean("successful"));
            assertNull(data.getJsonObject("details").getInteger(TOTAL_FAILURE_RECIPIENTS_KEY));
        } else {
            assertFalse(data.getBoolean("successful"));
            assertEquals(totalRecipientsFailure, data.getJsonObject("details").getInteger(TOTAL_FAILURE_RECIPIENTS_KEY));
        }
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

    private HttpRequest getMockBOPRequest(String path, String method, ExpectationResponseCallback expectationResponseCallback) {
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
            false
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
