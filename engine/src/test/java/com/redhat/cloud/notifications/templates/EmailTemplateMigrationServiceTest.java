package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.Template;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;

import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static com.redhat.cloud.notifications.templates.TemplateService.USE_TEMPLATES_FROM_DB_KEY;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EmailTemplateMigrationServiceTest {

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EntityManager entityManager;

    @Inject
    TemplateRepository templateRepository;

    @Inject
    TemplateService templateService;

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    @BeforeEach
    void beforeEach() {
        System.setProperty(USE_TEMPLATES_FROM_DB_KEY, "true");
    }

    @AfterEach
    void afterEach() {
        System.clearProperty(USE_TEMPLATES_FROM_DB_KEY);
    }

    @Test
    void testMigration() {
        /*
         * Bundle: rhel
         */
        Bundle rhel = findBundle("rhel");
        // App: advisor
        Application advisor = resourceHelpers.createApp(rhel.getId(), "advisor");
        EventType newRecommendation = resourceHelpers.createEventType(advisor.getId(), "new-recommendation");
        EventType resolvedRecommendation = resourceHelpers.createEventType(advisor.getId(), "resolved-recommendation");
        EventType deactivatedRecommendation = resourceHelpers.createEventType(advisor.getId(), "deactivated-recommendation");
        // App: compliance
        Application compliance = resourceHelpers.createApp(rhel.getId(), "compliance");
        EventType complianceBelowThreshold = resourceHelpers.createEventType(compliance.getId(), "compliance-below-threshold");
        EventType reportUploadFailed = resourceHelpers.createEventType(compliance.getId(), "report-upload-failed");
        // App: drift
        Application drift = resourceHelpers.createApp(rhel.getId(), "drift");
        EventType driftBaselineDetected = resourceHelpers.createEventType(drift.getId(), "drift-baseline-detected");
        // App: policies
        Application policies = findApp("rhel", "policies");
        EventType policyTriggered = findEventType("rhel", "policies", "policy-triggered");
        // App: vulnerability
        Application vulnerability = resourceHelpers.createApp(rhel.getId(), "vulnerability");
        EventType newCveCvss = resourceHelpers.createEventType(vulnerability.getId(), "new-cve-cvss");
        EventType newCveSeverity = resourceHelpers.createEventType(vulnerability.getId(), "new-cve-severity");
        EventType newCveSecurityRule = resourceHelpers.createEventType(vulnerability.getId(), "new-cve-security-rule");
        EventType anyCveKnownExploit = resourceHelpers.createEventType(vulnerability.getId(), "any-cve-known-exploit");

        /*
         * Bundle: openshift
         */
        Bundle openshift = resourceHelpers.createBundle("openshift");
        // App: advisor
        Application advisorOpenshift = resourceHelpers.createApp(openshift.getId(), "advisor");
        EventType newRecommendationOpenshift = resourceHelpers.createEventType(advisorOpenshift.getId(), "new-recommendation");

        /*
         * Bundle: application-services
         */
        Bundle applicationServices = resourceHelpers.createBundle("application-services");
        // App: rhosak
        Application rhosak = resourceHelpers.createApp(applicationServices.getId(), "rhosak");
        EventType scheduledUpgrade = resourceHelpers.createEventType(rhosak.getId(), "scheduled-upgrade");
        EventType disruption = resourceHelpers.createEventType(rhosak.getId(), "disruption");
        EventType instanceCreated = resourceHelpers.createEventType(rhosak.getId(), "instance-created");
        EventType instanceDeleted = resourceHelpers.createEventType(rhosak.getId(), "instance-deleted");
        EventType actionRequired = resourceHelpers.createEventType(rhosak.getId(), "action-required");

        /*
         * Bundle: ansible
         */
        Bundle ansible = resourceHelpers.createBundle("ansible");
        // App: reports
        Application reports = resourceHelpers.createApp(ansible.getId(), "reports");
        EventType reportAvailable = resourceHelpers.createEventType(reports.getId(), "report-available");

        /*
         * Bundle: console
         */
        Bundle console = findBundle("console");
        // App: notifications
        Application notifications = findApp("console", "notifications");
        EventType failedIntegration = resourceHelpers.createEventType(notifications.getId(), "failed-integration");
        // App: sources
        Application sources = resourceHelpers.createApp(console.getId(), "sources");
        EventType availabilityStatus = resourceHelpers.createEventType(sources.getId(), "availability-status");
        // App: rbac
        Application rbac = resourceHelpers.createApp(console.getId(), "rbac");
        EventType rhNewRoleAvailable = resourceHelpers.createEventType(rbac.getId(), "rh-new-role-available");
        EventType rhPlatformDefaultRoleUpdated = resourceHelpers.createEventType(rbac.getId(), "rh-platform-default-role-updated");
        EventType rhNonPlatformDefaultRoleUpdated = resourceHelpers.createEventType(rbac.getId(), "rh-non-platform-default-role-updated");
        EventType customRoleCreated = resourceHelpers.createEventType(rbac.getId(), "custom-role-created");
        EventType customRoleUpdated = resourceHelpers.createEventType(rbac.getId(), "custom-role-updated");
        EventType customRoleDeleted = resourceHelpers.createEventType(rbac.getId(), "custom-role-deleted");
        EventType rhNewRoleAddedToDefaultAccess = resourceHelpers.createEventType(rbac.getId(), "rh-new-role-added-to-default-access");
        EventType rhRoleRemovedFromDefaultAccess = resourceHelpers.createEventType(rbac.getId(), "rh-role-removed-from-default-access");
        EventType customDefaultAccessUpdated = resourceHelpers.createEventType(rbac.getId(), "custom-default-access-updated");
        EventType groupCreated = resourceHelpers.createEventType(rbac.getId(), "group-created");
        EventType groupUpdated = resourceHelpers.createEventType(rbac.getId(), "group-updated");
        EventType groupDeleted = resourceHelpers.createEventType(rbac.getId(), "group-deleted");
        EventType platformDefaultGroupTurnedIntoCustom = resourceHelpers.createEventType(rbac.getId(), "platform-default-group-turned-into-custom");

        clearDbTemplates();

        given()
                .basePath(API_INTERNAL)
                .when()
                .put("/template-engine/migrate")
                .then()
                .statusCode(200)
                .contentType(JSON);

        statelessSessionFactory.withSession(statelessSession -> {

            /*
             * Bundle: rhel
             */
            // App: advisor
            findAndCompileInstantEmailTemplate(newRecommendation.getId());
            findAndCompileInstantEmailTemplate(resolvedRecommendation.getId());
            findAndCompileInstantEmailTemplate(deactivatedRecommendation.getId());
            assertTrue(templateRepository.findAggregationEmailTemplate(rhel.getName(), advisor.getName(), DAILY).isEmpty());
            // App: compliance
            findAndCompileInstantEmailTemplate(complianceBelowThreshold.getId());
            findAndCompileInstantEmailTemplate(reportUploadFailed.getId());
            findAndCompileAggregationEmailTemplate(rhel.getName(), compliance.getName(), DAILY);
            // App: drift
            findAndCompileInstantEmailTemplate(driftBaselineDetected.getId());
            findAndCompileAggregationEmailTemplate(rhel.getName(), drift.getName(), DAILY);
            // App: policies
            findAndCompileInstantEmailTemplate(policyTriggered.getId());
            findAndCompileAggregationEmailTemplate(rhel.getName(), policies.getName(), DAILY);
            // App: vulnerability
            findAndCompileInstantEmailTemplate(newCveCvss.getId());
            findAndCompileInstantEmailTemplate(newCveSecurityRule.getId());
            findAndCompileInstantEmailTemplate(newCveSeverity.getId());
            findAndCompileInstantEmailTemplate(anyCveKnownExploit.getId());
            assertTrue(templateRepository.findAggregationEmailTemplate(rhel.getName(), vulnerability.getName(), DAILY).isEmpty());

            /*
             * Bundle: openshift
             */
            // App: advisor
            findAndCompileInstantEmailTemplate(newRecommendationOpenshift.getId());
            assertTrue(templateRepository.findAggregationEmailTemplate(openshift.getName(), advisorOpenshift.getName(), DAILY).isEmpty());

            /*
             * Bundle: application-services
             */
            // App: rhosak
            findAndCompileInstantEmailTemplate(scheduledUpgrade.getId());
            findAndCompileInstantEmailTemplate(disruption.getId());
            findAndCompileInstantEmailTemplate(instanceCreated.getId());
            findAndCompileInstantEmailTemplate(instanceDeleted.getId());
            findAndCompileInstantEmailTemplate(actionRequired.getId());
            findAndCompileAggregationEmailTemplate(applicationServices.getName(), rhosak.getName(), DAILY);

            /*
             * Bundle: ansible
             */
            // App: reports
            findAndCompileInstantEmailTemplate(reportAvailable.getId());
            assertTrue(templateRepository.findAggregationEmailTemplate(ansible.getName(), reports.getName(), DAILY).isEmpty());

            /*
             * Bundle: console
             */
            // App: notifications
            findAndCompileInstantEmailTemplate(failedIntegration.getId());
            assertTrue(templateRepository.findAggregationEmailTemplate(console.getName(), notifications.getName(), DAILY).isEmpty());
            // App: sources
            findAndCompileInstantEmailTemplate(availabilityStatus.getId());
            assertTrue(templateRepository.findAggregationEmailTemplate(console.getName(), sources.getName(), DAILY).isEmpty());
            // App: rbac
            /*
             TODO Include changes from https://github.com/RedHatInsights/notifications-backend/pull/1173 into this code.
            findAndCompileInstantEmailTemplate(rhNewRoleAvailable.getId());
            findAndCompileInstantEmailTemplate(rhPlatformDefaultRoleUpdated.getId());
            findAndCompileInstantEmailTemplate(rhNonPlatformDefaultRoleUpdated.getId());
            findAndCompileInstantEmailTemplate(customRoleCreated.getId());
            findAndCompileInstantEmailTemplate(customRoleUpdated.getId());
            findAndCompileInstantEmailTemplate(customRoleDeleted.getId());
            findAndCompileInstantEmailTemplate(rhNewRoleAddedToDefaultAccess.getId());
            findAndCompileInstantEmailTemplate(rhRoleRemovedFromDefaultAccess.getId());
            findAndCompileInstantEmailTemplate(customDefaultAccessUpdated.getId());
            findAndCompileInstantEmailTemplate(groupCreated.getId());
            findAndCompileInstantEmailTemplate(groupUpdated.getId());
            findAndCompileInstantEmailTemplate(groupDeleted.getId());
            findAndCompileInstantEmailTemplate(platformDefaultGroupTurnedIntoCustom.getId());
            assertTrue(templateRepository.findAggregationEmailTemplate(console.getName(), rbac.getName(), DAILY).isEmpty());
             */

        });

        clearDbTemplates();
    }

    private Bundle findBundle(String name) {
        return entityManager.createQuery("FROM Bundle WHERE name = :name", Bundle.class)
                .setParameter("name", name)
                .getSingleResult();
    }

    private Application findApp(String bundleName, String appName) {
        return entityManager.createQuery("FROM Application WHERE name = :appName AND bundle.name = :bundleName", Application.class)
                .setParameter("appName", appName)
                .setParameter("bundleName", bundleName)
                .getSingleResult();
    }

    private EventType findEventType(String bundleName, String appName, String eventTypeName) {
        return entityManager.createQuery("FROM EventType WHERE name = :eventTypeName AND application.name = :appName AND application.bundle.name = :bundleName", EventType.class)
                .setParameter("eventTypeName", eventTypeName)
                .setParameter("appName", appName)
                .setParameter("bundleName", bundleName)
                .getSingleResult();
    }

    private void clearDbTemplates() {
        given()
                .basePath(API_INTERNAL)
                .when().delete("/template-engine/migrate")
                .then()
                .statusCode(204);
        assertDbEmpty(Template.class, InstantEmailTemplate.class, AggregationEmailTemplate.class);
    }

    private void assertDbEmpty(Class<?>... entityClasses) {
        for (Class<?> entityClass : entityClasses) {
            long count = entityManager.createQuery("SELECT COUNT(*) FROM " + entityClass.getSimpleName(), Long.class)
                    .getSingleResult();
            assertEquals(0, count);
        }
    }

    private void findAndCompileInstantEmailTemplate(UUID eventTypeId) {
        InstantEmailTemplate emailTemplate = templateRepository.findInstantEmailTemplate(eventTypeId).get();
        templateService.compileTemplate(emailTemplate.getSubjectTemplate().getData(), emailTemplate.getSubjectTemplate().getName());
        templateService.compileTemplate(emailTemplate.getBodyTemplate().getData(), emailTemplate.getBodyTemplate().getName());
    }

    private void findAndCompileAggregationEmailTemplate(String bundleName, String appName, EmailSubscriptionType subscriptionType) {
        AggregationEmailTemplate emailTemplate = templateRepository.findAggregationEmailTemplate(bundleName, appName, subscriptionType).get();
        templateService.compileTemplate(emailTemplate.getSubjectTemplate().getData(), emailTemplate.getSubjectTemplate().getName());
        templateService.compileTemplate(emailTemplate.getBodyTemplate().getData(), emailTemplate.getBodyTemplate().getName());
    }
}
