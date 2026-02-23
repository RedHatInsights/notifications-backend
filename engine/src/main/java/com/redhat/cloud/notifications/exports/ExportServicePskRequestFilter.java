package com.redhat.cloud.notifications.exports;

import com.redhat.cloud.notifications.Constants;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Request filter that adds the PSK header to Export Service requests.
 * The PSK value is read once from the configuration property {@code export-service.psk}
 * and cached for subsequent requests.
 */
public class ExportServicePskRequestFilter implements ClientRequestFilter {

    private static final String EXPORT_SERVICE_PSK_CONFIG_KEY = "export-service.psk";

    private final String psk;

    public ExportServicePskRequestFilter() {
        psk = ConfigProvider.getConfig()
            .getOptionalValue(EXPORT_SERVICE_PSK_CONFIG_KEY, String.class)
            .orElse(null);
    }

    @Override
    public void filter(ClientRequestContext requestContext) {
        if (psk != null) {
            requestContext.getHeaders().putSingle(Constants.X_RH_EXPORT_SERVICE_PSK, psk);
        }
    }
}
