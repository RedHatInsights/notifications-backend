package com.redhat.cloud.notifications.connector.http;

import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * URL validator that validates target URLs for HTTP connectors.
 * This is the new version that replaces the Camel-based UrlValidator.
 */
@ApplicationScoped
public class UrlValidator {

    private static final String HTTP_SCHEME = "http";
    private static final String HTTPS_SCHEME = "https";

    public void validateTargetUrl(ExceptionProcessor.ProcessingContext context) throws Exception {
        String targetUrl = context.getTargetUrl();
        validateTargetUrl(targetUrl);
    }

    public void validateTargetUrl(String targetUrl) throws Exception {
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

    public boolean isValidUrl(String targetUrl) {
        try {
            validateTargetUrl(targetUrl);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isHttpsUrl(String targetUrl) {
        try {
            URI uri = new URI(targetUrl);
            return HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme());
        } catch (URISyntaxException | NullPointerException e) {
            return false;
        }
    }
}
