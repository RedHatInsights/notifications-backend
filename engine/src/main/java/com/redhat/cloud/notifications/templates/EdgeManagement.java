package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

// Name needs to be "EdgeManagement" to read templates from resources/templates/Edge
public class EdgeManagement implements EmailTemplate {

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (eventType.equals("image-creation")) {
            return Templates.imageCreationTitle();
        } else if (eventType.equals("update-devices")) {
            return Templates.updateDeviceTitle();
        }
        throw new UnsupportedOperationException(String.format(
                "No email title template for Edge event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (eventType.equals("image-creation")) {
            return Templates.imageCreationBody();
        } else if (eventType.equals("update-devices")) {
            return Templates.updateDeviceBody();
        }
        throw new UnsupportedOperationException(String.format(
                "No email body template for Edge event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return type == EmailSubscriptionType.INSTANT && (eventType.equals("image-creation") || eventType.equals("update-devices"));
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return type == EmailSubscriptionType.INSTANT;
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance updateDeviceTitle();

        public static native TemplateInstance imageCreationTitle();

        public static native TemplateInstance updateDeviceBody();

        public static native TemplateInstance imageCreationBody();

    }

}
