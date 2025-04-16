package com.redhat.cloud.notifications.qute.templates.mapping;

import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import java.util.Map;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.DRAWER;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.EMAIL_BODY;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.EMAIL_DAILY_DIGEST_BODY;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.EMAIL_TITLE;
import static java.util.Map.entry;

public class Rhel {
    public static final String BUNDLE_NAME = "rhel";

    public static final String ADVISOR_APP_NAME = "advisor";
    public static final String ADVISOR_FOLDER_NAME = "Advisor/";

    public static final String COMPLIANCE_APP_NAME = "compliance";
    public static final String COMPLIANCE_FOLDER_NAME = "Compliance/";

    public static final String EDGE_APP_NAME = "edge-management";
    public static final String EDGE_FOLDER_NAME = "EdgeManagement/";

    public static final String IMAGE_BUILDER_APP_NAME = "image-builder";
    public static final String IMAGE_BUILDER_FOLDER_NAME = "ImageBuilder/";

    public static final String INVENTORY_APP_NAME = "inventory";
    public static final String INVENTORY_FOLDER_NAME = "Inventory/";

    public static final String MALWARE_APP_NAME = "malware-detection";
    public static final String MALWARE_FOLDER_NAME = "MalwareDetection/";

    public static final String PATCH_APP_NAME = "patch";
    public static final String PATCH_FOLDER_NAME = "Patch/";

    public static final String POLICY_APP_NAME = "policies";
    public static final String POLICY_FOLDER_NAME = "Policies/";

    public static final String RESOURCE_OPTIMIZATION_APP_NAME = "resource-optimization";
    public static final String RESOURCE_OPTIMIZATION_FOLDER_NAME = "ResourceOptimization/";

    static final String TASK_APP_NAME = "tasks";
    static final String TASK_FOLDER_NAME = "Tasks/";

    public static final String VULNERABILITY_APP_NAME = "vulnerability";
    public static final String VULNERABILITY_FOLDER_NAME = "Vulnerability/";

