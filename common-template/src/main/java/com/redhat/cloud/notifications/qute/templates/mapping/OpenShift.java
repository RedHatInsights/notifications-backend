package com.redhat.cloud.notifications.qute.templates.mapping;

import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import java.util.Map;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.DRAWER;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.EMAIL_BODY;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.EMAIL_TITLE;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.GOOGLE_CHAT;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.MS_TEAMS;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.SLACK;
import static java.util.Map.entry;

public class OpenShift {
    public static final String BUNDLE_NAME = "openshift";

    static final String ADVISOR_APP_NAME = "advisor";
    static final String ADVISOR_FOLDER_NAME = "AdvisorOpenshift/";

    static final String COST_MANAGEMENT_APP_NAME = "cost-management";
    static final String COST_MANAGEMENT_FOLDER_NAME = "CostManagement/";

    public static final String CLUSTER_MANAGER_APP_NAME = "cluster-manager";
    static final String CLUSTER_MANAGER_FOLDER_NAME = "OCM/";

    public static final String ADVISOR_NEW_RECOMMENDATION = "new-recommendation";

    public static final String COST_MANAGEMENT_MISSING_COST_MODEL = "missing-cost-model";
    public static final String COST_MANAGEMENT_COST_MODEL_CREATE = "cost-model-create";
    public static final String COST_MANAGEMENT_COST_MODEL_UPDATE = "cost-model-update";
    public static final String COST_MANAGEMENT_COST_MODEL_REMOVE = "cost-model-remove";
    public static final String COST_MANAGEMENT_CM_OPERATOR_STALE = "cm-operator-stale";
    public static final String COST_MANAGEMENT_CM_OPERATOR_DATA_RECEIVED = "cm-operator-data-received";
    public static final String COST_MANAGEMENT_CM_OPERATOR_DATA_PROCESSED = "cm-operator-data-processed";

    public static final String CLUSTER_MANAGER_CLUSTER_UPDATE = "cluster-update";
    public static final String CLUSTER_MANAGER_CLUSTER_LIFECYCLE = "cluster-lifecycle";
    public static final String CLUSTER_MANAGER_CLUSTER_CONFIGURATION = "cluster-configuration";
    public static final String CLUSTER_MANAGER_CLUSTER_SUBSCRIPTION = "cluster-subscription";
    public static final String CLUSTER_MANAGER_CLUSTER_OWNERSHIP = "cluster-ownership";
    public static final String CLUSTER_MANAGER_CLUSTER_ACCESS = "cluster-access";
    public static final String CLUSTER_MANAGER_CLUSTER_SCALING = "cluster-scaling";
    public static final String CLUSTER_MANAGER_CAPACITY_MANAGEMENT = "capacity-management";
    public static final String CLUSTER_MANAGER_CLUSTER_SECURITY = "cluster-security";
    public static final String CLUSTER_MANAGER_CLUSTER_ADD_ON = "cluster-add-on";
    public static final String CLUSTER_MANAGER_CUSTOMER_SUPPORT = "customer-support";
    public static final String CLUSTER_MANAGER_CLUSTER_NETWORKING = "cluster-networking";
    public static final String CLUSTER_MANAGER_GENERAL_NOTIFICATION = "general-notification";

    public static final Map<TemplateDefinition, String> templatesMap = Map.ofEntries(
        // Advisor
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, ADVISOR_APP_NAME, ADVISOR_NEW_RECOMMENDATION), ADVISOR_FOLDER_NAME + "newRecommendationBody.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, ADVISOR_APP_NAME, ADVISOR_NEW_RECOMMENDATION), ADVISOR_FOLDER_NAME + "newRecommendationInstantEmailBody.html"),

