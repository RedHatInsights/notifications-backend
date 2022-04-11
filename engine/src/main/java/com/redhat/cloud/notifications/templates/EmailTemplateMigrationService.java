package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.Template;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.transaction.Transactional;
import javax.ws.rs.DELETE;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static java.nio.charset.StandardCharsets.UTF_8;

// TODO NOTIF-484 Remove this class when the templates DB migration is finished.
@Path(API_INTERNAL + "/template-engine/migrate")
public class EmailTemplateMigrationService {

    private static final Logger LOGGER = Logger.getLogger(EmailTemplateMigrationService.class);

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
    public void migrate() {

        LOGGER.debug("Migration starting");

        /*
         * Former src/main/resources/templates/Advisor folder.
         */
        getOrCreateTemplate("Advisor/insightsEmailBody", "html", "Advisor Insights email body");
        createInstantEmailTemplate(
                "rhel", "advisor", List.of("deactivated-recommendation"),
                "Advisor/deactivatedRecommendationInstantEmailTitle", "txt", "Advisor deactivated recommendation email title",
                "Advisor/deactivatedRecommendationInstantEmailBody", "html", "Advisor deactivated recommendation email body"
        );
        createInstantEmailTemplate(
                "rhel", "advisor", List.of("new-recommendation"),
                "Advisor/newRecommendationInstantEmailTitle", "txt", "Advisor new recommendation email title",
                "Advisor/newRecommendationInstantEmailBody", "html", "Advisor new recommendation email body"
        );
        createInstantEmailTemplate(
                "rhel", "advisor", List.of("resolved-recommendation"),
                "Advisor/resolvedRecommendationInstantEmailTitle", "txt", "Advisor resolved recommendation email title",
                "Advisor/resolvedRecommendationInstantEmailBody", "html", "Advisor resolved recommendation email body"
        );

        /*
         * Former src/main/resources/templates/AdvisorOpenshift folder.
         */
        getOrCreateTemplate("AdvisorOpenshift/insightsEmailBody", "html", "AdvisorOpenshift Insights email body");
        createInstantEmailTemplate(
                "openshift", "advisor", List.of("new-recommendation"),
                "AdvisorOpenshift/newRecommendationInstantEmailTitle", "txt", "AdvisorOpenshift new recommendation email title",
                "AdvisorOpenshift/newRecommendationInstantEmailBody", "html", "AdvisorOpenshift new recommendation email body"
        );

        /*
         * Former src/main/resources/templates/Ansible folder.
         */
        getOrCreateTemplate("Ansible/insightsEmailBody", "html", "Ansible Insights email body");
        createInstantEmailTemplate(
                "ansible", "reports", List.of("report-available"),
                "Ansible/instantEmailTitle", "txt", "Ansible instant email title",
                "Ansible/instantEmailBody", "html", "Ansible instant email body"
        );

        /*
         * Former src/main/resources/templates/Compliance folder.
         */
        getOrCreateTemplate("Compliance/insightsEmailBody", "html", "Compliance Insights email body");
        createInstantEmailTemplate(
                "rhel", "compliance", List.of("compliance-below-threshold"),
                "Compliance/complianceBelowThresholdEmailTitle", "txt", "Compliance below threshold email title",
                "Compliance/complianceBelowThresholdEmailBody", "html", "Compliance below threshold email body"
        );
        createInstantEmailTemplate(
                "rhel", "compliance", List.of("report-upload-failed"),
                "Compliance/reportUploadFailedEmailTitle", "txt", "Compliance report upload failed email title",
                "Compliance/reportUploadFailedEmailBody", "html", "Compliance report upload failed email body"
        );
        createDailyEmailTemplate(
                "rhel", "compliance",
                "Compliance/dailyEmailTitle", "txt", "Compliance daily email title",
                "Compliance/dailyEmailBody", "html", "Compliance daily email body"
        );

        /*
         * Former src/main/resources/templates/ConsoleNotifications folder.
         */
        createInstantEmailTemplate(
                "console", "notifications", List.of("failed-integration"),
                "ConsoleNotifications/failedIntegrationTitle", "txt", "Notifications failed integration email title",
                "ConsoleNotifications/failedIntegrationBody", "txt", "Notifications failed integration email body"
        );

        /*
         * Former src/main/resources/templates/Drift folder.
         */
        getOrCreateTemplate("Drift/insightsEmailBody", "html", "Drift Insights email body");
        createInstantEmailTemplate(
                "rhel", "drift", List.of("drift-baseline-detected"),
                "Drift/newBaselineDriftInstantEmailTitle", "txt", "Drift new baseline drift email title",
                "Drift/newBaselineDriftInstantEmailBody", "html", "Drift new baseline drift email body"
        );
        createDailyEmailTemplate(
                "rhel", "drift",
                "Drift/dailyEmailTitle", "txt", "Drift daily email title",
                "Drift/dailyEmailBody", "html", "Drift daily email body"
        );

        /*
         * Former src/main/resources/templates/Policies folder.
         */
        getOrCreateTemplate("Policies/insightsEmailBody", "html", "Policies Insights email body");
        createInstantEmailTemplate(
                "rhel", "policies", List.of("policy-triggered"),
                "Policies/instantEmailTitle", "txt", "Policies instant email title",
                "Policies/instantEmailBody", "html", "Policies instant email body"
        );
        createDailyEmailTemplate(
                "rhel", "policies",
                "Policies/dailyEmailTitle", "txt", "Policies daily email title",
                "Policies/dailyEmailBody", "html", "Policies daily email body"
        );

        /*
         * Former src/main/resources/templates/Rbac folder.
         */
        getOrCreateTemplate("Rbac/insightsEmailBody", "html", "Rbac Insights email body");
        createInstantEmailTemplate(
                "console", "rbac", List.of("rh-new-role-available", "rh-platform-default-role-updated", "rh-non-platform-default-role-updated", "custom-role-created", "custom-role-updated", "custom-role-deleted"),
                "Rbac/roleChangeEmailTitle", "txt", "Rbac role change email title",
                "Rbac/roleChangeEmailBody", "html", "Rbac role change email body"
        );
        createInstantEmailTemplate(
                "console", "rbac", List.of("rh-new-role-added-to-default-access", "rh-role-removed-from-default-access", "custom-default-access-updated", "group-created", "group-updated", "group-deleted"),
                "Rbac/groupChangeEmailTitle", "txt", "Rbac group change email title",
                "Rbac/groupChangeEmailBody", "html", "Rbac group change email body"
        );
        createInstantEmailTemplate(
                "console", "rbac", List.of("platform-default-group-turned-into-custom"),
                "Rbac/platformGroup2CustomEmailTitle", "txt", "Rbac platform group 2 custom email title",
                "Rbac/platformGroup2CustomEmailBody", "html", "Rbac platform group 2 custom email body"
        );

        /*
         * Former src/main/resources/templates/Rhosak folder.
         */
        getOrCreateTemplate("Rhosak/rhosakEmailBody", "html", "Rhosak email body");
        createInstantEmailTemplate(
                "application-services", "rhosak", List.of("disruption"),
                "Rhosak/serviceDisruptionTitle", "txt", "Rhosak service disruption email title",
                "Rhosak/serviceDisruptionBody", "html", "Rhosak service disruption email body"
        );
        createInstantEmailTemplate(
                "application-services", "rhosak", List.of("instance-created"),
                "Rhosak/instanceCreatedTitle", "txt", "Rhosak instance created email title",
                "Rhosak/instanceCreatedBody", "html", "Rhosak instance created email body"
        );
        createInstantEmailTemplate(
                "application-services", "rhosak", List.of("instance-deleted"),
                "Rhosak/instanceDeletedTitle", "txt", "Rhosak instance deleted email title",
                "Rhosak/instanceDeletedBody", "html", "Rhosak instance deleted email body"
        );
        createInstantEmailTemplate(
                "application-services", "rhosak", List.of("action-required"),
                "Rhosak/actionRequiredTitle", "txt", "Rhosak action required email title",
                "Rhosak/actionRequiredBody", "html", "Rhosak action required email body"
        );
        createInstantEmailTemplate(
                "application-services", "rhosak", List.of("scheduled-upgrade"),
                "Rhosak/scheduledUpgradeTitle", "txt", "Rhosak scheduled upgrade email title",
                "Rhosak/scheduledUpgradeBody", "html", "Rhosak scheduled upgrade email body"
        );
        createDailyEmailTemplate(
                "application-services", "rhosak",
                "Rhosak/dailyRhosakEmailsTitle", "txt", "Rhosak daily email title",
                "Rhosak/dailyRhosakEmailsBody", "html", "Rhosak daily email body"
        );

        /*
         * Former src/main/resources/templates/Sources folder.
         */
        getOrCreateTemplate("Sources/insightsEmailBody", "html", "Sources Insights email body");
        createInstantEmailTemplate(
                "console", "sources", List.of("availability-status"),
                "Sources/availabilityStatusEmailTitle", "txt", "Sources availability status email title",
                "Sources/availabilityStatusEmailBody", "html", "Sources availability status email body"
        );

        /*
         * Former src/main/resources/templates/Vulnerability folder.
         */
        getOrCreateTemplate("Vulnerability/insightsEmailBody", "html", "Vulnerability Insights email body");
        createInstantEmailTemplate(
                "rhel", "vulnerability", List.of("any-cve-known-exploit"),
                "Vulnerability/anyCveKnownExploitTitle", "txt", "Vulnerability any CVE known exploit email title",
                "Vulnerability/anyCveKnownExploitBody", "html", "Vulnerability any CVE exploit email body"
        );
        createInstantEmailTemplate(
                "rhel", "vulnerability", List.of("new-cve-severity"),
                "Vulnerability/newCveCritSeverityEmailTitle", "txt", "Vulnerability new CVE crit severity email title",
                "Vulnerability/newCveCritSeverityEmailBody", "html", "Vulnerability new CVE crit severity email body"
        );
        createInstantEmailTemplate(
                "rhel", "vulnerability", List.of("new-cve-cvss"),
                "Vulnerability/newCveHighCvssEmailTitle", "txt", "Vulnerability new CVE high cvss email title",
                "Vulnerability/newCveHighCvssEmailBody", "html", "Vulnerability new CVE high cvss email body"
        );
        createInstantEmailTemplate(
                "rhel", "vulnerability", List.of("new-cve-security-rule"),
                "Vulnerability/newCveSecurityRuleTitle", "txt", "Vulnerability new CVE security rule email title",
                "Vulnerability/newCveSecurityRuleBody", "html", "Vulnerability new CVE security rule email body"
        );

        LOGGER.debug("Migration ended");
    }

