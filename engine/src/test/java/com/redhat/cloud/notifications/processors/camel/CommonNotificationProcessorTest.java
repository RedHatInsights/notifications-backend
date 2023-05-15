package com.redhat.cloud.notifications.processors.camel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.processors.google.chat.GoogleChatNotificationProcessor;
import com.redhat.cloud.notifications.processors.slack.SlackNotification;
import com.redhat.cloud.notifications.processors.slack.SlackNotificationProcessor;
import com.redhat.cloud.notifications.processors.slack.SlackRoutesTest;
import com.redhat.cloud.notifications.processors.teams.TeamsNotificationProcessor;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class CommonNotificationProcessorTest extends CamelQuarkusTestSupport {

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SlackNotificationProcessor slackNotificationProcessor;

    @Inject
    GoogleChatNotificationProcessor googleChatNotificationProcessor;

    @Inject
    TeamsNotificationProcessor teamsNotificationProcessor;


    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testProcessSlack() throws Exception {
        SlackNotification notification = SlackRoutesTest.buildCamelSlackNotification("https://redhat.com");

        String notificationAsString = objectMapper.writeValueAsString(notification);
        Exchange exchange = createExchangeWithBody(notificationAsString);

        slackNotificationProcessor.process(exchange);

        verifyCommonFields(notification, exchange);
        assertEquals(notification.channel, exchange.getProperty("channel", String.class));
    }

    @Test
    void testProcessCommonNotifications() throws Exception {
        CamelNotification notification = CamelRoutesTest.buildCamelNotification("https://redhat.com");

        String notificationAsString = objectMapper.writeValueAsString(notification);
        Exchange exchange = createExchangeWithBody(notificationAsString);

        googleChatNotificationProcessor.process(exchange);
        verifyCommonFields(notification, exchange);

        exchange = createExchangeWithBody(notificationAsString);
        teamsNotificationProcessor.process(exchange);
        verifyCommonFields(notification, exchange);
    }

    private static void verifyCommonFields(CamelNotification notification, Exchange exchange) {
        assertEquals(notification.orgId, exchange.getProperty("orgId", String.class));
        assertEquals(notification.historyId, exchange.getProperty("historyId", UUID.class));
        assertEquals(notification.webhookUrl, exchange.getProperty("webhookUrl", String.class));
        assertEquals(notification.message, exchange.getIn().getBody(String.class));
    }

}
