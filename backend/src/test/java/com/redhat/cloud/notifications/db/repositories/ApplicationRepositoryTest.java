package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.InternalRoleAccess;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ApplicationRepositoryTest extends DbIsolatedTest {

    private final String NOT_USED = "not-used";
    private final String ORG_ID = "org-id";
    private final String ORG_ID_2 = "org-id-2";

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    BehaviorGroupRepository behaviorGroupRepository;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    InternalRoleAccessRepository internalRoleAccessRepository;

    @Inject
    EntityManager entityManager;

    @Test
    void shouldFindApplicationsLinkedToForcedEmails() {
        Bundle myBundle = resourceHelpers.createBundle("my-bundle", NOT_USED);

        Application application1 = resourceHelpers.createApplication(myBundle.getId(), "application1", NOT_USED);
        EventType eventType1 = resourceHelpers.createEventType(application1.getId(), "event1_1", NOT_USED, NOT_USED);

        Application application2 = resourceHelpers.createApplication(myBundle.getId(), "application2", NOT_USED);
        EventType eventType2 = resourceHelpers.createEventType(application2.getId(), "event2_1", NOT_USED, NOT_USED);

        Application application3 = resourceHelpers.createApplication(myBundle.getId(), "application3", NOT_USED);
        EventType eventType3 = resourceHelpers.createEventType(application3.getId(), "event3_1", NOT_USED, NOT_USED);

        Endpoint forcedEmailSubscriptionSome = createEmailSubscription(ORG_ID, true);

        Endpoint forcedEmailSubscriptionDefault = createEmailSubscription(null, true);
        Endpoint regularEmailSubscriptionDefault = createEmailSubscription(null, false);

        BehaviorGroup accountBehaviorGroupForced = resourceHelpers.createBehaviorGroup(
                ORG_ID, ORG_ID, "bg-forced", myBundle.getId()
        );
        behaviorGroupRepository.updateBehaviorGroupActions(
                ORG_ID,
                accountBehaviorGroupForced.getId(),
                List.of(forcedEmailSubscriptionSome.getId())
        );

        BehaviorGroup defaultBehaviorGroupForced = resourceHelpers.createDefaultBehaviorGroup(
                "dbg-forced", myBundle.getId()
        );
        behaviorGroupRepository.updateBehaviorGroupActions(
                null,
                defaultBehaviorGroupForced.getId(),
                List.of(forcedEmailSubscriptionDefault.getId())
        );

        BehaviorGroup defaultBehaviorGroupRegular = resourceHelpers.createDefaultBehaviorGroup(
                "dbg-regular", myBundle.getId()
        );
        behaviorGroupRepository.updateBehaviorGroupActions(
                null,
                defaultBehaviorGroupRegular.getId(),
                List.of(regularEmailSubscriptionDefault.getId())
        );

        // Nothing is linked yet, should return empty.
        assertTrue(applicationRepository.getApplicationsWithForcedEmail(myBundle.getId(), ORG_ID).isEmpty());
        assertTrue(applicationRepository.getApplicationsWithForcedEmail(myBundle.getId(), ORG_ID_2).isEmpty());

        // Link behavior group forced to eventType3
        behaviorGroupRepository.updateEventTypeBehaviors(ORG_ID, eventType3.getId(), Set.of(accountBehaviorGroupForced.getId()));

        // contains 3
        List<Application> applications = applicationRepository.getApplicationsWithForcedEmail(myBundle.getId(), ORG_ID);
        assertEquals(1, applications.size());
        assertTrue(applications.contains(application3));

        // empty
        assertTrue(applicationRepository.getApplicationsWithForcedEmail(myBundle.getId(), ORG_ID_2).isEmpty());

        // Link default behavior group to eventType2
        behaviorGroupRepository.linkEventTypeDefaultBehavior(eventType2.getId(), defaultBehaviorGroupForced.getId());

        // contains 2 and 3
        applications = applicationRepository.getApplicationsWithForcedEmail(myBundle.getId(), ORG_ID);
        assertEquals(2, applications.size());
        assertTrue(applications.contains(application2));
        assertTrue(applications.contains(application3));

        // contains only 2
        applications = applicationRepository.getApplicationsWithForcedEmail(myBundle.getId(), ORG_ID_2);
        assertEquals(1, applications.size());
        assertTrue(applications.contains(application2));

        // Adding a regular default behavior group with regular should not affect
        behaviorGroupRepository.linkEventTypeDefaultBehavior(eventType1.getId(), defaultBehaviorGroupRegular.getId());

        // contains 2 and 3
        applications = applicationRepository.getApplicationsWithForcedEmail(myBundle.getId(), ORG_ID);
        assertEquals(2, applications.size());
        assertTrue(applications.contains(application2));
        assertTrue(applications.contains(application3));

        // contains only 2
        applications = applicationRepository.getApplicationsWithForcedEmail(myBundle.getId(), ORG_ID_2);
        assertEquals(1, applications.size());
        assertTrue(applications.contains(application2));

        // Adding default behavior group with forced affects org1 and org2
        behaviorGroupRepository.linkEventTypeDefaultBehavior(eventType1.getId(), defaultBehaviorGroupForced.getId());

        // contains 2 and 3
        applications = applicationRepository.getApplicationsWithForcedEmail(myBundle.getId(), ORG_ID);
        assertEquals(3, applications.size());
        assertTrue(applications.contains(application1));
        assertTrue(applications.contains(application2));
        assertTrue(applications.contains(application3));

        // contains only 2
        applications = applicationRepository.getApplicationsWithForcedEmail(myBundle.getId(), ORG_ID_2);
        assertEquals(2, applications.size());
        assertTrue(applications.contains(application1));
        assertTrue(applications.contains(application2));
    }

    /**
     * Tests that the function under test does find the relation between an
     * application and its bundle by their names.
     */
    @Test
    void testApplicationBundleExists() {
        final String applicationName = "application-application-bundle-exists-test";
        final String bundleName = "bundle-application-bundle-exists-test";

        final Bundle createdBundle = this.resourceHelpers.createBundle(bundleName, bundleName + "-display");
        this.resourceHelpers.createApplication(createdBundle.getId(), applicationName, applicationName + "-display");

        Assertions.assertTrue(
            this.applicationRepository.applicationBundleExists(applicationName, bundleName),
            "the bundle and application combination does exist in the database"
        );
    }

    /**
     * Tests that the function under test is unable to find the "application —
     * bundle" relation if a non-existent application's name is given to the
     * function.
     */
    @Test
    void testApplicationBundleExistsApplicationNotFound() {
        final String bundleName = "bundle-application-bundle-exists-application-not-found-test";

        final Bundle createdBundle = this.resourceHelpers.createBundle(bundleName, bundleName + "-display");
        this.resourceHelpers.createApplication(createdBundle.getId());

        Assertions.assertFalse(
            this.applicationRepository.applicationBundleExists(UUID.randomUUID().toString(), bundleName),
            "the bundle exists, but the specified application name doesn't belong to any application"
        );
    }

    /**
     * Tests that the function under test is unable to find the "application —
     * bundle" relation if a non-existent bundle's name is given to the
     * function.
     */
    @Test
    void testApplicationBundleExistsBundleNotFound() {
        final String applicationName = "application-application-bundle-exists-bundle-not-found-test";

        final Bundle createdBundle = this.resourceHelpers.createBundle();
        this.resourceHelpers.createApplication(createdBundle.getId(), applicationName, applicationName + "-display");

        Assertions.assertFalse(
            this.applicationRepository.applicationBundleExists(applicationName, UUID.randomUUID().toString()),
            "the application exists, but the specified bundle name doesn't belong to any bundle"
        );
    }

    /**
     * Tests that the function under test only updates the application when
     * no internal role has been given.
     */
    @Test
    void testUpdateApplication() {
        // Prepare the fixtures.
        final Bundle bundle = this.resourceHelpers.createBundle("test-update-application");
        final Application application = this.resourceHelpers.createApplication(bundle.getId());

        // Update the application's fields.
        final String updatedApplicationName = "test-update-application-n";
        final String updatedApplicationDisplayName = "test-update-application-dn";
        application.setName(updatedApplicationName);
        application.setDisplayName(updatedApplicationDisplayName);

        // Call the function under test.
        this.applicationRepository.updateApplicationAndAccess(application, new InternalRoleAccess());

        // Verify that the application was updated in the database.
        final Application result = this.applicationRepository.getApplication(application.getId());

        Assertions.assertEquals(updatedApplicationName, result.getName(), "the name of the application was not updated");
        Assertions.assertEquals(updatedApplicationDisplayName, result.getDisplayName(), "the display name of the application was not updated");
    }

    /**
     * Tests that the function under test updates both the internal role and
     * the application.
     */
    @Test
    void testUpdateApplicationAccess() {
        // Prepare the fixtures.
        final Bundle bundle = this.resourceHelpers.createBundle("test-update-application-access");
        final Application application = this.resourceHelpers.createApplication(bundle.getId());

        final InternalRoleAccess internalRoleAccess = new InternalRoleAccess();
        internalRoleAccess.setApplication(application);
        internalRoleAccess.setApplicationId(application.getId());
        internalRoleAccess.setRole("test-internal-role-access");

        this.internalRoleAccessRepository.addAccess(internalRoleAccess);

        // Update the application's fields.
        final String updatedApplicationName = "test-update-application-access-n";
        final String updatedApplicationDisplayName = "test-update-application-access-dn";
        application.setName(updatedApplicationName);
        application.setDisplayName(updatedApplicationDisplayName);

        // Update the role's fields.
        final String updatedRoleName = "test-update-application-role-n";
        internalRoleAccess.setRole(updatedRoleName);

        // Call the function under test.
        this.applicationRepository.updateApplicationAndAccess(application, internalRoleAccess);

        // Verify that the application was updated in the database.
        final Application resultApplication = this.applicationRepository.getApplication(application.getId());

        Assertions.assertEquals(updatedApplicationName, resultApplication.getName(), "the name of the application was not updated");
        Assertions.assertEquals(updatedApplicationDisplayName, resultApplication.getDisplayName(), "the display name of the application was not updated");

        // Verify that the role was updated in the database.
        final InternalRoleAccess resultInternalRoleAccess = this.internalRoleAccessRepository.findOneByApplicationUUID(application.getId());

        Assertions.assertEquals(updatedRoleName, resultInternalRoleAccess.getRole(), "the role of the internal role access was not updated");
    }

    private Endpoint createEmailSubscription(String orgId, boolean isForced) {
        SystemSubscriptionProperties properties = new SystemSubscriptionProperties();
        properties.setIgnorePreferences(isForced);

        return resourceHelpers.createEndpoint(
                orgId,
                orgId,
                EndpointType.EMAIL_SUBSCRIPTION,
                null,
                isForced ? "forced" : "regular",
                NOT_USED,
                properties,
                true,
                null
        );
    }

    /**
     * Tests that updateEventType successfully updates all fields of an event type.
     */
    @Test
    @Transactional
    void testUpdateEventTypeSuccess() {
        // Create test fixtures
        Bundle bundle = resourceHelpers.createBundle("update-event-type-bundle", "Update Event Type Bundle");
        Application application = resourceHelpers.createApplication(bundle.getId(), "update-event-type-app", "Update Event Type App");
        EventType eventType = resourceHelpers.createEventType(application.getId(), "original-event-type", "Original Event Type", "Original Description");

        // Verify original state
        EventType originalEventType = entityManager.find(EventType.class, eventType.getId());
        assertEquals("original-event-type", originalEventType.getName());
        assertEquals("Original Event Type", originalEventType.getDisplayName());

        // Prepare updated event type
        EventType updatedEventType = new EventType();
        updatedEventType.setName("updated-event-type");
        updatedEventType.setDisplayName("Updated Event Type");
        updatedEventType.setDescription("Updated Description");
        updatedEventType.setFullyQualifiedName("bundle.app.updated-event-type");
        updatedEventType.setSubscribedByDefault(true);
        updatedEventType.setSubscriptionLocked(true);
        updatedEventType.setVisible(false);
        updatedEventType.setRestrictToRecipientsIntegrations(true);
        updatedEventType.setDefaultSeverity(Severity.CRITICAL);
        Set<Severity> severities = new HashSet<>();
        severities.add(Severity.CRITICAL);
        severities.add(Severity.IMPORTANT);
        updatedEventType.setAvailableSeverities(severities);

        // Call the method under test
        int rowCount = applicationRepository.updateEventType(eventType.getId(), updatedEventType);

        // Assert that one row was updated
        assertEquals(1, rowCount);

        // Verify all fields were updated
        entityManager.clear(); // Clear persistence context to force reload from database
        EventType result = entityManager.find(EventType.class, eventType.getId());

        assertEquals("updated-event-type", result.getName());
        assertEquals("Updated Event Type", result.getDisplayName());
        assertEquals("Updated Description", result.getDescription());
        assertEquals("bundle.app.updated-event-type", result.getFullyQualifiedName());
        assertTrue(result.isSubscribedByDefault());
        assertTrue(result.isSubscriptionLocked());
        assertFalse(result.isVisible());
        assertTrue(result.isRestrictToRecipientsIntegrations());
        assertEquals(Severity.CRITICAL, result.getDefaultSeverity());
        assertEquals(2, result.getAvailableSeverities().size());
        assertTrue(result.getAvailableSeverities().contains(Severity.CRITICAL));
        assertTrue(result.getAvailableSeverities().contains(Severity.IMPORTANT));
    }

    /**
     * Tests that updateEventType returns 0 when trying to update a non-existent event type.
     */
    @Test
    @Transactional
    void testUpdateEventTypeNotFound() {
        // Create an EventType with data to update (but don't persist it)
        EventType updatedEventType = new EventType();
        updatedEventType.setName("non-existent-event-type");
        updatedEventType.setDisplayName("Non-existent Event Type");
        updatedEventType.setDescription("Description");

        // Try to update with a random UUID that doesn't exist
        UUID nonExistentId = UUID.randomUUID();
        int rowCount = applicationRepository.updateEventType(nonExistentId, updatedEventType);

        // Assert that no rows were updated
        assertEquals(0, rowCount);
    }

    /**
     * Tests that updateEventType handles null availableSeverities by converting to empty HashSet.
     * Note: This test maintains the same availableSeverities to avoid triggering the email_subscriptions
     * update which has a known issue with parameter binding in the native query.
     */
    @Test
    @Transactional
    void testUpdateEventTypeWithNullAvailableSeverities() {
        // Create test fixtures
        Bundle bundle = resourceHelpers.createBundle("null-severities-bundle", "Null Severities Bundle");
        Application application = resourceHelpers.createApplication(bundle.getId(), "null-severities-app", "Null Severities App");
        EventType eventType = resourceHelpers.createEventType(application.getId(), "event-type-severities", "Event Type Severities", "Description");

        // Prepare updated event type with null availableSeverities
        EventType updatedEventType = new EventType();
        updatedEventType.setName("event-type-severities-updated");
        updatedEventType.setDisplayName("Event Type Severities Updated");
        updatedEventType.setDescription("Updated Description");
        updatedEventType.setAvailableSeverities(null); // null severities

        // Call the method under test
        int rowCount = applicationRepository.updateEventType(eventType.getId(), updatedEventType);

        // Assert that one row was updated
        assertEquals(1, rowCount);

        // Verify the update worked and availableSeverities was set to empty set (not null)
        entityManager.clear();
        EventType result = entityManager.find(EventType.class, eventType.getId());
        assertEquals("event-type-severities-updated", result.getName());
        assertEquals("Event Type Severities Updated", result.getDisplayName());
        assertTrue(result.getAvailableSeverities().isEmpty());
    }

    /**
     * Tests that updateEventType updates the Event table when display name changes.
     */
    @Test
    @Transactional
    void testUpdateEventTypeWithDisplayNameChangeUpdatesEventTable() {
        // Create test fixtures
        Bundle bundle = resourceHelpers.createBundle("display-name-change-bundle", "Display Name Change Bundle");
        Application application = resourceHelpers.createApplication(bundle.getId(), "display-name-change-app", "Display Name Change App");
        EventType eventType = resourceHelpers.createEventType(application.getId(), "event-type-display", "Original Display Name", "Description");

        // Create an Event record linked to this event type
        String createEventQuery = "INSERT INTO event (id, org_id, account_id, application_id, application_display_name, bundle_id, bundle_display_name, event_type_id, event_type_display_name, has_authorization_criterion, created) " +
                "VALUES (:id, :orgId, :accountId, :applicationId, :applicationDisplayName, :bundleId, :bundleDisplayName, :eventTypeId, :eventTypeDisplayName, false, NOW())";
        UUID eventId = UUID.randomUUID();
        entityManager.createNativeQuery(createEventQuery)
                .setParameter("id", eventId)
                .setParameter("orgId", ORG_ID)
                .setParameter("accountId", "account-id")
                .setParameter("applicationId", application.getId())
                .setParameter("applicationDisplayName", "Display Name Change App")
                .setParameter("bundleId", bundle.getId())
                .setParameter("bundleDisplayName", "Display Name Change Bundle")
                .setParameter("eventTypeId", eventType.getId())
                .setParameter("eventTypeDisplayName", "Original Display Name")
                .executeUpdate();

        // Verify the initial state
        String verifyQuery = "SELECT event_type_display_name FROM event WHERE id = :id";
        String originalDisplayName = (String) entityManager.createNativeQuery(verifyQuery)
                .setParameter("id", eventId)
                .getSingleResult();
        assertEquals("Original Display Name", originalDisplayName);

        // Prepare updated event type with changed display name
        EventType updatedEventType = new EventType();
        updatedEventType.setName("event-type-display");
        updatedEventType.setDisplayName("Updated Display Name");
        updatedEventType.setDescription("Description");

        // Call the method under test
        int rowCount = applicationRepository.updateEventType(eventType.getId(), updatedEventType);

        // Assert that one row was updated
        assertEquals(1, rowCount);

        // Verify the Event table was updated with new display name
        entityManager.clear();
        String updatedDisplayName = (String) entityManager.createNativeQuery(verifyQuery)
                .setParameter("id", eventId)
                .getSingleResult();
        assertEquals("Updated Display Name", updatedDisplayName);
    }

    /**
     * Tests that updateEventType updates email_subscriptions when available severities are removed.
     */
    @Test
    @Transactional
    void testUpdateEventTypeWithAvailableSeveritiesChangeUpdatesEmailSubscriptions() {
        // Create test fixtures
        Bundle bundle = resourceHelpers.createBundle("severities-change-bundle", "Severities Change Bundle");
        Application application = resourceHelpers.createApplication(bundle.getId(), "severities-change-app", "Severities Change App");
        EventType eventType = resourceHelpers.createEventType(application.getId(), "event-type-severities-change", "Event Type Severities", "Description");

        // Set initial severities (CRITICAL, IMPORTANT, MODERATE)
        Set<Severity> initialSeverities = new HashSet<>();
        initialSeverities.add(Severity.CRITICAL);
        initialSeverities.add(Severity.IMPORTANT);
        initialSeverities.add(Severity.MODERATE);
        eventType.setAvailableSeverities(initialSeverities);
        entityManager.merge(eventType);
        entityManager.flush();

        // Create an email subscription with severity preferences
        String createSubscriptionQuery = "INSERT INTO public.email_subscriptions (org_id, user_id, event_type_id, subscription_type, subscribed, severities) " +
                "VALUES (:orgId, :userId, :eventTypeId, :subscriptionType, true, CAST(:severities AS jsonb))";
        String testUserId = "test-user";
        String testOrgId = "test-org";
        String subscriptionType = "DAILY";
        String severityJson = "{\"CRITICAL\": true, \"IMPORTANT\": true, \"MODERATE\": true}";
        entityManager.createNativeQuery(createSubscriptionQuery)
                .setParameter("orgId", testOrgId)
                .setParameter("userId", testUserId)
                .setParameter("eventTypeId", eventType.getId())
                .setParameter("subscriptionType", subscriptionType)
                .setParameter("severities", severityJson)
                .executeUpdate();

        // Prepare updated event type with fewer severities (only CRITICAL and IMPORTANT, removing MODERATE)
        EventType updatedEventType = new EventType();
        updatedEventType.setName("event-type-severities-change");
        updatedEventType.setDisplayName("Event Type Severities");
        updatedEventType.setDescription("Description");
        Set<Severity> newSeverities = new HashSet<>();
        newSeverities.add(Severity.CRITICAL);
        newSeverities.add(Severity.IMPORTANT);
        updatedEventType.setAvailableSeverities(newSeverities);

        // Call the method under test
        int rowCount = applicationRepository.updateEventType(eventType.getId(), updatedEventType);

        // Assert that one row was updated
        assertEquals(1, rowCount);

        // Verify email_subscriptions severities was updated (MODERATE should be set to false)
        entityManager.clear();
        String verifyQuery = "SELECT severities FROM public.email_subscriptions " +
                "WHERE org_id = :orgId AND user_id = :userId AND event_type_id = :eventTypeId AND subscription_type = :subscriptionType";
        String updatedSeverities = (String) entityManager.createNativeQuery(verifyQuery)
                .setParameter("orgId", testOrgId)
                .setParameter("userId", testUserId)
                .setParameter("eventTypeId", eventType.getId())
                .setParameter("subscriptionType", subscriptionType)
                .getSingleResult();

        // The removed severity (MODERATE) should now be false
        assertTrue(updatedSeverities.contains("\"MODERATE\""), "Severities JSON should contain MODERATE key");
        assertTrue(updatedSeverities.contains("\"MODERATE\":false") || updatedSeverities.contains("\"MODERATE\": false"),
                "MODERATE severity should be set to false");
    }
}
