package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

// Name needs to be "Edge" to read templates from resources/templates/Edge
public class Edge implements EmailTemplate {

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (eventType.equals("image-creation")) {
            return Templates.imageCreationTitle();
        }
        return Templates.updateDeviceTitle();
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (eventType.equals("image-creation")) {
            return Templates.imageCreationBody();
        }
        return Templates.updateDeviceBody();
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return true;
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return true;
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance updateDeviceTitle();

        public static native TemplateInstance imageCreationTitle();

        public static native TemplateInstance updateDeviceBody();

        public static native TemplateInstance imageCreationBody();

    }

}
