package com.redhat.cloud.notifications.templates;

import com.cronutils.utils.StringUtils;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.Template;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.events.ConnectorReceiver.INTEGRATION_FAILED_EVENT_TYPE;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.INTEGRATION_DISABLED_EVENT_TYPE;
import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.DEACTIVATED_RECOMMENDATION;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.NEW_RECOMMENDATION;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.RESOLVED_RECOMMENDATION;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.nio.charset.StandardCharsets.UTF_8;

// TODO NOTIF-484 Remove this class when the templates DB migration is finished.
@Path(API_INTERNAL + "/template-engine/migrate")
public class EmailTemplateMigrationService {

    @Inject
    EntityManager entityManager;

    @Inject
    FeatureFlipper featureFlipper;

    /*
     * Templates from resources may evolve after the first migration to DB templates, before we enable DB templates
     * everywhere in notifications. This endpoint can be used to delete all DB templates before we trigger another
     * migration from resources to DB.
     */
    @DELETE
    @Transactional
    public void deleteAllTemplates() {
        entityManager.createQuery("DELETE FROM InstantEmailTemplate").executeUpdate();
        entityManager.createQuery("DELETE FROM AggregationEmailTemplate").executeUpdate();
        entityManager.createQuery("DELETE FROM Template where id not in (select theTemplate.id from IntegrationTemplate)").executeUpdate();
    }

