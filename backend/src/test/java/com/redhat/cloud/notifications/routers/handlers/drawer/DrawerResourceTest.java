package com.redhat.cloud.notifications.routers.handlers.drawer;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.DrawerEntryPayload;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.routers.models.Page;
import com.redhat.cloud.notifications.routers.models.UpdateNotificationDrawerStatus;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.Header;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_NOTIFICATIONS_V_1_0;
import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess;
import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.FULL_ACCESS;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class DrawerResourceTest extends DbIsolatedTest {

    private static final String PAYLOAD = "payload";
    private static final String PATH = API_NOTIFICATIONS_V_1_0 + "/notifications/drawer";

    @Inject
    EntityManager entityManager;

    @Inject
    ResourceHelpers resourceHelpers;

    @InjectMock
    BackendConfig backendConfig;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testMultiplePages(boolean useNormalizedQueries) {
        when(backendConfig.isDrawerEnabled(anyString())).thenReturn(true);
        when(backendConfig.isNormalizedQueriesEnabled(anyString())).thenReturn(useNormalizedQueries);
        final String USERNAME = "user-1";
        Header defaultIdentityHeader = mockRbac(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, USERNAME, FULL_ACCESS);

        Bundle bundle1 = resourceHelpers.createBundle("bundle-1");
        Application app1 = resourceHelpers.createApplication(bundle1.getId(), "app-1");
        EventType eventType1 = resourceHelpers.createEventType(app1.getId(), "event-type-1");

        // Mark EventType as drawer-enabled once
        markEventTypeAsDrawer(eventType1);

        // Create 30 events (no need to call createDrawerNotification per event - same EventType)
        for (int i = 0; i < 30; i++) {
            createEvent(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, bundle1, app1, eventType1, LocalDateTime.now(UTC), Severity.NONE);
        }

        // check default limit
        Page<DrawerEntryPayload> page = getDrawerEntries(defaultIdentityHeader, null, null, null, null, null, null, null, null, null);
        assertEquals(30, page.getMeta().getCount());
        assertEquals(20, page.getData().size());
        assertLinks(page.getLinks(), "first", "next", "last");

        // check forced limit
        page = getDrawerEntries(defaultIdentityHeader, null, null, null, null, null, null, 6, null, null);
        assertEquals(30, page.getMeta().getCount());
        assertEquals(6, page.getData().size());
        assertLinks(page.getLinks(), "first", "next", "last");

        // check offset
        page = getDrawerEntries(defaultIdentityHeader, null, null, null, null, null, null, 7, 28, null);
        assertEquals(30, page.getMeta().getCount());
        assertEquals(2, page.getData().size());

        // check offset on links
        page = getDrawerEntries(defaultIdentityHeader, null, null, null, null, null, null, 3, 1, null);
        assertEquals(30, page.getMeta().getCount());
        assertEquals(3, page.getData().size());
        assertLinks(page.getLinks(), "first", "prev", "next", "last");
        assertTrue(page.getLinks().get("prev").contains("limit=3&offset=0"));
        assertTrue(page.getLinks().get("last").contains("limit=3&offset=27"));
    }

    /**
     * Tests read status user isolation - each user can mark events as read independently.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testReadStatusUserIsolation(boolean useNormalizedQueries) {
        when(backendConfig.isDrawerEnabled(anyString())).thenReturn(true);
        when(backendConfig.isNormalizedQueriesEnabled(anyString())).thenReturn(useNormalizedQueries);

        final String USERNAME = "user-1";
        final String USERNAME2 = "user-2";

        Bundle bundle = resourceHelpers.createBundle("test-read-status-bundle");
        Application app = resourceHelpers.createApplication(bundle.getId(), "test-read-status-app");
        EventType eventType = resourceHelpers.createEventType(app.getId(), "test-read-status-event-type");

        // Mark EventType as drawer-enabled once
        markEventTypeAsDrawer(eventType);

        // Create 3 events (both users see all events due to subscribe-by-default)
        Event event1 = createEvent(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, bundle, app, eventType, LocalDateTime.now(UTC), Severity.LOW);
        Event event2 = createEvent(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, bundle, app, eventType, LocalDateTime.now(UTC), Severity.MODERATE);
        Event event3 = createEvent(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, bundle, app, eventType, LocalDateTime.now(UTC), Severity.IMPORTANT);

        Header headerUser1 = mockRbac(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, USERNAME, FULL_ACCESS);
        Header headerUser2 = mockRbac(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, USERNAME2, FULL_ACCESS);

        // Initially, both users have 0 read events
        Page<DrawerEntryPayload> page = getDrawerEntries(headerUser1, null, null, null, null, null, true, null, null, null);
        assertEquals(0, page.getData().size(), "User1 should have 0 read events initially");

        page = getDrawerEntries(headerUser2, null, null, null, null, null, true, null, null, null);
        assertEquals(0, page.getData().size(), "User2 should have 0 read events initially");

        // Both users have 3 unread events
        page = getDrawerEntries(headerUser1, null, null, null, null, null, false, null, null, null);
        assertEquals(3, page.getData().size(), "User1 should have 3 unread events");

        page = getDrawerEntries(headerUser2, null, null, null, null, null, false, null, null, null);
        assertEquals(3, page.getData().size(), "User2 should have 3 unread events");

        // User1 marks event1 as read
        Integer nbUpdates = updateDrawerEntriesReadStatus(headerUser1, Set.of(event1.getId()), true);
        assertEquals(1, nbUpdates);

        // User2 marks event2 as read
        nbUpdates = updateDrawerEntriesReadStatus(headerUser2, Set.of(event2.getId()), true);
        assertEquals(1, nbUpdates);

        // User1 should see 1 read event (event1), User2 should not see User1's read status
        page = getDrawerEntries(headerUser1, null, null, null, null, null, true, null, null, null);
        assertEquals(1, page.getData().size(), "User1 should see 1 read event");
        assertEquals(event1.getId(), page.getData().get(0).getEventId(), "User1's read event should be event1");

        // User2 should see 1 read event (event2), User1's read status is isolated
        page = getDrawerEntries(headerUser2, null, null, null, null, null, true, null, null, null);
        assertEquals(1, page.getData().size(), "User2 should see 1 read event");
        assertEquals(event2.getId(), page.getData().get(0).getEventId(), "User2's read event should be event2");

        // Verify unread counts are correct
        page = getDrawerEntries(headerUser1, null, null, null, null, null, false, null, null, null);
        assertEquals(2, page.getData().size(), "User1 should have 2 unread events after marking 1 as read");

        page = getDrawerEntries(headerUser2, null, null, null, null, null, false, null, null, null);
        assertEquals(2, page.getData().size(), "User2 should have 2 unread events after marking 1 as read");
    }

    /**
     * Tests org isolation and drawer feature flag per-org.
     * Ensures each org only sees their own events, and respects drawer enabled/disabled config.
     */
    @Test
    void testDrawerNotificationsOrgSpecific() {
        final String ORG_WITH_DRAWER_ENABLED = "org-with-drawer-enabled";
        final String ORG_WITH_DRAWER_DISABLED = "org-with-drawer-disabled";
        final String ANOTHER_ORG_WITH_DRAWER_ENABLED = "another-org-with-drawer-enabled";
        final String USERNAME = "user-test";

        when(backendConfig.isDrawerEnabled(eq(ORG_WITH_DRAWER_ENABLED))).thenReturn(true);
        when(backendConfig.isDrawerEnabled(eq(ORG_WITH_DRAWER_DISABLED))).thenReturn(false);
        when(backendConfig.isDrawerEnabled(eq(ANOTHER_ORG_WITH_DRAWER_ENABLED))).thenReturn(true);

        Bundle bundle = resourceHelpers.createBundle("bundle-org-test");
        Application app = resourceHelpers.createApplication(bundle.getId(), "app-org-test");
        EventType eventType = resourceHelpers.createEventType(app.getId(), "event-type-org-test");

        // Mark EventType as drawer-enabled once (same for all orgs)
        markEventTypeAsDrawer(eventType);

        // Create events for all three orgs with the SAME username
        // This tests data isolation - ensuring orgs can't see each other's data
        Event eventOrg1 = createEvent("account-1", ORG_WITH_DRAWER_ENABLED, bundle, app, eventType,
            LocalDateTime.now(UTC), Severity.LOW);
        Event eventOrg2 = createEvent("account-2", ORG_WITH_DRAWER_DISABLED, bundle, app, eventType,
            LocalDateTime.now(UTC), Severity.MODERATE);
        Event eventOrg3 = createEvent("account-3", ANOTHER_ORG_WITH_DRAWER_ENABLED, bundle, app, eventType,
            LocalDateTime.now(UTC), Severity.IMPORTANT);

        // Test Org 1: should see only their own notification (Severity.LOW), not Org 3's data
        Header headerOrg1 = mockRbac("account-1", ORG_WITH_DRAWER_ENABLED, USERNAME, FULL_ACCESS);
        Page<DrawerEntryPayload> pageOrg1 = getDrawerEntries(headerOrg1, null, null, null,
            null, null, null, null, null, null);
        assertEquals(1, pageOrg1.getMeta().getCount(),
            "Org 1 should see exactly 1 notification (their own), not cross-org data");
        assertEquals("LOW", pageOrg1.getData().get(0).getSeverity(),
            "Org 1 should see their own event with Severity.LOW");

        // Test Org 2: drawer disabled, should see 0 entries
        Header headerOrg2 = mockRbac("account-2", ORG_WITH_DRAWER_DISABLED, USERNAME, FULL_ACCESS);
        Page<DrawerEntryPayload> pageOrg2 = getDrawerEntries(headerOrg2, null, null, null,
            null, null, null, null, null, null);
        assertEquals(0, pageOrg2.getMeta().getCount(),
            "Org 2 with drawer disabled should see 0 notifications");

        // Test Org 3: should see only their own notification (Severity.IMPORTANT), proving data isolation
        Header headerOrg3 = mockRbac("account-3", ANOTHER_ORG_WITH_DRAWER_ENABLED, USERNAME, FULL_ACCESS);
        Page<DrawerEntryPayload> pageOrg3 = getDrawerEntries(headerOrg3, null, null, null,
            null, null, null, null, null, null);
        assertEquals(1, pageOrg3.getMeta().getCount(),
            "Org 3 should see exactly 1 notification (their own), proving org isolation");
        assertEquals("IMPORTANT", pageOrg3.getData().get(0).getSeverity(),
            "Org 3 should see their own event with Severity.IMPORTANT, not Org 1's data");
    }

    /**
     * Marks an EventType as drawer-enabled (includedInDrawer = true).
     */
    @Transactional
    void markEventTypeAsDrawer(EventType eventType) {
        // Use native query to avoid validation issues with detached entities
        entityManager.createNativeQuery(
                "UPDATE event_type SET included_in_drawer = true WHERE id = :eventTypeId AND included_in_drawer = false"
            )
            .setParameter("eventTypeId", eventType.getId())
            .executeUpdate();
    }

    @Transactional
    Event createEvent(String accountId, String orgId, Bundle bundle, Application app, EventType eventType, LocalDateTime created, Severity severity) {
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
        event.setSeverity(severity);
        event.setPayload(PAYLOAD);
        event.setRenderedDrawerNotification("Rendered notification for " + eventType.getDisplayName());
        entityManager.persist(event);
        return event;
    }

    private Header mockRbac(String accountId, String orgId, String username, RbacAccess access) {
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, username);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, access);
        return TestHelpers.createRHIdentityHeader(identityHeaderValue);
    }

    private static Page<DrawerEntryPayload> getDrawerEntries(Header identityHeader, Set<UUID> bundleIds, Set<UUID> appIds, Set<UUID> eventTypeIds,
                                                      LocalDateTime startDate, LocalDateTime endDate,
                                                      Boolean readStatus, Integer limit,
                                                      Integer offset, String sortBy) {
        RequestSpecification request = given()
                .header(identityHeader);
        if (bundleIds != null) {
            request.param("bundleIds", bundleIds);
        }
        if (appIds != null) {
            request.param("appIds", appIds);
        }
        if (eventTypeIds != null) {
            request.param("eventTypeIds", eventTypeIds);
        }
        if (startDate != null) {
            request.param("startDate", startDate.toString());
        }
        if (endDate != null) {
            request.param("endDate", endDate.toString());
        }
        if (readStatus != null) {
            request.param("readStatus", readStatus);
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
                .when().get(PATH)
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().as(new TypeRef<>() {
                });
    }

    private static Integer updateDrawerEntriesReadStatus(Header identityHeader, Set<UUID> drawerEntries, Boolean readStatus) {
        RequestSpecification request = given()
            .header(identityHeader);
        UpdateNotificationDrawerStatus updateNotificationDrawerStatus = new UpdateNotificationDrawerStatus();
        updateNotificationDrawerStatus.setNotificationIds(drawerEntries);
        updateNotificationDrawerStatus.setReadStatus(readStatus);

        return request
            .contentType(JSON)
            .body(Json.encode(updateNotificationDrawerStatus))
            .when().put(PATH + "/read")
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(new TypeRef<>() {
            });
    }

    private static void assertLinks(Map<String, String> links, String... expectedKeys) {
        assertEquals(expectedKeys.length, links.size());
        for (String key : expectedKeys) {
            assertTrue(links.containsKey(key));
        }
    }
}
