package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.EmailSubscriptionRepository;
import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscription;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.INSTANT;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class SubscriptionToEventTypeMigrationServiceTest extends DbIsolatedTest {

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Inject
    EmailSubscriptionRepository emailSubscriptionRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    ApplicationRepository applicationRepository;

    @Test
    void testEmailSubscription() {
        Header identity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);

        String accountId = "empty";
        String orgId = "empty";
        String username = "user";
        String username2 = "user2";

        String bundle = "rhel";
        String application = "policies";
        // create a second event type on policies
        resourceHelpers.createEventType(bundle, application, "new-event-type");
        Set<EventType> eventTypeSet = applicationRepository.getApplication(bundle, application).getEventTypes();
        assertEquals(2, eventTypeSet.size());

        // check subscriptions are empty
        List<EmailSubscription> emailSubscriptionList = emailSubscriptionRepository.getEmailSubscriptionsForUser(orgId, username);
        List<EventTypeEmailSubscription> eventTypeEmailSubscriptionList = emailSubscriptionRepository.getEmailSubscriptionsPerEventTypeForUser(orgId, username);
        assertEquals(0, emailSubscriptionList.size());
        assertEquals(0, eventTypeEmailSubscriptionList.size());

        // add subscriptons
        emailSubscriptionRepository.subscribe(accountId, orgId, username, bundle, application, INSTANT);
        emailSubscriptionRepository.subscribe(accountId, orgId, username2, bundle, application, INSTANT);
        for (EventType evt : eventTypeSet) {
            // remove eventType subscriptions for user 2
            emailSubscriptionRepository.unsubscribeEventType(orgId, username2, evt.getId(), INSTANT);
        }

        eventTypeEmailSubscriptionList = emailSubscriptionRepository.getEmailSubscriptionsPerEventTypeForUser(orgId, username2);
        assertEquals(0, eventTypeEmailSubscriptionList.size());

        // migrate from application to event type subscription level
        given()
            .basePath(API_INTERNAL)
            .header(identity)
            .contentType(JSON)
            .when()
            .put("/subscription-to-event-type/migrate")
            .then()
            .statusCode(204)
            .extract().asString();

        eventTypeEmailSubscriptionList = emailSubscriptionRepository.getEmailSubscriptionsPerEventTypeForUser(orgId, username2);
        assertEquals(2, eventTypeEmailSubscriptionList.size());

        eventTypeEmailSubscriptionList = emailSubscriptionRepository.getEmailSubscriptionsPerEventTypeForUser(orgId, username);
        assertEquals(2, eventTypeEmailSubscriptionList.size());
    }
}
