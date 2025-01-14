package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscription;
import com.redhat.cloud.notifications.models.SubscriptionType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

@QuarkusTest
public class SubscriptionRepositoryTest extends DbIsolatedTest {
    @InjectMock
    BackendConfig backendConfig;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    SubscriptionRepository subscriptionRepository;

    /**
     * Tests that the function under test only finds mixed cased user IDs from
     * email subscriptions.
     */
    @Test
    void testFindMixedCaseUserIds() {
        // Simulate that the default template is enabled so that we can create
        // instant subscriptions without facing any "missing template" errors.
        Mockito.when(this.backendConfig.isDefaultTemplateEnabled()).thenReturn(true);

        // Create a single event type we want to create the subscriptions for.
        final Bundle randomBundle = this.resourceHelpers.createBundle("random-bundle");
        final Application randomApplication = this.resourceHelpers.createApplication(randomBundle.getId(), "random-application");
        final EventType randomEventType = this.resourceHelpers.createEventType(randomApplication.getId(), "random-event-type");

        // Create five subscriptions with mixed case user IDs.
        final Random random = new Random();
        final Set<String> expectedUsernames = new HashSet<>(5);
        for (int i = 0; i < 5; i++) {
            final String userId;
            if (random.nextBoolean()) {
                userId = String.format("MixedCaseUSERid%d", i);
            } else {
                userId = String.format("UPPERCASEUSERID%d", i);
            }
            final String orgId = String.format("%d", i);

            // Store the created user ID to verify it afterward.
            expectedUsernames.add(userId);

            this.subscriptionRepository.updateSubscription(orgId, userId, randomEventType.getId(), SubscriptionType.INSTANT, random.nextBoolean());
        }

        // Create five subscriptions with lowercase usernames.
        for (int i = 0; i < 5; i++) {
            final String userId = String.format("lowercaseusername%d", i);
            final String orgId = String.format("%d", i);

            this.subscriptionRepository.updateSubscription(orgId, userId, randomEventType.getId(), SubscriptionType.INSTANT, random.nextBoolean());
        }

        // Call the function under test.
        final Set<String> userIds = this.subscriptionRepository.findMixedCaseUserIds();

        // Assert that the fetched user IDs are just the ones with mixed case.
        for (final String userId : userIds) {
            Assertions.assertTrue(
                expectedUsernames.contains(userId),
                String.format("a non-mixed-case user ID \"%s\" was fetched from the database", userId)
            );
        }
    }

    /**
     * Tests that the function under test only finds the email subscriptions
     * related to the given user ID.
     */
    @Test
    void testFindEmailSubscriptionsByUserId() {
        // Simulate that the default template is enabled so that we can create
        // instant subscriptions without facing any "missing template" errors.
        Mockito.when(this.backendConfig.isDefaultTemplateEnabled()).thenReturn(true);

        // Create a single event type we want to create the subscriptions for.
        final Bundle randomBundle = this.resourceHelpers.createBundle("random-bundle");
        final Application randomApplication = this.resourceHelpers.createApplication(randomBundle.getId(), "random-application");
        final EventType randomEventType = this.resourceHelpers.createEventType(randomApplication.getId(), "random-event-type");
        final EventType randomEventTypeTwo = this.resourceHelpers.createEventType(randomApplication.getId(), "random-event-type-two");
        final EventType randomEventTypeThree = this.resourceHelpers.createEventType(randomApplication.getId(), "random-event-type-three");

        // Create some subscriptions for two different users.
        final String userIdOne = "userIdOne";
        final String userIdTwo = "userIdTwo";

        final Random random = new Random();
        this.subscriptionRepository.updateSubscription(DEFAULT_ORG_ID, userIdOne, randomEventType.getId(), SubscriptionType.INSTANT, random.nextBoolean());
        this.subscriptionRepository.updateSubscription(DEFAULT_ORG_ID, userIdOne, randomEventTypeTwo.getId(), SubscriptionType.INSTANT, random.nextBoolean());
        this.subscriptionRepository.updateSubscription(DEFAULT_ORG_ID, userIdOne, randomEventTypeThree.getId(), SubscriptionType.INSTANT, random.nextBoolean());

        this.subscriptionRepository.updateSubscription(DEFAULT_ORG_ID, userIdTwo, randomEventType.getId(), SubscriptionType.INSTANT, random.nextBoolean());
        this.subscriptionRepository.updateSubscription(DEFAULT_ORG_ID, userIdTwo, randomEventTypeTwo.getId(), SubscriptionType.INSTANT, random.nextBoolean());
        this.subscriptionRepository.updateSubscription(DEFAULT_ORG_ID, userIdTwo, randomEventTypeThree.getId(), SubscriptionType.INSTANT, random.nextBoolean());

        // Call the function under test.
        final List<EventTypeEmailSubscription> subscriptions = this.subscriptionRepository.findEmailSubscriptionsByUserId(userIdOne);

        // Assert that all the subscriptions belong to the target user ID.
        for (final EventTypeEmailSubscription subscription : subscriptions) {
            Assertions.assertEquals(userIdOne, subscription.getUserId(), "fetched a subscription from a different user");
        }
    }

