package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.Template;
import io.quarkus.logging.Log;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.transaction.Transactional;
import javax.ws.rs.DELETE;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

// TODO NOTIF-484 Remove this class when the templates DB migration is finished.
@Path(API_INTERNAL + "/template-engine/migrate")
public class EmailTemplateMigrationService {

    @Inject
    EntityManager entityManager;

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
        entityManager.createQuery("DELETE FROM Template").executeUpdate();
    }

    @PUT
    @Transactional
    @Produces(APPLICATION_JSON)
    public List<String> migrate() {
        List<String> warnings = new ArrayList<>();

        Log.debug("Migration starting");

        /*
         * Former src/main/resources/templates/Advisor folder.
         */
        getOrCreateTemplate(warnings, "Advisor/insightsEmailBody", "html", "Advisor Insights email body");
        createInstantEmailTemplate(
                warnings, "rhel", "advisor", List.of("deactivated-recommendation"),
                "Advisor/deactivatedRecommendationInstantEmailTitle", "txt", "Advisor deactivated recommendation email title",
                "Advisor/deactivatedRecommendationInstantEmailBody", "html", "Advisor deactivated recommendation email body"
        );
        createInstantEmailTemplate(
                warnings, "rhel", "advisor", List.of("new-recommendation"),
                "Advisor/newRecommendationInstantEmailTitle", "txt", "Advisor new recommendation email title",
                "Advisor/newRecommendationInstantEmailBody", "html", "Advisor new recommendation email body"
        );
        createInstantEmailTemplate(
                warnings, "rhel", "advisor", List.of("resolved-recommendation"),
                "Advisor/resolvedRecommendationInstantEmailTitle", "txt", "Advisor resolved recommendation email title",
                "Advisor/resolvedRecommendationInstantEmailBody", "html", "Advisor resolved recommendation email body"
        );

        /*
         * Former src/main/resources/templates/AdvisorOpenshift folder.
         */
        getOrCreateTemplate(warnings, "AdvisorOpenshift/insightsEmailBody", "html", "AdvisorOpenshift Insights email body");
        createInstantEmailTemplate(
                warnings, "openshift", "advisor", List.of("new-recommendation"),
                "AdvisorOpenshift/newRecommendationInstantEmailTitle", "txt", "AdvisorOpenshift new recommendation email title",
                "AdvisorOpenshift/newRecommendationInstantEmailBody", "html", "AdvisorOpenshift new recommendation email body"
        );

        /*
         * Former src/main/resources/templates/Ansible folder.
         */
        getOrCreateTemplate(warnings, "Ansible/insightsEmailBody", "html", "Ansible Insights email body");
        createInstantEmailTemplate(
                warnings, "ansible", "reports", List.of("report-available"),
                "Ansible/instantEmailTitle", "txt", "Ansible instant email title",
                "Ansible/instantEmailBody", "html", "Ansible instant email body"
        );

        /*
         * Former src/main/resources/templates/Compliance folder.
         */
        getOrCreateTemplate(warnings, "Compliance/insightsEmailBody", "html", "Compliance Insights email body");
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
         * Former src/main/resources/templates/ConsoleNotifications folder.
         */
        createInstantEmailTemplate(
                warnings, "console", "notifications", List.of("failed-integration"),
                "ConsoleNotifications/failedIntegrationTitle", "txt", "Notifications failed integration email title",
                "ConsoleNotifications/failedIntegrationBody", "txt", "Notifications failed integration email body"
        );

        /*
         * Former src/main/resources/templates/Drift folder.
         */
        getOrCreateTemplate(warnings, "Drift/insightsEmailBody", "html", "Drift Insights email body");
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
        getOrCreateTemplate(warnings, "EdgeManagement/insightsEmailBody", "html", "EdgeManagement Insights email body");
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
        getOrCreateTemplate(warnings, "Inventory/insightsEmailBody", "html", "Inventory Insights email body");
        createInstantEmailTemplate(
                warnings, "rhel", "inventory", List.of("validation-error"),
                "Inventory/validationErrorEmailTitle", "txt", "Inventory instant email title",
                "Inventory/validationErrorEmailBody", "html", "Inventory instant email body"
        );
        createDailyEmailTemplate(
                warnings, "rhel", "Inventory",
                "Inventory/dailyEmailTitle", "txt", "Inventory daily email title",
                "Inventory/dailyEmailBody", "html", "Inventory daily email body"
        );

        /*
         * Former src/main/resources/templates/MalwareDetection folder.
         */
        getOrCreateTemplate(warnings, "MalwareDetection/insightsEmailBody", "html", "Malware Detection Insights email body");
        createInstantEmailTemplate(
                warnings, "rhel", "malware-detection", List.of("detected-malware"),
                "MalwareDetection/detectedMalwareInstantEmailTitle", "txt", "Malware Detection detected malware email title",
                "MalwareDetection/detectedMalwareInstantEmailBody", "html", "Malware Detection detected malware email body"
        );

        /*
        * Former src/main/resources/templates/Patch folder.
         */
        getOrCreateTemplate(warnings, "Patch/insightsEmailBody", "html", "Patch Insights email body");
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
        getOrCreateTemplate(warnings, "Policies/insightsEmailBody", "html", "Policies Insights email body");
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
        getOrCreateTemplate(warnings, "Rbac/insightsEmailBody", "html", "Rbac Insights email body");
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
         * Former src/main/resources/templates/Rhosak folder.
         */
        getOrCreateTemplate(warnings, "Rhosak/rhosakEmailBody", "html", "Rhosak email body");
        createInstantEmailTemplate(
                warnings, "application-services", "rhosak", List.of("disruption"),
                "Rhosak/serviceDisruptionTitle", "txt", "Rhosak service disruption email title",
                "Rhosak/serviceDisruptionBody", "html", "Rhosak service disruption email body"
        );
        createInstantEmailTemplate(
                warnings, "application-services", "rhosak", List.of("instance-created"),
                "Rhosak/instanceCreatedTitle", "txt", "Rhosak instance created email title",
                "Rhosak/instanceCreatedBody", "html", "Rhosak instance created email body"
        );
        createInstantEmailTemplate(
                warnings, "application-services", "rhosak", List.of("instance-deleted"),
                "Rhosak/instanceDeletedTitle", "txt", "Rhosak instance deleted email title",
                "Rhosak/instanceDeletedBody", "html", "Rhosak instance deleted email body"
        );
        createInstantEmailTemplate(
                warnings, "application-services", "rhosak", List.of("action-required"),
                "Rhosak/actionRequiredTitle", "txt", "Rhosak action required email title",
                "Rhosak/actionRequiredBody", "html", "Rhosak action required email body"
        );
        createInstantEmailTemplate(
                warnings, "application-services", "rhosak", List.of("scheduled-upgrade"),
                "Rhosak/scheduledUpgradeTitle", "txt", "Rhosak scheduled upgrade email title",
                "Rhosak/scheduledUpgradeBody", "html", "Rhosak scheduled upgrade email body"
        );
        createDailyEmailTemplate(
                warnings, "application-services", "rhosak",
                "Rhosak/dailyRhosakEmailsTitle", "txt", "Rhosak daily email title",
                "Rhosak/dailyRhosakEmailsBody", "html", "Rhosak daily email body"
        );

        /*
         * Former src/main/resources/templates/Sources folder.
         */
        getOrCreateTemplate(warnings, "Sources/insightsEmailBody", "html", "Sources Insights email body");
        createInstantEmailTemplate(
                warnings, "console", "sources", List.of("availability-status"),
                "Sources/availabilityStatusEmailTitle", "txt", "Sources availability status email title",
                "Sources/availabilityStatusEmailBody", "html", "Sources availability status email body"
        );

        /*
         * Former src/main/resources/templates/Vulnerability folder.
         */
        getOrCreateTemplate(warnings, "Vulnerability/insightsEmailBody", "html", "Vulnerability Insights email body");
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

        Log.debug("Migration ended");

        return warnings;
    }

    /*
     * Creates a template only if it does not already exist in the DB.
     * Existing templates are never updated by this migration service.
     */
    Template getOrCreateTemplate(List<String> warnings, String name, String extension, String description) {
        try {
            Template template = entityManager.createQuery("FROM Template WHERE name = :name", Template.class)
                    .setParameter("name", name)
                    .getSingleResult();
            warnings.add(String.format("Template found in DB: %s", name));
            return template;
        } catch (NoResultException e) {
            Log.infof("Creating template: %s", name);
            Template template = new Template();
            template.setName(name);
            template.setDescription(description);
            template.setData(loadResourceTemplate(name, extension));
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

        for (String eventTypeName : eventTypeNames) {
            Optional<EventType> eventType = findEventType(warnings, bundleName, appName, eventTypeName);
            if (eventType.isPresent()) {
                if (instantEmailTemplateExists(eventType.get())) {
                    warnings.add(String.format("Instant email template found in DB for event type: %s/%s/%s", bundleName, appName, eventTypeName));
                } else {
                    Template subjectTemplate = getOrCreateTemplate(warnings, subjectTemplateName, subjectTemplateExtension, subjectTemplateDescription);
                    Template bodyTemplate = getOrCreateTemplate(warnings, bodyTemplateName, bodyTemplateExtension, bodyTemplateDescription);

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

        Optional<Application> app = findApplication(warnings, bundleName, appName);
        if (app.isPresent()) {
            if (aggregationEmailTemplateExists(app.get())) {
                warnings.add(String.format("Aggregation email template found in DB for application: %s/%s", bundleName, appName));
            } else {
                Template subjectTemplate = getOrCreateTemplate(warnings, subjectTemplateName, subjectTemplateExtension, subjectTemplateDescription);
                Template bodyTemplate = getOrCreateTemplate(warnings, bodyTemplateName, bodyTemplateExtension, bodyTemplateDescription);

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
