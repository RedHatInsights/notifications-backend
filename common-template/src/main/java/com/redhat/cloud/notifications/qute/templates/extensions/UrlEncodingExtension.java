package com.redhat.cloud.notifications.qute.templates.extensions;

import io.quarkus.qute.TemplateExtension;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Qute helper methods for URL-safe rendering in templates.
 */
public class UrlEncodingExtension {

    /**
     * Percent-encodes a URL path segment using UTF-8 and "%20" for spaces.
     *
     * @param value raw path segment value
     * @return encoded value, or an empty string when input is {@code null}
     */
    @TemplateExtension
    public static String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
