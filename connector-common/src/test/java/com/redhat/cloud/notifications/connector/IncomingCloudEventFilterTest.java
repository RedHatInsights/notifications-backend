package com.redhat.cloud.notifications.connector;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import static com.redhat.cloud.notifications.connector.IncomingCloudEventFilter.X_RH_NOTIFICATIONS_CONNECTOR_HEADER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@QuarkusTest
public class IncomingCloudEventFilterTest extends CamelQuarkusTestSupport {

    @Inject
    IncomingCloudEventFilter incomingCloudEventFilter;

    @InjectMock
    ConnectorConfig connectorConfig;

    @BeforeEach
    void beforeEach() {
        when(connectorConfig.getConnectorName()).thenReturn("foo");
    }

    @Test
    void shouldAcceptValidConnectorHeader() {
        assertTrue(incomingCloudEventFilter.matches(buildExchange("foo")));
    }

    @Test
    void shouldRejectInvalidConnectorHeader() {
        assertFalse(incomingCloudEventFilter.matches(buildExchange("bar")));
    }

    private Exchange buildExchange(String connectorHeader) {
        Exchange exchange = createExchangeWithBody(null);
        exchange.getIn().setHeader(X_RH_NOTIFICATIONS_CONNECTOR_HEADER, connectorHeader);
        return exchange;
    }
}
