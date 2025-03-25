package com.redhat.cloud.notifications.qute.templates.mapping;

import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import java.util.Map;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.DRAWER;
import static java.util.Map.entry;


public class Console {
    static final String BUNDLE_NAME = "console";

    static final String INTEGRATIONS_FOLDER_NAME = "Integrations";

    static final String RBAC_APP_NAME = "rbac";
    static final String RBAC_FOLDER_NAME = "Rbac";

    static final String SOURCES_FOLDER_NAME = "Sources";

    public static final Map<TemplateDefinition, String> templatesMap = Map.ofEntries(
        // Integration
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, "integrations", "integration-disabled"), INTEGRATIONS_FOLDER_NAME + "/integrationDisabledBody.md"),

        // Rbac
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "rh-new-role-available"), RBAC_FOLDER_NAME + "/systemRoleAvailableBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "rh-platform-default-role-updated"), RBAC_FOLDER_NAME + "/platformRoleUpdatedBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "rh-non-platform-default-role-updated"), RBAC_FOLDER_NAME + "/nonPlatformRoleUpdatedBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "custom-role-created"), RBAC_FOLDER_NAME + "/customRoleCreatedBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "custom-role-updated"), RBAC_FOLDER_NAME + "/customRoleUpdatedBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "custom-role-deleted"), RBAC_FOLDER_NAME + "/customRoleDeletedBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "rh-new-role-added-to-default-access"), RBAC_FOLDER_NAME + "/roleAddedToPlatformGroupBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "rh-role-removed-from-default-access"), RBAC_FOLDER_NAME + "/roleRemovedFromPlatformGroupBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "custom-default-access-updated"),  RBAC_FOLDER_NAME + "/customPlatformGroupUpdatedBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "group-created"), RBAC_FOLDER_NAME + "/customGroupCreatedBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "group-updated"), RBAC_FOLDER_NAME + "/customGroupUpdatedBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "group-deleted"), RBAC_FOLDER_NAME + "/customGroupDeletedBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "platform-default-group-turned-into-custom"), RBAC_FOLDER_NAME + "/platformGroupToCustomBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "request-access"), RBAC_FOLDER_NAME + "/requestAccess.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, RBAC_APP_NAME, "rh-new-tam-request-created"), RBAC_FOLDER_NAME + "/tamAccessRequest.md"),

        // Sources
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, "sources", "availability-status"), SOURCES_FOLDER_NAME + "/availabilityStatusBody.md")
    );
}
