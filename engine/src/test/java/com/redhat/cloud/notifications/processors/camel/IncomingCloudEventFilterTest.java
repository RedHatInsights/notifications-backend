package com.redhat.cloud.notifications.processors.camel;

import io.quarkus.test.junit.QuarkusTest;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.processors.ConnectorSender.X_RH_NOTIFICATIONS_CONNECTOR_HEADER;
import static com.redhat.cloud.notifications.processors.camel.IncomingCloudEventFilter.EXCEPTION_MSG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class IncomingCloudEventFilterTest extends CamelQuarkusTestSupport {

    @Test
    void shouldThrowWhenAllowedConnectorHeaderIsNull() {
        Exception e = assertThrows(NullPointerException.class, () -> {
            new IncomingCloudEventFilter(null);
        });
        assertEquals(EXCEPTION_MSG, e.getMessage());
    }

    @Test
    void shouldAcceptValidConnectorHeader() {
        assertTrue(new IncomingCloudEventFilter("alpha").matches(buildExchange()));
    }

    @Test
    void shouldRejectInvalidConnectorHeader() {
        assertFalse(new IncomingCloudEventFilter("bravo").matches(buildExchange()));
    }

    private Exchange buildExchange() {
        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(X_RH_NOTIFICATIONS_CONNECTOR_HEADER, "alpha");
        return exchange;
    }
}
