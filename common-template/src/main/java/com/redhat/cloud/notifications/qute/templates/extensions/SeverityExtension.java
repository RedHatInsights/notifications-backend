package com.redhat.cloud.notifications.qute.templates.extensions;

import com.redhat.cloud.event.parser.ConsoleCloudEvent;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.qute.TemplateExtension;

public class SeverityExtension {

    /** Cloud events do not support the severity field */
    @TemplateExtension
    public static String severityAsEmailTitle(ConsoleCloudEvent event) {
        return "";
    }

    @TemplateExtension
    public static String severityAsEmailTitle(Action action) {
        String severity = action.getSeverity();
        if (severity == null || "UNDEFINED".equals(severity) || "NONE".equals(severity)) {
            return "";
        } else {
            return String.format("[%s] ", severity);
        }
    }
}
