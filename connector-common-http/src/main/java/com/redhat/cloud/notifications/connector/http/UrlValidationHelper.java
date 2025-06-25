package com.redhat.cloud.notifications.connector.http;


import org.apache.camel.Exchange;
import org.apache.http.ProtocolException;
import java.net.URI;
import java.net.URISyntaxException;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;

public class UrlValidationHelper {

    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";

    public static void validateTargetUrl(Exchange exchange) throws Exception {
        String targetUrl = exchange.getProperty(TARGET_URL, String.class);
        try {
            String scheme = (new URI(targetUrl)).getScheme();

            if (HTTP_SCHEME.equalsIgnoreCase(scheme)) {
                throw new ProtocolException("HTTP protocol is not supported");
            } else if (!HTTPS_SCHEME.equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("URL validation failed");
            }
            // handle case where url is null (should never happen)
        } catch (URISyntaxException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid target URL: " + targetUrl);
        }
    }

}
