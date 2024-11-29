package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.DrawerEntryPayload;
import com.redhat.cloud.notifications.models.DrawerNotification;
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

    @Test
    void testMultiplePages() {
        when(backendConfig.isDrawerEnabled()).thenReturn(true);
        final String USERNAME = "user-1";
        Header defaultIdentityHeader = mockRbac(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, USERNAME, FULL_ACCESS);

        Bundle bundle1 = resourceHelpers.createBundle("bundle-1");
        Application app1 = resourceHelpers.createApplication(bundle1.getId(), "app-1");
        EventType eventType1 = resourceHelpers.createEventType(app1.getId(), "event-type-1");

        for (int i = 0; i < 30; i++) {
            createDrawerNotification(USERNAME, createEvent(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, bundle1, app1, eventType1, LocalDateTime.now(UTC)));
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

    @Test
    void testFilters() {
        when(backendConfig.isDrawerEnabled()).thenReturn(true);
        Bundle createdBundle = resourceHelpers.createBundle("test-drawer-event-resource-bundle");
        Bundle createdBundle2 = resourceHelpers.createBundle("test-drawer-event-resource-bundle2");
        Application createdApplication = resourceHelpers.createApplication(createdBundle.getId(), "test-drawer-event-resource-application");
        Application createdApplication2 = resourceHelpers.createApplication(createdBundle2.getId(), "test-drawer-event-resource-application");
        EventType createdEventType = resourceHelpers.createEventType(createdApplication.getId(), "test-drawer-event-resource-event-type");
        EventType createdEventType2 = resourceHelpers.createEventType(createdApplication.getId(), "test-drawer-event-resource-event-type2");
        EventType createdEventType3 = resourceHelpers.createEventType(createdApplication2.getId(), "test-drawer-event-resource-event-type");
        Event createdEvent = createEvent(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, createdBundle, createdApplication, createdEventType, LocalDateTime.now(UTC));

        final String USERNAME = "user-1";
        final String USERNAME2 = "user-2";
        createDrawerNotification(USERNAME, createdEvent);
        createDrawerNotification(USERNAME, createEvent(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, createdBundle, createdApplication, createdEventType2, LocalDateTime.now(UTC)));
        createDrawerNotification(USERNAME, createEvent(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, createdBundle2, createdApplication2, createdEventType3, LocalDateTime.now(UTC)));
        createDrawerNotification(USERNAME2, createdEvent);
        Event eventInThePast = createEvent(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, createdBundle2, createdApplication2, createdEventType3, LocalDateTime.now(UTC).minusDays(5));
        createDrawerNotification(USERNAME2, eventInThePast);

        Event secondEventInThePast = createEvent(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, createdBundle2, createdApplication2, createdEventType3, LocalDateTime.now(UTC).minusDays(2));
        createDrawerNotification(USERNAME2, secondEventInThePast);

        Header defaultIdentityHeader = mockRbac(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, USERNAME, FULL_ACCESS);
        Header identityHeaderUser2 = mockRbac(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, USERNAME2, FULL_ACCESS);

        // should return 3 results
        Page<DrawerEntryPayload> page = getDrawerEntries(defaultIdentityHeader, null, null, null, null, null, null, null, null, null);
        assertEquals(3, page.getData().size());

        // should return 3 results
        page = getDrawerEntries(defaultIdentityHeader, Set.of(createdBundle2.getId(), createdBundle.getId()), null, null, null, null, null, null, null, null);
        assertEquals(3, page.getData().size());

        // should return 1 result
        page = getDrawerEntries(defaultIdentityHeader, Set.of(createdBundle2.getId()), null, null, null, null, null, null, null, null);
        assertEquals(1, page.getData().size());

        // should return 3 results, bundle parameter should be ignored
        page = getDrawerEntries(defaultIdentityHeader, Set.of(createdBundle2.getId()), Set.of(createdApplication.getId(), createdApplication2.getId()), null, null, null, null, null, null, null);
        assertEquals(3, page.getData().size());

        // should return 1 result
        page = getDrawerEntries(defaultIdentityHeader, null, Set.of(createdApplication2.getId()), null, null, null, null, null, null, null);
        assertEquals(1, page.getData().size());

        // should return 3 results
        page = getDrawerEntries(defaultIdentityHeader, null, null, Set.of(createdEventType.getId(), createdEventType2.getId(), createdEventType3.getId()), null, null, null, null, null, null);
        assertEquals(3, page.getData().size());

        // should return 1 result
        page = getDrawerEntries(defaultIdentityHeader, null, null, Set.of(createdEventType3.getId()), null, null, null, null, null, null);
        assertEquals(1, page.getData().size());

        // should return 1 result, application parameter should be ignored
        page = getDrawerEntries(defaultIdentityHeader, null, Set.of(createdApplication.getId()), Set.of(createdEventType3.getId()), null, null, null, null, null, null);
        assertEquals(1, page.getData().size());

        // should return 3 results
        page = getDrawerEntries(identityHeaderUser2, null, null, null, null, LocalDateTime.now(UTC), null, null, null, null);
        assertEquals(3, page.getData().size());

        // should return 2 results
        page = getDrawerEntries(identityHeaderUser2, null, null, null, null, LocalDateTime.now(UTC).minusDays(1), null, null, null, null);
        assertEquals(2, page.getData().size());

        // should return 1 result
        page = getDrawerEntries(identityHeaderUser2, null, null, null, LocalDateTime.now(UTC).minusHours(1), null, null, null, null, null);
        assertEquals(1, page.getData().size());

        // should return 1 result
        page = getDrawerEntries(identityHeaderUser2, null, null, null, LocalDateTime.now(UTC).minusDays(1), null, null, null, null, null);
        assertEquals(1, page.getData().size());

        // should return 1 result
        page = getDrawerEntries(identityHeaderUser2, null, null, null, LocalDateTime.now(UTC).minusDays(3), LocalDateTime.now(UTC).minusDays(1), null, null, null, null);
        assertEquals(1, page.getData().size());

        // should return 0 result
        page = getDrawerEntries(defaultIdentityHeader, null, null, null, null, null, true, null, null, null);
        assertEquals(0, page.getData().size());

        // should return 3 results
        page = getDrawerEntries(defaultIdentityHeader, null, null, null, null, null, false, null, null, null);
        assertEquals(3, page.getData().size());

        // only notification recipient can update read status
        Integer nbUpdates = updateDrawerEntriesReadStatus(identityHeaderUser2, Set.of(page.getData().get(0).getEventId()), true);
        assertEquals(0, nbUpdates);

        nbUpdates = updateDrawerEntriesReadStatus(defaultIdentityHeader, Set.of(page.getData().get(0).getEventId()), true);
        assertEquals(1, nbUpdates);

        // should return 1 result
        page = getDrawerEntries(defaultIdentityHeader, null, null, null, null, null, true, null, null, null);
        assertEquals(1, page.getData().size());

        // User-2 can't access to this read notification
        page = getDrawerEntries(identityHeaderUser2, null, null, null, null, null, true, null, null, null);
        assertEquals(0, page.getData().size());
    }

    @Transactional
    void createDrawerNotification(String userId, Event createdEvent) {
        DrawerNotification notificationDrawer = new DrawerNotification(DEFAULT_ORG_ID, userId, entityManager.find(Event.class, createdEvent.getId()));
        notificationDrawer.setCreated(createdEvent.getCreated());
        entityManager.persist(notificationDrawer);
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