    /**
     * Tests that an email subscription's user ID can be set to lowercase, and
     * that when it is not possible due to an already existing email
     * subscription, the email subscription gets deleted.
     */
    @Test
    void testSetSubscriptionUserIdLowercase() {
        // Simulate that the default template is enabled so that we can create
        // instant subscriptions without facing any "missing template" errors.
        Mockito.when(this.backendConfig.isDefaultTemplateEnabled()).thenReturn(true);

        // Create a single event type we want to create the subscriptions for.
        final Bundle randomBundle = this.resourceHelpers.createBundle("random-bundle");
        final Application randomApplication = this.resourceHelpers.createApplication(randomBundle.getId(), "random-application");
        final EventType randomEventType = this.resourceHelpers.createEventType(randomApplication.getId(), "random-event-type");
        final EventType randomEventTypeTwo = this.resourceHelpers.createEventType(randomApplication.getId(), "random-event-type-two");
        final EventType randomEventTypeThree = this.resourceHelpers.createEventType(randomApplication.getId(), "random-event-type-three");

        // Create some subscriptions for a user that should simply be
        // renamed to lowercase.
        final String userId = "userIdOne";
        this.subscriptionRepository.updateSubscription(DEFAULT_ORG_ID, userId, randomEventType.getId(), SubscriptionType.INSTANT, true);
        this.subscriptionRepository.updateSubscription(DEFAULT_ORG_ID, userId, randomEventTypeTwo.getId(), SubscriptionType.INSTANT, false);
        this.subscriptionRepository.updateSubscription(DEFAULT_ORG_ID, userId, randomEventTypeThree.getId(), SubscriptionType.INSTANT, true);

        // Find the email subscriptions for the user.
        final List<EventTypeEmailSubscription> originalSubscriptions = this.subscriptionRepository.findEmailSubscriptionsByUserId(userId);

        // Set the user ID to lowercase.
        for (final EventTypeEmailSubscription subscription : originalSubscriptions) {
            this.subscriptionRepository.setEmailSubscriptionUserIdLowercase(subscription);
        }

        // Attempt finding the email subscriptions again for the original user
        // ID.
        Assertions.assertEquals(
            0,
            this.subscriptionRepository.findEmailSubscriptionsByUserId(userId).size(),
            String.format("fetched email subscriptions for \"%s\", when their user ID should have been renamed to lowercase", userId)
        );

        // Attempt finding the email subscriptions but for the lowercase user
        // ID.
        Assertions.assertEquals(
            3,
            this.subscriptionRepository.findEmailSubscriptionsByUserId(userId.toLowerCase()).size(),
            String.format("user ID \"%s\" should have had all the subscriptions renamed to lowercase", userId.toLowerCase())
        );

        // Create the same subscriptions again for the previous user ID.
        this.subscriptionRepository.updateSubscription(DEFAULT_ORG_ID, userId, randomEventType.getId(), SubscriptionType.INSTANT, true);
        this.subscriptionRepository.updateSubscription(DEFAULT_ORG_ID, userId, randomEventTypeTwo.getId(), SubscriptionType.INSTANT, false);
        this.subscriptionRepository.updateSubscription(DEFAULT_ORG_ID, userId, randomEventTypeThree.getId(), SubscriptionType.INSTANT, true);

        // Find the email subscriptions for the user again.
        final List<EventTypeEmailSubscription> duplicatedMixedCaseSubscriptions = this.subscriptionRepository.findEmailSubscriptionsByUserId(userId);

        // Attempt setting the user ID to lowercase.
        for (final EventTypeEmailSubscription subscription : duplicatedMixedCaseSubscriptions) {
            this.subscriptionRepository.setEmailSubscriptionUserIdLowercase(subscription);
        }

        // Assert that the mixed case subscriptions were deleted.
        Assertions.assertEquals(
            0,
            this.subscriptionRepository.findEmailSubscriptionsByUserId(userId).size(),
            String.format("fetched email subscriptions for \"%s\", when the subscriptions themselves should have been deleted", userId)
        );

        // Assert that the email subscriptions with the lowercase user ID still
        // exist.
        Assertions.assertEquals(
            3,
            this.subscriptionRepository.findEmailSubscriptionsByUserId(userId.toLowerCase()).size(),
            String.format("user ID \"%s\" should still have all the lowercase email subscriptions after deleting the mixed case ones", userId.toLowerCase())
        );
    }
}
