package com.redhat.cloud.notifications.connector.http;

import org.apache.camel.Exchange;
import java.net.URI;
import java.net.URISyntaxException;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;

public class UrlValidator {

    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";

    public static void validateTargetUrl(Exchange exchange) throws Exception {
        String targetUrl = exchange.getProperty(TARGET_URL, String.class);
        try {
            String scheme = (new URI(targetUrl)).getScheme();
            if (!HTTPS_SCHEME.equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("Illegal URL scheme: " + scheme + ". Please use https.");
            }

            // handle case where url is null (should never happen)
        } catch (URISyntaxException | NullPointerException e) {
            throw new IllegalArgumentException("Invalid target URL: " + targetUrl);
        }
    }

}
