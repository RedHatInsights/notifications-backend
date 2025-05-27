package com.redhat.cloud.notifications.qute.templates.mapping;

import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import java.util.Map;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.*;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_APP_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CAPACITY_MANAGEMENT;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CLUSTER_ACCESS;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CLUSTER_ADD_ON;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CLUSTER_CONFIGURATION;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CLUSTER_LIFECYCLE;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CLUSTER_NETWORKING;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CLUSTER_OWNERSHIP;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CLUSTER_SCALING;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CLUSTER_SECURITY;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CLUSTER_SUBSCRIPTION;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CLUSTER_UPDATE;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CUSTOMER_SUPPORT;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_FOLDER_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_GENERAL_NOTIFICATION;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.ADVISOR_APP_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.ADVISOR_FOLDER_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.COMPLIANCE_APP_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.COMPLIANCE_FOLDER_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.INVENTORY_APP_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.INVENTORY_FOLDER_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.PATCH_APP_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.PATCH_FOLDER_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.POLICIES_APP_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.POLICY_FOLDER_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.RESOURCE_OPTIMIZATION_APP_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.RESOURCE_OPTIMIZATION_FOLDER_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.VULNERABILITY_APP_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.VULNERABILITY_FOLDER_NAME;
import static java.util.Map.entry;

public class SecureEmailTemplates {

    static final String SECURE_FOLDER_NAME = "Secure/";

    public static final Map<TemplateDefinition, String> templatesMap = Map.ofEntries(
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, Rhel.BUNDLE_NAME, ADVISOR_APP_NAME, null), SECURE_FOLDER_NAME + ADVISOR_FOLDER_NAME + "dailyEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, Rhel.BUNDLE_NAME, COMPLIANCE_APP_NAME, null), SECURE_FOLDER_NAME + COMPLIANCE_FOLDER_NAME + "dailyEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, Rhel.BUNDLE_NAME, INVENTORY_APP_NAME, null), SECURE_FOLDER_NAME + INVENTORY_FOLDER_NAME + "dailyEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, Rhel.BUNDLE_NAME, PATCH_APP_NAME, null), SECURE_FOLDER_NAME + PATCH_FOLDER_NAME + "dailyEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, Rhel.BUNDLE_NAME, POLICIES_APP_NAME, null), SECURE_FOLDER_NAME + POLICY_FOLDER_NAME + "dailyEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, Rhel.BUNDLE_NAME, RESOURCE_OPTIMIZATION_APP_NAME, null), SECURE_FOLDER_NAME + RESOURCE_OPTIMIZATION_FOLDER_NAME + "dailyEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BODY, Rhel.BUNDLE_NAME, VULNERABILITY_APP_NAME, null), SECURE_FOLDER_NAME + VULNERABILITY_FOLDER_NAME + "dailyEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_DAILY_DIGEST_BUNDLE_AGGREGATION_BODY, null, null, null), SECURE_FOLDER_NAME + "Common/insightsDailyEmailBody.html"),

        // Cluster manager
        entry(new TemplateDefinition(EMAIL_TITLE, OpenShift.BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, null), SECURE_FOLDER_NAME + CLUSTER_MANAGER_FOLDER_NAME + "instantEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, OpenShift.BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CLUSTER_UPDATE), SECURE_FOLDER_NAME + CLUSTER_MANAGER_FOLDER_NAME + "clusterUpdateInstantEmailBody.html"),
        /*entry(new TemplateDefinition(EMAIL_BODY, OpenShift.BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CLUSTER_LIFECYCLE), SECURE_FOLDER_NAME + CLUSTER_MANAGER_FOLDER_NAME + "clusterLifecycleInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, OpenShift.BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CLUSTER_CONFIGURATION), SECURE_FOLDER_NAME + CLUSTER_MANAGER_FOLDER_NAME + "clusterConfigurationInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, OpenShift.BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CLUSTER_SUBSCRIPTION), SECURE_FOLDER_NAME + CLUSTER_MANAGER_FOLDER_NAME + "clusterSubscriptionInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, OpenShift.BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CLUSTER_OWNERSHIP), SECURE_FOLDER_NAME + CLUSTER_MANAGER_FOLDER_NAME + "clusterOwnershipInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, OpenShift.BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CLUSTER_ACCESS), SECURE_FOLDER_NAME + CLUSTER_MANAGER_FOLDER_NAME + "clusterAccessInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, OpenShift.BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CLUSTER_SCALING), SECURE_FOLDER_NAME + CLUSTER_MANAGER_FOLDER_NAME + "clusterScalingInstantEmailBody.html"),*/
        entry(new TemplateDefinition(EMAIL_BODY, OpenShift.BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CAPACITY_MANAGEMENT), SECURE_FOLDER_NAME + CLUSTER_MANAGER_FOLDER_NAME + "capacityManagementInstantEmailBody.html")/*,
        entry(new TemplateDefinition(EMAIL_BODY, OpenShift.BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CLUSTER_SECURITY), SECURE_FOLDER_NAME + CLUSTER_MANAGER_FOLDER_NAME + "clusterSecurityInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, OpenShift.BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CLUSTER_ADD_ON), SECURE_FOLDER_NAME + CLUSTER_MANAGER_FOLDER_NAME + "clusterAddOnInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, OpenShift.BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CUSTOMER_SUPPORT), SECURE_FOLDER_NAME + CLUSTER_MANAGER_FOLDER_NAME + "customerSupportInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, OpenShift.BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_CLUSTER_NETWORKING), SECURE_FOLDER_NAME + CLUSTER_MANAGER_FOLDER_NAME + "clusterNetworkingInstantEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_BODY, OpenShift.BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, CLUSTER_MANAGER_GENERAL_NOTIFICATION), SECURE_FOLDER_NAME + CLUSTER_MANAGER_FOLDER_NAME + "generalNotificationInstantEmailBody.html")*/

        );
}
