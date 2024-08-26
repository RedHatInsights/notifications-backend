package com.redhat.cloud.notifications.routers.internal.errata;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.SubscriptionRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscription;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ErrataMigrationRepositoryTest extends DbIsolatedTest {
    @Inject
    ErrataMigrationRepository errataMigrationRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    SubscriptionRepository subscriptionRepository;

    /**
     * Tests that the function under test only finds the Errata event types.
     */
    @Test
    void testFindErrataEventTypes() {
        final Bundle errataBundle = this.resourceHelpers.createBundle("errata-bundle");
        final Application errataApplication = this.resourceHelpers.createApplication(errataBundle.getId(), ErrataMigrationRepository.ERRATA_APPLICATION_NAME);

        // Create five errata event types.
        final List<EventType> errataEventTypes = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            errataEventTypes.add(
                this.resourceHelpers.createEventType(errataApplication.getId(), String.format("errata-%s", UUID.randomUUID()))
            );
        }

        // Create some other event types.
        final Application anotherApplication = this.resourceHelpers.createApplication(errataBundle.getId());
        for (int i = 0; i < 10; i++) {
            this.resourceHelpers.createEventType(anotherApplication.getId(), String.format("other-event-type-%s", UUID.randomUUID()));
        }

        // Call the function under test.
        final List<EventType> resultEventTypes = this.errataMigrationRepository.findErrataEventTypes();

        // Assert that we only fetched the errata event types.
        Assertions.assertEquals(5, errataEventTypes.size(), "we only inserted 5 Errata event types for the test, but a different number of them were found");

        final List<UUID> errataEventTypeIds = errataEventTypes.stream().map(EventType::getId).toList();
        for (final EventType eventType : resultEventTypes) {
            Assertions.assertTrue(errataEventTypeIds.contains(eventType.getId()), "the function under test fetched an event type which is not of the Errata type");
        }
    }

    /**
     * Tests that the function under test saves the errata subscriptions as
     * intended.
     */
    @Test
    void testSaveErrataSubscriptions() {
        // Create a list of subscriptions that we want to save in our database.
        final String username1 = "username1";
        final String orgId1 = "orgId1";
        final ErrataSubscription subscription1 = new ErrataSubscription(username1, orgId1);

        final String username2 = "username2";
        final String orgId2 = "orgId2";
        final ErrataSubscription subscription2 = new ErrataSubscription(username2, orgId2);

        final String username3 = "username3";
        final String orgId3 = "orgId3";
        final ErrataSubscription subscription3 = new ErrataSubscription(username3, orgId3);

        final List<ErrataSubscription> subscriptions = List.of(subscription1, subscription2, subscription3);

        // Create a few event types we want to create the subscriptions for.
        final Bundle errataBundle = this.resourceHelpers.createBundle("errata-bundle");
        final Application errataApplication = this.resourceHelpers.createApplication(errataBundle.getId(), ErrataMigrationRepository.ERRATA_APPLICATION_NAME);
        final EventType errataEventType1 = this.resourceHelpers.createEventType(errataApplication.getId(), "errata-event-type-1");
        final EventType errataEventType2 = this.resourceHelpers.createEventType(errataApplication.getId(), "errata-event-type-2");

        // Call the function under test.
        this.errataMigrationRepository.saveErrataSubscriptions(subscriptions);

        // Assert that each user got subscribed to both event types.
        int totalNumberEmailSubscriptions = 0;
        for (final ErrataSubscription errataSubscription : subscriptions) {
            final List<EventTypeEmailSubscription> createdSubscriptions = this.subscriptionRepository.getEmailSubscriptionsPerEventTypeForUser(errataSubscription.org_id(), errataSubscription.username());
            Assertions.assertEquals(2, createdSubscriptions.size(), String.format("unexpected number of subscriptions created for user \"%s\" in the org ID \"%s\". Two expected.", errataSubscription.username(), errataSubscription.org_id()));

            for (final EventTypeEmailSubscription emailSubscription : createdSubscriptions) {
                Assertions.assertEquals(errataSubscription.username(), emailSubscription.getUserId(), "the fetched email subscription belong to a different user than the specified errata subscription");
                Assertions.assertEquals(errataSubscription.org_id(), emailSubscription.getOrgId(), "the fetched email subscription belong to a different user than the specified errata subscription");

                if (emailSubscription.getEventType().getId().equals(errataEventType1.getId())) {
                    totalNumberEmailSubscriptions++;
                    continue;
                }

                if (emailSubscription.getEventType().getId().equals(errataEventType2.getId())) {
                    totalNumberEmailSubscriptions++;
                    continue;
                }

                Assertions.fail(String.format("The user \"%s\" from the org ID \"%s\" is not subscribed to the Errata notifications, which means that the function under test failed", errataSubscription.username(), errataSubscription.org_id()));
            }
        }

        Assertions.assertEquals(6, totalNumberEmailSubscriptions, "6 email subscriptions should have been created, two per the three users that we have in this test");
    }

    /**
     * Tests that when an already existing email subscription is attempted to
     * be inserted, the "on conflict do nothing" statement ensures that we do
     * not raise duplicate constraint violations. This test is there to
     * simulate that subsequent runs will execute fine if we try to insert
     * duplicated subscriptions in the database.
     */
    @Test
    void testDuplicatedInsertionsDoNothing() {
        // Create duplicated
        final String username = "username";
        final String orgId = "orgId";

        final List<ErrataSubscription> subscriptions = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            subscriptions.add(new ErrataSubscription(username, orgId));
        }

        // Create a single event type we want to create the subscription for.
        final Bundle errataBundle = this.resourceHelpers.createBundle("errata-bundle");
        final Application errataApplication = this.resourceHelpers.createApplication(errataBundle.getId(), ErrataMigrationRepository.ERRATA_APPLICATION_NAME);
        this.resourceHelpers.createEventType(errataApplication.getId(), "errata-event-type");

        // Call the function under test.
        this.errataMigrationRepository.saveErrataSubscriptions(subscriptions);

        // Assert that out of the five duplicated subscriptions we attempted to
        // insert only one got inserted.
        final List<EventTypeEmailSubscription> createdSubscriptions = this.subscriptionRepository.getEmailSubscriptionsPerEventTypeForUser(orgId, username);
        Assertions.assertEquals(1, createdSubscriptions.size());
    }
}