        // Cost Management
        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, COST_MANAGEMENT_MISSING_COST_MODEL), COST_MANAGEMENT_FOLDER_NAME + "MissingCostModelBody.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, COST_MANAGEMENT_MISSING_COST_MODEL), COST_MANAGEMENT_FOLDER_NAME + "MissingCostModelEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, COST_MANAGEMENT_COST_MODEL_CREATE), COST_MANAGEMENT_FOLDER_NAME + "CostModelCreateBody.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, COST_MANAGEMENT_COST_MODEL_CREATE), COST_MANAGEMENT_FOLDER_NAME + "CostModelCreateEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, COST_MANAGEMENT_COST_MODEL_UPDATE), COST_MANAGEMENT_FOLDER_NAME + "CostModelUpdateBody.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, COST_MANAGEMENT_COST_MODEL_UPDATE), COST_MANAGEMENT_FOLDER_NAME + "CostModelUpdateEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, COST_MANAGEMENT_COST_MODEL_REMOVE), COST_MANAGEMENT_FOLDER_NAME + "CostModelRemoveBody.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, COST_MANAGEMENT_COST_MODEL_REMOVE), COST_MANAGEMENT_FOLDER_NAME + "CostModelRemoveEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, COST_MANAGEMENT_CM_OPERATOR_STALE), COST_MANAGEMENT_FOLDER_NAME + "CmOperatorStaleBody.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, COST_MANAGEMENT_CM_OPERATOR_STALE), COST_MANAGEMENT_FOLDER_NAME + "CmOperatorStaleEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, COST_MANAGEMENT_CM_OPERATOR_DATA_RECEIVED), COST_MANAGEMENT_FOLDER_NAME + "CmOperatorDataReceivedBody.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, COST_MANAGEMENT_CM_OPERATOR_DATA_RECEIVED), COST_MANAGEMENT_FOLDER_NAME + "CmOperatorDataReceivedEmailBody.html"),

        entry(new TemplateDefinition(DRAWER, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, COST_MANAGEMENT_CM_OPERATOR_DATA_PROCESSED), COST_MANAGEMENT_FOLDER_NAME + "CmOperatorDataProcessedBody.md"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, COST_MANAGEMENT_APP_NAME, COST_MANAGEMENT_CM_OPERATOR_DATA_PROCESSED), COST_MANAGEMENT_FOLDER_NAME + "CmOperatorDataProcessedEmailBody.html"),

        // Cluster manager
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, null), CLUSTER_MANAGER_FOLDER_NAME + "instantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CLUSTER_UPDATE), CLUSTER_MANAGER_FOLDER_NAME + "clusterUpdateInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CLUSTER_LIFECYCLE), CLUSTER_MANAGER_FOLDER_NAME + "clusterLifecycleInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CLUSTER_CONFIGURATION), CLUSTER_MANAGER_FOLDER_NAME + "clusterConfigurationInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CLUSTER_SUBSCRIPTION), CLUSTER_MANAGER_FOLDER_NAME + "clusterSubscriptionInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CLUSTER_OWNERSHIP), CLUSTER_MANAGER_FOLDER_NAME + "clusterOwnershipInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CLUSTER_ACCESS), CLUSTER_MANAGER_FOLDER_NAME + "clusterAccessInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CLUSTER_SCALING), CLUSTER_MANAGER_FOLDER_NAME + "clusterScalingInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CAPACITY_MANAGEMENT), CLUSTER_MANAGER_FOLDER_NAME + "capacityManagementInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CLUSTER_SECURITY), CLUSTER_MANAGER_FOLDER_NAME + "clusterSecurityInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CLUSTER_ADD_ON), CLUSTER_MANAGER_FOLDER_NAME + "clusterAddOnInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CUSTOMER_SUPPORT), CLUSTER_MANAGER_FOLDER_NAME + "customerSupportInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CLUSTER_NETWORKING), CLUSTER_MANAGER_FOLDER_NAME + "clusterNetworkingInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_GENERAL_NOTIFICATION), CLUSTER_MANAGER_FOLDER_NAME + "generalNotificationInstantEmailBody.html"),

        entry(new TemplateDefinition(GOOGLE_CHAT, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, null), CLUSTER_MANAGER_FOLDER_NAME + "ocmDefault.json"),
        entry(new TemplateDefinition(MS_TEAMS, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, null), CLUSTER_MANAGER_FOLDER_NAME + "ocmDefault.json"),
        entry(new TemplateDefinition(SLACK, BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, null), CLUSTER_MANAGER_FOLDER_NAME + "ocmDefault.md")
    );
}
