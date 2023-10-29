package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.IntegrationTemplate;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.models.Template;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.events.ConnectorReceiver.INTEGRATION_FAILED_EVENT_TYPE;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.INTEGRATION_DISABLED_EVENT_TYPE;
import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
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
    FeatureFlipper featureFlipper;

    @Test
    void testMigration() {
        /*
         * Bundle: rhel
         */
        Bundle rhel = findOrCreateBundle("rhel");
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
        // App: edge-management
        Application edgeManagement = resourceHelpers.createApp(rhel.getId(), "edge-management");
        EventType imageCreation = resourceHelpers.createEventType(edgeManagement.getId(), "image-creation");
        EventType updateDevices = resourceHelpers.createEventType(edgeManagement.getId(), "update-devices");
        // App: inventory
        Application inventory = resourceHelpers.createApp(rhel.getId(), "inventory");
        EventType inventoryValidationError = resourceHelpers.createEventType(inventory.getId(), "validation-error");
        // App: malware-detection
        Application malwareDetection = resourceHelpers.createApp(rhel.getId(), "malware-detection");
        EventType detectedMalware = resourceHelpers.createEventType(malwareDetection.getId(), "detected-malware");
        // App: patch
        Application patch = resourceHelpers.createApp(rhel.getId(), "patch");
        EventType newAdvisories = resourceHelpers.createEventType(patch.getId(), "new-advisory");
        // App: policies
        Application policies = findOrCreateApplication("policies", rhel);
        EventType policyTriggered = findOrCreateEventType("policy-triggered", policies);
        // App: resource-optimization
        Application resourceOptimization = resourceHelpers.createApp(rhel.getId(), "resource-optimization");
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
        // App: cost-management
        Application costManagement = resourceHelpers.createApp(openshift.getId(), "cost-management");
        EventType missingCostModel = resourceHelpers.createEventType(costManagement.getId(), "missing-cost-model");
        EventType costModelCreate = resourceHelpers.createEventType(costManagement.getId(), "cost-model-create");
        EventType costModelUpdate = resourceHelpers.createEventType(costManagement.getId(), "cost-model-update");
        EventType costModelRemove = resourceHelpers.createEventType(costManagement.getId(), "cost-model-remove");
        EventType operatorStale = resourceHelpers.createEventType(costManagement.getId(), "cm-operator-stale");
        EventType operatorDataProcessed = resourceHelpers.createEventType(costManagement.getId(), "cm-operator-data-processed");
        EventType operatorDataReceived = resourceHelpers.createEventType(costManagement.getId(), "cm-operator-data-received");

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
        Bundle console = findOrCreateBundle("console");
        // App: integrations
        Application integrations = findOrCreateApplication("integrations", console);
        EventType integrationFailed = findOrCreateEventType(INTEGRATION_FAILED_EVENT_TYPE, integrations);
        EventType integrationDisabled = findOrCreateEventType(INTEGRATION_DISABLED_EVENT_TYPE, integrations);
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

        /*
         * Bundle: rhel
         */
        // App: advisor
        findAndCompileInstantEmailTemplate(newRecommendation.getId());
        findAndCompileInstantEmailTemplate(resolvedRecommendation.getId());
        findAndCompileInstantEmailTemplate(deactivatedRecommendation.getId());
        findAndCompileAggregationEmailTemplate(rhel.getName(), advisor.getName(), DAILY);

        // App: compliance
        findAndCompileInstantEmailTemplate(complianceBelowThreshold.getId());
        findAndCompileInstantEmailTemplate(reportUploadFailed.getId());
        findAndCompileAggregationEmailTemplate(rhel.getName(), compliance.getName(), DAILY);
        // App: drift
        findAndCompileInstantEmailTemplate(driftBaselineDetected.getId());
        findAndCompileAggregationEmailTemplate(rhel.getName(), drift.getName(), DAILY);
        // App: edge-management
        findAndCompileInstantEmailTemplate(imageCreation.getId());
        findAndCompileInstantEmailTemplate(updateDevices.getId());
        // App: inventory
        findAndCompileInstantEmailTemplate(inventoryValidationError.getId());
        findAndCompileAggregationEmailTemplate(rhel.getName(), inventory.getName(), DAILY);
        // App: malware-detection
        findAndCompileInstantEmailTemplate(detectedMalware.getId());
        assertTrue(templateRepository.findAggregationEmailTemplate(rhel.getName(), malwareDetection.getName(), DAILY).isEmpty());
        // App: patch
        findAndCompileInstantEmailTemplate(newAdvisories.getId());
        findAndCompileAggregationEmailTemplate(rhel.getName(), patch.getName(), DAILY);
        // App: policies
        findAndCompileInstantEmailTemplate(policyTriggered.getId());
        findAndCompileAggregationEmailTemplate(rhel.getName(), policies.getName(), DAILY);
        // App: resource-optimization
        findAndCompileAggregationEmailTemplate(rhel.getName(), resourceOptimization.getName(), DAILY);
        // App: vulnerability
        findAndCompileInstantEmailTemplate(newCveCvss.getId());
        findAndCompileInstantEmailTemplate(newCveSecurityRule.getId());
        findAndCompileInstantEmailTemplate(newCveSeverity.getId());
        findAndCompileInstantEmailTemplate(anyCveKnownExploit.getId());
        findAndCompileAggregationEmailTemplate(rhel.getName(), vulnerability.getName(), DAILY);

        /*
         * Bundle: openshift
         */
        // App: advisor
        findAndCompileInstantEmailTemplate(newRecommendationOpenshift.getId());
        assertTrue(templateRepository.findAggregationEmailTemplate(openshift.getName(), advisorOpenshift.getName(), DAILY).isEmpty());
        // App: cost-management
        findAndCompileInstantEmailTemplate(missingCostModel.getId());
        findAndCompileInstantEmailTemplate(costModelCreate.getId());
        findAndCompileInstantEmailTemplate(costModelUpdate.getId());
        findAndCompileInstantEmailTemplate(costModelRemove.getId());
        findAndCompileInstantEmailTemplate(operatorStale.getId());
        findAndCompileInstantEmailTemplate(operatorDataProcessed.getId());
        findAndCompileInstantEmailTemplate(operatorDataReceived.getId());
        assertTrue(templateRepository.findAggregationEmailTemplate(openshift.getName(), costManagement.getName(), DAILY).isEmpty());

        /*
         * Bundle: ansible
         */
        // App: reports
        findAndCompileInstantEmailTemplate(reportAvailable.getId());
        assertTrue(templateRepository.findAggregationEmailTemplate(ansible.getName(), reports.getName(), DAILY).isEmpty());

        /*
         * Bundle: console
         */
        // App: integrations
        findAndCompileInstantEmailTemplate(integrationFailed.getId());
        findAndCompileInstantEmailTemplate(integrationDisabled.getId());
        assertTrue(templateRepository.findAggregationEmailTemplate(console.getName(), integrations.getName(), DAILY).isEmpty());
        // App: sources
        findAndCompileInstantEmailTemplate(availabilityStatus.getId());
        assertTrue(templateRepository.findAggregationEmailTemplate(console.getName(), sources.getName(), DAILY).isEmpty());
        // App: rbac
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

        clearDbTemplates();
    }

    private void clearDbTemplates() {
        given()
                .basePath(API_INTERNAL)
                .when().delete("/template-engine/migrate")
                .then()
                .statusCode(204);
        assertDbEmpty(InstantEmailTemplate.class, AggregationEmailTemplate.class);
        assertDbEquals(Template.class, IntegrationTemplate.class);
    }

    private void assertDbEmpty(Class<?>... entityClasses) {
        for (Class<?> entityClass : entityClasses) {
            long count = entityManager.createQuery("SELECT COUNT(*) FROM " + entityClass.getSimpleName(), Long.class)
                    .getSingleResult();
            assertEquals(0, count);
        }
    }

    private void assertDbEquals(Class entityClass, Class otherEntityClass) {
        long count = entityManager.createQuery("SELECT COUNT(*) FROM " + entityClass.getSimpleName(), Long.class)
                .getSingleResult();
        long countOtherClass = entityManager.createQuery("SELECT COUNT(*) FROM " + otherEntityClass.getSimpleName(), Long.class)
            .getSingleResult();
        assertEquals(count, countOtherClass);
    }

    private void findAndCompileInstantEmailTemplate(UUID eventTypeId) {
        InstantEmailTemplate emailTemplate = templateRepository.findInstantEmailTemplate(eventTypeId).get();
        templateService.compileTemplate(emailTemplate.getSubjectTemplate().getData(), emailTemplate.getSubjectTemplate().getName());
        templateService.compileTemplate(emailTemplate.getBodyTemplate().getData(), emailTemplate.getBodyTemplate().getName());
    }

    private void findAndCompileAggregationEmailTemplate(String bundleName, String appName, SubscriptionType subscriptionType) {
        AggregationEmailTemplate emailTemplate = templateRepository.findAggregationEmailTemplate(bundleName, appName, subscriptionType).get();
        templateService.compileTemplate(emailTemplate.getSubjectTemplate().getData(), emailTemplate.getSubjectTemplate().getName());
        templateService.compileTemplate(emailTemplate.getBodyTemplate().getData(), emailTemplate.getBodyTemplate().getName());
    }

    private Bundle findOrCreateBundle(String bundleName) {
        try {
            return resourceHelpers.findBundle(bundleName);
        } catch (NoResultException nre) {
            return resourceHelpers.createBundle(bundleName);
        }
    }

    private Application findOrCreateApplication(String applicationName, Bundle bundle) {
        try {
            return resourceHelpers.findApp(bundle.getName(), applicationName);
        } catch (NoResultException nre) {
            return resourceHelpers.createApp(bundle.getId(), applicationName);
        }
    }

    private EventType findOrCreateEventType(String eventType, Application application) {
        try {
            return resourceHelpers.findEventType(application.getId(), eventType);
        } catch (NoResultException nre) {
            return resourceHelpers.createEventType(application.getId(), eventType);
        }
    }
}
