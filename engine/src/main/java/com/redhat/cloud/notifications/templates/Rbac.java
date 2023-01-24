package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

// Name needs to be "Rbac" to read templates from resources/templates/Rbac
@ApplicationScoped
public class Rbac implements EmailTemplate {

    protected static final String RH_NEW_ROLE_AVAILABLE = "rh-new-role-available";
    protected static final String RH_PLATFORM_DEFAULT_ROLE_UPDATED = "rh-platform-default-role-updated";
    protected static final String RH_NON_PLATFORM_DEFAULT_ROLE_UPDATED = "rh-non-platform-default-role-updated";
    protected static final String CUSTOM_ROLE_CREATED = "custom-role-created";
    protected static final String CUSTOM_ROLE_UPDATED = "custom-role-updated";
    protected static final String CUSTOM_ROLE_DELETED = "custom-role-deleted";
    protected static final String RH_NEW_ROLE_ADDED_TO_DEFAULT_ACCESS = "rh-new-role-added-to-default-access";
    protected static final String RH_ROLE_REMOVED_FROM_DEFAULT_ACCESS = "rh-role-removed-from-default-access";
    protected static final String CUSTOM_DEFAULT_ACCESS_UPDATED = "custom-default-access-updated";
    protected static final String GROUP_CREATED = "group-created";
    protected static final String GROUP_UPDATED = "group-updated";
    protected static final String GROUP_DELETED = "group-deleted";
    protected static final String PLATFORM_DEFAULT_GROUP_TURNED_INTO_CUSTOM = "platform-default-group-turned-into-custom";
    @Inject
    FeatureFlipper featureFlipper;

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        switch (eventType) {
            case RH_NEW_ROLE_AVAILABLE:
                return getSystemRoleAvailableEmailTitle();
            case RH_PLATFORM_DEFAULT_ROLE_UPDATED:
                return getPlatformRoleUpdatedEmailTitle();
            case RH_NON_PLATFORM_DEFAULT_ROLE_UPDATED:
                return getNonPlatformRoleUpdatedEmailTitle();
            case CUSTOM_ROLE_CREATED:
                return getCustomRoleCreatedEmailTitle();
            case CUSTOM_ROLE_UPDATED:
                return getCustomRoleUpdatedEmailTitle();
            case CUSTOM_ROLE_DELETED:
                return getCustomRoleDeletedEmailTitle();
            case RH_NEW_ROLE_ADDED_TO_DEFAULT_ACCESS:
                return getRoleAddedToPlatformGroupEmailTitle();
            case RH_ROLE_REMOVED_FROM_DEFAULT_ACCESS:
                return getRoleRemovedFromPlatformGroupEmailTitle();
            case CUSTOM_DEFAULT_ACCESS_UPDATED:
                return getCustomPlatformGroupUpdatedEmailTitle();
            case GROUP_CREATED:
                return getCustomGroupCreatedEmailTitle();
            case GROUP_UPDATED:
                return getCustomGroupUpdatedEmailTitle();
            case GROUP_DELETED:
                return getCustomGroupDeletedEmailTitle();
            case PLATFORM_DEFAULT_GROUP_TURNED_INTO_CUSTOM:
                return getPlatformGroupToCustomEmailTitle();
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
            case RH_NEW_ROLE_AVAILABLE:
                return getSystemRoleAvailableEmailBody();
            case RH_PLATFORM_DEFAULT_ROLE_UPDATED:
                return getPlatformRoleUpdatedEmailBody();
            case RH_NON_PLATFORM_DEFAULT_ROLE_UPDATED:
                return getNonPlatformRoleUpdatedEmailBody();
            case CUSTOM_ROLE_CREATED:
                return getCustomRoleCreatedEmailBody();
            case CUSTOM_ROLE_UPDATED:
                return getCustomRoleUpdatedEmailBody();
            case CUSTOM_ROLE_DELETED:
                return getCustomRoleDeletedEmailBody();
            case RH_NEW_ROLE_ADDED_TO_DEFAULT_ACCESS:
                return getRoleAddedToPlatformGroupEmailBody();
            case RH_ROLE_REMOVED_FROM_DEFAULT_ACCESS:
                return getRoleRemovedFromPlatformGroupEmailBody();
            case CUSTOM_DEFAULT_ACCESS_UPDATED:
                return getCustomPlatformGroupUpdatedEmailBody();
            case GROUP_CREATED:
                return getCustomGroupCreatedEmailBody();
            case GROUP_UPDATED:
                return getCustomGroupUpdatedEmailBody();
            case GROUP_DELETED:
                return getCustomGroupDeletedEmailBody();
            case PLATFORM_DEFAULT_GROUP_TURNED_INTO_CUSTOM:
                return getPlatformGroupToCustomEmailBody();
            default:
                throw new UnsupportedOperationException(String.format(
                    "No email title template for RBAC event_type: %s found.",
                    eventType
                ));
        }
    }

    private TemplateInstance getSystemRoleAvailableEmailBody() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.systemRoleAvailableEmailBodyV2();
        }
        return Templates.systemRoleAvailableEmailBody();
    }

    private TemplateInstance getPlatformRoleUpdatedEmailBody() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.platformRoleUpdatedEmailBodyV2();
        }
        return Templates.platformRoleUpdatedEmailBody();
    }

    private TemplateInstance getNonPlatformRoleUpdatedEmailBody() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.nonPlatformRoleUpdatedEmailBodyV2();
        }
        return Templates.nonPlatformRoleUpdatedEmailBody();
    }

    private TemplateInstance getCustomRoleCreatedEmailBody() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.customRoleCreatedEmailBodyV2();
        }
        return Templates.customRoleCreatedEmailBody();
    }

    private TemplateInstance getCustomRoleUpdatedEmailBody() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.customRoleUpdatedEmailBodyV2();
        }
        return Templates.customRoleUpdatedEmailBody();
    }

    private TemplateInstance getCustomRoleDeletedEmailBody() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.customRoleDeletedEmailBodyV2();
        }
        return Templates.customRoleDeletedEmailBody();
    }

    private TemplateInstance getRoleAddedToPlatformGroupEmailBody() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.roleAddedToPlatformGroupEmailBodyV2();
        }
        return Templates.roleAddedToPlatformGroupEmailBody();
    }

    private TemplateInstance getRoleRemovedFromPlatformGroupEmailBody() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.roleRemovedFromPlatformGroupEmailBodyV2();
        }
        return Templates.roleRemovedFromPlatformGroupEmailBody();
    }

    private TemplateInstance getCustomPlatformGroupUpdatedEmailBody() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.customPlatformGroupUpdatedEmailBodyV2();
        }
        return Templates.customPlatformGroupUpdatedEmailBody();
    }

    private TemplateInstance getCustomGroupCreatedEmailBody() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.customGroupCreatedEmailBodyV2();
        }
        return Templates.customGroupCreatedEmailBody();
    }

    private TemplateInstance getCustomGroupUpdatedEmailBody() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.customGroupUpdatedEmailBodyV2();
        }
        return Templates.customGroupUpdatedEmailBody();
    }

    private TemplateInstance getCustomGroupDeletedEmailBody() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.customGroupDeletedEmailBodyV2();
        }
        return Templates.customGroupDeletedEmailBody();
    }

    private TemplateInstance getPlatformGroupToCustomEmailBody() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.platformGroupToCustomEmailBodyV2();
        }
        return Templates.platformGroupToCustomEmailBody();
    }

    private TemplateInstance getSystemRoleAvailableEmailTitle() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.systemRoleAvailableEmailTitleV2();
        }
        return Templates.systemRoleAvailableEmailTitle();
    }

    private TemplateInstance getPlatformRoleUpdatedEmailTitle() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.platformRoleUpdatedEmailTitleV2();
        }
        return Templates.platformRoleUpdatedEmailTitle();
    }

    private TemplateInstance getNonPlatformRoleUpdatedEmailTitle() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.nonPlatformRoleUpdatedEmailTitleV2();
        }
        return Templates.nonPlatformRoleUpdatedEmailTitle();
    }

    private TemplateInstance getCustomRoleCreatedEmailTitle() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.customRoleCreatedEmailTitleV2();
        }
        return Templates.customRoleCreatedEmailTitle();
    }

    private TemplateInstance getCustomRoleUpdatedEmailTitle() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.customRoleUpdatedEmailTitleV2();
        }
        return Templates.customRoleUpdatedEmailTitle();
    }

    private TemplateInstance getCustomRoleDeletedEmailTitle() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.customRoleDeletedEmailTitleV2();
        }
        return Templates.customRoleDeletedEmailTitle();
    }

    private TemplateInstance getRoleAddedToPlatformGroupEmailTitle() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.roleAddedToPlatformGroupEmailTitleV2();
        }
        return Templates.roleAddedToPlatformGroupEmailTitle();
    }

    private TemplateInstance getRoleRemovedFromPlatformGroupEmailTitle() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.roleRemovedFromPlatformGroupEmailTitleV2();
        }
        return Templates.roleRemovedFromPlatformGroupEmailTitle();
    }

    private TemplateInstance getCustomPlatformGroupUpdatedEmailTitle() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.customPlatformGroupUpdatedEmailTitleV2();
        }
        return Templates.customPlatformGroupUpdatedEmailTitle();
    }

    private TemplateInstance getCustomGroupCreatedEmailTitle() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.customGroupCreatedEmailTitleV2();
        }
        return Templates.customGroupCreatedEmailTitle();
    }

    private TemplateInstance getCustomGroupUpdatedEmailTitle() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.customGroupUpdatedEmailTitleV2();
        }
        return Templates.customGroupUpdatedEmailTitle();
    }

    private TemplateInstance getCustomGroupDeletedEmailTitle() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.customGroupDeletedEmailTitleV2();
        }
        return Templates.customGroupDeletedEmailTitle();
    }

    private TemplateInstance getPlatformGroupToCustomEmailTitle() {
        if (featureFlipper.isRbacEmailTemplatesV2Enabled()) {
            return Templates.platformGroupToCustomEmailTitleV2();
        }
        return Templates.platformGroupToCustomEmailTitle();
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

        public static native TemplateInstance nonPlatformRoleUpdatedEmailTitle();

        public static native TemplateInstance nonPlatformRoleUpdatedEmailBody();

        public static native TemplateInstance customRoleCreatedEmailTitle();

        public static native TemplateInstance customRoleCreatedEmailBody();

        public static native TemplateInstance customRoleUpdatedEmailTitle();

        public static native TemplateInstance customRoleUpdatedEmailBody();

        public static native TemplateInstance customRoleDeletedEmailTitle();

        public static native TemplateInstance customRoleDeletedEmailBody();

        public static native TemplateInstance roleRemovedFromPlatformGroupEmailTitle();

        public static native TemplateInstance roleRemovedFromPlatformGroupEmailBody();

        public static native TemplateInstance roleAddedToPlatformGroupEmailTitle();

        public static native TemplateInstance roleAddedToPlatformGroupEmailBody();

        public static native TemplateInstance customPlatformGroupUpdatedEmailTitle();

        public static native TemplateInstance customPlatformGroupUpdatedEmailBody();

        public static native TemplateInstance customGroupCreatedEmailTitle();

        public static native TemplateInstance customGroupCreatedEmailBody();

        public static native TemplateInstance customGroupDeletedEmailTitle();

        public static native TemplateInstance customGroupDeletedEmailBody();

        public static native TemplateInstance customGroupUpdatedEmailTitle();

        public static native TemplateInstance customGroupUpdatedEmailBody();

        public static native TemplateInstance platformGroupToCustomEmailTitle();

        public static native TemplateInstance platformGroupToCustomEmailBody();

        public static native TemplateInstance systemRoleAvailableEmailTitleV2();

        public static native TemplateInstance systemRoleAvailableEmailBodyV2();

        public static native TemplateInstance platformRoleUpdatedEmailTitleV2();

        public static native TemplateInstance platformRoleUpdatedEmailBodyV2();

        public static native TemplateInstance nonPlatformRoleUpdatedEmailTitleV2();

        public static native TemplateInstance nonPlatformRoleUpdatedEmailBodyV2();

        public static native TemplateInstance customRoleCreatedEmailTitleV2();

        public static native TemplateInstance customRoleCreatedEmailBodyV2();

        public static native TemplateInstance customRoleUpdatedEmailTitleV2();

        public static native TemplateInstance customRoleUpdatedEmailBodyV2();

        public static native TemplateInstance customRoleDeletedEmailTitleV2();

        public static native TemplateInstance customRoleDeletedEmailBodyV2();

        public static native TemplateInstance roleRemovedFromPlatformGroupEmailTitleV2();

        public static native TemplateInstance roleRemovedFromPlatformGroupEmailBodyV2();

        public static native TemplateInstance roleAddedToPlatformGroupEmailTitleV2();

        public static native TemplateInstance roleAddedToPlatformGroupEmailBodyV2();

        public static native TemplateInstance customPlatformGroupUpdatedEmailTitleV2();

        public static native TemplateInstance customPlatformGroupUpdatedEmailBodyV2();

        public static native TemplateInstance customGroupCreatedEmailTitleV2();

        public static native TemplateInstance customGroupCreatedEmailBodyV2();

        public static native TemplateInstance customGroupDeletedEmailTitleV2();

        public static native TemplateInstance customGroupDeletedEmailBodyV2();

        public static native TemplateInstance customGroupUpdatedEmailTitleV2();

        public static native TemplateInstance customGroupUpdatedEmailBodyV2();

        public static native TemplateInstance platformGroupToCustomEmailTitleV2();

        public static native TemplateInstance platformGroupToCustomEmailBodyV2();
    }

}
