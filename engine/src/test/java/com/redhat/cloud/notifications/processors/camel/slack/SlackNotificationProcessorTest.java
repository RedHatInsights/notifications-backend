package com.redhat.cloud.notifications.processors.camel.slack;

import com.redhat.cloud.notifications.processors.camel.CamelNotificationProcessorTest;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;

import java.util.UUID;

import static com.redhat.cloud.notifications.processors.camel.CamelNotificationProcessor.CLOUD_EVENT_ID_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class SlackNotificationProcessorTest extends CamelNotificationProcessorTest {

    @Inject
    SlackNotificationProcessor slackNotificationProcessor;

    @Override
    protected Processor getProcessor() {
        return slackNotificationProcessor;
    }

    @Test
    protected void testProcess() throws Exception {
        String cloudEventId = UUID.randomUUID().toString();
        SlackNotification notification = SlackRoutesTest.buildCamelSlackNotification("https://redhat.com");

        String notificationAsString = objectMapper.writeValueAsString(notification);
        Exchange exchange = createExchangeWithBody(notificationAsString);
        exchange.getIn().setHeader(CLOUD_EVENT_ID_HEADER, cloudEventId);

        getProcessor().process(exchange);

        verifyCommonFields(cloudEventId, notification, exchange);
        assertEquals(notification.channel, exchange.getProperty("channel", String.class));
    }

}
