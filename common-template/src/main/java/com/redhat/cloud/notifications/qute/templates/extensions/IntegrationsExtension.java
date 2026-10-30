package com.redhat.cloud.notifications.qute.templates.extensions;

import com.redhat.cloud.notifications.ingress.Context;
import io.quarkus.qute.TemplateExtension;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class IntegrationsExtension extends BaseExtension {

    @TemplateExtension
    public static String integrationSettingsUrl(Context context, String baseUrl) {
        Object category = context.getAdditionalProperties().get("endpoint_category");
        Object name = context.getAdditionalProperties().get("endpoint_name");
        String encodedCategory = URLEncoder.encode(category == null ? "" : String.valueOf(category), StandardCharsets.UTF_8);
        String encodedName = URLEncoder.encode(name == null ? "" : String.valueOf(name), StandardCharsets.UTF_8);
        return baseUrl + "/settings/integrations?category=" + encodedCategory + "&name=" + encodedName;
    }

    @TemplateExtension
    public static String integrationLink(Context context, String style, String href) {
        Object name = context.getAdditionalProperties().get("endpoint_name");
        String label = name == null ? "(unnamed)" : escapeHtml(String.valueOf(name));
        return "<a style=\"" + style + "\" href=\"" + href + "\" target=\"_blank\"><b>" + label + "</b></a>";
    }
}
