package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
