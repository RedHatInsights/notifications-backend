package com.redhat.cloud.notifications.qute.templates.mapping;

import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import java.util.Map;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.DRAWER;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.EMAIL_BODY;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.EMAIL_DAILY_DIGEST_BODY;
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

    public static final String POLICIES_APP_NAME = "policies";
    public static final String POLICY_FOLDER_NAME = "Policies/";

    public static final String RESOURCE_OPTIMIZATION_APP_NAME = "resource-optimization";
    public static final String RESOURCE_OPTIMIZATION_FOLDER_NAME = "ResourceOptimization/";

    static final String TASK_APP_NAME = "tasks";
    static final String TASK_FOLDER_NAME = "Tasks/";

    public static final String VULNERABILITY_APP_NAME = "vulnerability";
    public static final String VULNERABILITY_FOLDER_NAME = "Vulnerability/";

    public static final String ADVISOR_DEACTIVATED_RECOMMENDATION = "deactivated-recommendation";
    public static final String ADVISOR_NEW_RECOMMENDATION = "new-recommendation";
    public static final String ADVISOR_RESOLVED_RECOMMENDATION = "resolved-recommendation";

    public static final String COMPLIANCE_COMPLIANCE_BELOW_THRESHOLD = "compliance-below-threshold";
    public static final String COMPLIANCE_REPORT_UPLOAD_FAILED = "report-upload-failed";

    public static final String EDGE_IMAGE_CREATION = "image-creation";
    public static final String EDGE_UPDATE_DEVICES = "update-devices";

    public static final String IMAGE_BUILDER_LAUNCH_SUCCESS = "launch-success";
    public static final String IMAGE_BUILDER_LAUNCH_FAILED = "launch-failed";

    public static final String INVENTORY_NEW_SYSTEM_REGISTERED = "new-system-registered";
    public static final String INVENTORY_SYSTEM_BECAME_STALE = "system-became-stale";
    public static final String INVENTORY_SYSTEM_DELETED = "system-deleted";
    public static final String INVENTORY_VALIDATION_ERROR = "validation-error";

    public static final String MALWARE_DETECTED_MALWARE = "detected-malware";

    public static final String PATCH_NEW_ADVISORY = "new-advisory";

    public static final String POLICIES_POLICY_TRIGGERED = "policy-triggered";

    public static final String TASK_EXECUTED_TASK_COMPLETED = "executed-task-completed";
    public static final String TASK_JOB_FAILED = "job-failed";

    public static final String VULNERABILITY_ANY_CVE_KNOWN_EXPLOIT = "any-cve-known-exploit";
    public static final String VULNERABILITY_NEW_CVE_SEVERITY = "new-cve-severity";
    public static final String VULNERABILITY_NEW_CVE_CVSS = "new-cve-cvss";
    public static final String VULNERABILITY_NEW_CVE_SECURITY_RULE = "new-cve-security-rule";

    public static final Map<TemplateDefinition, String> templatesMap = Map.ofEntries(
        // Advisor
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, ADVISOR_APP_NAME, ADVISOR_DEACTIVATED_RECOMMENDATION), ADVISOR_FOLDER_NAME + "deactivatedRecommendationBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, ADVISOR_APP_NAME, ADVISOR_NEW_RECOMMENDATION), ADVISOR_FOLDER_NAME + "newRecommendationBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, ADVISOR_APP_NAME, ADVISOR_RESOLVED_RECOMMENDATION), ADVISOR_FOLDER_NAME + "resolvedRecommendationBody.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, ADVISOR_APP_NAME, ADVISOR_DEACTIVATED_RECOMMENDATION), ADVISOR_FOLDER_NAME + "deactivatedRecommendationInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, ADVISOR_APP_NAME, ADVISOR_NEW_RECOMMENDATION), ADVISOR_FOLDER_NAME + "newRecommendationInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, ADVISOR_APP_NAME, ADVISOR_RESOLVED_RECOMMENDATION), ADVISOR_FOLDER_NAME + "resolvedRecommendationInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, ADVISOR_APP_NAME, null), ADVISOR_FOLDER_NAME + "dailyEmailBody.html"),

        // Compliance
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COMPLIANCE_APP_NAME, COMPLIANCE_COMPLIANCE_BELOW_THRESHOLD), COMPLIANCE_FOLDER_NAME + "belowThresholdBody.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, COMPLIANCE_APP_NAME, COMPLIANCE_COMPLIANCE_BELOW_THRESHOLD), COMPLIANCE_FOLDER_NAME + "complianceBelowThresholdEmailBody.html"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COMPLIANCE_APP_NAME, COMPLIANCE_REPORT_UPLOAD_FAILED), COMPLIANCE_FOLDER_NAME + "reportUploadFailedBody.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, COMPLIANCE_APP_NAME, COMPLIANCE_REPORT_UPLOAD_FAILED), COMPLIANCE_FOLDER_NAME + "reportUploadFailedEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, COMPLIANCE_APP_NAME, null), COMPLIANCE_FOLDER_NAME + "dailyEmailBody.html"),

        // Edge management
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, EDGE_APP_NAME, EDGE_IMAGE_CREATION), EDGE_FOLDER_NAME + "imageCreationBody.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, EDGE_APP_NAME, EDGE_IMAGE_CREATION), EDGE_FOLDER_NAME + "imageCreationEmailBody.html"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, EDGE_APP_NAME, EDGE_UPDATE_DEVICES), EDGE_FOLDER_NAME + "updateDeviceBody.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, EDGE_APP_NAME, EDGE_UPDATE_DEVICES), EDGE_FOLDER_NAME + "updateDeviceEmailBody.html"),

        // Image builder
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, IMAGE_BUILDER_APP_NAME, IMAGE_BUILDER_LAUNCH_SUCCESS), IMAGE_BUILDER_FOLDER_NAME + "launchSuccessInstantEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, IMAGE_BUILDER_APP_NAME, IMAGE_BUILDER_LAUNCH_FAILED), IMAGE_BUILDER_FOLDER_NAME + "launchFailedEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, IMAGE_BUILDER_APP_NAME, null), IMAGE_BUILDER_FOLDER_NAME + "dailyEmailBody.html"),

        // Inventory
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, INVENTORY_APP_NAME, INVENTORY_NEW_SYSTEM_REGISTERED), INVENTORY_FOLDER_NAME + "newSystemRegisteredEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, INVENTORY_APP_NAME, INVENTORY_SYSTEM_BECAME_STALE), INVENTORY_FOLDER_NAME + "systemBecameStaleBody.html"),

        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, INVENTORY_APP_NAME, INVENTORY_SYSTEM_DELETED), INVENTORY_FOLDER_NAME + "systemDeletedEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, INVENTORY_APP_NAME, INVENTORY_VALIDATION_ERROR), INVENTORY_FOLDER_NAME + "validationErrorBody.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, INVENTORY_APP_NAME, INVENTORY_VALIDATION_ERROR), INVENTORY_FOLDER_NAME + "validationErrorEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, INVENTORY_APP_NAME, null), INVENTORY_FOLDER_NAME + "dailyEmailBody.html"),

        // Malware detection
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, MALWARE_APP_NAME, MALWARE_DETECTED_MALWARE), MALWARE_FOLDER_NAME + "detectedMalwareBody.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, MALWARE_APP_NAME, MALWARE_DETECTED_MALWARE), MALWARE_FOLDER_NAME + "detectedMalwareInstantEmailBody.html"),

        // Patch
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, PATCH_APP_NAME, PATCH_NEW_ADVISORY), PATCH_FOLDER_NAME + "newAdvisoriesBody.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, PATCH_APP_NAME, PATCH_NEW_ADVISORY), PATCH_FOLDER_NAME + "newAdvisoriesInstantEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, PATCH_APP_NAME, null), PATCH_FOLDER_NAME + "dailyEmailBody.html"),

        // Policies
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, POLICIES_APP_NAME, POLICIES_POLICY_TRIGGERED), POLICY_FOLDER_NAME + "policyTriggeredBody.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, POLICIES_APP_NAME, POLICIES_POLICY_TRIGGERED), POLICY_FOLDER_NAME + "instantEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, POLICIES_APP_NAME, null), POLICY_FOLDER_NAME + "dailyEmailBody.html"),

        // Resource optimization
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, RESOURCE_OPTIMIZATION_APP_NAME, null), RESOURCE_OPTIMIZATION_FOLDER_NAME + "dailyEmailBody.html"),

        // Task
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, TASK_APP_NAME, TASK_EXECUTED_TASK_COMPLETED), TASK_FOLDER_NAME + "executedTaskCompletedEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, TASK_APP_NAME, TASK_JOB_FAILED), TASK_FOLDER_NAME + "jobFailedEmailBody.html"),

        // Vulnerability
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, VULNERABILITY_APP_NAME, VULNERABILITY_ANY_CVE_KNOWN_EXPLOIT), VULNERABILITY_FOLDER_NAME + "anyCveKnownExploitBody.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, VULNERABILITY_APP_NAME, VULNERABILITY_ANY_CVE_KNOWN_EXPLOIT), VULNERABILITY_FOLDER_NAME + "anyCveKnownExploitEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, VULNERABILITY_APP_NAME, VULNERABILITY_NEW_CVE_SEVERITY), VULNERABILITY_FOLDER_NAME + "newCveCritSeverityBody.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, VULNERABILITY_APP_NAME, VULNERABILITY_NEW_CVE_SEVERITY), VULNERABILITY_FOLDER_NAME + "newCveCritSeverityEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, VULNERABILITY_APP_NAME, VULNERABILITY_NEW_CVE_CVSS), VULNERABILITY_FOLDER_NAME + "newCveHighCvssBody.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, VULNERABILITY_APP_NAME, VULNERABILITY_NEW_CVE_CVSS), VULNERABILITY_FOLDER_NAME + "newCveHighCvssEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, VULNERABILITY_APP_NAME, VULNERABILITY_NEW_CVE_SECURITY_RULE), VULNERABILITY_FOLDER_NAME + "newCveSecurityRuleBody.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, VULNERABILITY_APP_NAME, VULNERABILITY_NEW_CVE_SECURITY_RULE), VULNERABILITY_FOLDER_NAME + "newCveSecurityRuleEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, BUNDLE_NAME, VULNERABILITY_APP_NAME, null), VULNERABILITY_FOLDER_NAME + "dailyEmailBody.html")
    );
}
