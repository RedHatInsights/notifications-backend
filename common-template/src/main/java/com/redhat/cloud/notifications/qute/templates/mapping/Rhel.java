package com.redhat.cloud.notifications.qute.templates.mapping;

import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import java.util.Map;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.DRAWER;
import static java.util.Map.entry;

public class Rhel {
    static final String BUNDLE_NAME = "rhel";

    static final String ADVISOR_APP_NAME = "advisor";
    static final String ADVISOR_FOLDER_NAME = "Advisor";

    static final String COMPLIANCE_APP_NAME = "compliance";
    static final String COMPLIANCE_FOLDER_NAME = "Compliance";

    static final String DRIFT_APP_NAME = "drift";
    static final String DRIFT_FOLDER_NAME = "Drift";

    static final String EDGE_APP_NAME = "edge-management";
    static final String EDGE_FOLDER_NAME = "EdgeManagement";

    static final String INVENTORY_APP_NAME = "inventory";
    static final String INVENTORY_FOLDER_NAME = "Inventory";

    static final String MALWARE_APP_NAME = "malware-detection";
    static final String MALWARE_FOLDER_NAME = "MalwareDetection";

    static final String PATCH_APP_NAME = "patch";
    static final String PATCH_FOLDER_NAME = "Patch";

    static final String POLICY_APP_NAME = "policies";
    static final String POLICY_FOLDER_NAME = "Policies";

    static final String VULNERABILITY_APP_NAME = "vulnerability";
    static final String VULNERABILITY_FOLDER_NAME = "Vulnerability";

    public static final Map<TemplateDefinition, String> templatesMap = Map.ofEntries(
        // Advisor
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, ADVISOR_APP_NAME, "deactivated-recommendation"), ADVISOR_FOLDER_NAME + "/deactivatedRecommendationBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, ADVISOR_APP_NAME, "new-recommendation"), ADVISOR_FOLDER_NAME + "/newRecommendationBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, ADVISOR_APP_NAME, "resolved-recommendation"), ADVISOR_FOLDER_NAME + "/resolvedRecommendationBody.md"),

        // Compliance
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COMPLIANCE_APP_NAME, "compliance-below-threshold"), COMPLIANCE_FOLDER_NAME + "/belowThresholdBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COMPLIANCE_APP_NAME, "report-upload-failed"), COMPLIANCE_FOLDER_NAME + "/reportUploadFailedBody.md"),

        // Drift
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, DRIFT_APP_NAME, "drift-baseline-detected"), DRIFT_FOLDER_NAME + "/newBaselineDriftBody.md"),

        // Edge management
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, EDGE_APP_NAME, "image-creation"), EDGE_FOLDER_NAME + "/imageCreationBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, EDGE_APP_NAME, "update-devices"), EDGE_FOLDER_NAME + "/updateDeviceBody.md"),

        // Inventory
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, INVENTORY_APP_NAME, "validation-error"), INVENTORY_FOLDER_NAME + "/validationErrorBody.md"),

        // Malware detection
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, MALWARE_APP_NAME, "detected-malware"), MALWARE_FOLDER_NAME + "/detectedMalwareBody.md"),

        // Patch
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, PATCH_APP_NAME, "new-advisory"), PATCH_FOLDER_NAME + "/newAdvisoriesBody.md"),

        // Policies
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, POLICY_APP_NAME, "policy-triggered"), POLICY_FOLDER_NAME + "/instantBody.md"),

        // Vulnerability
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, VULNERABILITY_APP_NAME, "any-cve-known-exploit"), VULNERABILITY_FOLDER_NAME + "/anyCveKnownExploitBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, VULNERABILITY_APP_NAME, "new-cve-severity"), VULNERABILITY_FOLDER_NAME + "/newCveCritSeverityBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, VULNERABILITY_APP_NAME, "new-cve-cvss"), VULNERABILITY_FOLDER_NAME + "/newCveHighCvssBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, VULNERABILITY_APP_NAME, "new-cve-security-rule"), VULNERABILITY_FOLDER_NAME + "/newCveSecurityRuleBody.md")
    );
}
