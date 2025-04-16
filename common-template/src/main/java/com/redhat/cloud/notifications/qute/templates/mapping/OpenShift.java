package com.redhat.cloud.notifications.qute.templates.mapping;

import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import java.util.Map;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.DRAWER;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.EMAIL_BODY;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.EMAIL_TITLE;
import static java.util.Map.entry;

public class OpenShift {
    static final String BUNDLE_NAME = "openshift";

    static final String ADVISOR_APP_NAME = "advisor";
    static final String ADVISOR_FOLDER_NAME = "AdvisorOpenshift/";

    static final String COST_MANAGEMENT_APP_NAME = "cost-management";
    static final String COST_MANAGEMENT_FOLDER_NAME = "CostManagement/";

    static final String CLUSTER_MANAGER_APP_NAME = "cluster-manager";
    static final String CLUSTER_MANAGER_FOLDER_NAME = "OCM/";


    public static final Map<TemplateDefinition, String> templatesMap = Map.ofEntries(
        // Advisor
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, ADVISOR_APP_NAME, "new-recommendation"), ADVISOR_FOLDER_NAME + "newRecommendationBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, ADVISOR_APP_NAME, "new-recommendation"), ADVISOR_FOLDER_NAME + "newRecommendationInstantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, ADVISOR_APP_NAME, "new-recommendation"), ADVISOR_FOLDER_NAME + "newRecommendationInstantEmailBody.html"),

        // Cost Management
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "missing-cost-model"), COST_MANAGEMENT_FOLDER_NAME + "MissingCostModelBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "missing-cost-model"), COST_MANAGEMENT_FOLDER_NAME + "MissingCostModelEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "missing-cost-model"), COST_MANAGEMENT_FOLDER_NAME + "MissingCostModelEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cost-model-create"), COST_MANAGEMENT_FOLDER_NAME + "CostModelCreateBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cost-model-create"), COST_MANAGEMENT_FOLDER_NAME + "CostModelCreateEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cost-model-create"), COST_MANAGEMENT_FOLDER_NAME + "CostModelCreateEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cost-model-update"), COST_MANAGEMENT_FOLDER_NAME + "CostModelUpdateBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cost-model-update"), COST_MANAGEMENT_FOLDER_NAME + "CostModelUpdateEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cost-model-update"), COST_MANAGEMENT_FOLDER_NAME + "CostModelUpdateEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cost-model-remove"), COST_MANAGEMENT_FOLDER_NAME + "CostModelRemoveBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cost-model-remove"), COST_MANAGEMENT_FOLDER_NAME + "CostModelRemoveEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cost-model-remove"), COST_MANAGEMENT_FOLDER_NAME + "CostModelRemoveEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cm-operator-stale"), COST_MANAGEMENT_FOLDER_NAME + "CmOperatorStaleBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cm-operator-stale"), COST_MANAGEMENT_FOLDER_NAME + "CmOperatorStaleEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cm-operator-stale"), COST_MANAGEMENT_FOLDER_NAME + "CmOperatorStaleEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cm-operator-data-received"), COST_MANAGEMENT_FOLDER_NAME + "CmOperatorDataReceivedBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cm-operator-data-received"), COST_MANAGEMENT_FOLDER_NAME + "CmOperatorDataReceivedEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cm-operator-data-received"), COST_MANAGEMENT_FOLDER_NAME + "CmOperatorDataReceivedEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cm-operator-data-processed"), COST_MANAGEMENT_FOLDER_NAME + "CmOperatorDataProcessedBody.md"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cm-operator-data-processed"), COST_MANAGEMENT_FOLDER_NAME + "CmOperatorDataProcessedEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, "cm-operator-data-processed"), COST_MANAGEMENT_FOLDER_NAME + "CmOperatorDataProcessedEmailBody.html"),

        // Cluster manager
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "cluster-update"), CLUSTER_MANAGER_FOLDER_NAME + "clusterUpdateInstantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "cluster-update"), CLUSTER_MANAGER_FOLDER_NAME + "clusterUpdateInstantEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "cluster-lifecycle"), CLUSTER_MANAGER_FOLDER_NAME + "clusterLifecycleInstantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "cluster-lifecycle"), CLUSTER_MANAGER_FOLDER_NAME + "clusterLifecycleInstantEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "cluster-configuration"), CLUSTER_MANAGER_FOLDER_NAME + "clusterConfigurationInstantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "cluster-configuration"), CLUSTER_MANAGER_FOLDER_NAME + "clusterConfigurationInstantEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "cluster-subscription"), CLUSTER_MANAGER_FOLDER_NAME + "clusterSubscriptionInstantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "cluster-subscription"), CLUSTER_MANAGER_FOLDER_NAME + "clusterSubscriptionInstantEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "cluster-ownership"), CLUSTER_MANAGER_FOLDER_NAME + "clusterOwnershipInstantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "cluster-ownership"), CLUSTER_MANAGER_FOLDER_NAME + "clusterOwnershipInstantEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "cluster-access"), CLUSTER_MANAGER_FOLDER_NAME + "clusterAccessInstantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "cluster-access"), CLUSTER_MANAGER_FOLDER_NAME + "clusterAccessInstantEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "cluster-scaling"), CLUSTER_MANAGER_FOLDER_NAME + "clusterScalingInstantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "cluster-scaling"), CLUSTER_MANAGER_FOLDER_NAME + "clusterScalingInstantEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "capacity-management"), CLUSTER_MANAGER_FOLDER_NAME + "capacityManagementInstantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "capacity-management"), CLUSTER_MANAGER_FOLDER_NAME + "capacityManagementInstantEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "cluster-security"), CLUSTER_MANAGER_FOLDER_NAME + "clusterSecurityInstantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "cluster-security"), CLUSTER_MANAGER_FOLDER_NAME + "clusterSecurityInstantEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "cluster-add-on"), CLUSTER_MANAGER_FOLDER_NAME + "clusterAddOnInstantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "cluster-add-on"), CLUSTER_MANAGER_FOLDER_NAME + "clusterAddOnInstantEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "customer-support"), CLUSTER_MANAGER_FOLDER_NAME + "customerSupportInstantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "customer-support"), CLUSTER_MANAGER_FOLDER_NAME + "customerSupportInstantEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "cluster-networking"), CLUSTER_MANAGER_FOLDER_NAME + "clusterNetworkingInstantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "cluster-networking"), CLUSTER_MANAGER_FOLDER_NAME + "clusterNetworkingInstantEmailBody.html"),

        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "general-notification"), CLUSTER_MANAGER_FOLDER_NAME + "generalNotificationInstantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, "general-notification"), CLUSTER_MANAGER_FOLDER_NAME + "generalNotificationInstantEmailBody.html")
    );
}
