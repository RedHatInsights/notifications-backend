package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.NotificationHistory;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import io.vertx.core.json.JsonArray;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.API_INTEGRATIONS_V_1_0;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static com.redhat.cloud.notifications.routers.EventLogMigrationService.BATCH_SIZE_KEY;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EventLogMigrationServiceTest extends DbIsolatedTest {

    private static final String ACCOUNT_ID = "test-account";

    @Inject
    Mutiny.Session session;

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @BeforeAll
    static void beforeAll() {
        System.setProperty(BATCH_SIZE_KEY, "2");
    }

    @AfterAll
    static void afterAll() {
        System.clearProperty(BATCH_SIZE_KEY);
    }

    @Test
    void test() {
        Header identityHeader = initRbacMock();

        // First, we need some old-fashioned history data (without Event).
        Endpoint ep = createEndpoint();
        UUID historyId1 = createNotificationHistory(ep);
        UUID historyId2 = createNotificationHistory(ep);
        UUID historyId3 = createNotificationHistory(ep);

        // If we try to retrieve that data, the API returns nothing because the data retrieval query requires an Event.
        JsonArray historyEntries = getEndpointHistory(identityHeader, ep.getId());
        assertTrue(historyEntries.isEmpty());

        // It's time to migrate! (in other words, create one Event for each existing NotificationHistory record)
        EventLogMigrationService.MigrationReport report = migrate();

        // The migration report should contain as many history records as the data we persisted above.
        assertEquals(3L, report.getUpdatedHistoryRecords().get());

        // Let's check the history data again.
        historyEntries = getEndpointHistory(identityHeader, ep.getId());

        // The migration added an Event to each NotificationHistory record so now the API returns the expected history entries.
        assertEquals(3, historyEntries.size());
        assertContains(historyEntries, historyId1);
        assertContains(historyEntries, historyId2);
        assertContains(historyEntries, historyId3);

        // If the migration is executed again, it won't update any history record.
        report = migrate();
        assertEquals(0L, report.getUpdatedHistoryRecords().get());
    }

    private Header initRbacMock() {
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(ACCOUNT_ID, "user");
        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);
        return TestHelpers.createIdentityHeader(identityHeaderValue);
    }

    private Endpoint createEndpoint() {
        Endpoint ep = new Endpoint();
        ep.setType(WEBHOOK);
        ep.setAccountId(ACCOUNT_ID);
        ep.setName("test-ep");
        ep.setDescription("test-description");
        session.persist(ep)
                .onItem().call(session::flush)
                .await().indefinitely();
        return ep;
    }

    private UUID createNotificationHistory(Endpoint ep) {
        NotificationHistory history = new NotificationHistory();
        history.setId(UUID.randomUUID());
        history.setAccountId(ACCOUNT_ID);
        history.setEndpoint(ep);
        history.setInvocationTime(1L);
        history.setInvocationResult(true);
        session.persist(history)
                .onItem().call(session::flush)
                .await().indefinitely();
        return history.getId();
    }

    private EventLogMigrationService.MigrationReport migrate() {
        return given()
                .queryParam("confirmation-token", EventLogMigrationService.CONFIRMATION_TOKEN)
                .when()
                .get("/internal/event_log/migrate")
                .then()
                .statusCode(200)
                .extract().as(EventLogMigrationService.MigrationReport.class);
    }

    private JsonArray getEndpointHistory(Header identityHeader, UUID endpointId) {
        String responseBody = given()
                .basePath(API_INTEGRATIONS_V_1_0)
                .header(identityHeader)
                .pathParam("endpointId", endpointId)
                .when()
                .get("/endpoints/{endpointId}/history")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().asString();
        return new JsonArray(responseBody);
    }

    private void assertContains(JsonArray historyEntries, UUID historyEntry) {
        for (int i = 0; i < historyEntries.size(); i++) {
            if (historyEntries.getJsonObject(i).getString("id").equals(historyEntry.toString())) {
                return;
            }
        }
        fail();
    }
}
