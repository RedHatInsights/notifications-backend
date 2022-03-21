package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

// Name needs to be "Rbac" to read templates from resources/templates/Rbac
public class Rbac implements EmailTemplate {

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            switch (eventType) {
                case "rh-new-role-available":
                case "rh-platform-defaul-role-upated":
                case "rh-non-platform-default-role-updated":
                case "custom-role-created":
                case "custom-role-updated":
                case "custom-role-deleted":
                    return Templates.roleChangeEmailTitle();
                case "rh-new-role-added-to-default-access":
                case "rh-role-removed-from-default-access":
                case "custom-default-access-updated":
                case "group-created":
                case "group-upated":
                case "group-deleted":
                    return Templates.groupChangeEmailTitle();
                case "platform-default-group-turned-into-custom":
                    return Templates.platformGroup2CustomEmailBody();
            }
        }
        throw new UnsupportedOperationException(String.format(
                "No email title template for RBAC event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            switch (eventType) {
                case "rh-new-role-available":
                case "rh-platform-defaul-role-upated":
                case "rh-non-platform-default-role-updated":
                case "custom-role-created":
                case "custom-role-updated":
                case "custom-role-deleted":
                    return Templates.roleChangeEmailBody();
                case "rh-new-role-added-to-default-access":
                case "rh-role-removed-from-default-access":
                case "custom-default-access-updated":
                case "group-created":
                case "group-upated":
                case "group-deleted":
                    return Templates.groupChangeEmailBody();
                case "platform-default-group-turned-into-custom":
                    return Templates.platformGroup2CustomEmailBody();
            }
        }

        throw new UnsupportedOperationException(String.format(
                "No email body template for Rbac event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
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

        public static native TemplateInstance roleChangeEmailTitle();

        public static native TemplateInstance roleChangeEmailBody();

        public static native TemplateInstance groupChangeEmailTitle();

        public static native TemplateInstance groupChangeEmailBody();

        public static native TemplateInstance platformGroup2CustomEmailTitle();

        public static native TemplateInstance platformGroup2CustomEmailBody();
    }

}
