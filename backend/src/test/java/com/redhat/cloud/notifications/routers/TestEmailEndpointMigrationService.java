package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.BehaviorGroupAction;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class TestEmailEndpointMigrationService extends DbIsolatedTest {

    private static final String ACCOUNT1 = "account-1";
    private static final String ACCOUNT2 = "account-2";
    private static final String ACCOUNT3 = "account-3";
    private static final String ACCOUNT4 = "account-4";
    private static final String ACCOUNT5 = "account-5";

    @Inject
    Mutiny.Session session;

    @Test
    void testMigration() {

        // setup
        Bundle bundle1 = createBundle("migration-test-bundle");

        createBehaviorGroupActions(
                createBehaviorGroup(ACCOUNT5, bundle1, "b-1"),
                createEndpoint(ACCOUNT5, EndpointType.WEBHOOK, "w-1")
        );

        for (String account : Arrays.asList(ACCOUNT1, ACCOUNT2, ACCOUNT3, ACCOUNT4)) {
            createBehaviorGroupActions(
                    createBehaviorGroup(account, bundle1, "b-1"),
                    createEndpoint(account, EndpointType.EMAIL_SUBSCRIPTION, "e-1"),
                    createEndpoint(account, EndpointType.WEBHOOK, "w-1")
            );

            createBehaviorGroupActions(
                    createBehaviorGroup(account, bundle1, "b-2"),
                    createEndpoint(account, EndpointType.EMAIL_SUBSCRIPTION, "e-2"),
                    createEndpoint(account, EndpointType.WEBHOOK, "w-2")
            );

            Endpoint endpoint1 = createEndpoint(account, EndpointType.EMAIL_SUBSCRIPTION, "e-3");
            createNotificationHistory(endpoint1);
            Endpoint endpoint2 = createEndpoint(account, EndpointType.EMAIL_SUBSCRIPTION, "e-4");
            createNotificationHistory(endpoint2);

            createBehaviorGroupActions(
                    createBehaviorGroup(account, bundle1, "b-3"),
                    endpoint1,
                    createEndpoint(account, EndpointType.WEBHOOK, "w-3"),
                    endpoint2
            );

            createBehaviorGroupActions(
                    createBehaviorGroup(account, bundle1, "b-4"),
                    createEndpoint(account, EndpointType.WEBHOOK, "w-4")
            );

            createBehaviorGroupActions(
                    createBehaviorGroup(account, bundle1, "b-5")
            );

            createEndpoint(account, EndpointType.EMAIL_SUBSCRIPTION, "e-4");
        }

        EmailEndpointMigrationService.MigrationReport report = migrate().extract().as(EmailEndpointMigrationService.MigrationReport.class);
        assertEquals(16, report.getDeletedEndpoints().get());

        // Repeated behavior -> endpoint links are deleted
        assertEquals(8, report.getUpdatedBehaviorGroupActions().get());
        assertEquals(4, report.getUpdatedAccounts().get());

        assertEquals(1, getEmailEndpoints(ACCOUNT1).size());
        assertEquals(1, getEmailEndpoints(ACCOUNT2).size());
        assertEquals(1, getEmailEndpoints(ACCOUNT3).size());
        assertEquals(1, getEmailEndpoints(ACCOUNT4).size());
        assertEquals(0, getEmailEndpoints(ACCOUNT5).size());

        assertEquals(7, getActions(ACCOUNT1).size());
        assertEquals(7, getActions(ACCOUNT2).size());
        assertEquals(7, getActions(ACCOUNT3).size());
        assertEquals(7, getActions(ACCOUNT4).size());
        assertEquals(1, getActions(ACCOUNT5).size());

    }

    private Bundle createBundle(String name) {
        Bundle bundle = new Bundle();
        bundle.setName(name);
        bundle.setDisplayName("Migration bundle");
        session.persist(bundle)
                .call(session::flush)
                .await().indefinitely();
        return bundle;
    }

    private Endpoint createEndpoint(String accountId, EndpointType type, String name) {
        Endpoint endpoint = new Endpoint();
        endpoint.setAccountId(accountId);
        endpoint.setType(type);
        endpoint.setName(name);
        endpoint.setDescription("Migration endpoint");
        session.persist(endpoint)
                .call(session::flush)
                .await().indefinitely();
        return endpoint;
    }

    private BehaviorGroup createBehaviorGroup(String accountId, Bundle bundle, String name) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setAccountId(accountId);
        behaviorGroup.setBundle(bundle);
        behaviorGroup.setBundleId(bundle.getId());
        behaviorGroup.setDisplayName(name);
        session.persist(behaviorGroup)
                .call(session::flush)
                .await().indefinitely();
        return behaviorGroup;
    }

    private void createNotificationHistory(Endpoint endpoint) {
        NotificationHistory history = new NotificationHistory();
        history.setId(UUID.randomUUID());
        history.setAccountId("not used");
        history.setEndpoint(endpoint);
        history.setInvocationResult(Boolean.TRUE);
        history.setInvocationTime(1L);
        history.setCreated(LocalDateTime.now());
        session.persist(history)
                .call(session::flush)
                .await().indefinitely();
    }

    private List<BehaviorGroupAction> createBehaviorGroupActions(BehaviorGroup behaviorGroup, Endpoint...endpoints) {
        return Arrays.stream(endpoints).map(endpoint -> {
            BehaviorGroupAction action = new BehaviorGroupAction(behaviorGroup, endpoint);
            session.persist(action)
                    .call(session::flush)
                    .await().indefinitely();
            return action;
        }).collect(Collectors.toList());
    }

    private List<Endpoint> getEmailEndpoints(String account) {
        return session.createQuery("FROM Endpoint e WHERE e.type = :endpointType AND e.accountId = :accountId", Endpoint.class)
                .setParameter("endpointType", EndpointType.EMAIL_SUBSCRIPTION)
                .setParameter("accountId", account)
                .getResultList().await().indefinitely();
    }

    private List<BehaviorGroupAction> getActions(String account) {
        return session.createQuery("FROM BehaviorGroupAction a WHERE a.behaviorGroup.accountId = :accountId", BehaviorGroupAction.class)
                .setParameter("accountId", account)
                .getResultList().await().indefinitely();
    }

    private ValidatableResponse migrate() {
        return given()
                .queryParam("confirmation-token", EmailEndpointMigrationService.CONFIRMATION_TOKEN)
                .when()
                .get("/internal/email_endpoint/migrate")
                .then()
                .statusCode(200);
    }
}
