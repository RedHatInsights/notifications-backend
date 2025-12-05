package com.redhat.cloud.notifications.qute.templates.extensions;

import io.quarkus.qute.TemplateExtension;

public class SeverityExtension {

    @TemplateExtension
    public static String severityAsEmailTitle(String severity) {
        if ("".equals(severity) || "UNDEFINED".equals(severity) || "NONE".equals(severity)) {
            return "";
        } else {
            return String.format("[%s] ", severity);
        }
    }
}
