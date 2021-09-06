package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.routers.models.EventLogEntry;
import com.redhat.cloud.notifications.routers.models.EventLogEntryAction;
import com.redhat.cloud.notifications.routers.models.Page;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.Header;
import io.restassured.specification.RequestSpecification;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_1_0;
import static com.redhat.cloud.notifications.MockServerClientConfig.RbacAccess;
import static com.redhat.cloud.notifications.MockServerClientConfig.RbacAccess.FULL_ACCESS;
import static com.redhat.cloud.notifications.MockServerClientConfig.RbacAccess.NO_ACCESS;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.time.ZoneOffset.UTC;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EventServiceTest extends DbIsolatedTest {

    private static final String OTHER_ACCOUNT_ID = "other-account-id";
    private static final LocalDateTime NOW = LocalDateTime.now(UTC);

    @Inject
    Mutiny.StatelessSession statelessSession;

    @Inject
    ResourceHelpers resourceHelpers;

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @Test
    void testAllQueryParams() {
        /*
         * This method is very long, but splitting it into several smaller ones would mean we have to recreate lots of
         * database records for each test. To avoid doing that, the data is only persisted once and many tests are run
         * from the same data.
         */

        Header defaultIdentityHeader = mockRbac(DEFAULT_ACCOUNT_ID, "user", FULL_ACCESS);
        Header otherIdentityHeader = mockRbac(OTHER_ACCOUNT_ID, "other-username", FULL_ACCESS);

        Bundle bundle1 = resourceHelpers.createBundle("bundle-1", "Bundle 1");
        Bundle bundle2 = resourceHelpers.createBundle("bundle-2", "Bundle 2");

        Application app1 = resourceHelpers.createApplication(bundle1.getId(), "app-1", "Application 1");
        Application app2 = resourceHelpers.createApplication(bundle2.getId(), "app-2", "Application 2");

        EventType eventType1 = resourceHelpers.createEventType(app1.getId(), "event-type-1", "Event type 1", "Event type 1");
        EventType eventType2 = resourceHelpers.createEventType(app2.getId(), "event-type-2", "Event type 2", "Event type 2");

        Event event1 = createEvent(DEFAULT_ACCOUNT_ID, eventType1, NOW.minusDays(5L));
        Event event2 = createEvent(DEFAULT_ACCOUNT_ID, eventType2, NOW);
        Event event3 = createEvent(DEFAULT_ACCOUNT_ID, eventType2, NOW.minusDays(2L));
        Event event4 = createEvent(OTHER_ACCOUNT_ID, eventType2, NOW.minusDays(10L));

        Endpoint endpoint1 = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, WEBHOOK);
        Endpoint endpoint2 = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, EMAIL_SUBSCRIPTION);

        NotificationHistory history1 = resourceHelpers.createNotificationHistory(event1, endpoint1);
        NotificationHistory history2 = resourceHelpers.createNotificationHistory(event1, endpoint2);
        NotificationHistory history3 = resourceHelpers.createNotificationHistory(event2, endpoint1);
        NotificationHistory history4 = resourceHelpers.createNotificationHistory(event3, endpoint2);

        /*
         * Test #1
         * Account: DEFAULT_ACCOUNT_ID
         * Request: No filter
         * Expected response: All event log entries from DEFAULT_ACCOUNT_ID should be returned
         */
        Page<EventLogEntry> page = getEventLogPage(defaultIdentityHeader, null, null, null, null, null, null, null, null);
        assertEquals(3, page.getMeta().getCount());
        assertEquals(3, page.getData().size());
        assertSameEvent(page.getData().get(0), event2, history3);
        assertSameEvent(page.getData().get(1), event3, history4);
        assertSameEvent(page.getData().get(2), event1, history1, history2);
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #2
         * Account: OTHER_ACCOUNT_ID
         * Request: No filter
         * Expected response: All event log entries from OTHER_ACCOUNT_ID should be returned
         */
        page = getEventLogPage(otherIdentityHeader, null, null, null, null, null, null, null, null);
        assertEquals(1, page.getMeta().getCount());
        assertEquals(1, page.getData().size());
        assertSameEvent(page.getData().get(0), event4);
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #3
         * Account: DEFAULT_ACCOUNT_ID
         * Request: Unknown bundle
         */
        page = getEventLogPage(defaultIdentityHeader, Set.of(randomUUID()), null, null, null, null, null, null, null);
        assertEquals(0, page.getMeta().getCount());
        assertTrue(page.getData().isEmpty());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #4
         * Account: DEFAULT_ACCOUNT_ID
         * Request: One existing bundle
         */
        page = getEventLogPage(defaultIdentityHeader, Set.of(bundle1.getId()), null, null, null, null, null, null, null);
        assertEquals(1, page.getMeta().getCount());
        assertEquals(1, page.getData().size());
        assertSameEvent(page.getData().get(0), event1, history1, history2);
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #5
         * Account: DEFAULT_ACCOUNT_ID
         * Request: Multiple existing bundles, sort by ascending bundle names
         */
        page = getEventLogPage(defaultIdentityHeader, Set.of(bundle1.getId(), bundle2.getId()), null, null, null, null, null, null, "bundle:asc");
        assertEquals(3, page.getMeta().getCount());
        assertEquals(3, page.getData().size());
        assertSameEvent(page.getData().get(0), event1, history1, history2);
        assertSameEvent(page.getData().get(1), event2, history3);
        assertSameEvent(page.getData().get(2), event3, history4);
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #6
         * Account: DEFAULT_ACCOUNT_ID
         * Request: Unknown application
         */
        page = getEventLogPage(defaultIdentityHeader, null, Set.of(randomUUID()), null, null, null, null, null, null);
        assertEquals(0, page.getMeta().getCount());
        assertTrue(page.getData().isEmpty());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #7
         * Account: DEFAULT_ACCOUNT_ID
         * Request: One existing application
         */
        page = getEventLogPage(defaultIdentityHeader, null, Set.of(app2.getId()), null, null, null, null, null, null);
        assertEquals(2, page.getMeta().getCount());
        assertEquals(2, page.getData().size());
        assertSameEvent(page.getData().get(0), event2, history3);
        assertSameEvent(page.getData().get(1), event3, history4);
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #8
         * Account: DEFAULT_ACCOUNT_ID
         * Request: Multiple existing applications, sort by ascending application names
         */
        page = getEventLogPage(defaultIdentityHeader, null, Set.of(app1.getId(), app2.getId()), null, null, null, null, null, "application:asc");
        assertEquals(3, page.getMeta().getCount());
        assertEquals(3, page.getData().size());
        assertSameEvent(page.getData().get(0), event1, history1, history2);
        assertSameEvent(page.getData().get(1), event2, history3);
        assertSameEvent(page.getData().get(2), event3, history4);
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #9
         * Account: DEFAULT_ACCOUNT_ID
         * Request: Unknown event type
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, "unknown", null, null, null, null, null);
        assertEquals(0, page.getMeta().getCount());
        assertTrue(page.getData().isEmpty());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #10
         * Account: DEFAULT_ACCOUNT_ID
         * Request: Existing event type
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, eventType1.getName().substring(2), null, null, null, null, null);
        assertEquals(1, page.getMeta().getCount());
        assertEquals(1, page.getData().size());
        assertSameEvent(page.getData().get(0), event1, history1, history2);
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #11
         * Account: DEFAULT_ACCOUNT_ID
         * Request: Start date three days in the past
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, null, NOW.minusDays(3L), null, null, null, null);
        assertEquals(2, page.getMeta().getCount());
        assertEquals(2, page.getData().size());
        assertSameEvent(page.getData().get(0), event2, history3);
        assertSameEvent(page.getData().get(1), event3, history4);
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #12
         * Account: DEFAULT_ACCOUNT_ID
         * Request: End date three days in the past
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, null, null, NOW.minusDays(3L), null, null, null);
        assertEquals(1, page.getMeta().getCount());
        assertEquals(1, page.getData().size());
        assertSameEvent(page.getData().get(0), event1, history1, history2);
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #13
         * Account: DEFAULT_ACCOUNT_ID
         * Request: Both start and end date are set
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, null, NOW.minusDays(3L), NOW.minusDays(1L), null, null, null);
        assertEquals(1, page.getMeta().getCount());
        assertEquals(1, page.getData().size());
        assertSameEvent(page.getData().get(0), event3, history4);
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #14
         * Account: DEFAULT_ACCOUNT_ID
         * Request: Let's try all request params at once!
         */
        page = getEventLogPage(defaultIdentityHeader, Set.of(bundle2.getId()), Set.of(app2.getId()), eventType2.getName(), NOW.minusDays(3L), NOW.minusDays(1L), 10, 0, "created:desc");
        assertEquals(1, page.getMeta().getCount());
        assertEquals(1, page.getData().size());
        assertSameEvent(page.getData().get(0), event3, history4);
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #15
         * Account: DEFAULT_ACCOUNT_ID
         * Request: No filter, limit without offset
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, null, null, null, 2, null, null);
        assertEquals(3, page.getMeta().getCount());
        assertEquals(2, page.getData().size());
        assertSameEvent(page.getData().get(0), event2, history3);
        assertSameEvent(page.getData().get(1), event3, history4);
        assertLinks(page.getLinks(), "first", "last", "next");

        /*
         * Test #16
         * Account: DEFAULT_ACCOUNT_ID
         * Request: No filter, limit with offset
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, null, null, null, 1, 2, null);
        assertEquals(3, page.getMeta().getCount());
        assertEquals(1, page.getData().size());
        assertSameEvent(page.getData().get(0), event1, history1, history2);
        assertLinks(page.getLinks(), "first", "last", "prev");

        /*
         * Test #17
         * Account: DEFAULT_ACCOUNT_ID
         * Request: No filter, sort by ascending event names
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, null, null, null, null, null, "event:asc");
        assertEquals(3, page.getMeta().getCount());
        assertEquals(3, page.getData().size());
        assertSameEvent(page.getData().get(0), event1, history1, history2);
        assertSameEvent(page.getData().get(1), event2, history3);
        assertSameEvent(page.getData().get(2), event3, history4);
        assertLinks(page.getLinks(), "first", "last");
    }

    @Test
    void testInsufficientPrivileges() {
        Header noAccessIdentityHeader = mockRbac("tenant", "noAccess", NO_ACCESS);
        given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(noAccessIdentityHeader)
                .when().get("/event")
                .then()
                .statusCode(403)
                .contentType(JSON);
    }

    @Test
    void testInvalidSortBy() {
        Header identityHeader = mockRbac(DEFAULT_ACCOUNT_ID, "user", FULL_ACCESS);
        given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .param("sortBy", "I am not valid!")
                .when().get("/event")
                .then()
                .statusCode(400)
                .contentType(JSON);
    }

    @Test
    void testInvalidLimit() {
        Header identityHeader = mockRbac(DEFAULT_ACCOUNT_ID, "user", FULL_ACCESS);
        given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .param("limit", 0)
                .when().get("/event")
                .then()
                .statusCode(400)
                .contentType(JSON);
        given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader)
                .param("limit", 999999)
                .when().get("/event")
                .then()
                .statusCode(400)
                .contentType(JSON);
    }

    private Event createEvent(String accountId, EventType eventType, LocalDateTime created) {
        Event event = new Event();
        event.setAccountId(accountId);
        event.setEventType(eventType);
        event.setCreated(created);
        statelessSession.insert(event)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .await()
                .assertCompleted();
        return event;
    }

    private Header mockRbac(String tenant, String username, RbacAccess access) {
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, username);
        mockServerConfig.addMockRbacAccess(identityHeaderValue, access);
        return TestHelpers.createIdentityHeader(identityHeaderValue);
    }

    private static Page<EventLogEntry> getEventLogPage(Header identityHeader, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeName,
                                                       LocalDateTime startDate, LocalDateTime endDate, Integer limit, Integer offset, String sortBy) {
        RequestSpecification request = given()
                .basePath(API_NOTIFICATIONS_V_1_0)
                .header(identityHeader);
        if (bundleIds != null) {
            request.param("bundleIds", bundleIds);
        }
        if (appIds != null) {
            request.param("appIds", appIds);
        }
        if (eventTypeName != null) {
            request.param("eventTypeName", eventTypeName);
        }
        if (startDate != null) {
            request.param("startDate", startDate.toLocalDate().toString());
        }
        if (endDate != null) {
            request.param("endDate", endDate.toLocalDate().toString());
        }
        if (limit != null) {
            request.param("limit", limit);
        }
        if (offset != null) {
            request.param("offset", offset);
        }
        if (sortBy != null) {
            request.param("sortBy", sortBy);
        }
        return request
                .when().get("/event")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().as(new TypeRef<Page<EventLogEntry>>() { });
    }

    private static void assertSameEvent(EventLogEntry eventLogEntry, Event event, NotificationHistory... historyEntries) {
        assertEquals(event.getId(), eventLogEntry.getId());
        // Jackson's serialization gets rid of nanoseconds so an equals between the LocalDateTime objects won't work.
        assertEquals(event.getCreated().toEpochSecond(UTC), eventLogEntry.getCreated().toEpochSecond(UTC));
        assertEquals(event.getEventType().getApplication().getBundle().getDisplayName(), eventLogEntry.getBundle());
        assertEquals(event.getEventType().getApplication().getDisplayName(), eventLogEntry.getApplication());
        assertEquals(event.getEventType().getDisplayName(), eventLogEntry.getEventType());
        if (historyEntries == null) {
            assertTrue(eventLogEntry.getActions().isEmpty());
        } else {
            assertEquals(historyEntries.length, eventLogEntry.getActions().size());
            for (EventLogEntryAction eventLogEntryAction : eventLogEntry.getActions()) {
                Optional<NotificationHistory> historyEntry = Arrays.stream(historyEntries)
                        .filter(entry -> entry.getId().equals(eventLogEntryAction.getId())).findAny();
                assertTrue(historyEntry.isPresent());
                assertEquals(historyEntry.get().getEndpoint().getType(), eventLogEntryAction.getEndpointType());
                assertEquals(historyEntry.get().isInvocationResult(), eventLogEntryAction.getInvocationResult());
            }
        }
    }

    private static void assertLinks(Map<String, String> links, String... expectedKeys) {
        assertEquals(expectedKeys.length, links.size());
        for (String key : expectedKeys) {
            assertTrue(links.containsKey(key));
        }
    }
}
