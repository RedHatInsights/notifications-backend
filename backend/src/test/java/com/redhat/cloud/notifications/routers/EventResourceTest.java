package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.NotificationStatus;
import com.redhat.cloud.notifications.routers.models.EventLogEntry;
import com.redhat.cloud.notifications.routers.models.EventLogEntryAction;
import com.redhat.cloud.notifications.routers.models.Page;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.Header;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess;
import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.FULL_ACCESS;
import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.NOTIFICATIONS_ACCESS_ONLY;
import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.NOTIFICATIONS_READ_ACCESS_ONLY;
import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.NO_ACCESS;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.models.EndpointType.CAMEL;
import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static com.redhat.cloud.notifications.routers.EventResource.PATH;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.time.ZoneOffset.UTC;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EventResourceTest extends DbIsolatedTest {

    private static final String OTHER_ACCOUNT_ID = "other-account-id";
    private static final String OTHER_ORG_ID = "other-org-id";
    private static final LocalDateTime NOW = LocalDateTime.now(UTC);
    private static final String PAYLOAD = "payload";

    @Inject
    EntityManager entityManager;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EndpointRepository endpointRepository;

    @Test
    void shouldNotBeAllowedTogetEventLogsWhenUserHasNotificationsAccessRightsOnly() {
        Header defaultIdentityHeader = mockRbac(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "user2", NOTIFICATIONS_ACCESS_ONLY);
        given()
                .header(defaultIdentityHeader)
                .when().get(PATH)
                .then()
                .statusCode(403);
    }

    @Test
    void testAllQueryParams() {
        /*
         * This method is very long, but splitting it into several smaller ones would mean we have to recreate lots of
         * database records for each test. To avoid doing that, the data is only persisted once and many tests are run
         * from the same data.
         */

        Header defaultIdentityHeader = mockRbac(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "user", FULL_ACCESS);
        Header otherIdentityHeader = mockRbac(OTHER_ACCOUNT_ID, OTHER_ORG_ID, "other-username", FULL_ACCESS);

        Bundle bundle1 = resourceHelpers.createBundle("bundle-1", "Bundle 1");
        Bundle bundle2 = resourceHelpers.createBundle("bundle-2", "Bundle 2");
        Application app1 = resourceHelpers.createApplication(bundle1.getId(), "app-1", "Application 1");
        Application app2 = resourceHelpers.createApplication(bundle2.getId(), "app-2", "Application 2");
        EventType eventType1 = resourceHelpers.createEventType(app1.getId(), "event-type-1", "Event type 1", "Event type 1");
        EventType eventType2 = resourceHelpers.createEventType(app2.getId(), "event-type-2", "Event type 2", "Event type 2");
        Event event1 = createEvent(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, bundle1, app1, eventType1, NOW.minusDays(5L));
        Event event2 = createEvent(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, bundle2, app2, eventType2, NOW);
        Event event3 = createEvent(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, bundle2, app2, eventType2, NOW.minusDays(2L));
        Event event4 = createEvent(OTHER_ACCOUNT_ID, OTHER_ORG_ID, bundle2, app2, eventType2, NOW.minusDays(10L));
        Endpoint endpoint1 = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, WEBHOOK);
        Endpoint endpoint2 = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EMAIL_SUBSCRIPTION);
        Endpoint endpoint3 = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, CAMEL, "SlAcK");
        NotificationHistory history1 = resourceHelpers.createNotificationHistory(event1, endpoint1, NotificationStatus.SUCCESS);
        NotificationHistory history2 = resourceHelpers.createNotificationHistory(event1, endpoint2, NotificationStatus.FAILED_PROCESSING);
        NotificationHistory history3 = resourceHelpers.createNotificationHistory(event2, endpoint1, NotificationStatus.SUCCESS);
        NotificationHistory history4 = resourceHelpers.createNotificationHistory(event3, endpoint2, NotificationStatus.SUCCESS);
        NotificationHistory history5 = resourceHelpers.createNotificationHistory(event3, endpoint3, NotificationStatus.SUCCESS);
        endpointRepository.deleteEndpoint(DEFAULT_ORG_ID, endpoint1.getId());
        endpointRepository.deleteEndpoint(DEFAULT_ORG_ID, endpoint2.getId());
        endpointRepository.deleteEndpoint(DEFAULT_ORG_ID, endpoint3.getId());

        /*
         * Test #1
         * Account: DEFAULT_ACCOUNT_ID
         * Request: No filter
         * Expected response: All event log entries from DEFAULT_ACCOUNT_ID should be returned
         */
        Page<EventLogEntry> page = getEventLogPage(defaultIdentityHeader, null, null, null, null, null, null, null, null, null, null, null, false, true);
        assertEquals(3, page.getMeta().getCount());
        assertEquals(3, page.getData().size());
        assertSameEvent(page.getData().get(0), event2, history3);
        assertSameEvent(page.getData().get(1), event3, history4, history5);
        assertSameEvent(page.getData().get(2), event1, history1, history2);
        assertNull(page.getData().get(0).getPayload());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #2
         * Account: OTHER_ACCOUNT_ID
         * Request: No filter
         * Expected response: All event log entries from OTHER_ACCOUNT_ID should be returned
         */
        page = getEventLogPage(otherIdentityHeader, null, null, null, null, null, null, null, null, null, null, null, false, true);
        assertEquals(1, page.getMeta().getCount());
        assertEquals(1, page.getData().size());
        assertSameEvent(page.getData().get(0), event4);
        assertNull(page.getData().get(0).getPayload());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #3
         * Account: DEFAULT_ACCOUNT_ID
         * Request: Unknown bundle
         */
        page = getEventLogPage(defaultIdentityHeader, Set.of(randomUUID()), null, null, null, null, null, null, null, null, null, null, false, true);
        assertEquals(0, page.getMeta().getCount());
        assertTrue(page.getData().isEmpty());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #4
         * Account: DEFAULT_ACCOUNT_ID
         * Request: One existing bundle
         */
        page = getEventLogPage(defaultIdentityHeader, Set.of(bundle1.getId()), null, null, null, null, null, null, null, null, null, null, false, true);
        assertEquals(1, page.getMeta().getCount());
        assertEquals(1, page.getData().size());
        assertSameEvent(page.getData().get(0), event1, history1, history2);
        assertNull(page.getData().get(0).getPayload());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #5
         * Account: DEFAULT_ACCOUNT_ID
         * Request: Multiple existing bundles, sort by ascending bundle names
         */
        page = getEventLogPage(defaultIdentityHeader, Set.of(bundle1.getId(), bundle2.getId()), null, null, null, null, null, null, null, null, null, "bundle:asc", false, true);
        assertEquals(3, page.getMeta().getCount());
        assertEquals(3, page.getData().size());
        assertSameEvent(page.getData().get(0), event1, history1, history2);
        assertSameEvent(page.getData().get(1), event2, history3);
        assertSameEvent(page.getData().get(2), event3, history4, history5);
        assertNull(page.getData().get(0).getPayload());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #6
         * Account: DEFAULT_ACCOUNT_ID
         * Request: Unknown application
         */
        page = getEventLogPage(defaultIdentityHeader, null, Set.of(randomUUID()), null, null, null, null, null, null, null, null, null, false, true);
        assertEquals(0, page.getMeta().getCount());
        assertTrue(page.getData().isEmpty());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #7
         * Account: DEFAULT_ACCOUNT_ID
         * Request: One existing application
         */
        page = getEventLogPage(defaultIdentityHeader, null, Set.of(app2.getId()), null, null, null, null, null, null, null, null, null, false, true);
        assertEquals(2, page.getMeta().getCount());
        assertEquals(2, page.getData().size());
        assertSameEvent(page.getData().get(0), event2, history3);
        assertSameEvent(page.getData().get(1), event3, history4, history5);
        assertNull(page.getData().get(0).getPayload());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #8
         * Account: DEFAULT_ACCOUNT_ID
         * Request: Multiple existing applications, sort by ascending application names
         */
        page = getEventLogPage(defaultIdentityHeader, null, Set.of(app1.getId(), app2.getId()), null, null, null, null, null, null, null, null, "application:asc", false, true);
        assertEquals(3, page.getMeta().getCount());
        assertEquals(3, page.getData().size());
        assertSameEvent(page.getData().get(0), event1, history1, history2);
        assertSameEvent(page.getData().get(1), event2, history3);
        assertSameEvent(page.getData().get(2), event3, history4, history5);
        assertNull(page.getData().get(0).getPayload());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #9
         * Account: DEFAULT_ACCOUNT_ID
         * Request: Unknown event type
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, "unknown", null, null, null, null, null, null, null, null, false, true);
        assertEquals(0, page.getMeta().getCount());
        assertTrue(page.getData().isEmpty());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #10
         * Account: DEFAULT_ACCOUNT_ID
         * Request: Existing event type
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, eventType1.getDisplayName().substring(2).toUpperCase(), null, null, null, null, null, null, null, null, false, true);
        assertEquals(1, page.getMeta().getCount());
        assertEquals(1, page.getData().size());
        assertSameEvent(page.getData().get(0), event1, history1, history2);
        assertNull(page.getData().get(0).getPayload());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #11
         * Account: DEFAULT_ACCOUNT_ID
         * Request: Start date three days in the past
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, null, NOW.minusDays(3L), null, null, null, null, null, null, null, false, true);
        assertEquals(2, page.getMeta().getCount());
        assertEquals(2, page.getData().size());
        assertSameEvent(page.getData().get(0), event2, history3);
        assertSameEvent(page.getData().get(1), event3, history4, history5);
        assertNull(page.getData().get(0).getPayload());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #12
         * Account: DEFAULT_ACCOUNT_ID
         * Request: End date three days in the past
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, null, null, NOW.minusDays(3L), null, null, null, null, null, null, false, true);
        assertEquals(1, page.getMeta().getCount());
        assertEquals(1, page.getData().size());
        assertSameEvent(page.getData().get(0), event1, history1, history2);
        assertNull(page.getData().get(0).getPayload());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #13
         * Account: DEFAULT_ACCOUNT_ID
         * Request: Both start and end date are set
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, null, NOW.minusDays(3L), NOW.minusDays(1L), null, null, null, null, null, null, false, true);
        assertEquals(1, page.getMeta().getCount());
        assertEquals(1, page.getData().size());
        assertSameEvent(page.getData().get(0), event3, history4, history5);
        assertNull(page.getData().get(0).getPayload());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #14
         * Account: DEFAULT_ACCOUNT_ID
         * Request: Let's try all request params at once!
         */
        page = getEventLogPage(defaultIdentityHeader, Set.of(bundle2.getId()), Set.of(app2.getId()), eventType2.getDisplayName(), NOW.minusDays(3L), NOW.minusDays(1L), Set.of(EMAIL_SUBSCRIPTION.name()), Set.of(TRUE), null, 10, 0, "created:desc", true, true);
        assertEquals(1, page.getMeta().getCount());
        assertEquals(1, page.getData().size());
        assertSameEvent(page.getData().get(0), event3, history4, history5);
        assertEquals(PAYLOAD, page.getData().get(0).getPayload());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #15
         * Account: DEFAULT_ACCOUNT_ID
         * Request: No filter, limit without offset
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, null, null, null, null, null, null, 2, null, null, false, true);
        assertEquals(3, page.getMeta().getCount());
        assertEquals(2, page.getData().size());
        assertSameEvent(page.getData().get(0), event2, history3);
        assertSameEvent(page.getData().get(1), event3, history4, history5);
        assertNull(page.getData().get(0).getPayload());
        assertLinks(page.getLinks(), "first", "last", "next");

        /*
         * Test #16
         * Account: DEFAULT_ACCOUNT_ID
         * Request: No filter, limit with offset
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, null, null, null, null, null, null, 1, 2, null, false, true);
        assertEquals(3, page.getMeta().getCount());
        assertEquals(1, page.getData().size());
        assertSameEvent(page.getData().get(0), event1, history1, history2);
        assertNull(page.getData().get(0).getPayload());
        assertLinks(page.getLinks(), "first", "last", "prev");

        /*
         * Test #17
         * Account: DEFAULT_ACCOUNT_ID
         * Request: No filter, sort by ascending event names
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, null, null, null, null, null, null, null, null, "event:asc", false, true);
        assertEquals(3, page.getMeta().getCount());
        assertEquals(3, page.getData().size());
        assertSameEvent(page.getData().get(0), event1, history1, history2);
        assertSameEvent(page.getData().get(1), event2, history3);
        assertSameEvent(page.getData().get(2), event3, history4, history5);
        assertNull(page.getData().get(0).getPayload());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #18
         * Account: DEFAULT_ACCOUNT_ID
         * Request: WEBHOOK endpoints
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, null, null, null, Set.of(WEBHOOK.name()), null, null, null, null, null, false, true);
        assertEquals(2, page.getMeta().getCount());
        assertEquals(2, page.getData().size());
        assertSameEvent(page.getData().get(0), event2, history3);
        assertSameEvent(page.getData().get(1), event1, history1, history2);
        assertNull(page.getData().get(0).getPayload());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #19
         * Account: DEFAULT_ACCOUNT_ID
         * Request: Invocation succeeded
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, null, null, null, null, Set.of(TRUE), null, null, null, null, false, true);
        assertEquals(3, page.getMeta().getCount());
        assertEquals(3, page.getData().size());
        assertSameEvent(page.getData().get(0), event2, history3);
        assertSameEvent(page.getData().get(1), event3, history4, history5);
        assertSameEvent(page.getData().get(2), event1, history1, history2);
        assertNull(page.getData().get(0).getPayload());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #20
         * Account: DEFAULT_ACCOUNT_ID
         * Request: EMAIL_SUBSCRIPTION endpoints and invocation failed
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, null, null, null, Set.of(EMAIL_SUBSCRIPTION.name()), Set.of(FALSE), null, null, null, null, false, true);
        assertEquals(1, page.getMeta().getCount());
        assertEquals(1, page.getData().size());
        assertSameEvent(page.getData().get(0), event1, history1, history2);
        assertNull(page.getData().get(0).getPayload());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #21
         * Account: DEFAULT_ACCOUNT_ID
         * Request: No filter
         * Expected response: All event log entries from DEFAULT_ACCOUNT_ID should be returned without actions
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, null, null, null, null, null, null, null, null, null, false, false);
        assertEquals(3, page.getMeta().getCount());
        assertEquals(3, page.getData().size());
        assertSameEvent(page.getData().get(0), event2);
        assertSameEvent(page.getData().get(1), event3);
        assertSameEvent(page.getData().get(2), event1);
        assertNull(page.getData().get(0).getPayload());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #22
         * Account: DEFAULT_ACCOUNT_ID
         * Request: CAMEL endpoints
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, null, null, null, Set.of(CAMEL.name()), null, null, null, null, null, false, true);
        assertEquals(1, page.getMeta().getCount());
        assertEquals(1, page.getData().size());
        assertSameEvent(page.getData().get(0), event3, history4, history5);
        assertNull(page.getData().get(0).getPayload());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #23
         * Account: DEFAULT_ACCOUNT_ID
         * Request: CAMEL:SPLUNK endpoints
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, null, null, null, Set.of("camel:splunk"), null, null, null, null, null, false, true);
        assertEquals(0, page.getMeta().getCount());
        assertEquals(0, page.getData().size());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #24
         * Account: DEFAULT_ACCOUNT_ID
         * Request: CAMEL:SLACK endpoints
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, null, null, null, Set.of("camel:slack"), null, null, null, null, null, false, true);
        assertEquals(1, page.getMeta().getCount());
        assertEquals(1, page.getData().size());
        assertSameEvent(page.getData().get(0), event3, history4, history5);
        assertNull(page.getData().get(0).getPayload());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #25
         * Account: DEFAULT_ACCOUNT_ID
         * Request: CAMEL:SLACK and EMAIL endpoints
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, null, null, null, Set.of("camel:SLACK", EMAIL_SUBSCRIPTION.name()), null, null, null, null, null, false, true);
        assertEquals(2, page.getMeta().getCount());
        assertEquals(2, page.getData().size());
        assertSameEvent(page.getData().get(0), event3, history4, history5);
        assertSameEvent(page.getData().get(1), event1, history1, history2);
        assertNull(page.getData().get(0).getPayload());
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #26
         * Account: DEFAULT_ACCOUNT_ID
         * Request: Mixing bundle and an app from a different bundle
         */
        page = getEventLogPage(defaultIdentityHeader, Set.of(bundle1.getId()), Set.of(app2.getId()), null, null, null, null, null, null, 10, 0, null, true, true);
        assertEquals(3, page.getMeta().getCount());
        assertEquals(3, page.getData().size());
        assertSameEvent(page.getData().get(0), event2, history3);
        assertSameEvent(page.getData().get(1), event3, history4, history5);
        assertSameEvent(page.getData().get(2), event1, history1, history2);
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #27
         * Account: DEFAULT_ACCOUNT_ID
         * Request: Using status = SUCCESS
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, null, null, null, null, null, Set.of(NotificationStatus.SUCCESS), 10, 0, null, false, true);
        assertEquals(3, page.getMeta().getCount());
        assertEquals(3, page.getData().size());
        assertSameEvent(page.getData().get(2), event1, history1, history2);
        assertSameEvent(page.getData().get(0), event2, history3);
        assertSameEvent(page.getData().get(1), event3, history4, history5);
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #28
         * Account: DEFAULT_ACCOUNT_ID
         * Request: Using status = FAILED_PROCESSING
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, null, null, null, null, null, Set.of(NotificationStatus.FAILED_PROCESSING), 10, 0, null, false, true);
        assertEquals(1, page.getMeta().getCount());
        assertEquals(1, page.getData().size());
        assertSameEvent(page.getData().get(0), event1, history1, history2);
        assertLinks(page.getLinks(), "first", "last");

        /*
         * Test #29
         * Account: DEFAULT_ACCOUNT_ID
         * Request: Using status = PROCESSING
         */
        page = getEventLogPage(defaultIdentityHeader, null, null, null, null, null, null, null, Set.of(NotificationStatus.PROCESSING), 10, 0, null, false, true);
        assertEquals(0, page.getMeta().getCount());
        assertEquals(0, page.getData().size());
        assertLinks(page.getLinks(), "first", "last");
    }

    @Test
    void testInsufficientPrivileges() {
        Header noAccessIdentityHeader = mockRbac("tenant", DEFAULT_ORG_ID, "noAccess", NO_ACCESS);
        given()
                .header(noAccessIdentityHeader)
                .when().get(PATH)
                .then()
                .statusCode(403);
    }

    @Test
    void testInvalidSortBy() {
        Header identityHeader = mockRbac(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "user", FULL_ACCESS);
        given()
                .header(identityHeader)
                .param("sortBy", "I am not valid!")
                .when().get(PATH)
                .then()
                .statusCode(400)
                .contentType(JSON);
    }

    @Test
    void testInvalidLimit() {
        Header identityHeader = mockRbac(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "user", FULL_ACCESS);
        given()
                .header(identityHeader)
                .param("limit", 0)
                .when().get(PATH)
                .then()
                .statusCode(400)
                .contentType(JSON);
        given()
                .header(identityHeader)
                .param("limit", 999999)
                .when().get(PATH)
                .then()
                .statusCode(400)
                .contentType(JSON);
    }

    @Test
    void shouldBeAllowedToGetEventLogs() {
        Header readAccessIdentityHeader = mockRbac("tenant", DEFAULT_ORG_ID, "user-read-access", NOTIFICATIONS_READ_ACCESS_ONLY);
        given()
                .header(readAccessIdentityHeader)
                .when().get(PATH)
                .then()
                .statusCode(200)
                .contentType(JSON);
    }

    @Transactional
    Event createEvent(String accountId, String orgId, Bundle bundle, Application app, EventType eventType, LocalDateTime created) {
        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setAccountId(accountId);
        event.setOrgId(orgId);
        event.setBundleId(bundle.getId());
        event.setBundleDisplayName(bundle.getDisplayName());
        event.setApplicationId(app.getId());
        event.setApplicationDisplayName(app.getDisplayName());
        event.setEventType(eventType);
        event.setEventTypeDisplayName(eventType.getDisplayName());
        event.setCreated(created);
        event.setPayload(PAYLOAD);
        entityManager.persist(event);
        return event;
    }

    private Header mockRbac(String accountId, String orgId, String username, RbacAccess access) {
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, username);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, access);
        return TestHelpers.createRHIdentityHeader(identityHeaderValue);
    }

    private static Page<EventLogEntry> getEventLogPage(Header identityHeader, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                                                       LocalDateTime startDate, LocalDateTime endDate, Set<String> endpointTypes,
                                                       Set<Boolean> invocationResults, Set<NotificationStatus> status, Integer limit,
                                                       Integer offset, String sortBy, boolean includePayload, boolean includeActions) {
        RequestSpecification request = given()
                .header(identityHeader);
        if (bundleIds != null) {
            request.param("bundleIds", bundleIds);
        }
        if (appIds != null) {
            request.param("appIds", appIds);
        }
        if (eventTypeDisplayName != null) {
            request.param("eventTypeDisplayName", eventTypeDisplayName);
        }
        if (startDate != null) {
            request.param("startDate", startDate.toLocalDate().toString());
        }
        if (endDate != null) {
            request.param("endDate", endDate.toLocalDate().toString());
        }
        if (endpointTypes != null) {
            request.param("endpointTypes", endpointTypes);
        }
        if (invocationResults != null) {
            request.param("invocationResults", invocationResults);
        }
        if (status != null) {
            request.param("status", status);
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
        if (includePayload) {
            request.param("includePayload", true);
        }
        if (includeActions) {
            request.param("includeActions", true);
        }
        return request
                .when().get(PATH)
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().as(new TypeRef<>() {
                });
    }

    private static void assertSameEvent(EventLogEntry eventLogEntry, Event event, NotificationHistory... historyEntries) {
        assertEquals(event.getId(), eventLogEntry.getId());
        // Jackson's serialization gets rid of nanoseconds so an equals between the LocalDateTime objects won't work.
        assertEquals(event.getCreated().toEpochSecond(UTC), eventLogEntry.getCreated().toEpochSecond(UTC));
        assertEquals(event.getBundleDisplayName(), eventLogEntry.getBundle());
        assertEquals(event.getApplicationDisplayName(), eventLogEntry.getApplication());
        assertEquals(event.getEventTypeDisplayName(), eventLogEntry.getEventType());
        if (historyEntries == null) {
            assertTrue(eventLogEntry.getActions().isEmpty());
        } else {
            assertEquals(historyEntries.length, eventLogEntry.getActions().size());
            for (EventLogEntryAction eventLogEntryAction : eventLogEntry.getActions()) {
                Optional<NotificationHistory> historyEntry = Arrays.stream(historyEntries)
                        .filter(entry -> entry.getId().equals(eventLogEntryAction.getId())).findAny();
                assertTrue(historyEntry.isPresent());
                assertEquals(historyEntry.get().getEndpointType(), eventLogEntryAction.getEndpointType());
                assertEquals(historyEntry.get().getEndpointSubType(), eventLogEntryAction.getEndpointSubType());
                assertEquals(historyEntry.get().isInvocationResult(), eventLogEntryAction.getInvocationResult());
                assertEquals(historyEntry.get().getStatus(), eventLogEntryAction.getStatus());
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