    @PUT
    @Transactional
    @Produces(APPLICATION_JSON)
    public List<String> migrate() {
        List<String> warnings = new ArrayList<>();

        Log.debug("Migration starting");
        if (featureFlipper.isUseSecuredEmailTemplates()) {
            getOrCreateTemplate("Secure/Common/insightsEmailBody", "html", "Common Insights email body");
            createDailyEmailTemplate(
                warnings, "rhel", "advisor",
                "Secure/Advisor/dailyEmailTitle", "txt", "Advisor daily email title",
                "Secure/Advisor/dailyEmailBody", "html", "Advisor daily email body"
            );

            createDailyEmailTemplate(
                warnings, "rhel", "compliance",
                "Secure/Compliance/dailyEmailTitle", "txt", "Compliance daily email title",
                "Secure/Compliance/dailyEmailBody", "html", "Compliance daily email body"
            );
            createDailyEmailTemplate(
                warnings, "rhel", "drift",
                "Secure/Drift/dailyEmailTitle", "txt", "Drift daily email title",
                "Secure/Drift/dailyEmailBody", "html", "Drift daily email body"
            );
            createDailyEmailTemplate(
                warnings, "rhel", "inventory",
                "Secure/Inventory/dailyEmailTitle", "txt", "Inventory daily email title",
                "Secure/Inventory/dailyEmailBody", "html", "Inventory daily email body"
            );
            createDailyEmailTemplate(
                warnings, "rhel", "patch",
                "Secure/Patch/dailyEmailTitle", "txt", "Patch daily email title",
                "Secure/Patch/dailyEmailBody", "html", "Patch daily email body"
            );
            createDailyEmailTemplate(
                warnings, "rhel", "policies",
                "Secure/Policies/dailyEmailTitle", "txt", "Policies daily email title",
                "Secure/Policies/dailyEmailBody", "html", "Policies daily email body"
            );
            createDailyEmailTemplate(
                warnings, "rhel", "resource-optimization",
                "Secure/ResourceOptimization/dailyEmailTitle", "txt", "Resource Optimization daily email title",
                "Secure/ResourceOptimization/dailyEmailBody", "html", "Resource Optimization daily email body"
            );
            createDailyEmailTemplate(
                warnings, "rhel", "vulnerability",
                "Secure/Vulnerability/dailyEmailTitle", "txt", "Vulnerability daily email title",
                "Secure/Vulnerability/dailyEmailBody", "html", "Vulnerability daily email body"
            );
        } else {

            /*
             * Former src/main/resources/templates/Advisor folder.
             */
            createInstantEmailTemplate(
                warnings, "rhel", "advisor", List.of(DEACTIVATED_RECOMMENDATION),
                "Advisor/deactivatedRecommendationInstantEmailTitle", "txt", "Advisor deactivated recommendation email title",
                "Advisor/deactivatedRecommendationInstantEmailBody", "html", "Advisor deactivated recommendation email body"
            );
            createInstantEmailTemplate(
                warnings, "rhel", "advisor", List.of(NEW_RECOMMENDATION),
                "Advisor/newRecommendationInstantEmailTitle", "txt", "Advisor new recommendation email title",
                "Advisor/newRecommendationInstantEmailBody", "html", "Advisor new recommendation email body"
            );
            createInstantEmailTemplate(
                warnings, "rhel", "advisor", List.of(RESOLVED_RECOMMENDATION),
                "Advisor/resolvedRecommendationInstantEmailTitle", "txt", "Advisor resolved recommendation email title",
                "Advisor/resolvedRecommendationInstantEmailBody", "html", "Advisor resolved recommendation email body"
            );

            createDailyEmailTemplate(
                warnings, "rhel", "advisor",
                "Advisor/dailyEmailTitle", "txt", "Advisor daily email title",
                "Advisor/dailyEmailBody", "html", "Advisor daily email body"
            );

            /*
             * Former src/main/resources/templates/AdvisorOpenshift folder.
             */
            createInstantEmailTemplate(
                warnings, "openshift", "advisor", List.of("new-recommendation"),
                "AdvisorOpenshift/newRecommendationInstantEmailTitle", "txt", "AdvisorOpenshift new recommendation email title",
                "AdvisorOpenshift/newRecommendationInstantEmailBody", "html", "AdvisorOpenshift new recommendation email body"
            );

            /*
             * Former src/main/resources/templates/Ansible folder.
             */
            createInstantEmailTemplate(
                warnings, "ansible", "reports", List.of("report-available"),
                "Ansible/instantEmailTitle", "txt", "Ansible instant email title",
                "Ansible/instantEmailBody", "html", "Ansible instant email body"
            );

            /*
             * Former src/main/resources/templates/Compliance folder.
             */
            createInstantEmailTemplate(
                warnings, "rhel", "compliance", List.of("compliance-below-threshold"),
                "Compliance/complianceBelowThresholdEmailTitle", "txt", "Compliance below threshold email title",
                "Compliance/complianceBelowThresholdEmailBody", "html", "Compliance below threshold email body"
            );
            createInstantEmailTemplate(
                warnings, "rhel", "compliance", List.of("report-upload-failed"),
                "Compliance/reportUploadFailedEmailTitle", "txt", "Compliance report upload failed email title",
                "Compliance/reportUploadFailedEmailBody", "html", "Compliance report upload failed email body"
            );
            createDailyEmailTemplate(
                warnings, "rhel", "compliance",
                "Compliance/dailyEmailTitle", "txt", "Compliance daily email title",
                "Compliance/dailyEmailBody", "html", "Compliance daily email body"
            );

            /*
             * Former src/main/resources/templates/CostManagement folder.
             */
            createInstantEmailTemplate(
                warnings, "openshift", "cost-management", List.of("missing-cost-model"),
                "CostManagement/MissingCostModelEmailTitle", "txt", "Cost Management missing cost model email title",
                "CostManagement/MissingCostModelEmailBody", "html", "Cost Management missing cost model email body"
            );
            createInstantEmailTemplate(
                warnings, "openshift", "cost-management", List.of("cost-model-create"),
                "CostManagement/CostModelCreateEmailTitle", "txt", "Cost Management cost model create email title",
                "CostManagement/CostModelCreateEmailBody", "html", "Cost Management cost model create email body"
            );
            createInstantEmailTemplate(
                warnings, "openshift", "cost-management", List.of("cost-model-update"),
                "CostManagement/CostModelUpdateEmailTitle", "txt", "Cost Management cost model update email title",
                "CostManagement/CostModelUpdateEmailBody", "html", "Cost Management cost model update email body"
            );
            createInstantEmailTemplate(
                warnings, "openshift", "cost-management", List.of("cost-model-remove"),
                "CostManagement/CostModelRemoveEmailTitle", "txt", "Cost Management cost model remove email title",
                "CostManagement/CostModelRemoveEmailBody", "html", "Cost Management cost model remove email body"
            );
            createInstantEmailTemplate(
                warnings, "openshift", "cost-management", List.of("cm-operator-stale"),
                "CostManagement/CmOperatorStaleEmailTitle", "txt", "Cost Management operator stale email title",
                "CostManagement/CmOperatorStaleEmailBody", "html", "Cost Management operator stale email body"
            );
            createInstantEmailTemplate(
                warnings, "openshift", "cost-management", List.of("cm-operator-data-processed"),
                "CostManagement/CmOperatorDataProcessedEmailTitle", "txt", "Cost Management operator data processed email title",
                "CostManagement/CmOperatorDataProcessedEmailBody", "html", "Cost Management operator data processed email body"
            );
            createInstantEmailTemplate(
                warnings, "openshift", "cost-management", List.of("cm-operator-data-received"),
                "CostManagement/CmOperatorDataReceivedEmailTitle", "txt", "Cost Management operator data received email title",
                "CostManagement/CmOperatorDataReceivedEmailBody", "html", "Cost Management operator data received email body"
            );

            /*
             * Former src/main/resources/templates/Drift folder.
             */
            createInstantEmailTemplate(
                warnings, "rhel", "drift", List.of("drift-baseline-detected"),
                "Drift/newBaselineDriftInstantEmailTitle", "txt", "Drift new baseline drift email title",
                "Drift/newBaselineDriftInstantEmailBody", "html", "Drift new baseline drift email body"
            );
            createDailyEmailTemplate(
                warnings, "rhel", "drift",
                "Drift/dailyEmailTitle", "txt", "Drift daily email title",
                "Drift/dailyEmailBody", "html", "Drift daily email body"
            );

            /*
             * Former src/main/resources/templates/EdgeManagement folder.
             */
            createInstantEmailTemplate(
                warnings, "rhel", "edge-management", List.of("image-creation"),
                "EdgeManagement/imageCreationTitle", "txt", "EdgeManagement image creation email title",
                "EdgeManagement/imageCreationBody", "html", "EdgeManagement image creation email body"
            );
            createInstantEmailTemplate(
                warnings, "rhel", "edge-management", List.of("update-devices"),
                "EdgeManagement/updateDeviceTitle", "txt", "EdgeManagement update devices email title",
                "EdgeManagement/updateDeviceBody", "html", "EdgeManagement update devices email body"
            );

            /*
             * Former src/main/resources/templates/Inventory folder.
             */
            createInstantEmailTemplate(
                warnings, "rhel", "inventory", List.of("validation-error"),
                "Inventory/validationErrorEmailTitle", "txt", "Inventory instant email title",
                "Inventory/validationErrorEmailBody", "html", "Inventory instant email body"
            );
            createDailyEmailTemplate(
                warnings, "rhel", "inventory",
                "Inventory/dailyEmailTitle", "txt", "Inventory daily email title",
                "Inventory/dailyEmailBody", "html", "Inventory daily email body"
            );

            /*
             * Former src/main/resources/templates/Integrations folder.
             */
            createInstantEmailTemplate(
                warnings, "console", "integrations", List.of(INTEGRATION_FAILED_EVENT_TYPE),
                "Integrations/failedIntegrationTitle", "txt", "Integrations failed integration email title",
                "Integrations/failedIntegrationBody", "html", "Integrations failed integration email body"
            );
            createInstantEmailTemplate(
                warnings, "console", "integrations", List.of(INTEGRATION_DISABLED_EVENT_TYPE),
                "Integrations/integrationDisabledTitle", "txt", "Integrations disabled integration email title",
                "Integrations/integrationDisabledBody", "html", "Integrations disabled integration email body"
            );

            /*
             * Former src/main/resources/templates/MalwareDetection folder.
             */
            createInstantEmailTemplate(
                warnings, "rhel", "malware-detection", List.of("detected-malware"),
                "MalwareDetection/detectedMalwareInstantEmailTitle", "txt", "Malware Detection detected malware email title",
                "MalwareDetection/detectedMalwareInstantEmailBody", "html", "Malware Detection detected malware email body"
            );

            /*
             * Former src/main/resources/templates/OCM folder.
             */
            createInstantEmailTemplate(
                    warnings, "openshift", "ocm", List.of("severity-critical"),
                    "OCM/severityCriticalInstantEmailTitle", "txt", "OCM Service Logs critical severity email title",
                    "OCM/severityCriticalInstantEmailBody", "html", "OCM Service Logs critical severity email body"
            );
            createInstantEmailTemplate(
                    warnings, "openshift", "ocm", List.of("severity-debug"),
                    "OCM/severityDebugInstantEmailTitle", "txt", "OCM Service Logs debug severity email title",
                    "OCM/severityDebugInstantEmailBody", "html", "OCM Service Logs debug severity email body"
            );
            createInstantEmailTemplate(
                    warnings, "openshift", "ocm", List.of("severity-error"),
                    "OCM/severityErrorInstantEmailTitle", "txt", "OCM Service Logs error severity email title",
                    "OCM/severityErrorInstantEmailBody", "html", "OCM Service Logs error severity email body"
            );
            createInstantEmailTemplate(
                    warnings, "openshift", "ocm", List.of("severity-fatal"),
                    "OCM/severityFatalInstantEmailTitle", "txt", "OCM Service Logs fatal severity email title",
                    "OCM/severityFatalInstantEmailBody", "html", "OCM Service Logs fatal severity email body"
            );
            createInstantEmailTemplate(
                    warnings, "openshift", "ocm", List.of("severity-info"),
                    "OCM/severityInfoInstantEmailTitle", "txt", "OCM Service Logs info severity email title",
                    "OCM/severityInfoInstantEmailBody", "html", "OCM Service Logs info severity email body"
            );
            createInstantEmailTemplate(
                    warnings, "openshift", "ocm", List.of("severity-major"),
                    "OCM/severityMajorInstantEmailTitle", "txt", "OCM Service Logs major severity email title",
                    "OCM/severityMajorInstantEmailBody", "html", "OCM Service Logs major severity email body"
            );
            createInstantEmailTemplate(
                    warnings, "openshift", "ocm", List.of("severity-warning"),
                    "OCM/severityWarningInstantEmailTitle", "txt", "OCM Service Logs warning severity email title",
                    "OCM/severityWarningInstantEmailBody", "html", "OCM Service Logs warning severity email body"
            );

            /*
             * Former src/main/resources/templates/Patch folder.
             */
            createInstantEmailTemplate(
                warnings, "rhel", "patch", List.of("new-advisory"),
                "Patch/newAdvisoriesInstantEmailTitle", "txt", "Patch instant advisories email title",
                "Patch/newAdvisoriesInstantEmailBody", "html", "Patch instant advisories email body"
            );
            createDailyEmailTemplate(
                warnings, "rhel", "patch",
                "Patch/dailyEmailTitle", "txt", "Patch daily email title",
                "Patch/dailyEmailBody", "html", "Patch daily email body"
            );

            /*
             * Former src/main/resources/templates/Policies folder.
             */
            createInstantEmailTemplate(
                warnings, "rhel", "policies", List.of("policy-triggered"),
                "Policies/instantEmailTitle", "txt", "Policies instant email title",
                "Policies/instantEmailBody", "html", "Policies instant email body"
            );
            createDailyEmailTemplate(
                warnings, "rhel", "policies",
                "Policies/dailyEmailTitle", "txt", "Policies daily email title",
                "Policies/dailyEmailBody", "html", "Policies daily email body"
            );

            /*
             * Former src/main/resources/templates/Rbac folder.
             */
            createInstantEmailTemplate(
                warnings, "console", "rbac", List.of("rh-new-role-available"),
                "Rbac/systemRoleAvailableEmailTitle", "txt", "Rbac system role available email title",
                "Rbac/systemRoleAvailableEmailBody", "html", "Rbac system role available email body"
            );
            createInstantEmailTemplate(
                warnings, "console", "rbac", List.of("rh-platform-default-role-updated"),
                "Rbac/platformRoleUpdatedEmailTitle", "txt", "Rbac platform role updated email title",
                "Rbac/platformRoleUpdatedEmailBody", "html", "Rbac platform role updated email body"
            );
            createInstantEmailTemplate(
                warnings, "console", "rbac", List.of("rh-non-platform-default-role-updated"),
                "Rbac/nonPlatformRoleUpdatedEmailTitle", "txt", "Rbac non platform role updated email title",
                "Rbac/nonPlatformRoleUpdatedEmailBody", "html", "Rbac non platform role updated email body"
            );
            createInstantEmailTemplate(
                warnings, "console", "rbac", List.of("custom-role-created"),
                "Rbac/customRoleCreatedEmailTitle", "txt", "Rbac custom role created email title",
                "Rbac/customRoleCreatedEmailBody", "html", "Rbac custom role created email body"
            );
            createInstantEmailTemplate(
                warnings, "console", "rbac", List.of("custom-role-updated"),
                "Rbac/customRoleUpdatedEmailTitle", "txt", "Rbac custom role updated email title",
                "Rbac/customRoleUpdatedEmailBody", "html", "Rbac custom role updated email body"
            );
            createInstantEmailTemplate(
                warnings, "console", "rbac", List.of("custom-role-deleted"),
                "Rbac/customRoleDeletedEmailTitle", "txt", "Rbac custom role deleted email title",
                "Rbac/customRoleDeletedEmailBody", "html", "Rbac custom role deleted email body"
            );
            createInstantEmailTemplate(
                warnings, "console", "rbac", List.of("rh-new-role-added-to-default-access"),
                "Rbac/roleAddedToPlatformGroupEmailTitle", "txt", "Rbac role added to platform group email title",
                "Rbac/roleAddedToPlatformGroupEmailBody", "html", "Rbac role added to platform group email body"
            );
            createInstantEmailTemplate(
                warnings, "console", "rbac", List.of("rh-role-removed-from-default-access"),
                "Rbac/roleRemovedFromPlatformGroupEmailTitle", "txt", "Rbac role removed from platform group email title",
                "Rbac/roleRemovedFromPlatformGroupEmailBody", "html", "Rbac role removed from platform group email body"
            );
            createInstantEmailTemplate(
                warnings, "console", "rbac", List.of("custom-default-access-updated"),
                "Rbac/customPlatformGroupUpdatedEmailTitle", "txt", "Rbac custom platform group updated email title",
                "Rbac/customPlatformGroupUpdatedEmailBody", "html", "Rbac custom platform group updated email body"
            );
            createInstantEmailTemplate(
                warnings, "console", "rbac", List.of("group-created"),
                "Rbac/customGroupCreatedEmailTitle", "txt", "Rbac custom group created email title",
                "Rbac/customGroupCreatedEmailBody", "html", "Rbac custom group created email body"
            );
            createInstantEmailTemplate(
                warnings, "console", "rbac", List.of("group-updated"),
                "Rbac/customGroupUpdatedEmailTitle", "txt", "Rbac custom group updated email title",
                "Rbac/customGroupUpdatedEmailBody", "html", "Rbac custom group updated email body"
            );
            createInstantEmailTemplate(
                warnings, "console", "rbac", List.of("group-deleted"),
                "Rbac/customGroupDeletedEmailTitle", "txt", "Rbac custom group deleted email title",
                "Rbac/customGroupDeletedEmailBody", "html", "Rbac custom group deleted email body"
            );
            createInstantEmailTemplate(
                warnings, "console", "rbac", List.of("platform-default-group-turned-into-custom"),
                "Rbac/platformGroupToCustomEmailTitle", "txt", "Rbac platform group to custom email title",
                "Rbac/platformGroupToCustomEmailBody", "html", "Rbac platform group to custom email body"
            );

            /*
             * Former src/main/resources/templates/ResourceOptimization folder.
             */
            createDailyEmailTemplate(
                warnings, "rhel", "resource-optimization",
                "ResourceOptimization/dailyEmailTitle", "txt", "Resource Optimization daily email title",
                "ResourceOptimization/dailyEmailBody", "html", "Resource Optimization daily email body"
            );

            /*
             * Former src/main/resources/templates/Sources folder.
             */
            createInstantEmailTemplate(
                warnings, "console", "sources", List.of("availability-status"),
                "Sources/availabilityStatusEmailTitle", "txt", "Sources availability status email title",
                "Sources/availabilityStatusEmailBody", "html", "Sources availability status email body"
            );

            /*
             * Former src/main/resources/templates/Vulnerability folder.
             */
            createInstantEmailTemplate(
                warnings, "rhel", "vulnerability", List.of("any-cve-known-exploit"),
                "Vulnerability/anyCveKnownExploitTitle", "txt", "Vulnerability any CVE known exploit email title",
                "Vulnerability/anyCveKnownExploitBody", "html", "Vulnerability any CVE known exploit email body"
            );
            createInstantEmailTemplate(
                warnings, "rhel", "vulnerability", List.of("new-cve-severity"),
                "Vulnerability/newCveCritSeverityEmailTitle", "txt", "Vulnerability new CVE crit severity email title",
                "Vulnerability/newCveCritSeverityEmailBody", "html", "Vulnerability new CVE crit severity email body"
            );
            createInstantEmailTemplate(
                warnings, "rhel", "vulnerability", List.of("new-cve-cvss"),
                "Vulnerability/newCveHighCvssEmailTitle", "txt", "Vulnerability new CVE high cvss email title",
                "Vulnerability/newCveHighCvssEmailBody", "html", "Vulnerability new CVE high cvss email body"
            );
            createInstantEmailTemplate(
                warnings, "rhel", "vulnerability", List.of("new-cve-security-rule"),
                "Vulnerability/newCveSecurityRuleTitle", "txt", "Vulnerability new CVE security rule email title",
                "Vulnerability/newCveSecurityRuleBody", "html", "Vulnerability new CVE security rule email body"
            );
            createDailyEmailTemplate(
                warnings, "rhel", "vulnerability",
                "Vulnerability/dailyEmailTitle", "txt", "Vulnerability daily email title",
                "Vulnerability/dailyEmailBody", "html", "Vulnerability daily email body"
            );

            /*
             * Former src/main/resources/templates/ImageBuilder folder.
             */

            createInstantEmailTemplate(
                warnings, "rhel", "image-builder", List.of("launch-success"),
                "ImageBuilder/launchSuccessInstantEmailTitle", "txt", "Image Builder launch success title",
                "ImageBuilder/launchSuccessInstantEmailBody", "html", "Image Builder launch success body"
            );
            createInstantEmailTemplate(
                warnings, "rhel", "image-builder", List.of("launch-failed"),
                "ImageBuilder/launchFailedInstantEmailTitle", "txt", "Image Builder launch failed title",
                "ImageBuilder/launchFailedEmailBody", "html", "Image Builder launch failed body"
            );
            createDailyEmailTemplate(
                warnings, "rhel", "image-builder",
                "ImageBuilder/dailyEmailTitle", "txt", "Image Builder daily digest title",
                "ImageBuilder/dailyEmailBody", "html", "Image Builder daily digest body"
            );

            getOrCreateTemplate("Common/insightsEmailBody", "html", "Common Insights email body");
        }
        Log.debug("Migration ended");

        return warnings;
    }

