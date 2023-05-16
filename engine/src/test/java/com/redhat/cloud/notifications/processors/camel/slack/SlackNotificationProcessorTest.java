package com.redhat.cloud.notifications.processors.camel.slack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.processors.camel.CamelNotificationProcessorTest;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class SlackNotificationProcessorTest extends CamelNotificationProcessorTest {

    @Inject
    SlackNotificationProcessor slackNotificationProcessor;

    @Inject
    ObjectMapper objectMapper;

    @Override
    protected Processor getProcessor() {
        return slackNotificationProcessor;
    }

    @Test
    protected void testProcess() throws Exception {
        SlackNotification notification = SlackRoutesTest.buildCamelSlackNotification("https://redhat.com");

        String notificationAsString = objectMapper.writeValueAsString(notification);
        Exchange exchange = createExchangeWithBody(notificationAsString);

        getProcessor().process(exchange);

        verifyCommonFields(notification, exchange);
        assertEquals(notification.channel, exchange.getProperty("channel", String.class));
    }

}
