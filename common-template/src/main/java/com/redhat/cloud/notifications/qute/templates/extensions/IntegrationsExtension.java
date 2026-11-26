package com.redhat.cloud.notifications.qute.templates.extensions;

import io.quarkus.qute.TemplateExtension;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Qute helper methods used by the Integrations email templates.
 *
 * <p>The helpers operate on the {@code action.context} {@code Map}. In production {@code EmailMessageHandler}
 * passes the raw {@code eventData} map (not an {@code ingress.Action}) as the {@code action}, so the template
 * is always rendered against maps; the tests do the same to exercise the real rendering path.</p>
 */
public class IntegrationsExtension extends BaseExtension {

    @TemplateExtension
    public static String integrationSettingsUrl(Map<String, Object> context, String baseUrl) {
        Object category = context.get("endpoint_category");
        Object name = context.get("endpoint_name");
        String encodedCategory = URLEncoder.encode(category == null ? "" : String.valueOf(category), StandardCharsets.UTF_8);
        String encodedName = URLEncoder.encode(name == null ? "" : String.valueOf(name), StandardCharsets.UTF_8);
        return baseUrl + "/settings/integrations?category=" + encodedCategory + "&name=" + encodedName;
    }

    @TemplateExtension
    public static String integrationLink(Map<String, Object> context, String style, String href) {
        Object name = context.get("endpoint_name");
        String label = name == null ? "(unnamed)" : escapeHtml(String.valueOf(name));
        return "<a style=\"" + style + "\" href=\"" + href + "\" target=\"_blank\"><b>" + label + "</b></a>";
    }

    /**
     * Builds the "Settings &gt; Integrations" anchor used in the review prompt. The {@code label} is a
     * trusted, translated constant coming from the message bundle and is therefore inserted as-is; keeping
     * the {@code <a>} markup here (rather than in the message strings) honors the template i18n convention
     * that message bundles must not embed HTML.
     */
    @TemplateExtension
    public static String settingsLink(Map<String, Object> context, String style, String href, String label) {
        return "<a style=\"" + style + "\" href=\"" + href + "\" target=\"_blank\">" + label + "</a>";
    }

    /**
     * HTML-escapes an arbitrary value before it is inserted into an HTML email where Qute auto-escaping is
     * disabled ({@code quarkus.qute.content-types.html=none}). Used for the remote endpoint error details,
     * which originate from an untrusted remote endpoint response.
     */
    @TemplateExtension
    public static String htmlEscape(Object value) {
        return value == null ? "" : escapeHtml(String.valueOf(value));
    }
}
