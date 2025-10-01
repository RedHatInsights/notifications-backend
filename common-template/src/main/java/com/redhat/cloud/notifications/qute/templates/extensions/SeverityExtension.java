package com.redhat.cloud.notifications.qute.templates.extensions;

import com.redhat.cloud.event.parser.ConsoleCloudEvent;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.Severity;
import io.quarkus.qute.TemplateExtension;

public class SeverityExtension {

    /** Cloud events do not support the severity field */
    @TemplateExtension
    static String severityAsEmailTitle(ConsoleCloudEvent event) {
        return "";
    }

    @TemplateExtension
    static String severityAsEmailTitle(Action action) {
        Severity severity;
        try {
            severity = Severity.valueOf(action.getSeverity());
        } catch (Exception ignored) {
            severity = Severity.UNDEFINED;
        }

        if (severity != Severity.UNDEFINED && severity != Severity.NONE) {
            return String.format("[%s] ", severity);
        }

        return "";
    }
}
