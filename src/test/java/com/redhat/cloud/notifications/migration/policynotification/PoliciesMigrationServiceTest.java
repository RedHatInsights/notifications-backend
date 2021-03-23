package com.redhat.cloud.notifications.migration.policynotification;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.migration.policynotification.PoliciesMigrationService.MigrateResponse;
import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class PoliciesMigrationServiceTest {

    @InjectMock
    @RestClient
    PolicyNotifications policyNotifications;

    @Inject
    EndpointEmailSubscriptionResources emailSubscriptionResources;

    @Inject
    ApplicationResources applicationResources;

    @Inject
    EndpointResources endpointResources;

    @Test
    void testSingleUserMigration() {
        String accountId1 = "single-user-migration-1";
        String userId1 = "user1";

        List<PoliciesEmailSubscription> policies = new ArrayList<>();
        policies.add(new PoliciesEmailSubscription(accountId1, userId1, PoliciesMigrationService.DAILY_EMAIL_TYPE));

        Mockito
        .when(policyNotifications.getSubscriptions())
            .thenReturn(Uni.createFrom().item(policies));

        MigrateResponse response = given()
                .contentType(ContentType.JSON)
                .when().get("/internal/policies-notifications/migrate")
                .then()
                .statusCode(200)
                .extract().response().as(MigrateResponse.class);

        assertEquals(1, response.eventTypesMigrated.get());
        assertEquals(1, response.accountsMigrated.get());

        assertUserEventType(accountId1, userId1, EmailSubscriptionType.DAILY);
        assertAccountHasEmailAction(accountId1);
    }

    @Test
    void testMultipleUsersMigration() {
        String accountId1 = "multi-user-migration-1";
        String[] users = {"user1", "user2", "user3", "user4"};

        List<PoliciesEmailSubscription> policies = new ArrayList<>();
        for (String user: users) {
            policies.add(new PoliciesEmailSubscription(accountId1, user, PoliciesMigrationService.DAILY_EMAIL_TYPE));
            policies.add(new PoliciesEmailSubscription(accountId1, user, PoliciesMigrationService.INSTANT_EMAIL_TYPE));
        }

        Mockito
                .when(policyNotifications.getSubscriptions())
                .thenReturn(Uni.createFrom().item(policies));

        MigrateResponse response = given()
                .contentType(ContentType.JSON)
                .when().get("/internal/policies-notifications/migrate")
                .then()
                .statusCode(200)
                .extract().response().as(MigrateResponse.class);

        assertEquals(8, response.eventTypesMigrated.get()); // 4 users with 2 eventTypes each
        assertEquals(1, response.accountsMigrated.get());

        for (String user: users) {
            assertUserEventType(accountId1, user, EmailSubscriptionType.DAILY);
        }

        assertAccountHasEmailAction(accountId1);
    }

    @Test
    void testMultipleUsersAndAccountsMigration() {

        List<PoliciesEmailSubscription> policies = new ArrayList<>();

        String accountIdFormat = "multi-users-and-account-migration-%d";
        String userFormat = "user-%d-%d";

        int accountCount = 20;
        int userCountPerAccount = 11;

        for (int i = 0; i < accountCount; ++i) {
            for (int j = 0; j < userCountPerAccount; ++j) {
                String eventType = j % 2 == 0 ? PoliciesMigrationService.INSTANT_EMAIL_TYPE : PoliciesMigrationService.DAILY_EMAIL_TYPE;
                policies.add(new PoliciesEmailSubscription(
                        String.format(accountIdFormat, i),
                        String.format(userFormat, i, j),
                        eventType
                ));
            }
        }

        Mockito
                .when(policyNotifications.getSubscriptions())
                .thenReturn(Uni.createFrom().item(policies));

        MigrateResponse response = given()
                .contentType(ContentType.JSON)
                .when().get("/internal/policies-notifications/migrate")
                .then()
                .statusCode(200)
                .extract().response().as(MigrateResponse.class);

        assertEquals(accountCount * userCountPerAccount, response.eventTypesMigrated.get());
        assertEquals(accountCount, response.accountsMigrated.get());

        for (int i = 0; i < accountCount; ++i) {
            String accountId = String.format(accountIdFormat, i);
            assertAccountHasEmailAction(accountId);
            for (int j = 0; j < userCountPerAccount; ++j) {
                EmailSubscriptionType type = j % 2 == 0 ? EmailSubscriptionType.INSTANT : EmailSubscriptionType.DAILY;
                assertUserEventType(accountId, String.format(userFormat, i, j), type);
            }
        }
    }

    private void assertUserEventType(String accountId, String userId, EmailSubscriptionType emailSubscriptionType) {
        EmailSubscription emailSubscription = emailSubscriptionResources
                .getEmailSubscription(accountId, userId, PoliciesMigrationService.BUNDLE, PoliciesMigrationService.APPLICATION, emailSubscriptionType)
                .await().indefinitely();

        assertEquals(accountId, emailSubscription.getAccountId());
        assertEquals(userId, emailSubscription.getUsername());
        assertEquals(PoliciesMigrationService.BUNDLE, emailSubscription.getBundle());
        assertEquals(PoliciesMigrationService.APPLICATION, emailSubscription.getApplication());
        assertEquals(emailSubscriptionType, emailSubscription.getType());
    }

    private void assertAccountHasEmailAction(String accountId) {
        // assert that the account has the email action for insights/policies/policy-triggered
        EventType eventType = applicationResources.getEventType(
                PoliciesMigrationService.BUNDLE,
                PoliciesMigrationService.APPLICATION,
                PoliciesMigrationService.EVENT_TYPE
        ).await().indefinitely();

        List<Endpoint> endpoints = endpointResources.getLinkedEndpoints(accountId, eventType.getId(), new Query()).collectItems().asList().await().indefinitely();

        assertEquals(1, endpoints.size());
        assertEquals(EndpointType.EMAIL_SUBSCRIPTION, endpoints.get(0).getType());
    }
}
