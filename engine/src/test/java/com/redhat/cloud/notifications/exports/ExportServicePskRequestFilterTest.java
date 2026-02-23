package com.redhat.cloud.notifications.exports;

import com.redhat.cloud.notifications.Constants;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ExportServicePskRequestFilter} to verify that the PSK header
 * is correctly added to requests when configured.
 */
@QuarkusTest
public class ExportServicePskRequestFilterTest {

    private static final String EXPORT_SERVICE_PSK_CONFIG_KEY = "export-service.psk";

    @Test
    @DisplayName("Should add PSK header when filter is invoked")
    void shouldAddPskHeaderWhenFilterIsInvoked() {
        ExportServicePskRequestFilter filter = new ExportServicePskRequestFilter();
        ClientRequestContext context = createMockContext();

        filter.filter(context);

        // Verify the PSK header was added
        Object pskHeader = context.getHeaders().getFirst(Constants.X_RH_EXPORT_SERVICE_PSK);
        assertNotNull(pskHeader, "PSK header should be present");
        assertFalse(pskHeader.toString().isEmpty(), "PSK header should not be empty");
    }

    @Test
    @DisplayName("Should use correct header name for PSK")
    void shouldUseCorrectHeaderNameForPsk() {
        ExportServicePskRequestFilter filter = new ExportServicePskRequestFilter();
        ClientRequestContext context = createMockContext();

        filter.filter(context);

        // Verify the correct header name is used (x-rh-exports-psk)
        assertEquals("x-rh-exports-psk", Constants.X_RH_EXPORT_SERVICE_PSK,
            "Header name constant should be 'x-rh-exports-psk'");
        assertNotNull(context.getHeaders().getFirst("x-rh-exports-psk"),
            "PSK should be set with the correct header name");
    }

    @Test
    @DisplayName("Should use PSK value from configuration")
    void shouldUsePskValueFromConfiguration() {
        ExportServicePskRequestFilter filter = new ExportServicePskRequestFilter();
        ClientRequestContext context = createMockContext();

        filter.filter(context);

        // Get the expected PSK from configuration
        Optional<String> expectedPsk = ConfigProvider.getConfig()
            .getOptionalValue(EXPORT_SERVICE_PSK_CONFIG_KEY, String.class);

        assertTrue(expectedPsk.isPresent(), "PSK should be configured in test environment");

        // Verify the filter used the correct PSK value
        assertEquals(expectedPsk.get(), context.getHeaders().getFirst(Constants.X_RH_EXPORT_SERVICE_PSK),
            "PSK header should contain the configured PSK value");
    }

    @Test
    @DisplayName("Should set single header value (not append)")
    void shouldSetSingleHeaderValue() {
        ExportServicePskRequestFilter filter = new ExportServicePskRequestFilter();
        ClientRequestContext context = createMockContext();

        // Call filter twice
        filter.filter(context);
        filter.filter(context);

        // Verify only one header value is present (putSingle should replace, not append)
        assertEquals(1, context.getHeaders().get(Constants.X_RH_EXPORT_SERVICE_PSK).size(),
            "PSK header should have exactly one value (putSingle behavior)");
    }

    /**
     * Creates a mock ClientRequestContext with a real MultivaluedMap for headers.
     */
    private ClientRequestContext createMockContext() {
        ClientRequestContext context = Mockito.mock(ClientRequestContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        Mockito.when(context.getHeaders()).thenReturn(headers);
        return context;
    }
}
