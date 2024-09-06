package com.redhat.cloud.notifications.routers.internal.errata;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.ErrataMigrationRepository;
import com.redhat.cloud.notifications.db.repositories.SubscriptionRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscription;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import org.apache.http.HttpStatus;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static com.redhat.cloud.notifications.routers.internal.errata.ErrataUserPreferencesMigrationResource.EVENT_TYPE_NAME_BUGFIX;
import static com.redhat.cloud.notifications.routers.internal.errata.ErrataUserPreferencesMigrationResource.EVENT_TYPE_NAME_ENHANCEMENT;
import static com.redhat.cloud.notifications.routers.internal.errata.ErrataUserPreferencesMigrationResource.EVENT_TYPE_NAME_SECURITY;
import static io.restassured.RestAssured.given;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ErrataUserPreferencesMigrationResourceTest extends DbIsolatedTest {
    @InjectSpy
    BackendConfig backendConfig;

    @InjectSpy
    ErrataMigrationRepository errataMigrationRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Inject
    SubscriptionRepository subscriptionRepository;

    /**
     * Tests that when there is an unsupported Errata event type the call just
     * fails.
     * @throws URISyntaxException if the JSON file's URL is not properly built.
     */
    @Test
    void testUnsupportedEventType() throws URISyntaxException {
        // Reduce the batch size so that the loop runs more than once, and
        // therefore we can test that it works for large files.
        Mockito.when(this.backendConfig.getErrataMigrationBatchSize()).thenReturn(2);

        // Simulate that the repository returns a non-Errata event type.
        final EventType notAnErrataEventType = new EventType();
        notAnErrataEventType.setName("not-an-errata-event-type");

        Mockito.when(this.errataMigrationRepository.findErrataEventTypes()).thenReturn(List.of(notAnErrataEventType));

        // Load the file input for the request.
        final URL jsonResourceUrl = this.getClass().getResource("/errata/subscriptions/errata_subscriptions.json");
        if (jsonResourceUrl == null) {
            Assertions.fail("The path of the JSON test file is incorrect");
        }

        final File file = Paths.get(jsonResourceUrl.toURI()).toFile();

        // Call the endpoint under test.
        final String response = given()
            .basePath(Constants.API_INTERNAL)
            .header(TestHelpers.createTurnpikeIdentityHeader("user", this.adminRole))
            .when()
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .multiPart("jsonFile", file)
            .post("/team-nado/migrate/json")
            .then()
            .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
            .extract()
            .asString();

        Assertions.assertEquals("A non errata event type was detected. Nothing was executed.", response, "unexpected response received for when an unexpected event type gets fetched from the database");
    }


     /**
     * Tests that uploading a JSON file with the Errata subscriptions creates
     * the corresponding subscriptions in our database.
     * @throws URISyntaxException if the JSON file's URL is not properly built.
     */
    @Test
    void testJsonMigration() throws URISyntaxException {
        // Load the file input for the request.
        final URL jsonResourceUrl = this.getClass().getResource("/errata/subscriptions/errata_subscriptions.json");
        if (jsonResourceUrl == null) {
            Assertions.fail("The path of the JSON test file is incorrect");
        }

        final File file = Paths.get(jsonResourceUrl.toURI()).toFile();

        // Create a few event types we want to create the subscriptions for.
        final Bundle errataBundle = this.resourceHelpers.createBundle("errata-bundle");
        final Application errataApplication = this.resourceHelpers.createApplication(errataBundle.getId(), ErrataMigrationRepository.ERRATA_APPLICATION_NAME);
        final EventType eventTypeBugFix = this.resourceHelpers.createEventType(errataApplication.getId(), EVENT_TYPE_NAME_BUGFIX);
        final EventType eventTypeEnhancement = this.resourceHelpers.createEventType(errataApplication.getId(), EVENT_TYPE_NAME_ENHANCEMENT);
        final EventType eventTypeSecurity = this.resourceHelpers.createEventType(errataApplication.getId(), EVENT_TYPE_NAME_SECURITY);

        // Send the request.
        given()
            .basePath(Constants.API_INTERNAL)
            .header(TestHelpers.createTurnpikeIdentityHeader("user", this.adminRole))
            .when()
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .multiPart("jsonFile", file)
            .post("/team-nado/migrate/json")
            .then()
            .statusCode(HttpStatus.SC_NO_CONTENT);

        // Assert that the email subscriptions got created.
        final List<EventTypeEmailSubscription> userACreatedSubscriptions = this.subscriptionRepository.getEmailSubscriptionsPerEventTypeForUser("12345", "a");
        this.assertEmailSubscriptionDataIsCorrect(Set.of(eventTypeBugFix, eventTypeEnhancement, eventTypeSecurity), "a", userACreatedSubscriptions);

        final List<EventTypeEmailSubscription> userBCreatedSubscriptions = this.subscriptionRepository.getEmailSubscriptionsPerEventTypeForUser("12345", "b");
        this.assertEmailSubscriptionDataIsCorrect(Set.of(eventTypeBugFix), "b", userBCreatedSubscriptions);

        final List<EventTypeEmailSubscription> userCCreatedSubscriptions = this.subscriptionRepository.getEmailSubscriptionsPerEventTypeForUser("12345", "c");
        this.assertEmailSubscriptionDataIsCorrect(Set.of(eventTypeEnhancement), "c", userCCreatedSubscriptions);

        final List<EventTypeEmailSubscription> userDCreatedSubscriptions = this.subscriptionRepository.getEmailSubscriptionsPerEventTypeForUser("12345", "d");
        this.assertEmailSubscriptionDataIsCorrect(Set.of(eventTypeSecurity), "d", userDCreatedSubscriptions);

        final List<EventTypeEmailSubscription> userECreatedSubscriptions = this.subscriptionRepository.getEmailSubscriptionsPerEventTypeForUser("12345", "e");
        Assertions.assertTrue(userECreatedSubscriptions.isEmpty(), "no subscriptions should have been created for user e, yet the retrieved subscriptions list from the database is not empty");
    }

    /**
     * Assert that the created email subscriptions are correct.
     * @param expectedSubscribedEventTypes the expected event types for which
     *                                     the email subscriptions should have
     *                                     been created.
     * @param expectedUsername the username for which the email subscriptions
     *                         should have been created.
     * @param createdEmailSubscriptions the created email subscriptions that
     *                                  are present in our database.
     */
    private void assertEmailSubscriptionDataIsCorrect(final Set<EventType> expectedSubscribedEventTypes, final String expectedUsername, List<EventTypeEmailSubscription> createdEmailSubscriptions) {
        Assertions.assertEquals(
            expectedSubscribedEventTypes.size(),
            createdEmailSubscriptions.size(),
            String.format(
                "unexpected number of created email subscriptions for user \"%s\". \"%s\" expected, got \"%s\": %s",
                expectedUsername, expectedSubscribedEventTypes.size(), createdEmailSubscriptions.size(), createdEmailSubscriptions
            ));

        for (final EventTypeEmailSubscription emailSubscription : createdEmailSubscriptions) {
            Assertions.assertEquals(expectedUsername, emailSubscription.getUserId(), "the fetched email subscription belong to a different user than the specified errata subscription");
            Assertions.assertEquals("12345", emailSubscription.getOrgId(), "the fetched email subscription belong to a different user than the specified errata subscription");

            Assertions.assertTrue(
                expectedSubscribedEventTypes.contains(emailSubscription.getEventType()),
                String.format(
                    "user \"%s\"'s email subscription contains an event type \"%s\" that is not from the expected list: %s",
                    expectedUsername,
                    emailSubscription.getEventType(),
                    expectedSubscribedEventTypes
                )
            );
        }
    }
}
