package com.redhat.cloud.notifications.connector;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.redhat.cloud.notifications.connector.IncomingCloudEventFilter.X_RH_NOTIFICATIONS_CONNECTOR_HEADER;
import static org.apache.camel.test.junit5.TestSupport.createExchangeWithBody;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@QuarkusTest
public class IncomingCloudEventFilterTest extends CamelQuarkusTestSupport {

    @Inject
    IncomingCloudEventFilter incomingCloudEventFilter;

    @InjectMock
    ConnectorConfig connectorConfig;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void shouldAcceptValidConnectorHeaderSingleValue() {
        mockSupportedConnectorHeaders("foo");
        assertTrue(incomingCloudEventFilter.matches(buildExchange("foo")));
    }

    @Test
    void shouldAcceptValidConnectorHeaderMultipleValues() {
        mockSupportedConnectorHeaders("bar", "foo");
        assertTrue(incomingCloudEventFilter.matches(buildExchange("foo")));
    }

    @Test
    void shouldRejectInvalidConnectorHeader() {
        mockSupportedConnectorHeaders("foo");
        assertFalse(incomingCloudEventFilter.matches(buildExchange("bar")));
    }

    private void mockSupportedConnectorHeaders(String... supportedConnectorHeaders) {
        when(connectorConfig.getSupportedConnectorHeaders()).thenReturn(List.of(supportedConnectorHeaders));
    }

    private Exchange buildExchange(String connectorHeader) {
        Exchange exchange = createExchangeWithBody(this.context, null);
        exchange.getIn().setHeader(X_RH_NOTIFICATIONS_CONNECTOR_HEADER, connectorHeader);
        return exchange;
    }
}
