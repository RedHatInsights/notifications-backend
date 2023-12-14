package com.redhat.cloud.notifications.connector.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.MockServerLifecycleManager;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.model.bop.SendEmailsRequest;
import com.redhat.cloud.notifications.connector.email.model.settings.User;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.CONNECTOR_TO_ENGINE;
import static com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder.ENGINE_TO_CONNECTOR;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.START_TIME;
import static com.redhat.cloud.notifications.connector.email.CloudEventHistoryBuilder.TOTAL_FAILURE_RECIPIENTS_KEY;
import static com.redhat.cloud.notifications.connector.email.CloudEventHistoryBuilder.TOTAL_RECIPIENTS_KEY;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.EMAIL_RECIPIENTS;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.RECIPIENT_SETTINGS;
import static com.redhat.cloud.notifications.connector.email.constants.Routes.SEND_EMAIL_BOP;
import static com.redhat.cloud.notifications.connector.email.constants.Routes.SPLIT_AND_SEND;
import static org.apache.camel.builder.AdviceWith.adviceWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockserver.model.HttpResponse.response;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class RecipientsListTest extends CamelQuarkusTestSupport {

    @Inject
    EmailConnectorConfig emailConnectorConfig;

    @Inject
    ProducerTemplate producerTemplate;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    Exchange test(ExpectationResponseCallback verifyEmptyRequest, ExpectationResponseCallback bopResponse) throws Exception {
        if (bopResponse == null) {
            bopResponse = req -> response().withStatusCode(200);
        }

        MockServerLifecycleManager.getClient().reset();
        getMockHttpRequest("/internal/recipients-resolver", "PUT", verifyEmptyRequest);
        getMockBOPRequest("/v1/sendEmails", "POST", bopResponse);

        Exchange exchange = createExchangeWithBody(new HashSet<>());
        exchange.setProperty(EMAIL_RECIPIENTS, new HashSet<>());
        exchange.setProperty(RECIPIENT_SETTINGS, new ArrayList<>());
        exchange.setProperty(START_TIME, System.currentTimeMillis());

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

        return exchange;

    }

    @Test
    void testEmpty() throws Exception {

        ExpectationResponseCallback verifyEmptyRequest = req -> response().withBody("[]").withStatusCode(200);

        Exchange exchange = test(verifyEmptyRequest, null);
        MockEndpoint successEndpoint = getMockEndpoint("mock:direct:" + CONNECTOR_TO_ENGINE);
        MockEndpoint splitRoute = getMockEndpoint("mock:direct:" + SPLIT_AND_SEND);

        splitRoute.expectedMessageCount(0);
        successEndpoint.expectedMessageCount(1);

        producerTemplate.send("seda:" + ENGINE_TO_CONNECTOR, exchange);
        splitRoute.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testNotEmpty(boolean emailsInternalOnlyEnabled) throws Exception {
        try {
            emailConnectorConfig.setEmailsInternalOnlyEnabled(emailsInternalOnlyEnabled);
            Set<User> users = TestUtils.createUsers("user-1", "user-2", "user-3", "user-4", "user-5", "user-6", "user-7");
            String strUsers = objectMapper.writeValueAsString(users);
            ExpectationResponseCallback verifyEmptyRequest = req -> response().withBody(strUsers).withStatusCode(200);

            Exchange exchange = test(verifyEmptyRequest, null);
            Set<String> emailRecipients = new HashSet<>();
            emailRecipients.add("redhat_user@redhat.com");
            emailRecipients.add("external_user@noway.com");
            exchange.setProperty(EMAIL_RECIPIENTS, emailRecipients);
            int usersAndRecipientsTotalNumber = emailRecipients.size() + users.size();

            MockEndpoint successEndpoint = getMockEndpoint("mock:direct:" + CONNECTOR_TO_ENGINE);
            MockEndpoint splitRoute = getMockEndpoint("mock:direct:" + SPLIT_AND_SEND);
            MockEndpoint bopRoute = getMockEndpoint("mock:direct:" + SEND_EMAIL_BOP);
            MockEndpoint kafkaEndpoint = getMockEndpoint("mock:kafka:" + emailConnectorConfig.getOutgoingKafkaTopic());

            splitRoute.expectedMessageCount(1);
            successEndpoint.expectedMessageCount(1);
            bopRoute.expectedMessageCount(3);
            kafkaEndpoint.expectedMessageCount(1);

            producerTemplate.send("seda:" + ENGINE_TO_CONNECTOR, exchange);
            splitRoute.assertIsSatisfied();
            successEndpoint.assertIsSatisfied();
            bopRoute.assertIsSatisfied();
            kafkaEndpoint.assertIsSatisfied(2000);

            checkRecipientsAndHistory(usersAndRecipientsTotalNumber, bopRoute, kafkaEndpoint, 0, emailsInternalOnlyEnabled);
        } finally {
            emailConnectorConfig.setEmailsInternalOnlyEnabled(false);
        }
    }

    private static void checkRecipientsAndHistory(int usersAndRecipientsTotalNumber, MockEndpoint bopRoute, MockEndpoint kafkaEndpoint, int totalRecipientsFailure, boolean emailsInternalOnlyEnabled) {
        // check recipients sent to bop
        List<Exchange> receivedExchanges  = bopRoute.getReceivedExchanges();
        Set<String> receivedEmails = new HashSet<>();
        for (Exchange  receivedExchange : receivedExchanges) {
            Set<String> receivedEmailsOnExchangeMsg = receivedExchange.getIn().getBody(Set.class);
            assertTrue(receivedEmailsOnExchangeMsg.size() <= 3);
            receivedEmails.addAll(receivedEmailsOnExchangeMsg);
        }
        if (emailsInternalOnlyEnabled) {
            assertFalse(receivedEmails.contains("external_user@noway.com"));
            assertEquals(usersAndRecipientsTotalNumber - 1, receivedEmails.size());
        } else {
            assertTrue(receivedEmails.contains("external_user@noway.com"));
            assertEquals(usersAndRecipientsTotalNumber, receivedEmails.size());
        }

        // check metrics sent to engine
        Exchange kafkaMessage  = kafkaEndpoint.getReceivedExchanges().stream().findFirst().get();
        JsonObject payload = new JsonObject(kafkaMessage.getIn().getBody(String.class));
        JsonObject data = new JsonObject(payload.getString("data"));
        if (totalRecipientsFailure == 0) {
            assertTrue(data.getBoolean("successful"));
        } else {
            assertFalse(data.getBoolean("successful"));
        }
        if (emailsInternalOnlyEnabled) {
            assertEquals(usersAndRecipientsTotalNumber - 1, data.getJsonObject("details").getInteger(TOTAL_RECIPIENTS_KEY));
        } else {
            assertEquals(usersAndRecipientsTotalNumber, data.getJsonObject("details").getInteger(TOTAL_RECIPIENTS_KEY));
        }
        assertEquals(totalRecipientsFailure, data.getJsonObject("details").getInteger(TOTAL_FAILURE_RECIPIENTS_KEY));
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
}
