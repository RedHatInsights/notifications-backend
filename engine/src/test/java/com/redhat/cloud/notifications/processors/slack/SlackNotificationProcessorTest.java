package com.redhat.cloud.notifications.processors.slack;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import java.util.UUID;

import static com.redhat.cloud.notifications.processors.slack.SlackRouteBuilderTest.buildNotification;
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
        SlackNotification notification = buildNotification("https://redhat.com");

        String notificationAsString = objectMapper.writeValueAsString(notification);
        Exchange exchange = createExchangeWithBody(notificationAsString);

        slackNotificationProcessor.process(exchange);

        assertEquals(notification.orgId, exchange.getProperty("orgId", String.class));
        assertEquals(notification.historyId, exchange.getProperty("historyId", UUID.class));
        assertEquals(notification.webhookUrl, exchange.getProperty("webhookUrl", String.class));
        assertEquals(notification.channel, exchange.getProperty("channel", String.class));
        assertEquals(notification.message, exchange.getIn().getBody(String.class));
    }
}
