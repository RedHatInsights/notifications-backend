package com.redhat.cloud.notifications.templates.extensions;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.NotificationsConsoleCloudEvent;
import io.quarkus.qute.TemplateExtension;

public class ActionExtension {

    @TemplateExtension
    public static boolean isCloudEvent(NotificationsConsoleCloudEvent event) {
        return true;
    }

    @TemplateExtension
    public static boolean isCloudEvent(Action action) {
        return false;
    }
}
