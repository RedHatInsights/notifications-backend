package com.redhat.cloud.notifications.processors.slack;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class SlackNotificationProcessorTest extends CamelQuarkusTestSupport {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SlackNotificationProcessor slackNotificationProcessor;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testProcess() throws Exception {
        SlackNotification notification = new SlackNotification();
        notification.channel = "#notifications";
        notification.webhookUrl = "https://redhat.com";
        notification.message = "Hello, World!";

        String notificationAsString = objectMapper.writeValueAsString(notification);
        Exchange exchange = createExchangeWithBody(notificationAsString);

        slackNotificationProcessor.process(exchange);

        assertEquals(notification.channel, exchange.getIn().getHeader("channel", String.class));
        assertEquals(notification.webhookUrl, exchange.getIn().getHeader("webhookUrl", String.class));
        assertEquals(notification.message, exchange.getIn().getBody(String.class));
    }
}
