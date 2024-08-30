package com.redhat.cloud.notifications.routers.internal.errata;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
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
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import org.apache.http.HttpStatus;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;

import static io.restassured.RestAssured.given;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ErrataUserPreferencesMigrationResourceTest extends DbIsolatedTest {
    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    SubscriptionRepository subscriptionRepository;

    /**
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
            Assertions.fail("The path of the CSV test file is incorrect");
        }

        final File file = Paths.get(jsonResourceUrl.toURI()).toFile();

        // Create a few event types we want to create the subscriptions for.
        final Bundle errataBundle = this.resourceHelpers.createBundle("errata-bundle");
        final Application errataApplication = this.resourceHelpers.createApplication(errataBundle.getId(), ErrataMigrationRepository.ERRATA_APPLICATION_NAME);
        final EventType errataEventType1 = this.resourceHelpers.createEventType(errataApplication.getId(), "errata-event-type-1");
        final EventType errataEventType2 = this.resourceHelpers.createEventType(errataApplication.getId(), "errata-event-type-2");

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

        this.assertEmailSubscriptionsWereCreated(List.of(errataEventType1, errataEventType2));
    }

    /**
     * Assert that the email subscriptions were properly created.
     * @param errataEventTypes the created Errata event types the users should
     *                         have been subscribed to.
     */
    private void assertEmailSubscriptionsWereCreated(final List<EventType> errataEventTypes) {
        // Set up the usernames and the org ID from the test files.
        final List<String> usernames = List.of("a", "b", "c", "d", "e");
        final String orgId = "12345";

        // Assert that each user got subscribed to both event types.
        int totalNumberEmailSubscriptions = 0;
        for (final String username : usernames) {
            final List<EventTypeEmailSubscription> createdSubscriptions = this.subscriptionRepository.getEmailSubscriptionsPerEventTypeForUser(orgId, username);
            Assertions.assertEquals(2, createdSubscriptions.size(), String.format("unexpected number of subscriptions created for user \"%s\" in the org ID \"%s\". Two expected.", username, orgId));

            for (final EventTypeEmailSubscription emailSubscription : createdSubscriptions) {
                Assertions.assertEquals(username, emailSubscription.getUserId(), "the fetched email subscription belong to a different user than the specified errata subscription");
                Assertions.assertEquals(orgId, emailSubscription.getOrgId(), "the fetched email subscription belong to a different user than the specified errata subscription");

                if (emailSubscription.getEventType().getId().equals(errataEventTypes.getFirst().getId())) {
                    totalNumberEmailSubscriptions++;
                    continue;
                }

                if (emailSubscription.getEventType().getId().equals(errataEventTypes.getLast().getId())) {
                    totalNumberEmailSubscriptions++;
                    continue;
                }

                Assertions.fail(String.format("The user \"%s\" from the org ID \"%s\" is not subscribed to the Errata notifications, which means that the function under test failed", username, orgId));
            }
        }

        Assertions.assertEquals(10, totalNumberEmailSubscriptions, "6 email subscriptions should have been created, two per the three users that we have in this test");
    }
}
