package com.redhat.cloud.notifications.connector.v2;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.redhat.cloud.notifications.connector.v2.IncomingCloudEventFilter.X_RH_NOTIFICATIONS_CONNECTOR_HEADER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@QuarkusTest
public class IncomingCloudEventFilterTest {

    @Inject
    IncomingCloudEventFilter incomingCloudEventFilter;

    @InjectMock
    ConnectorConfig connectorConfig;

    @Test
    void shouldAcceptValidConnectorHeaderSingleValue() {
        mockSupportedConnectorHeaders("foo");
        assertTrue(incomingCloudEventFilter.accept(buildMessageContext("foo")));
    }

    @Test
    void shouldAcceptValidConnectorHeaderMultipleValues() {
        mockSupportedConnectorHeaders("bar", "foo");
        assertTrue(incomingCloudEventFilter.accept(buildMessageContext("foo")));
    }

    @Test
    void shouldRejectInvalidConnectorHeader() {
        mockSupportedConnectorHeaders("foo");
        assertFalse(incomingCloudEventFilter.accept(buildMessageContext("bar")));
    }

    private void mockSupportedConnectorHeaders(String... supportedConnectorHeaders) {
        when(connectorConfig.getSupportedConnectorHeaders()).thenReturn(List.of(supportedConnectorHeaders));
    }

    private MessageContext buildMessageContext(String connectorHeader) {
        MessageContext context = new MessageContext();
        context.setHeaders(Map.of(X_RH_NOTIFICATIONS_CONNECTOR_HEADER, Optional.of(connectorHeader)));
        return context;
    }
}

