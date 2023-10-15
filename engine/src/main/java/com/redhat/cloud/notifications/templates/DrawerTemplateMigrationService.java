package com.redhat.cloud.notifications.templates;

import com.cronutils.utils.StringUtils;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.IntegrationTemplate;
import com.redhat.cloud.notifications.models.Template;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.redhat.cloud.notifications.events.ConnectorReceiver.INTEGRATION_FAILED_EVENT_TYPE;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.INTEGRATION_DISABLED_EVENT_TYPE;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.*;
import static java.nio.charset.StandardCharsets.UTF_8;

@ApplicationScoped
public class DrawerTemplateMigrationService {

    @Inject
    EntityManager entityManager;

    @Transactional
    public List<String> migrate() {
        List<String> warnings = new ArrayList<>();

        Log.debug("Migration starting");

        // Advisor
        createDrawerIntegrationTemplate(
            warnings, "rhel", "advisor", List.of(DEACTIVATED_RECOMMENDATION),
            "Advisor/deactivatedRecommendationBody", "html", "Advisor deactivated recommendation drawer body"
        );
        createDrawerIntegrationTemplate(
            warnings, "rhel", "advisor", List.of(NEW_RECOMMENDATION),
            "Advisor/newRecommendationBody", "html", "Advisor new recommendation drawer body"
        );
        createDrawerIntegrationTemplate(
            warnings, "rhel", "advisor", List.of(RESOLVED_RECOMMENDATION),
            "Advisor/resolvedRecommendationBody", "html", "Advisor resolved recommendation drawer body"
        );

        // Advisor - openShift
        createDrawerIntegrationTemplate(
            warnings, "openshift", "advisor", List.of("new-recommendation"),
            "AdvisorOpenShift/newRecommendationBody", "html", "Advisor new recommendation drawer body"
        );

        // Ansible
        createDrawerIntegrationTemplate(
            warnings, "ansible", "reports", List.of("report-available"),
            "Ansible/reportAvailableBody", "html", "Ansible report available drawer body"
        );

        // Compliance
        createDrawerIntegrationTemplate(
            warnings, "rhel", "compliance", List.of("compliance-below-threshold"),
            "Compliance/belowThresholdBody", "html", "Compliance below threshold drawer body"
        );

        createDrawerIntegrationTemplate(
            warnings, "rhel", "compliance", List.of("report-upload-failed"),
            "Compliance/reportUploadFailedBody", "html", "Compliance report upload failed drawer body"
        );

        // cost management
        createDrawerIntegrationTemplate(
            warnings, "openshift", "cost-management", List.of("missing-cost-model"),
            "CostManagement/MissingCostModelBody", "html", "Cost Management missing cost model drawer body"
        );

        createDrawerIntegrationTemplate(
            warnings, "openshift", "cost-management", List.of("cost-model-create"),
            "CostManagement/CostModelCreateBody", "html", "Cost Management cost model create drawer body"
        );

        createDrawerIntegrationTemplate(
            warnings, "openshift", "cost-management", List.of("cost-model-update"),
            "CostManagement/CostModelUpdateBody", "html", "Cost Management cost model update drawer body"
        );

        createDrawerIntegrationTemplate(
            warnings, "openshift", "cost-management", List.of("cost-model-remove"),
            "CostManagement/CostModelRemoveBody", "html", "Cost Management cost model remove drawer body"
        );

        createDrawerIntegrationTemplate(
            warnings, "openshift", "cost-management", List.of("cm-operator-stale"),
            "CostManagement/CmOperatorStaleBody", "html", "Cost Management operator stale drawer body"
        );

        createDrawerIntegrationTemplate(
            warnings, "openshift", "cost-management", List.of("cm-operator-data-received"),
            "CostManagement/CmOperatorDataReceivedBody", "html", "Cost Management operator data received drawer body"
        );
        createDrawerIntegrationTemplate(
            warnings, "openshift", "cost-management", List.of("cm-operator-data-processed"),
            "CostManagement/CmOperatorDataProcessedBody", "html", "Cost Management operator data processed drawer body"
        );

        // Drift
        createDrawerIntegrationTemplate(
            warnings, "rhel", "drift", List.of("drift-baseline-detected"),
            "Drift/newBaselineDriftBody", "html", "Drift new baseline drift drawer body"
        );

        // EdgeManagement
        createDrawerIntegrationTemplate(
            warnings, "rhel", "edge-management", List.of("image-creation"),
            "EdgeManagement/imageCreationBody", "html", "EdgeManagement image creation drawer body"
        );

        createDrawerIntegrationTemplate(
            warnings, "rhel", "edge-management", List.of("update-devices"),
            "EdgeManagement/updateDeviceBody", "html", "EdgeManagement update devices drawer body"
        );

        // Integrations
        createDrawerIntegrationTemplate(
            warnings, "console", "integrations", List.of(INTEGRATION_FAILED_EVENT_TYPE),
            "Integrations/failedIntegrationBody", "html", "Integrations failed integration drawer body"
        );

        createDrawerIntegrationTemplate(
            warnings, "console", "integrations", List.of(INTEGRATION_DISABLED_EVENT_TYPE),
            "Integrations/integrationDisabledBody", "html", "Integrations disabled integration drawer body"
        );

        // Inventory
        createDrawerIntegrationTemplate(
            warnings, "rhel", "inventory", List.of("validation-error"),
            "Inventory/validationErrorBody", "html", "Inventory instant drawer body"
        );

        // Malware detection
        createDrawerIntegrationTemplate(
            warnings, "rhel", "malware-detection", List.of("detected-malware"),
            "MalwareDetection/detectedMalwareBody", "html", "Malware Detection detected malware drawer body"
        );

        // Patch
        createDrawerIntegrationTemplate(
            warnings, "rhel", "patch", List.of("new-advisory"),
            "Patch/newAdvisoriesBody", "html", "Patch instant advisories drawer body"
        );

        // Policies
        createDrawerIntegrationTemplate(
            warnings, "rhel", "policies", List.of("policy-triggered"),
            "Policies/instantBody", "html", "Policies instant drawer body"
        );

        // Rbac
        createDrawerIntegrationTemplate(
            warnings, "console", "rbac", List.of("rh-new-role-available"),
            "Rbac/systemRoleAvailableBody", "html", "Rbac system role available drawer body"
        );

        createDrawerIntegrationTemplate(
            warnings, "console", "rbac", List.of("rh-platform-default-role-updated"),
            "Rbac/platformRoleUpdatedBody", "html", "Rbac platform role updated drawer body"
        );

        createDrawerIntegrationTemplate(
            warnings, "console", "rbac", List.of("rh-non-platform-default-role-updated"),
            "Rbac/nonPlatformRoleUpdatedBody", "html", "Rbac non platform role updated drawer body"
        );

        createDrawerIntegrationTemplate(
            warnings, "console", "rbac", List.of("custom-role-created"),
            "Rbac/customRoleCreatedBody", "html", "Rbac custom role created drawer body"
        );

        createDrawerIntegrationTemplate(
            warnings, "console", "rbac", List.of("custom-role-updated"),
            "Rbac/customRoleUpdatedBody", "html", "Rbac custom role updated drawer body"
        );

        createDrawerIntegrationTemplate(
            warnings, "console", "rbac", List.of("custom-role-deleted"),
            "Rbac/customRoleDeletedBody", "html", "Rbac custom role deleted drawer body"
        );

        createDrawerIntegrationTemplate(
            warnings, "console", "rbac", List.of("rh-new-role-added-to-default-access"),
            "Rbac/roleAddedToPlatformGroupBody", "html", "Rbac role added to platform group drawer body"
        );

        createDrawerIntegrationTemplate(
            warnings, "console", "rbac", List.of("rh-role-removed-from-default-access"),
            "Rbac/roleRemovedFromPlatformGroupBody", "html", "Rbac role removed from platform group drawer body"
        );

        createDrawerIntegrationTemplate(
            warnings, "console", "rbac", List.of("custom-default-access-updated"),
            "Rbac/customPlatformGroupUpdatedBody", "html", "Rbac custom platform group updated drawer body"
        );

        createDrawerIntegrationTemplate(
            warnings, "console", "rbac", List.of("group-created"),
            "Rbac/customGroupCreatedBody", "html", "Rbac custom group created drawer body"
        );

        createDrawerIntegrationTemplate(
            warnings, "console", "rbac", List.of("group-updated"),
            "Rbac/customGroupUpdatedBody", "html", "Rbac custom group updated drawer body"
        );

        createDrawerIntegrationTemplate(
            warnings, "console", "rbac", List.of("group-deleted"),
            "Rbac/customGroupDeletedBody", "html", "Rbac custom group deleted drawer body"
        );

        createDrawerIntegrationTemplate(
            warnings, "console", "rbac", List.of("platform-default-group-turned-into-custom"),
            "Rbac/platformGroupToCustomBody", "html", "Rbac platform group to custom drawer body"
        );

        // Sources
        createDrawerIntegrationTemplate(
            warnings, "console", "sources", List.of("availability-status"),
            "Sources/availabilityStatusBody", "html", "Sources availability status drawer body"
        );

        // Vulnerability
        createDrawerIntegrationTemplate(
            warnings, "rhel", "vulnerability", List.of("any-cve-known-exploit"),
            "Vulnerability/anyCveKnownExploitBody", "html", "Vulnerability any CVE known exploit drawer body"
        );

        createDrawerIntegrationTemplate(
            warnings, "rhel", "vulnerability", List.of("new-cve-severity"),
            "Vulnerability/newCveCritSeverityBody", "html", "Vulnerability new CVE crit severity drawer body"
        );

        createDrawerIntegrationTemplate(
            warnings, "rhel", "vulnerability", List.of("new-cve-cvss"),
            "Vulnerability/newCveHighCvssBody", "html", "Vulnerability new CVE high cvss drawer body"
        );

        createDrawerIntegrationTemplate(
            warnings, "rhel", "vulnerability", List.of("new-cve-security-rule"),
            "Vulnerability/newCveSecurityRuleBody", "html", "Vulnerability new CVE security rule drawer body"
        );

        Log.debug("Migration ended");

        return warnings;
    }