    public static final Map<TemplateDefinition, String> templatesMap = Map.ofEntries(
        // Advisor
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, ADVISOR_APP_NAME, "deactivated-recommendation"), ADVISOR_FOLDER_NAME + "deactivatedRecommendationBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, ADVISOR_APP_NAME, "new-recommendation"), ADVISOR_FOLDER_NAME + "newRecommendationBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, ADVISOR_APP_NAME, "resolved-recommendation"), ADVISOR_FOLDER_NAME + "resolvedRecommendationBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, ADVISOR_APP_NAME, "deactivated-recommendation"), ADVISOR_FOLDER_NAME + "deactivatedRecommendationInstantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, ADVISOR_APP_NAME, "deactivated-recommendation"), ADVISOR_FOLDER_NAME + "deactivatedRecommendationInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, ADVISOR_APP_NAME, "new-recommendation"), ADVISOR_FOLDER_NAME + "newRecommendationInstantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, ADVISOR_APP_NAME, "new-recommendation"), ADVISOR_FOLDER_NAME + "newRecommendationInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, ADVISOR_APP_NAME, "resolved-recommendation"), ADVISOR_FOLDER_NAME + "resolvedRecommendationInstantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, ADVISOR_APP_NAME, "resolved-recommendation"), ADVISOR_FOLDER_NAME + "resolvedRecommendationInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, ADVISOR_APP_NAME, null), ADVISOR_FOLDER_NAME + "dailyEmailBody.html"),

        // Compliance
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COMPLIANCE_APP_NAME, "compliance-below-threshold"), COMPLIANCE_FOLDER_NAME + "belowThresholdBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, COMPLIANCE_APP_NAME, "compliance-below-threshold"), COMPLIANCE_FOLDER_NAME + "complianceBelowThresholdEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, COMPLIANCE_APP_NAME, "compliance-below-threshold"), COMPLIANCE_FOLDER_NAME + "complianceBelowThresholdEmailBody.html"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COMPLIANCE_APP_NAME, "report-upload-failed"), COMPLIANCE_FOLDER_NAME + "reportUploadFailedBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, COMPLIANCE_APP_NAME, "report-upload-failed"), COMPLIANCE_FOLDER_NAME + "reportUploadFailedEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, COMPLIANCE_APP_NAME, "report-upload-failed"), COMPLIANCE_FOLDER_NAME + "reportUploadFailedEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, COMPLIANCE_APP_NAME, null), COMPLIANCE_FOLDER_NAME + "dailyEmailBody.html"),

        // Edge management
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, EDGE_APP_NAME, "image-creation"), EDGE_FOLDER_NAME + "imageCreationBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, EDGE_APP_NAME, "image-creation"), EDGE_FOLDER_NAME + "imageCreationEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, EDGE_APP_NAME, "image-creation"), EDGE_FOLDER_NAME + "imageCreationEmailBody.html"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, EDGE_APP_NAME, "update-devices"), EDGE_FOLDER_NAME + "updateDeviceBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, EDGE_APP_NAME, "update-devices"), EDGE_FOLDER_NAME + "updateDeviceEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, EDGE_APP_NAME, "update-devices"), EDGE_FOLDER_NAME + "updateDeviceEmailBody.html"),

        // Image builder
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, IMAGE_BUILDER_APP_NAME, "launch-success"), IMAGE_BUILDER_FOLDER_NAME + "launchSuccessInstantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, IMAGE_BUILDER_APP_NAME, "launch-success"), IMAGE_BUILDER_FOLDER_NAME + "launchSuccessInstantEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, IMAGE_BUILDER_APP_NAME, "launch-failed"), IMAGE_BUILDER_FOLDER_NAME + "launchFailedInstantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, IMAGE_BUILDER_APP_NAME, "launch-failed"), IMAGE_BUILDER_FOLDER_NAME + "launchFailedEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, IMAGE_BUILDER_APP_NAME, null), IMAGE_BUILDER_FOLDER_NAME + "dailyEmailBody.html"),

        // Inventory
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, INVENTORY_APP_NAME, "new-system-registered"), INVENTORY_FOLDER_NAME + "newSystemRegisteredEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, INVENTORY_APP_NAME, "new-system-registered"), INVENTORY_FOLDER_NAME + "newSystemRegisteredEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, INVENTORY_APP_NAME, "system-became-stale"), INVENTORY_FOLDER_NAME + "systemBecameStaleTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, INVENTORY_APP_NAME, "system-became-stale"), INVENTORY_FOLDER_NAME + "systemBecameStaleBody.html"),

        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, INVENTORY_APP_NAME, "system-deleted"), INVENTORY_FOLDER_NAME + "systemDeletedEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, INVENTORY_APP_NAME, "system-deleted"), INVENTORY_FOLDER_NAME + "systemDeletedEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, INVENTORY_APP_NAME, "validation-error"), INVENTORY_FOLDER_NAME + "validationErrorBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, INVENTORY_APP_NAME, "validation-error"), INVENTORY_FOLDER_NAME + "validationErrorEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, INVENTORY_APP_NAME, "validation-error"), INVENTORY_FOLDER_NAME + "validationErrorEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, INVENTORY_APP_NAME, null), INVENTORY_FOLDER_NAME + "dailyEmailBody.html"),

        // Malware detection
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, MALWARE_APP_NAME, "detected-malware"), MALWARE_FOLDER_NAME + "detectedMalwareBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, MALWARE_APP_NAME, "detected-malware"), MALWARE_FOLDER_NAME + "detectedMalwareInstantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, MALWARE_APP_NAME, "detected-malware"), MALWARE_FOLDER_NAME + "detectedMalwareInstantEmailBody.html"),

        // Patch
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, PATCH_APP_NAME, "new-advisory"), PATCH_FOLDER_NAME + "newAdvisoriesBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, PATCH_APP_NAME, "new-advisory"), PATCH_FOLDER_NAME + "newAdvisoriesInstantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, PATCH_APP_NAME, "new-advisory"), PATCH_FOLDER_NAME + "newAdvisoriesInstantEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, PATCH_APP_NAME, null), PATCH_FOLDER_NAME + "dailyEmailBody.html"),

        // Policies
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, POLICY_APP_NAME, "policy-triggered"), POLICY_FOLDER_NAME + "policyTriggeredBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, POLICY_APP_NAME, "policy-triggered"), POLICY_FOLDER_NAME + "instantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, POLICY_APP_NAME, "policy-triggered"), POLICY_FOLDER_NAME + "instantEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, POLICY_APP_NAME, null), POLICY_FOLDER_NAME + "dailyEmailBody.html"),

        // Resource optimization
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, RESOURCE_OPTIMIZATION_APP_NAME, null), RESOURCE_OPTIMIZATION_FOLDER_NAME + "dailyEmailBody.html"),

        // Task
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, TASK_APP_NAME, "executed-task-completed"), TASK_FOLDER_NAME + "executedTaskCompletedEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, TASK_APP_NAME, "executed-task-completed"), TASK_FOLDER_NAME + "executedTaskCompletedEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, TASK_APP_NAME, "job-failed"), TASK_FOLDER_NAME + "jobFailedEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, TASK_APP_NAME, "job-failed"), TASK_FOLDER_NAME + "jobFailedEmailBody.html"),

        // Vulnerability
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, VULNERABILITY_APP_NAME, "any-cve-known-exploit"), VULNERABILITY_FOLDER_NAME + "anyCveKnownExploitBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, VULNERABILITY_APP_NAME, "any-cve-known-exploit"), VULNERABILITY_FOLDER_NAME + "anyCveKnownExploitEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, VULNERABILITY_APP_NAME, "any-cve-known-exploit"), VULNERABILITY_FOLDER_NAME + "anyCveKnownExploitEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, VULNERABILITY_APP_NAME, "new-cve-severity"), VULNERABILITY_FOLDER_NAME + "newCveCritSeverityBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, VULNERABILITY_APP_NAME, "new-cve-severity"), VULNERABILITY_FOLDER_NAME + "newCveCritSeverityEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, VULNERABILITY_APP_NAME, "new-cve-severity"), VULNERABILITY_FOLDER_NAME + "newCveCritSeverityEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, VULNERABILITY_APP_NAME, "new-cve-cvss"), VULNERABILITY_FOLDER_NAME + "newCveHighCvssBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, VULNERABILITY_APP_NAME, "new-cve-cvss"), VULNERABILITY_FOLDER_NAME + "newCveHighCvssEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, VULNERABILITY_APP_NAME, "new-cve-cvss"), VULNERABILITY_FOLDER_NAME + "newCveHighCvssEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, VULNERABILITY_APP_NAME, "new-cve-security-rule"), VULNERABILITY_FOLDER_NAME + "newCveSecurityRuleBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, VULNERABILITY_APP_NAME, "new-cve-security-rule"), VULNERABILITY_FOLDER_NAME + "newCveSecurityRuleEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, VULNERABILITY_APP_NAME, "new-cve-security-rule"), VULNERABILITY_FOLDER_NAME + "newCveSecurityRuleEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, VULNERABILITY_APP_NAME, null), VULNERABILITY_FOLDER_NAME + "dailyEmailBody.html")
    );
}
