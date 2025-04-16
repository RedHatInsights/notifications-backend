package com.redhat.cloud.notifications.qute.templates.mapping;

import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import java.util.Map;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.DRAWER;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.EMAIL_BODY;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.EMAIL_TITLE;
import static java.util.Map.entry;


public class Console {
    static final String BUNDLE_NAME = "console";

    static final String INTEGRATIONS_APP_NAME = "integrations";
    static final String INTEGRATIONS_FOLDER_NAME = "Integrations/";

    static final String RBAC_APP_NAME = "rbac";
    static final String RBAC_FOLDER_NAME = "Rbac/";

    static final String SOURCES_APP_NAME = "sources";
    static final String SOURCES_FOLDER_NAME = "Sources/";

    public static final Map<TemplateDefinition, String> templatesMap = Map.ofEntries(
        // Integration
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, INTEGRATIONS_APP_NAME, "integration-disabled"), INTEGRATIONS_FOLDER_NAME + "integrationDisabledBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, INTEGRATIONS_APP_NAME, "integration-disabled"), INTEGRATIONS_FOLDER_NAME + "integrationDisabledTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, INTEGRATIONS_APP_NAME, "integration-disabled"), INTEGRATIONS_FOLDER_NAME + "integrationDisabledBody.html"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, INTEGRATIONS_APP_NAME, "general-communication"), INTEGRATIONS_FOLDER_NAME + "generalCommunicationTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, INTEGRATIONS_APP_NAME, "general-communication"), INTEGRATIONS_FOLDER_NAME + "generalCommunicationBody.html"),

        // Rbac
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "rh-new-role-available"), RBAC_FOLDER_NAME + "systemRoleAvailableBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, RBAC_APP_NAME, "rh-new-role-available"), RBAC_FOLDER_NAME + "systemRoleAvailableEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, RBAC_APP_NAME, "rh-new-role-available"), RBAC_FOLDER_NAME + "systemRoleAvailableEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "rh-platform-default-role-updated"), RBAC_FOLDER_NAME + "platformRoleUpdatedBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, RBAC_APP_NAME, "rh-platform-default-role-updated"), RBAC_FOLDER_NAME + "platformRoleUpdatedEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, RBAC_APP_NAME, "rh-platform-default-role-updated"), RBAC_FOLDER_NAME + "platformRoleUpdatedEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "rh-non-platform-default-role-updated"), RBAC_FOLDER_NAME + "nonPlatformRoleUpdatedBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, RBAC_APP_NAME, "rh-non-platform-default-role-updated"), RBAC_FOLDER_NAME + "nonPlatformRoleUpdatedEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, RBAC_APP_NAME, "rh-non-platform-default-role-updated"), RBAC_FOLDER_NAME + "nonPlatformRoleUpdatedEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "custom-role-created"), RBAC_FOLDER_NAME + "customRoleCreatedBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, RBAC_APP_NAME, "custom-role-created"), RBAC_FOLDER_NAME + "customRoleCreatedEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, RBAC_APP_NAME, "custom-role-created"), RBAC_FOLDER_NAME + "customRoleCreatedEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "custom-role-updated"), RBAC_FOLDER_NAME + "customRoleUpdatedBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, RBAC_APP_NAME, "custom-role-updated"), RBAC_FOLDER_NAME + "customRoleUpdatedEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, RBAC_APP_NAME, "custom-role-updated"), RBAC_FOLDER_NAME + "customRoleUpdatedEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "custom-role-deleted"), RBAC_FOLDER_NAME + "customRoleDeletedBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, RBAC_APP_NAME, "custom-role-deleted"), RBAC_FOLDER_NAME + "customRoleDeletedEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, RBAC_APP_NAME, "custom-role-deleted"), RBAC_FOLDER_NAME + "customRoleDeletedEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "rh-new-role-added-to-default-access"), RBAC_FOLDER_NAME + "roleAddedToPlatformGroupBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, RBAC_APP_NAME, "rh-new-role-added-to-default-access"), RBAC_FOLDER_NAME + "roleAddedToPlatformGroupEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, RBAC_APP_NAME, "rh-new-role-added-to-default-access"), RBAC_FOLDER_NAME + "roleAddedToPlatformGroupEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "rh-role-removed-from-default-access"), RBAC_FOLDER_NAME + "roleRemovedFromPlatformGroupBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, RBAC_APP_NAME, "rh-role-removed-from-default-access"), RBAC_FOLDER_NAME + "roleRemovedFromPlatformGroupEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, RBAC_APP_NAME, "rh-role-removed-from-default-access"), RBAC_FOLDER_NAME + "roleRemovedFromPlatformGroupEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "custom-default-access-updated"),  RBAC_FOLDER_NAME + "customPlatformGroupUpdatedBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, RBAC_APP_NAME, "custom-default-access-updated"), RBAC_FOLDER_NAME + "customPlatformGroupUpdatedEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, RBAC_APP_NAME, "custom-default-access-updated"), RBAC_FOLDER_NAME + "customPlatformGroupUpdatedEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "group-created"), RBAC_FOLDER_NAME + "customGroupCreatedBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, RBAC_APP_NAME, "group-created"), RBAC_FOLDER_NAME + "customGroupCreatedEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, RBAC_APP_NAME, "group-created"), RBAC_FOLDER_NAME + "customGroupCreatedEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "group-updated"), RBAC_FOLDER_NAME + "customGroupUpdatedBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, RBAC_APP_NAME, "group-updated"), RBAC_FOLDER_NAME + "customGroupUpdatedEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, RBAC_APP_NAME, "group-updated"), RBAC_FOLDER_NAME + "customGroupUpdatedEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "group-deleted"), RBAC_FOLDER_NAME + "customGroupDeletedBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, RBAC_APP_NAME, "group-deleted"), RBAC_FOLDER_NAME + "customGroupDeletedEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, RBAC_APP_NAME, "group-deleted"), RBAC_FOLDER_NAME + "customGroupDeletedEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "platform-default-group-turned-into-custom"), RBAC_FOLDER_NAME + "platformGroupToCustomBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, RBAC_APP_NAME, "platform-default-group-turned-into-custom"), RBAC_FOLDER_NAME + "platformGroupToCustomEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, RBAC_APP_NAME, "platform-default-group-turned-into-custom"), RBAC_FOLDER_NAME + "platformGroupToCustomEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "request-access"), RBAC_FOLDER_NAME + "requestAccess.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, RBAC_APP_NAME, "request-access"), RBAC_FOLDER_NAME + "requestAccessEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, RBAC_APP_NAME, "request-access"), RBAC_FOLDER_NAME + "requestAccessEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "rh-new-tam-request-created"), RBAC_FOLDER_NAME + "tamAccessRequest.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, RBAC_APP_NAME, "rh-new-tam-request-created"), RBAC_FOLDER_NAME + "tamAccessRequestEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, RBAC_APP_NAME, "rh-new-tam-request-created"), RBAC_FOLDER_NAME + "tamAccessRequestEmailBody.html"),

        // Sources
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, SOURCES_APP_NAME, "availability-status"), SOURCES_FOLDER_NAME + "availabilityStatusBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, SOURCES_APP_NAME, "availability-status"), SOURCES_FOLDER_NAME + "availabilityStatusEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, SOURCES_APP_NAME, "availability-status"), SOURCES_FOLDER_NAME + "availabilityStatusEmailBody.html")
    );
}
