package com.redhat.cloud.notifications.processors.camel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;

import java.util.UUID;

import static com.redhat.cloud.notifications.processors.camel.CamelNotificationProcessor.CLOUD_EVENT_ID_HEADER;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.ID;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.processors.camel.ExchangeProperty.WEBHOOK_URL;
import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class CamelNotificationProcessorTest extends CamelQuarkusTestSupport {

    @Inject
    protected ObjectMapper objectMapper;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    protected abstract Processor getProcessor();

    @Test
    protected void testProcess() throws Exception {
        String cloudEventId = UUID.randomUUID().toString();
        CamelNotification notification = CamelRoutesTest.buildCamelNotification("https://redhat.com");

        String notificationAsString = objectMapper.writeValueAsString(notification);
        Exchange exchange = createExchangeWithBody(notificationAsString);
        exchange.getIn().setHeader(CLOUD_EVENT_ID_HEADER, cloudEventId);

        getProcessor().process(exchange);
        verifyCommonFields(cloudEventId, notification, exchange);
    }

    protected static void verifyCommonFields(String cloudEventId, CamelNotification notification, Exchange exchange) {
        assertEquals(cloudEventId, exchange.getProperty(ID, String.class));
        assertEquals(notification.orgId, exchange.getProperty(ORG_ID, String.class));
        assertEquals(notification.webhookUrl, exchange.getProperty(WEBHOOK_URL, String.class));
        assertEquals(notification.message, exchange.getIn().getBody(String.class));
    }

}
