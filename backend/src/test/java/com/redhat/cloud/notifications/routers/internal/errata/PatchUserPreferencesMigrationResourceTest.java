package com.redhat.cloud.notifications.routers.internal.errata;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.BehaviorGroupRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointEventTypeRepository;
import com.redhat.cloud.notifications.db.repositories.ErrataMigrationRepository;
import com.redhat.cloud.notifications.db.repositories.SubscriptionRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscription;
import com.redhat.cloud.notifications.models.SubscriptionType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import org.apache.http.HttpStatus;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.SubscriptionType.INSTANT;
import static com.redhat.cloud.notifications.routers.internal.userpreferencesmigration.PatchUserPreferencesMigrationResource.PATCH_NEW_ADVISORY_EVENT_TYPE;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class PatchUserPreferencesMigrationResourceTest extends DbIsolatedTest {
    public static final String DEFAULT_ORG_ID = "12345";
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

    @Inject
    EndpointEventTypeRepository endpointEventTypeRepository;

    @Inject
    BehaviorGroupRepository behaviorGroupRepository;

     /**
     * Tests that uploading a JSON file with the Errata subscriptions creates
     * the corresponding subscriptions in our database.
     * @throws URISyntaxException if the JSON file's URL is not properly built.
     */
    @Test
    void testJsonMigration() throws URISyntaxException {
        // Load the file input for the request.
        final URL jsonResourceUrl = getClass().getResource("/patch/subscriptions/patch_subscriptions.json");
        if (jsonResourceUrl == null) {
            fail("The path of the JSON test file is incorrect");
        }

        final File file = Paths.get(jsonResourceUrl.toURI()).toFile();

        // Create a few event types we want to create the subscriptions for.
        final UUID rhelBundle = resourceHelpers.getBundleId("rhel");
        final Application patchApplication = resourceHelpers.createApplication(rhelBundle, "patch");
        final EventType eventTypeNewAdvisory = resourceHelpers.createEventType(patchApplication.getId(), PATCH_NEW_ADVISORY_EVENT_TYPE);

        // test when a user has already setup some preferences
        subscriptionRepository.subscribe(DEFAULT_ORG_ID, "d", eventTypeNewAdvisory.getId(), DAILY);

        // org id should not have any endpoint linked to patch event type
        List<Endpoint> endpointAssociatedToEventTypeList = endpointEventTypeRepository.findEndpointsByEventTypeId(DEFAULT_ORG_ID, eventTypeNewAdvisory.getId(), null, Optional.empty());
        assertEquals(0, endpointAssociatedToEventTypeList.size());

        List<BehaviorGroup> behaviorGroupList = behaviorGroupRepository.findBehaviorGroupsByEventTypeId(DEFAULT_ORG_ID, eventTypeNewAdvisory.getId(), null);
        assertEquals(0, behaviorGroupList.size());

        // Send the request.
        given()
            .basePath(Constants.API_INTERNAL)
            .header(TestHelpers.createTurnpikeIdentityHeader("user", adminRole))
            .when()
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .multiPart("jsonFile", file)
            .post("/patch/migrate/json")
            .then()
            .statusCode(HttpStatus.SC_NO_CONTENT);

        // call it twice to check no dupplicates
        given()
            .basePath(Constants.API_INTERNAL)
            .header(TestHelpers.createTurnpikeIdentityHeader("user", adminRole))
            .when()
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .multiPart("jsonFile", file)
            .post("/patch/migrate/json")
            .then()
            .statusCode(HttpStatus.SC_NO_CONTENT);

        // Assert that the email subscriptions got created.
        final List<EventTypeEmailSubscription> userACreatedSubscriptions = subscriptionRepository.getEmailSubscriptionsPerEventTypeForUser(DEFAULT_ORG_ID, "a");
        assertEmailSubscriptionDataIsCorrect(Set.of(DAILY), "a", userACreatedSubscriptions, eventTypeNewAdvisory.getId());

        final List<EventTypeEmailSubscription> userBCreatedSubscriptions = subscriptionRepository.getEmailSubscriptionsPerEventTypeForUser(DEFAULT_ORG_ID, "b");
        assertEmailSubscriptionDataIsCorrect(Set.of(INSTANT), "b", userBCreatedSubscriptions, eventTypeNewAdvisory.getId());

        final List<EventTypeEmailSubscription> userCCreatedSubscriptions = subscriptionRepository.getEmailSubscriptionsPerEventTypeForUser(DEFAULT_ORG_ID, "c");
        assertEmailSubscriptionDataIsCorrect(Set.of(INSTANT, DAILY), "c", userCCreatedSubscriptions, eventTypeNewAdvisory.getId());

        // because user d already has some user preferences for Patch, we ignored preferences from migration file for him
        final List<EventTypeEmailSubscription> userDCreatedSubscriptions = subscriptionRepository.getEmailSubscriptionsPerEventTypeForUser(DEFAULT_ORG_ID, "d");
        assertEmailSubscriptionDataIsCorrect(Set.of(DAILY), "d", userDCreatedSubscriptions, eventTypeNewAdvisory.getId());

        endpointAssociatedToEventTypeList = endpointEventTypeRepository.findEndpointsByEventTypeId(DEFAULT_ORG_ID, eventTypeNewAdvisory.getId(), null, Optional.empty());
        assertEquals(1, endpointAssociatedToEventTypeList.size());
        assertEquals(EndpointType.EMAIL_SUBSCRIPTION, endpointAssociatedToEventTypeList.getFirst().getType());

        behaviorGroupList = behaviorGroupRepository.findBehaviorGroupsByEventTypeId(DEFAULT_ORG_ID, eventTypeNewAdvisory.getId(), null);
        assertEquals(1, behaviorGroupList.size());
        assertEquals(1, behaviorGroupList.getFirst().getBehaviors().size());
        assertEquals(eventTypeNewAdvisory.getId(), behaviorGroupList.getFirst().getBehaviors().stream().findFirst().get().getEventType().getId());
        assertEquals(1, behaviorGroupList.getFirst().getActions().size());
        assertEquals(endpointAssociatedToEventTypeList.getFirst().getId(), behaviorGroupList.getFirst().getActions().getFirst().getEndpoint().getId());
    }


    private void assertEmailSubscriptionDataIsCorrect(final Set<SubscriptionType> subscriptions, final String expectedUsername, List<EventTypeEmailSubscription> createdEmailSubscriptions, UUID patchNewAdvisoryId) {
        assertEquals(
            subscriptions.size(),
            createdEmailSubscriptions.size());

        for (final EventTypeEmailSubscription emailSubscription : createdEmailSubscriptions) {
            assertEquals(expectedUsername, emailSubscription.getUserId());
            assertEquals(DEFAULT_ORG_ID, emailSubscription.getOrgId());

            assertTrue(
                subscriptions.contains(emailSubscription.getSubscriptionType()));

            assertEquals(emailSubscription.getEventType().getId(), patchNewAdvisoryId);
        }
    }
}
