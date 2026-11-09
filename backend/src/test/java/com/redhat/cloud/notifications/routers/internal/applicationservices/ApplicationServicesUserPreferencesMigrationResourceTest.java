package com.redhat.cloud.notifications.routers.internal.applicationservices;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.ApplicationServicesMigrationRepository;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.Constants.API_INTERNAL_NOTIFICATIONS_BACKEND;
import static com.redhat.cloud.notifications.db.repositories.ApplicationServicesMigrationRepository.APPLICATION_SERVICES_BUNDLE_NAME;
import static io.restassured.RestAssured.given;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ApplicationServicesUserPreferencesMigrationResourceTest extends DbIsolatedTest {

    @InjectSpy
    ApplicationServicesMigrationRepository applicationServicesMigrationRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testUnsupportedEventTypesAreSkipped(String basePath) throws URISyntaxException {
        final EventType notAnAppServicesEventType = new EventType();
        notAnAppServicesEventType.setName("not-an-app-services-event-type");

        Mockito.when(applicationServicesMigrationRepository.findApplicationServicesEventTypes()).thenReturn(List.of(notAnAppServicesEventType));

        final URL jsonResourceUrl = getClass().getResource("/application-services/subscriptions/application_services_subscriptions.json");
        if (jsonResourceUrl == null) {
            Assertions.fail("The path of the JSON test file is incorrect");
        }

        final File file = Paths.get(jsonResourceUrl.toURI()).toFile();

        given()
            .basePath(basePath)
            .header(TestHelpers.createTurnpikeIdentityHeader("user", adminRole))
            .when()
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .multiPart("jsonFile", file)
            .post("/application-services/migrate/json")
            .then()
            .statusCode(HttpStatus.SC_NO_CONTENT);
    }

    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_BACKEND})
    void testJsonMigration(String basePath) throws URISyntaxException {
        final URL jsonResourceUrl = getClass().getResource("/application-services/subscriptions/application_services_subscriptions.json");
        if (jsonResourceUrl == null) {
            Assertions.fail("The path of the JSON test file is incorrect");
        }

        final File file = Paths.get(jsonResourceUrl.toURI()).toFile();

        final Bundle bundle = resourceHelpers.createBundle(APPLICATION_SERVICES_BUNDLE_NAME);
        final Application application = resourceHelpers.createApplication(bundle.getId(), ApplicationServicesMigrationRepository.APPLICATION_SERVICES_APPLICATION_NAME);
        final EventType eventTypeRhbk = resourceHelpers.createEventType(application.getId(), "rhbk");
        final EventType eventTypeAppplatform = resourceHelpers.createEventType(application.getId(), "appplatform");
        final EventType eventTypeDataGrid = resourceHelpers.createEventType(application.getId(), "data-grid");
        final EventType eventTypeRedhatQuarkus = resourceHelpers.createEventType(application.getId(), "redhat-quarkus");

        given()
            .basePath(basePath)
            .header(TestHelpers.createTurnpikeIdentityHeader("user", adminRole))
            .when()
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .multiPart("jsonFile", file)
            .post("/application-services/migrate/json")
            .then()
            .statusCode(HttpStatus.SC_NO_CONTENT);

        final List<EventTypeEmailSubscription> userASubscriptions = subscriptionRepository.getEmailSubscriptionsPerEventTypeForUser("12345", "a");
        assertEmailSubscriptionDataIsCorrect(Set.of(eventTypeRhbk, eventTypeAppplatform), "a", userASubscriptions);

        final List<EventTypeEmailSubscription> userBSubscriptions = subscriptionRepository.getEmailSubscriptionsPerEventTypeForUser("12345", "b");
        assertEmailSubscriptionDataIsCorrect(Set.of(eventTypeRhbk), "b", userBSubscriptions);

        final List<EventTypeEmailSubscription> userCSubscriptions = subscriptionRepository.getEmailSubscriptionsPerEventTypeForUser("12345", "c");
        assertEmailSubscriptionDataIsCorrect(Set.of(eventTypeDataGrid), "c", userCSubscriptions);

        final List<EventTypeEmailSubscription> userDSubscriptions = subscriptionRepository.getEmailSubscriptionsPerEventTypeForUser("12345", "d");
        assertEmailSubscriptionDataIsCorrect(Set.of(eventTypeRedhatQuarkus), "d", userDSubscriptions);
    }

    private void assertEmailSubscriptionDataIsCorrect(final Set<EventType> expectedSubscribedEventTypes, final String expectedUsername, List<EventTypeEmailSubscription> createdEmailSubscriptions) {
        Assertions.assertEquals(
            expectedSubscribedEventTypes.size(),
            createdEmailSubscriptions.size(),
            String.format(
                "unexpected number of created email subscriptions for user \"%s\". \"%s\" expected, got \"%s\": %s",
                expectedUsername, expectedSubscribedEventTypes.size(), createdEmailSubscriptions.size(), createdEmailSubscriptions
            ));

        for (final EventTypeEmailSubscription emailSubscription : createdEmailSubscriptions) {
            Assertions.assertEquals(expectedUsername, emailSubscription.getUserId(), "the fetched email subscription belongs to a different user than expected");
            Assertions.assertEquals("12345", emailSubscription.getOrgId(), "the fetched email subscription has a different org ID than expected");

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
