package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointDefault;
import com.redhat.cloud.notifications.models.EndpointTarget;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.routers.BehaviorGroupMigrationService.CONFIRMATION_TOKEN;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class BehaviorGroupMigrationServiceTest extends DbIsolatedTest {

    private static final String ACCOUNT1 = "account-1";
    private static final String ACCOUNT2 = "account-2";
    private static final String ACCOUNT3 = "account-3";
    private static final String ACCOUNT4 = "account-4";

    @Inject
    Mutiny.Session session;

    @Test
    void testFullMigration() {

        /**************************************
         * Bundle > App > EventType hierarchy *
         **************************************/

        /*
         * - Bundle: test-bundle-1
         *   - App: test-app-1
         *     - EventType: test-event-type-1
         *     - EventType: test-event-type-2
         *   - App: test-app-2
         *     - EventType: test-event-type-3
         * - Bundle: test-bundle-2
         *   - App: test-app-3
         *     - EventType: test-event-type-4
         */

        Bundle bundle1 = createBundle("test-bundle-1");
        Application app1 = createApp(bundle1, "test-app-1");
        EventType eventType1 = createEventType(app1, "test-event-type-1");
        EventType eventType2 = createEventType(app1, "test-event-type-2");
        Application app2 = createApp(bundle1, "test-app-2");
        EventType eventType3 = createEventType(app2, "test-event-type-3");
        Bundle bundle2 = createBundle("test-bundle-2");
        Application app3 = createApp(bundle2, "test-app-3");
        EventType eventType4 = createEventType(app3, "test-event-type-4");

        /*************
         * Account 1 *
         *************/

        Endpoint endpoint1 = createEndpoint(ACCOUNT1, EndpointType.EMAIL_SUBSCRIPTION, "test-endpoint-1");
        Endpoint endpoint2 = createEndpoint(ACCOUNT1, EndpointType.WEBHOOK, "test-endpoint-2");

        /*
         * Default actions creation.
         * This inserts records into the 'endpoint_defaults' table.
         */
        addEndpointToDefaults(ACCOUNT1, endpoint1);
        addEndpointToDefaults(ACCOUNT1, endpoint2);

        /*
         * Default actions activation.
         * This inserts records into the 'endpoint_targets' table.
         */
        Endpoint defaultEndpoint1 = createEndpoint(ACCOUNT1, EndpointType.DEFAULT, "default-endpoint-1");
        linkEndpointToEventType(ACCOUNT1, defaultEndpoint1, eventType1);
        linkEndpointToEventType(ACCOUNT1, defaultEndpoint1, eventType4);

        /*
         * Non-default actions creation.
         * This inserts records into the 'endpoint_targets' table.
         */
        linkEndpointToEventType(ACCOUNT1, endpoint1, eventType3);
        linkEndpointToEventType(ACCOUNT1, endpoint2, eventType3);

        /*************
         * Account 2 *
         *************/

        Endpoint endpoint3 = createEndpoint(ACCOUNT2, EndpointType.EMAIL_SUBSCRIPTION, "test-endpoint-3");
        Endpoint endpoint4 = createEndpoint(ACCOUNT2, EndpointType.WEBHOOK, "test-endpoint-4");
        Endpoint endpoint5 = createEndpoint(ACCOUNT2, EndpointType.WEBHOOK, "test-endpoint-5");

        /*
         * Default action creation.
         * This inserts a record into the 'endpoint_defaults' table.
         */
        addEndpointToDefaults(ACCOUNT2, endpoint3);

        /*
         * Default actions activation.
         * This inserts a record into the 'endpoint_targets' table.
         */
        Endpoint defaultEndpoint2 = createEndpoint(ACCOUNT2, EndpointType.DEFAULT, "default-endpoint-2");
        linkEndpointToEventType(ACCOUNT2, defaultEndpoint2, eventType1);

        /*
         * Non-default actions creation.
         * This inserts records into the 'endpoint_targets' table.
         */
        linkEndpointToEventType(ACCOUNT2, endpoint4, eventType2);
        linkEndpointToEventType(ACCOUNT2, endpoint5, eventType2);
        linkEndpointToEventType(ACCOUNT2, endpoint4, eventType3);

        /*************
         * Account 3 *
         *************/

        Endpoint endpoint6 = createEndpoint(ACCOUNT3, EndpointType.WEBHOOK, "test-endpoint-6");

        /*
         * Default action creation.
         * This inserts a record into the 'endpoint_defaults' table.
         */
        addEndpointToDefaults(ACCOUNT3, endpoint6);

        /*************
         * Account 4 *
         *************/

        Endpoint endpoint7 = createEndpoint(ACCOUNT4, EndpointType.WEBHOOK, "test-endpoint-7");
        Endpoint endpoint8 = createEndpoint(ACCOUNT4, EndpointType.WEBHOOK, "test-endpoint-8");

        /*
         * Non-default actions creation.
         * This inserts records into the 'endpoint_targets' table.
         */
        linkEndpointToEventType(ACCOUNT4, endpoint7, eventType1);
        linkEndpointToEventType(ACCOUNT4, endpoint8, eventType1);
        linkEndpointToEventType(ACCOUNT4, endpoint7, eventType2);
        linkEndpointToEventType(ACCOUNT4, endpoint7, eventType3);
        linkEndpointToEventType(ACCOUNT4, endpoint8, eventType3);
        linkEndpointToEventType(ACCOUNT4, endpoint7, eventType4);

        /*************
         * Migration *
         *************/

        migrate();

        /**************
         * Assertions *
         **************/

        /*
         * First, let's check that all links established between event types and endpoints using the old tables are
         * still working with the behavior groups tables.
         */
        assertEndpoints(ACCOUNT1, eventType1.getId(), endpoint1, endpoint2);
        assertEndpoints(ACCOUNT1, eventType3.getId(), endpoint1, endpoint2);
        assertEndpoints(ACCOUNT1, eventType4.getId(), endpoint1, endpoint2);
        assertEndpoints(ACCOUNT2, eventType1.getId(), endpoint3);
        assertEndpoints(ACCOUNT2, eventType2.getId(), endpoint4, endpoint5);
        assertEndpoints(ACCOUNT2, eventType3.getId(), endpoint4);
        assertEndpoints(ACCOUNT4, eventType1.getId(), endpoint7, endpoint8);
        assertEndpoints(ACCOUNT4, eventType2.getId(), endpoint7);
        assertEndpoints(ACCOUNT4, eventType3.getId(), endpoint7, endpoint8);
        assertEndpoints(ACCOUNT4, eventType4.getId(), endpoint7);

        // Now let's check the behavior groups that were created during the migration.
        List<BehaviorGroup> behaviorGroups = getAllBehaviorGroups();

        /*
         * There are 3 bundles in the DB (bundle1, bundle2 and rhel from DbCleaner) and 3 accounts with default actions
         * (accounts 1, 2 and 3) so 9 behavior groups should be created from the default actions.
         * Account 1 also has non-default actions but these match an existing behavior group which is reused.
         * Account 2 also has non-default actions linked with 2 different event types, so 2 more behavior groups should
         * be created for that account.
         * Account 4 only has non-default actions linked with 4 different event types, but there should be 1 behavior
         * group aggregation and 3 behavior groups should be created in the end.
         * 3 x 3 + 2 + 3 = 14
         */
        assertEquals(14, behaviorGroups.size());

        // Account 1 should have 3 behavior groups created from both default and non-default actions.
        assertEquals(3, behaviorGroups.stream().filter(behaviorGroup -> behaviorGroup.getAccountId().equals(ACCOUNT1)).count());

        // Account 2 should have 3 behavior groups created from default actions and 2 behavior groups created from non-default actions.
        assertEquals(5, behaviorGroups.stream().filter(behaviorGroup -> behaviorGroup.getAccountId().equals(ACCOUNT2)).count());

        // Account 3 should have 3 behavior groups created from default actions.
        assertEquals(3, behaviorGroups.stream().filter(behaviorGroup -> behaviorGroup.getAccountId().equals(ACCOUNT3)).count());

        // Account 4 should have 3 behavior groups created from non-default actions.
        assertEquals(3, behaviorGroups.stream().filter(behaviorGroup -> behaviorGroup.getAccountId().equals(ACCOUNT4)).count());

        assertEquals(7, behaviorGroups.stream().filter(behaviorGroup -> behaviorGroup.getBundle().equals(bundle1)).count());
        assertEquals(4, behaviorGroups.stream().filter(behaviorGroup -> behaviorGroup.getBundle().equals(bundle2)).count());
    }

    @Test
    void testMigrateWithoutConfirmationToken() {
        given()
                .when()
                .get("/internal/behaviorGroups/migrate")
                .then()
                .statusCode(400);
        assertTrue(getAllBehaviorGroups().isEmpty());
    }

    @Test
    void testMigrateEmptyBundle() {
        // This test checks that no exception is thrown if the migration is run with an empty bundle (no app).
        createBundle("empty-bundle");
        migrate();
        assertTrue(getAllBehaviorGroups().isEmpty());
    }

    @Test
    void testMigrateEmptyApp() {
        // This test checks that no exception is thrown if the migration is run with an empty app (no event type).
        Bundle bundle = createBundle("migration-bundle");
        createApp(bundle, "empty-app");
        migrate();
        assertTrue(getAllBehaviorGroups().isEmpty());
    }

    @Test
    void testMigrateNotLinkedEventType() {
        // This test checks that no exception is thrown if the migration is run with an event type not linked to anything.
        Bundle bundle = createBundle("migration-bundle");
        Application app = createApp(bundle, "migration-app");
        createEventType(app, "migration-event-type");
        migrate();
        assertTrue(getAllBehaviorGroups().isEmpty());
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

    private Application createApp(Bundle bundle, String name) {
        Application app = new Application();
        app.setBundle(bundle);
        app.setBundleId(bundle.getId());
        app.setName(name);
        app.setDisplayName("Migration app");
        session.persist(app)
                .call(session::flush)
                .await().indefinitely();
        return app;
    }

    private EventType createEventType(Application app, String name) {
        EventType eventType = new EventType();
        eventType.setApplication(app);
        eventType.setApplicationId(app.getId());
        eventType.setName(name);
        eventType.setDisplayName("Migration event type");
        session.persist(eventType)
                .call(session::flush)
                .await().indefinitely();
        return eventType;
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

    private void addEndpointToDefaults(String accountId, Endpoint endpoint) {
        EndpointDefault endpointDefault = new EndpointDefault(accountId, endpoint);
        session.persist(endpointDefault)
                .call(session::flush)
                .await().indefinitely();
    }

    private void linkEndpointToEventType(String accountId, Endpoint endpoint, EventType eventType) {
        EndpointTarget endpointTarget = new EndpointTarget(accountId, endpoint, eventType);
        session.persist(endpointTarget)
                .call(session::flush)
                .await().indefinitely();
    }

    private void migrate() {
        given()
                .queryParam("confirmation-token", CONFIRMATION_TOKEN)
                .when()
                .get("/internal/behaviorGroups/migrate")
                .then()
                .statusCode(200);
    }

    private void assertEndpoints(String accountId, UUID eventTypeId, Endpoint... expectedEndpoints) {
        String query = "SELECT e FROM Endpoint e JOIN e.behaviorGroupActions bga JOIN bga.behaviorGroup.behaviors b " +
                "WHERE bga.behaviorGroup.accountId = :accountId AND b.eventType.id = :eventTypeId";
        List<Endpoint> actualEndpoints = session.createQuery(query, Endpoint.class)
                .setParameter("accountId", accountId)
                .setParameter("eventTypeId", eventTypeId)
                .getResultList()
                .await().indefinitely();
        assertEquals(expectedEndpoints.length, actualEndpoints.size());
        assertTrue(actualEndpoints.containsAll(Arrays.asList(expectedEndpoints)));
    }

    private List<BehaviorGroup> getAllBehaviorGroups() {
        return session.createQuery("FROM BehaviorGroup", BehaviorGroup.class)
                .getResultList()
                .await().indefinitely();
    }
}
