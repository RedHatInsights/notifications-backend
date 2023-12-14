package com.redhat.cloud.notifications.connector.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.MockServerLifecycleManager;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.model.settings.User;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
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

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder.ENGINE_TO_CONNECTOR;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.START_TIME;
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

    Exchange test(ExpectationResponseCallback verifyEmptyRequest) throws Exception {

        getMockHttpRequest("/internal/recipients-resolver", "PUT", verifyEmptyRequest);

        Exchange exchange = createExchangeWithBody(new HashSet<>());
        exchange.setProperty(EMAIL_RECIPIENTS, new HashSet<>());
        exchange.setProperty(RECIPIENT_SETTINGS, new ArrayList<>());
        exchange.setProperty(START_TIME, System.currentTimeMillis());

        adviceWith(SPLIT_AND_SEND, context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpointsAndSkip(
                    "direct:" + SEND_EMAIL_BOP
                );
            }
        });

        adviceWith(emailConnectorConfig.getConnectorName(), context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpoints(
                    "direct:" + SUCCESS,
                            "direct:" + SPLIT_AND_SEND
                );
            }
        });

        return exchange;

    }

    @Test
    void testEmpty() throws Exception {

        ExpectationResponseCallback verifyEmptyRequest = req -> response().withBody("[]").withStatusCode(200);

        Exchange exchange = test(verifyEmptyRequest);
        MockEndpoint successEndpoint = getMockEndpoint("mock:direct:" + SUCCESS);
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

            Exchange exchange = test(verifyEmptyRequest);
            Set<String> emailRecipients = new HashSet<>();
            emailRecipients.add("redhat_user@redhat.com");
            emailRecipients.add("external_user@noway.com");
            exchange.setProperty(EMAIL_RECIPIENTS, emailRecipients);
            int usersAndRecipientsTotalNumber = emailRecipients.size() + users.size();

            MockEndpoint successEndpoint = getMockEndpoint("mock:direct:" + SUCCESS);
            MockEndpoint splitRoute = getMockEndpoint("mock:direct:" + SPLIT_AND_SEND);
            MockEndpoint bopRoute = getMockEndpoint("mock:direct:" + SEND_EMAIL_BOP);

            splitRoute.expectedMessageCount(1);
            successEndpoint.expectedMessageCount(1);
            bopRoute.expectedMessageCount(3);

            producerTemplate.send("seda:" + ENGINE_TO_CONNECTOR, exchange);
            splitRoute.assertIsSatisfied();
            successEndpoint.assertIsSatisfied();
            bopRoute.assertIsSatisfied();
            List<Exchange> receivedExchanges = bopRoute.getReceivedExchanges();
            Set<String> receivedEmails = new HashSet<>();
            for (Exchange receivedExchange : receivedExchanges) {
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
        } finally {
            emailConnectorConfig.setEmailsInternalOnlyEnabled(false);
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
}
