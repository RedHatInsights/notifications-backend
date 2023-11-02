package com.redhat.cloud.notifications.connector.email;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.FILTERED_USERS;
import static com.redhat.cloud.notifications.connector.email.constants.Routes.SEND_EMAIL_BOP;
import static com.redhat.cloud.notifications.connector.email.constants.Routes.SEND_EMAIL_BOP_CHOICE;
import static com.redhat.cloud.notifications.connector.email.constants.Routes.SEND_EMAIL_BOP_SINGLE_PER_USER;
import static org.apache.camel.builder.AdviceWith.adviceWith;

@QuarkusTest
public class EmptyRecipientsTest extends CamelQuarkusTestSupport {

    @Inject
    ProducerTemplate producerTemplate;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void test() throws Exception {

        Exchange exchange = createExchangeWithBody("");
        exchange.setProperty(FILTERED_USERS, new HashSet<>());

        adviceWith(SEND_EMAIL_BOP_CHOICE, context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpointsAndSkip(
                        "direct:" + SEND_EMAIL_BOP_SINGLE_PER_USER,
                        "direct:" + SEND_EMAIL_BOP,
                        "direct:" + SUCCESS
                );
            }
        });

        MockEndpoint sendEmailBopSinglePerUserEndpoint = getMockEndpoint("mock:direct:" + SEND_EMAIL_BOP_SINGLE_PER_USER);
        sendEmailBopSinglePerUserEndpoint.expectedMessageCount(0);

        MockEndpoint sendEmailBopEndpoint = getMockEndpoint("mock:direct:" + SEND_EMAIL_BOP);
        sendEmailBopEndpoint.expectedMessageCount(0);

        MockEndpoint successEndpoint = getMockEndpoint("mock:direct:" + SUCCESS);
        successEndpoint.expectedMessageCount(1);

        producerTemplate.send("direct:" + SEND_EMAIL_BOP_CHOICE, exchange);

        sendEmailBopSinglePerUserEndpoint.assertIsSatisfied();
        sendEmailBopEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();
    }
}
