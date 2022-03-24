package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

// Name needs to be "Rbac" to read templates from resources/templates/Rbac
public class Rbac implements EmailTemplate {

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        switch (eventType) {
            case "rh-new-role-available":
                return Templates.systemRoleAvailableEmailTitle();
            case "rh-platform-default-role-updated":
                return Templates.platformRoleUpdatedEmailTitle();
            case "rh-non-platform-default-role-updated":
                return Templates.nonPlatformRoleUpdatedEmailTitle();
            case "custom-role-created":
                return Templates.customRoleCreatedEmailTitle();
            case "custom-role-updated":
                return Templates.customRoleUpdatedEmailTitle();
            case "custom-role-deleted":
                return Templates.customRoleDeletedEmailTitle();
            case "rh-new-role-added-to-default-access":
                return Templates.roleAddedToPlatformGroupEmailTitle();
            case "rh-role-removed-from-default-access":
                return Templates.roleRemovedFromPlatformGroupEmailTitle();
            case "custom-default-access-updated":
                return Templates.customPlatformGroupUpdatedEmailTitle();
            case "group-created":
                return Templates.customGroupCreatedEmailTitle()
            case "group-updated":
                return Templates.customGroupUpdatedEmailTitle()
            case "group-deleted":
                return Templates.customGroupDeletedEmailTitle();
            case "platform-default-group-turned-into-custom":
                return Templates.platformGroup2CustomEmailTitle();
            default:
                throw new UnsupportedOperationException(String.format(
                    "No email title template for RBAC event_type: %s found.",
                    eventType
                ));
        }
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        switch (eventType) {
            case "rh-new-role-available":
                return Templates.systemRoleAvailableEmailBody();
            case "rh-platform-default-role-updated":
                return Templates.platformRoleUpdatedEmailBody();
            case "rh-non-platform-default-role-updated":
                return Templates.nonPlatformRoleUpdatedEmailBody();
            case "custom-role-created":
                return Templates.customRoleCreatedEmailBody();
            case "custom-role-updated":
                return Templates.customRoleUpdatedEmailBody();
            case "custom-role-deleted":
                return Templates.customRoleDeletedEmailBody();
            case "rh-new-role-added-to-default-access":
                return Templates.roleAddedToPlatformGroupEmailBody();
            case "rh-role-removed-from-default-access":
                return Templates.roleRemovedFromPlatformGroupEmailBody();
            case "custom-default-access-updated":
                return Templates.customPlatformGroupUpdatedEmailBody();
            case "group-created":
                return Templates.customGroupCreatedEmailBody()
            case "group-updated":
                return Templates.customGroupUpdatedEmailBody()
            case "group-deleted":
                return Templates.customGroupDeletedEmailBody();
            case "platform-default-group-turned-into-custom":
                return Templates.platformGroup2CustomEmailBody();
            default:
                throw new UnsupportedOperationException(String.format(
                    "No email title template for RBAC event_type: %s found.",
                    eventType
                ));
        }
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return true;
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return type == EmailSubscriptionType.INSTANT;
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance systemRoleAvailableEmailTitle();

        public static native TemplateInstance systemRoleAvailableEmailBody();

        public static native TemplateInstance platformRoleUpdatedEmailTitle();

        public static native TemplateInstance platformRoleUpdatedEmailBody();

        pubilc static native TemplateInstance nonPlatformRoleUpdatedEmailTitle();

        pubilc static native TemplateInstance nonPlatformRoleUpdatedEmailBody();

        pubilc static native TemplateInstance customRoleCreatedEmailTitle();
        
        pubilc static native TemplateInstance customRoleCreatedEmailBody();

        pubilc static native TemplateInstance customRoleUpdatedEmailTitle();

        pubilc static native TemplateInstance customRoleUpdatedEmailBody();

        pubilc static native TemplateInstance customRoleDeletedEmailTitle();

        pubilc static native TemplateInstance customRoleDeletedEmailBody();

        pubilc static native TemplateInstance roleRemovedFromPlatformGroupEmailTitle();

        pubilc static native TemplateInstance roleRemovedFromPlatformGroupEmailBody();

        pubilc static native TemplateInstance roleAddedToPlatformGroupEmailTitle();

        pubilc static native TemplateInstance roleAddedToPlatformGroupEmailBody();

        pubilc static native TemplateInstance customPlatformGroupUpdatedEmailTitle();

        pubilc static native TemplateInstance customPlatformGroupUpdatedEmailBody();

        pubilc static native TemplateInstance customGroupCreatedEmailTitle();

        pubilc static native TemplateInstance customGroupCreatedEmailBody();

        pubilc static native TemplateInstance customGroupDeletedEmailTitle();

        pubilc static native TemplateInstance customGroupDeletedEmailBody();

        pubilc static native TemplateInstance customGroupUpdatedEmailTitle();

        pubilc static native TemplateInstance customGroupUpdatedEmailBody();

        public static native TemplateInstance platformGroupToCustomEmailTitle();

        public static native TemplateInstance platformGroupToCustomEmailBody();
    }

}
