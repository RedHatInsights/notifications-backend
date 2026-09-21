package com.redhat.cloud.notifications.routers.handlers.event;

import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.NotificationStatus;
import com.redhat.cloud.notifications.routers.models.EventLogEntry;
import com.redhat.cloud.notifications.routers.models.Page;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.Header;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.FULL_ACCESS;
import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.NOTIFICATIONS_ACCESS_ONLY;
import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.NO_ACCESS;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_USER;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EventResourceV3Test extends DbIsolatedTest {

    private static final LocalDateTime NOW = LocalDateTime.now(UTC).truncatedTo(ChronoUnit.DAYS).plusHours(12L);
    private static final String PATH = "/notifications/events";

    @InjectSpy
    BackendConfig backendConfig;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EntityManager entityManager;

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_NOTIFICATIONS_V_3_0;
        MockServerConfig.clearRbac();
        Mockito.when(this.backendConfig.isRBACEnabled()).thenReturn(true);
    }

    private Header initRbacMock(String accountId, String orgId, String username, MockServerConfig.RbacAccess access) {
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, username);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, access);
        return TestHelpers.createRHIdentityHeader(identityHeaderValue);
    }

    @Test
    void testGetEventsReturnsPagedResults() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        Bundle bundle = resourceHelpers.createBundle("v3-bundle", "V3 Bundle");
        Application app = resourceHelpers.createApplication(bundle.getId(), "v3-app", "V3 Application");
        EventType eventType = resourceHelpers.createEventType(app.getId(), "v3-event-type", "V3 Event Type", "V3 Event Type");

        Event event1 = createEvent(bundle, app, eventType, NOW.minusDays(1L));
        Event event2 = createEvent(bundle, app, eventType, NOW);

        Endpoint endpoint = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, WEBHOOK);
        resourceHelpers.createNotificationHistory(event1, endpoint, NotificationStatus.SUCCESS);
        resourceHelpers.createNotificationHistory(event2, endpoint, NotificationStatus.SUCCESS);

        Page<EventLogEntry> page = given()
            .header(identityHeader)
            .param("includeActions", true)
            .when().get(PATH)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().body().as(new TypeRef<>() { });

        assertEquals(2, page.getMeta().getCount());
        assertEquals(2, page.getData().size());

        EventLogEntry latest = page.getData().get(0);
        assertEquals(event2.getId(), latest.getId());
        assertNotNull(latest.getBundle());
        assertNotNull(latest.getApplication());
        assertNotNull(latest.getEventType());
        assertEquals(1, latest.getActions().size());
    }

    @Test
    void testGetEventsWithFilters() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        Bundle bundle1 = resourceHelpers.createBundle("v3-filter-b1", "V3 Filter B1");
        Bundle bundle2 = resourceHelpers.createBundle("v3-filter-b2", "V3 Filter B2");
        Application app1 = resourceHelpers.createApplication(bundle1.getId(), "v3-filter-a1", "V3 Filter A1");
        Application app2 = resourceHelpers.createApplication(bundle2.getId(), "v3-filter-a2", "V3 Filter A2");
        EventType et1 = resourceHelpers.createEventType(app1.getId(), "v3-filter-et1", "V3 Filter ET1", "V3 Filter ET1");
        EventType et2 = resourceHelpers.createEventType(app2.getId(), "v3-filter-et2", "V3 Filter ET2", "V3 Filter ET2");

        createEvent(bundle1, app1, et1, NOW.minusDays(1L));
        createEvent(bundle2, app2, et2, NOW);

        Page<EventLogEntry> page = given()
            .header(identityHeader)
            .param("bundleIds", bundle1.getId())
            .when().get(PATH)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().body().as(new TypeRef<>() { });

        assertEquals(1, page.getMeta().getCount());
        assertEquals(bundle1.getDisplayName(), page.getData().get(0).getBundle());
    }

    @Test
    void testGetEventsWithPagination() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        Bundle bundle = resourceHelpers.createBundle("v3-page-bundle", "V3 Page Bundle");
        Application app = resourceHelpers.createApplication(bundle.getId(), "v3-page-app", "V3 Page App");
        EventType et = resourceHelpers.createEventType(app.getId(), "v3-page-et", "V3 Page ET", "V3 Page ET");

        createEvent(bundle, app, et, NOW.minusDays(2L));
        createEvent(bundle, app, et, NOW.minusDays(1L));
        createEvent(bundle, app, et, NOW);

        Page<EventLogEntry> page = given()
            .header(identityHeader)
            .param("limit", 2)
            .param("offset", 0)
            .when().get(PATH)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().body().as(new TypeRef<>() { });

        assertEquals(3, page.getMeta().getCount());
        assertEquals(2, page.getData().size());
        assertTrue(page.getLinks().containsKey("next"));
    }

    @Test
    void testGetEventsExcludesPayloadByDefault() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        Bundle bundle = resourceHelpers.createBundle("v3-payload-bundle", "V3 Payload Bundle");
        Application app = resourceHelpers.createApplication(bundle.getId(), "v3-payload-app", "V3 Payload App");
        EventType et = resourceHelpers.createEventType(app.getId(), "v3-payload-et", "V3 Payload ET", "V3 Payload ET");

        createEvent(bundle, app, et, NOW);

        Page<EventLogEntry> page = given()
            .header(identityHeader)
            .when().get(PATH)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().body().as(new TypeRef<>() { });

        assertEquals(1, page.getMeta().getCount());
        assertNull(page.getData().get(0).getPayload());
    }

    @Test
    void testGetEventsWithAppIdFilter() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        Bundle bundle = resourceHelpers.createBundle("v3-appfilter-bundle", "V3 AppFilter Bundle");
        Application app1 = resourceHelpers.createApplication(bundle.getId(), "v3-appfilter-a1", "V3 AppFilter A1");
        Application app2 = resourceHelpers.createApplication(bundle.getId(), "v3-appfilter-a2", "V3 AppFilter A2");
        EventType et1 = resourceHelpers.createEventType(app1.getId(), "v3-appfilter-et1", "V3 AppFilter ET1", "V3 AppFilter ET1");
        EventType et2 = resourceHelpers.createEventType(app2.getId(), "v3-appfilter-et2", "V3 AppFilter ET2", "V3 AppFilter ET2");

        createEvent(bundle, app1, et1, NOW.minusDays(1L));
        createEvent(bundle, app2, et2, NOW);

        Page<EventLogEntry> page = given()
            .header(identityHeader)
            .param("appIds", app1.getId())
            .when().get(PATH)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().body().as(new TypeRef<>() { });

        assertEquals(1, page.getMeta().getCount());
        assertEquals(app1.getDisplayName(), page.getData().get(0).getApplication());
    }

    @Test
    void testGetEventsWithIncludeDetails() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        Bundle bundle = resourceHelpers.createBundle("v3-details-bundle", "V3 Details Bundle");
        Application app = resourceHelpers.createApplication(bundle.getId(), "v3-details-app", "V3 Details App");
        EventType et = resourceHelpers.createEventType(app.getId(), "v3-details-et", "V3 Details ET", "V3 Details ET");

        Event event = createEvent(bundle, app, et, NOW);
        Endpoint endpoint = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, WEBHOOK);
        resourceHelpers.createNotificationHistory(event, endpoint, NotificationStatus.SUCCESS);

        Page<EventLogEntry> page = given()
            .header(identityHeader)
            .param("includeActions", true)
            .param("includeDetails", true)
            .when().get(PATH)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().body().as(new TypeRef<>() { });

        assertEquals(1, page.getMeta().getCount());
        assertEquals(1, page.getData().get(0).getActions().size());
    }

    @Test
    void testGetEventsInsufficientPrivileges() {
        Header noAccessHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER + "no-access", NO_ACCESS);

        given()
            .header(noAccessHeader)
            .when().get(PATH)
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);
    }

    @Test
    void testGetEventsNotificationsAccessOnlyForbidden() {
        Header header = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER + "notif-only", NOTIFICATIONS_ACCESS_ONLY);

        given()
            .header(header)
            .when().get(PATH)
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);
    }

    @Test
    void testGetEventsInvalidSortBy() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        given()
            .header(identityHeader)
            .param("sortBy", "invalid_field")
            .when().get(PATH)
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void testGetEventsInvalidLimit() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        given()
            .header(identityHeader)
            .param("limit", 0)
            .when().get(PATH)
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);

        given()
            .header(identityHeader)
            .param("limit", 999999)
            .when().get(PATH)
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void testGetEventsWithDateRange() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        Bundle bundle = resourceHelpers.createBundle("v3-date-bundle", "V3 Date Bundle");
        Application app = resourceHelpers.createApplication(bundle.getId(), "v3-date-app", "V3 Date App");
        EventType et = resourceHelpers.createEventType(app.getId(), "v3-date-et", "V3 Date ET", "V3 Date ET");

        createEvent(bundle, app, et, NOW.minusDays(5L));
        Event recentEvent = createEvent(bundle, app, et, NOW.minusDays(1L));

        Page<EventLogEntry> page = given()
            .header(identityHeader)
            .param("startDateTime", NOW.minusDays(2L).toString())
            .when().get(PATH)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().body().as(new TypeRef<>() { });

        assertEquals(1, page.getMeta().getCount());
        assertEquals(recentEvent.getId(), page.getData().get(0).getId());
    }

    @Test
    void testGetEventsOrgIsolation() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);
        Header otherHeader = initRbacMock("other-acct", "other-org", "other-user", FULL_ACCESS);

        Bundle bundle = resourceHelpers.createBundle("v3-org-bundle", "V3 Org Bundle");
        Application app = resourceHelpers.createApplication(bundle.getId(), "v3-org-app", "V3 Org App");
        EventType et = resourceHelpers.createEventType(app.getId(), "v3-org-et", "V3 Org ET", "V3 Org ET");

        createEvent(bundle, app, et, NOW);
        createEvent("other-acct", "other-org", bundle, app, et, NOW.minusHours(1L));

        Page<EventLogEntry> page1 = given()
            .header(identityHeader)
            .when().get(PATH)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().body().as(new TypeRef<>() { });

        Page<EventLogEntry> page2 = given()
            .header(otherHeader)
            .when().get(PATH)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().body().as(new TypeRef<>() { });

        assertEquals(1, page1.getMeta().getCount());
        assertEquals(1, page2.getMeta().getCount());
    }

    @Test
    void testGetEventsEmptyResults() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        Page<EventLogEntry> page = given()
            .header(identityHeader)
            .param("bundleIds", java.util.UUID.randomUUID())
            .when().get(PATH)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().body().as(new TypeRef<>() { });

        assertEquals(0, page.getMeta().getCount());
        assertTrue(page.getData().isEmpty());
    }

    @Test
    void testGetEventsWithEndDate() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        Bundle bundle = resourceHelpers.createBundle("v3-enddate-bundle", "V3 EndDate Bundle");
        Application app = resourceHelpers.createApplication(bundle.getId(), "v3-enddate-app", "V3 EndDate App");
        EventType eventType = resourceHelpers.createEventType(app.getId(), "v3-enddate-et", "V3 EndDate ET", "V3 EndDate ET");

        Event oldEvent = createEvent(bundle, app, eventType, NOW.minusDays(5L));
        createEvent(bundle, app, eventType, NOW);

        Page<EventLogEntry> page = given()
            .header(identityHeader)
            .param("endDateTime", NOW.minusDays(3L).toString())
            .when().get(PATH)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().body().as(new TypeRef<>() { });

        assertEquals(1, page.getMeta().getCount());
        assertEquals(oldEvent.getId(), page.getData().get(0).getId());
    }

    @Test
    void testGetEventsIncludesPayloadWhenRequested() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        Bundle bundle = resourceHelpers.createBundle("v3-incpayload-bundle", "V3 IncPayload Bundle");
        Application app = resourceHelpers.createApplication(bundle.getId(), "v3-incpayload-app", "V3 IncPayload App");
        EventType et = resourceHelpers.createEventType(app.getId(), "v3-incpayload-et", "V3 IncPayload ET", "V3 IncPayload ET");

        createEvent(bundle, app, et, NOW);

        Page<EventLogEntry> page = given()
            .header(identityHeader)
            .param("includePayload", true)
            .when().get(PATH)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().body().as(new TypeRef<>() { });

        assertEquals(1, page.getMeta().getCount());
        assertNotNull(page.getData().get(0).getPayload());
        assertEquals("test-payload", page.getData().get(0).getPayload());
    }

    @Test
    void testGetEventsExcludesActionsWhenNotRequested() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        Bundle bundle = resourceHelpers.createBundle("v3-noactions-bundle", "V3 NoActions Bundle");
        Application app = resourceHelpers.createApplication(bundle.getId(), "v3-noactions-app", "V3 NoActions App");
        EventType et = resourceHelpers.createEventType(app.getId(), "v3-noactions-et", "V3 NoActions ET", "V3 NoActions ET");

        Event event = createEvent(bundle, app, et, NOW);
        Endpoint endpoint = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, WEBHOOK);
        resourceHelpers.createNotificationHistory(event, endpoint, NotificationStatus.SUCCESS);

        Page<EventLogEntry> page = given()
            .header(identityHeader)
            .param("includeActions", false)
            .when().get(PATH)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().body().as(new TypeRef<>() { });

        assertEquals(1, page.getMeta().getCount());
        assertTrue(page.getData().get(0).getActions().isEmpty());
    }

    @Test
    void testGetEventsWithNullHistoryEntries() {
        Header identityHeader = initRbacMock(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER, FULL_ACCESS);

        Bundle bundle = resourceHelpers.createBundle("v3-nullhist-bundle", "V3 NullHist Bundle");
        Application app = resourceHelpers.createApplication(bundle.getId(), "v3-nullhist-app", "V3 NullHist App");
        EventType et = resourceHelpers.createEventType(app.getId(), "v3-nullhist-et", "V3 NullHist ET", "V3 NullHist ET");

        createEvent(bundle, app, et, NOW);

        Page<EventLogEntry> page = given()
            .header(identityHeader)
            .param("includeActions", true)
            .when().get(PATH)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().body().as(new TypeRef<>() { });

        assertEquals(1, page.getMeta().getCount());
        assertNotNull(page.getData().get(0).getActions());
        assertTrue(page.getData().get(0).getActions().isEmpty());
    }

    @Transactional
    Event createEvent(Bundle bundle, Application app, EventType eventType, LocalDateTime created) {
        return createEvent(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, bundle, app, eventType, created);
    }

    @Transactional
    Event createEvent(String accountId, String orgId, Bundle bundle, Application app, EventType eventType, LocalDateTime created) {
        Event event = new Event();
        event.setAccountId(accountId);
        event.setOrgId(orgId);
        event.setBundleId(bundle.getId());
        event.setBundleDisplayName(bundle.getDisplayName());
        event.setApplicationId(app.getId());
        event.setApplicationDisplayName(app.getDisplayName());
        event.setEventType(eventType);
        event.setEventTypeDisplayName(eventType.getDisplayName());
        event.setCreated(created);
        event.setPayload("test-payload");
        entityManager.persist(event);
        return entityManager.merge(event);
    }
}