    /*
     * Creates a template only if it does not already exist in the DB.
     * Existing templates are never updated by this migration service.
     */
    Template getOrCreateTemplate(String name, String extension, String description) {
        try {
            Template template = entityManager.createQuery("FROM Template WHERE name = :name", Template.class)
                    .setParameter("name", name)
                    .getSingleResult();
            LOGGER.infof("Template found in DB: %s", name);
            return template;
        } catch (NoResultException e) {
            LOGGER.infof("Creating template: %s", name);
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
    private void createInstantEmailTemplate(String bundleName, String appName, List<String> eventTypeNames,
            String subjectTemplateName, String subjectTemplateExtension, String subjectTemplateDescription,
            String bodyTemplateName, String bodyTemplateExtension, String bodyTemplateDescription) {

        for (String eventTypeName : eventTypeNames) {
            Optional<EventType> eventType = findEventType(bundleName, appName, eventTypeName);
            if (eventType.isPresent() && !instantEmailTemplateExists(eventType.get())) {

                Template subjectTemplate = getOrCreateTemplate(subjectTemplateName, subjectTemplateExtension, subjectTemplateDescription);
                Template bodyTemplate = getOrCreateTemplate(bodyTemplateName, bodyTemplateExtension, bodyTemplateDescription);

                LOGGER.infof("Creating instant email template for event type: %s/%s/%s", bundleName, appName, eventTypeName);

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

    private Optional<EventType> findEventType(String bundleName, String appName, String eventTypeName) {
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
            LOGGER.infof("Event type not found: %s/%s/%s", bundleName, appName, eventTypeName);
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
    private void createDailyEmailTemplate(String bundleName, String appName,
                                          String subjectTemplateName, String subjectTemplateExtension, String subjectTemplateDescription,
                                          String bodyTemplateName, String bodyTemplateExtension, String bodyTemplateDescription) {

        Optional<Application> app = findApplication(bundleName, appName);
        if (app.isPresent() && !aggregationEmailTemplateExists(app.get())) {

            Template subjectTemplate = getOrCreateTemplate(subjectTemplateName, subjectTemplateExtension, subjectTemplateDescription);
            Template bodyTemplate = getOrCreateTemplate(bodyTemplateName, bodyTemplateExtension, bodyTemplateDescription);

            LOGGER.infof("Creating daily email template for application: %s/%s", bundleName, appName);

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

    private Optional<Application> findApplication(String bundleName, String appName) {
        String hql = "FROM Application WHERE name = :appName AND bundle.name = :bundleName";
        try {
            Application app = entityManager.createQuery(hql, Application.class)
                    .setParameter("appName", appName)
                    .setParameter("bundleName", bundleName)
                    .getSingleResult();
            return Optional.of(app);
        } catch (NoResultException e) {
            LOGGER.infof("Application not found: %s/%s", bundleName, appName);
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
