package com.redhat.cloud.notifications.processors.camel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class CamelNotificationProcessorTest extends CamelQuarkusTestSupport {
    @Inject
    ObjectMapper objectMapper;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    protected abstract Processor getProcessor();

    @Test
    protected void testProcess() throws Exception {
        CamelNotification notification = CamelRoutesTest.buildCamelNotification("https://redhat.com");

        String notificationAsString = objectMapper.writeValueAsString(notification);
        Exchange exchange = createExchangeWithBody(notificationAsString);

        getProcessor().process(exchange);
        verifyCommonFields(notification, exchange);
    }

    protected static void verifyCommonFields(CamelNotification notification, Exchange exchange) {
        assertEquals(notification.orgId, exchange.getProperty("orgId", String.class));
        assertEquals(notification.historyId, exchange.getProperty("historyId", UUID.class));
        assertEquals(notification.webhookUrl, exchange.getProperty("webhookUrl", String.class));
        assertEquals(notification.message, exchange.getIn().getBody(String.class));
    }

}
