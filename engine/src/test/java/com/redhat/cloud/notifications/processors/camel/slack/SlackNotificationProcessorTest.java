package com.redhat.cloud.notifications.processors.camel.slack;

import com.redhat.cloud.notifications.processors.camel.CamelNotificationProcessorTest;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;

import java.util.UUID;

import static com.redhat.cloud.notifications.processors.camel.CamelNotificationProcessor.CLOUD_EVENT_ID;
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

        JsonObject notificationAsString = JsonObject.mapFrom(notification);
        notificationAsString.put(CLOUD_EVENT_ID, cloudEventId);
        Exchange exchange = createExchangeWithBody(notificationAsString.encode());

        getProcessor().process(exchange);

        verifyCommonFields(cloudEventId, notification, exchange);
        assertEquals(notification.channel, exchange.getProperty("channel", String.class));
    }

}
