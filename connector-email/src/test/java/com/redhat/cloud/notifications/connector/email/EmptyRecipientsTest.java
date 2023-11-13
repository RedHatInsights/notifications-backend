package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
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
import static org.apache.camel.builder.AdviceWith.adviceWith;

@QuarkusTest
public class EmptyRecipientsTest extends CamelQuarkusTestSupport {

    @Inject
    EmailConnectorConfig emailConnectorConfig;

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

        adviceWith(SEND_EMAIL_BOP, context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpointsAndSkip(
                        emailConnectorConfig.getBopURL() + "*",
                        "direct:" + SUCCESS
                );
            }
        });

        MockEndpoint bopEndpoint = getMockEndpoint("mock:" + emailConnectorConfig.getBopURL().replace("https://", "https:"), false);
        bopEndpoint.expectedMessageCount(0);

        MockEndpoint successEndpoint = getMockEndpoint("mock:direct:" + SUCCESS, false);
        successEndpoint.expectedMessageCount(1);

        producerTemplate.send("direct:" + SEND_EMAIL_BOP, exchange);

        bopEndpoint.assertIsSatisfied();
        successEndpoint.assertIsSatisfied();
    }
}