    /*
     * Creates a template only if it does not already exist in the DB.
     * Existing templates are never updated by this migration service.
     */
    Template getOrCreateTemplate(String name, String extension, String description) {
        String templateFromFS = loadResourceTemplate(name, extension);
        if (name.contains("V2")) {
            name = name.replace("V2", "");
        }
        try {
            boolean hasBeenUpdated = false;
            Template template = entityManager.createQuery("FROM Template WHERE name = :name", Template.class)
                    .setParameter("name", name)
                    .getSingleResult();
            if (!template.getData().equals(templateFromFS)) {
                template.setData(templateFromFS);
                hasBeenUpdated = true;
            }
            Log.infof("Template found in DB: %s" + (hasBeenUpdated ? " has been updated" : StringUtils.EMPTY), name);
            return template;
        } catch (NoResultException e) {
            Log.infof("Creating template: %s", name);
            Template template = new Template();
            template.setName(name);
            template.setDescription(description);
            template.setData(templateFromFS);
            entityManager.persist(template);
            return template;
        }
    }

    private String loadResourceTemplate(String name, String extension) {
        String path = "/templates/" + name + "." + extension;
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (inputStream == null) {
                throw new IllegalStateException("Template resource file not found: " + path);
            } else {
                return new String(inputStream.readAllBytes(), UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Template loading from resource file failed: " + path, e);
        }
    }

    /*
     * Creates an instant email template and the underlying templates only if:
     * - the event type exists in the DB
     * - the instant email template does not already exist in the DB
     * Existing instant email templates are never updated by this migration service.
     */
    private void createInstantEmailTemplate(List<String> warnings, String bundleName, String appName, List<String> eventTypeNames,
            String subjectTemplateName, String subjectTemplateExtension, String subjectTemplateDescription,
            String bodyTemplateName, String bodyTemplateExtension, String bodyTemplateDescription) {

        if (!featureFlipper.isUseSecuredEmailTemplates()) {
            subjectTemplateName += "V2";
            bodyTemplateName += "V2";
        }

        for (String eventTypeName : eventTypeNames) {
            Optional<EventType> eventType = findEventType(warnings, bundleName, appName, eventTypeName);
            Template subjectTemplate = getOrCreateTemplate(subjectTemplateName, subjectTemplateExtension, subjectTemplateDescription);
            Template bodyTemplate = getOrCreateTemplate(bodyTemplateName, bodyTemplateExtension, bodyTemplateDescription);
            if (eventType.isPresent()) {
                if (!instantEmailTemplateExists(eventType.get())) {
                    Log.infof("Creating instant email template for event type: %s/%s/%s", bundleName, appName, eventTypeName);

                    InstantEmailTemplate emailTemplate = new InstantEmailTemplate();
                    emailTemplate.setEventType(eventType.get());
                    emailTemplate.setSubjectTemplate(subjectTemplate);
                    emailTemplate.setSubjectTemplateId(subjectTemplate.getId());
                    emailTemplate.setBodyTemplate(bodyTemplate);
                    emailTemplate.setBodyTemplateId(bodyTemplate.getId());

                    entityManager.persist(emailTemplate);
                }
            }
        }
    }

    private Optional<EventType> findEventType(List<String> warnings, String bundleName, String appName, String eventTypeName) {
        String hql = "FROM EventType WHERE name = :eventTypeName AND application.name = :appName " +
                "AND application.bundle.name = :bundleName";
        try {
            EventType eventType = entityManager.createQuery(hql, EventType.class)
                    .setParameter("eventTypeName", eventTypeName)
                    .setParameter("appName", appName)
                    .setParameter("bundleName", bundleName)
                    .getSingleResult();
            return Optional.of(eventType);
        } catch (NoResultException e) {
            warnings.add(String.format("Event type not found: %s/%s/%s", bundleName, appName, eventTypeName));
            return Optional.empty();
        }
    }

    private boolean instantEmailTemplateExists(EventType eventType) {
        String hql = "SELECT COUNT(*) FROM InstantEmailTemplate WHERE eventType = :eventType";
        long count = entityManager.createQuery(hql, Long.class)
                .setParameter("eventType", eventType)
                .getSingleResult();
        return count > 0;
    }

    /*
     * Creates an aggregation email template and the underlying templates only if:
     * - the application exists in the DB
     * - the aggregation email template does not already exist in the DB
     * Existing aggregation email templates are never updated by this migration service.
     */
    private void createDailyEmailTemplate(List<String> warnings, String bundleName, String appName,
                                          String subjectTemplateName, String subjectTemplateExtension, String subjectTemplateDescription,
                                          String bodyTemplateName, String bodyTemplateExtension, String bodyTemplateDescription) {
        if (!featureFlipper.isUseSecuredEmailTemplates()) {
            subjectTemplateName += "V2";
            bodyTemplateName += "V2";
        }
        Optional<Application> app = findApplication(warnings, bundleName, appName);
        if (app.isPresent()) {
            Template subjectTemplate = getOrCreateTemplate(subjectTemplateName, subjectTemplateExtension, subjectTemplateDescription);
            Template bodyTemplate = getOrCreateTemplate(bodyTemplateName, bodyTemplateExtension, bodyTemplateDescription);
            if (!aggregationEmailTemplateExists(app.get())) {
                Log.infof("Creating daily email template for application: %s/%s", bundleName, appName);

                AggregationEmailTemplate emailTemplate = new AggregationEmailTemplate();
                emailTemplate.setApplication(app.get());
                emailTemplate.setSubscriptionType(DAILY);
                emailTemplate.setSubjectTemplate(subjectTemplate);
                emailTemplate.setSubjectTemplateId(subjectTemplate.getId());
                emailTemplate.setBodyTemplate(bodyTemplate);
                emailTemplate.setBodyTemplateId(bodyTemplate.getId());

                entityManager.persist(emailTemplate);
            }
        }
    }

    private Optional<Application> findApplication(List<String> warnings, String bundleName, String appName) {
        String hql = "FROM Application WHERE name = :appName AND bundle.name = :bundleName";
        try {
            Application app = entityManager.createQuery(hql, Application.class)
                    .setParameter("appName", appName)
                    .setParameter("bundleName", bundleName)
                    .getSingleResult();
            return Optional.of(app);
        } catch (NoResultException e) {
            warnings.add(String.format("Application not found: %s/%s", bundleName, appName));
            return Optional.empty();
        }
    }

    private boolean aggregationEmailTemplateExists(Application app) {
        String hql = "SELECT COUNT(*) FROM AggregationEmailTemplate WHERE application = :app " +
                "AND subscriptionType = :subscriptionType";
        long count = entityManager.createQuery(hql, Long.class)
                .setParameter("app", app)
                .setParameter("subscriptionType", DAILY)
                .getSingleResult();
        return count > 0;
    }
}