    private void createDrawerIntegrationTemplate(List<String> warnings, String bundleName, String appName, List<String> eventTypeNames,
                                                String bodyTemplateName, String bodyTemplateExtension, String bodyTemplateDescription) {
        createIntegrationTemplate(warnings, bundleName, appName, eventTypeNames,
            bodyTemplateName, bodyTemplateExtension, bodyTemplateDescription, "drawer");
    }

    /*
     * Creates an instant email template and the underlying templates only if:
     * - the event type exists in the DB
     * - the instant email template does not already exist in the DB
     * Existing instant email templates are never updated by this migration service.
     */
    private void createIntegrationTemplate(List<String> warnings, String bundleName, String appName, List<String> eventTypeNames,
                                           String bodyTemplateName, String bodyTemplateExtension, String bodyTemplateDescription, String integrationType) {

        for (String eventTypeName : eventTypeNames) {
            Optional<EventType> eventType = findEventType(warnings, bundleName, appName, eventTypeName);

            Template bodyTemplate = getOrCreateTemplate(bodyTemplateName, bodyTemplateExtension, bodyTemplateDescription, integrationType);
            if (eventType.isPresent() && !integrationTemplateExists(eventType.get(), integrationType)) {
                Log.infof("Creating integration template for event type: %s/%s/%s", bundleName, appName, eventTypeName);

                IntegrationTemplate integrationTemplate = new IntegrationTemplate();
                integrationTemplate.setTemplateKind(IntegrationTemplate.TemplateKind.EVENT_TYPE);
                integrationTemplate.setIntegrationType(integrationType);
                integrationTemplate.setCreated(LocalDateTime.now());
                integrationTemplate.setApplication(eventType.get().getApplication());
                integrationTemplate.setEventType(eventType.get());
                integrationTemplate.setTheTemplate(bodyTemplate);
                entityManager.persist(integrationTemplate);
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

    private boolean integrationTemplateExists(EventType eventType, String integrationType) {
        String hql = "SELECT COUNT(*) FROM IntegrationTemplate WHERE eventType = :eventType and integrationType = :integrationType";
        long count = entityManager.createQuery(hql, Long.class)
                .setParameter("eventType", eventType)
                .setParameter("integrationType", integrationType)
                .getSingleResult();
        return count > 0;
    }

    /*
     * Creates a template only if it does not already exist in the DB.
     * Existing templates are never updated by this migration service.
     */
    @Transactional
    Template getOrCreateTemplate(String name, String extension, String description, String integrationType) {
        String templateFromFS = loadResourceTemplate(name, extension, integrationType);
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

    private String loadResourceTemplate(String name, String extension, String integrationType) {
        String path = "/templates/" + integrationType + "/" + name + "." + extension;
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
}
