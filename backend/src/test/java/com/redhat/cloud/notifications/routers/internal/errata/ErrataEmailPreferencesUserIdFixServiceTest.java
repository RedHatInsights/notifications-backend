package com.redhat.cloud.notifications.routers.internal.errata;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.SubscriptionRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscription;
import com.redhat.cloud.notifications.models.SubscriptionType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.http.HttpStatus;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_USER;
import static io.restassured.RestAssured.given;

@QuarkusTest

public class ErrataEmailPreferencesUserIdFixServiceTest extends DbIsolatedTest {
    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Inject
    ResourceHelpers resourceHelpers;

    @InjectMock
    BackendConfig backendConfig;

    @Inject
    SubscriptionRepository subscriptionRepository;

    /**
     * Tests that the email subscriptions' user IDs get lowercased, or deleted
     * if there already exist an email subscription with that key.
     */
    @Test
    void testUserPreferencesUserIdFix() {
        // Simulate that the default template is enabled so that we can create
        // instant subscriptions without facing any "missing template" errors.
        Mockito.when(this.backendConfig.isDefaultTemplateEnabled()).thenReturn(true);

        // Create a single event type we want to create the subscriptions for.
        final Bundle randomBundle = this.resourceHelpers.createBundle("random-bundle");
        final Application randomApplication = this.resourceHelpers.createApplication(randomBundle.getId(), "random-application");
        final EventType randomEventType = this.resourceHelpers.createEventType(randomApplication.getId(), "random-event-type");
        final EventType randomEventTypeTwo = this.resourceHelpers.createEventType(randomApplication.getId(), "random-event-type-two");
        final EventType randomEventTypeThree = this.resourceHelpers.createEventType(randomApplication.getId(), "random-event-type-three");

        // Create two subscriptions that are duplicated and therefore should
        // trigger a deletion, and another one that simply should get renamed.
        final String userId = "userIdOne";
        this.subscriptionRepository.subscribe(DEFAULT_ORG_ID, userId.toLowerCase(), randomEventType.getId(), SubscriptionType.INSTANT);
        this.subscriptionRepository.subscribe(DEFAULT_ORG_ID, userId.toLowerCase(), randomEventTypeTwo.getId(), SubscriptionType.INSTANT);
        this.subscriptionRepository.subscribe(DEFAULT_ORG_ID, userId, randomEventTypeThree.getId(), SubscriptionType.INSTANT);

        this.subscriptionRepository.subscribe(DEFAULT_ORG_ID, userId, randomEventType.getId(), SubscriptionType.INSTANT);
        this.subscriptionRepository.subscribe(DEFAULT_ORG_ID, userId, randomEventTypeTwo.getId(), SubscriptionType.INSTANT);

        // Assert that there are 5 subscriptions in total.
        Assertions.assertEquals(
            5,
            this.subscriptionRepository.findEmailSubscriptionsByUserId(userId.toLowerCase()).size() + this.subscriptionRepository.findEmailSubscriptionsByUserId(userId).size(),
            "five email subscriptions should have been created for the user"
        );

        // Send the request.
        given()
            .basePath(Constants.API_INTERNAL)
            .header(TestHelpers.createTurnpikeIdentityHeader(DEFAULT_USER, this.adminRole))
            .when()
            .post("/team-nado/migrate/rename-lowercase")
            .then()
            .statusCode(HttpStatus.SC_NO_CONTENT);

        // Assert that after the migration, the subscriptions were reduced
        // to just three, and that all af them have a lowercase user ID.
        final List<EventTypeEmailSubscription> subscriptions = this.subscriptionRepository.findEmailSubscriptionsByUserId(userId.toLowerCase());

        Assertions.assertEquals(
            3,
            subscriptions.size(),
            "after running the migration, only three subscriptions should be left"
        );

        for (final EventTypeEmailSubscription subscription : subscriptions) {
            Assertions.assertEquals(
                userId.toLowerCase(),
                subscription.getUserId(),
                "the user ID should have been renamed to lowercase"
            );
        }
    }
}
