package com.redhat.cloud.notifications.qute.templates.mapping;

import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import java.util.Map;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.DRAWER;
import static java.util.Map.entry;

public class OpenShift {
    static final String BUNDLE_NAME = "openshift";

    static final String ADVISOR_APP_NAME = "advisor";
    static final String ADVISOR_FOLDER_NAME = "AdvisorOpenShift/";

    static final String COST_MANAGEMENT_APP_NAME = "cost-management";
    static final String COST_MANAGEMENT_FOLDER_NAME = "CostManagement/";

    public static final Map<TemplateDefinition, String> templatesMap = Map.ofEntries(
        // Advisor
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, ADVISOR_APP_NAME, "new-recommendation"), ADVISOR_FOLDER_NAME + "newRecommendationBody.md"),

        // Cost Management
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "missing-cost-model"), COST_MANAGEMENT_FOLDER_NAME + "MissingCostModelBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cost-model-create"), COST_MANAGEMENT_FOLDER_NAME + "CostModelCreateBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cost-model-update"), COST_MANAGEMENT_FOLDER_NAME + "CostModelUpdateBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cost-model-remove"), COST_MANAGEMENT_FOLDER_NAME + "CostModelRemoveBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cm-operator-stale"), COST_MANAGEMENT_FOLDER_NAME + "CmOperatorStaleBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cm-operator-data-received"), COST_MANAGEMENT_FOLDER_NAME + "CmOperatorDataReceivedBody.md"),
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cm-operator-data-processed"), COST_MANAGEMENT_FOLDER_NAME + "CmOperatorDataProcessedBody.md")
    );
}
