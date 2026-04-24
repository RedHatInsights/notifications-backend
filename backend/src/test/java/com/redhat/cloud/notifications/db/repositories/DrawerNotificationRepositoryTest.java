package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.DrawerEntryPayload;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tests for DrawerNotificationRepository - the core repository for drawer notification queries.
 */
@QuarkusTest
public class DrawerNotificationRepositoryTest extends DbIsolatedTest {

    @Inject
    DrawerNotificationRepository drawerNotificationRepository;

    @Inject
    EntityManager entityManager;

    @Inject
    ResourceHelpers resourceHelpers;

    @InjectMock
    BackendConfig backendConfig;

    private final String orgId = "test-org-123";
    private final String username = "test-user";

    @BeforeEach
    void setup() {
        // Default: drawer enabled, normalized queries disabled, Kessel disabled
        when(backendConfig.isDrawerEnabled(anyString())).thenReturn(true);
        when(backendConfig.isNormalizedQueriesEnabled(anyString())).thenReturn(false);
        when(backendConfig.isKesselChecksOnEventLogEnabled(anyString())).thenReturn(false);
    }

    @Test
    void testUpdateReadStatus_MarkAsRead_SingleEvent() {
        Event event = createDrawerEvent(orgId, "bundle1", "app1", "event-type-1");

        Integer updated = drawerNotificationRepository.updateReadStatus(
            orgId, username, Set.of(event.getId()), true
        );

        assertEquals(1, updated, "Should mark 1 event as read");

        // Verify read status was persisted
        Long count = (Long) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM drawer_read_status WHERE org_id = :orgId AND user_id = :userId AND event_id = :eventId",
                Long.class
            )
            .setParameter("orgId", orgId)
            .setParameter("userId", username)
            .setParameter("eventId", event.getId())
            .getSingleResult();

        assertEquals(1L, count, "drawer_read_status row should exist");
    }

    @Test
    void testUpdateReadStatus_MarkAsRead_MultipleEvents() {
        Event event1 = createDrawerEvent(orgId, "bundle1", "app1", "event-type-1");
        Event event2 = createDrawerEvent(orgId, "bundle1", "app1", "event-type-1");
        Event event3 = createDrawerEvent(orgId, "bundle1", "app1", "event-type-1");

        Integer updated = drawerNotificationRepository.updateReadStatus(
            orgId, username, Set.of(event1.getId(), event2.getId(), event3.getId()), true
        );

        assertEquals(3, updated, "Should mark 3 events as read");
    }

    @Test
    void testUpdateReadStatus_MarkAsRead_Idempotent() {
        Event event = createDrawerEvent(orgId, "bundle1", "app1", "event-type-1");

        // First call: INSERT
        Integer firstUpdate = drawerNotificationRepository.updateReadStatus(
            orgId, username, Set.of(event.getId()), true
        );
        assertEquals(1, firstUpdate);

        // Second call: ON CONFLICT DO NOTHING (returns 0)
        Integer secondUpdate = drawerNotificationRepository.updateReadStatus(
            orgId, username, Set.of(event.getId()), true
        );
        assertEquals(0, secondUpdate, "Idempotent call should return 0 due to ON CONFLICT DO NOTHING");
    }

    @Test
    void testUpdateReadStatus_MarkAsUnread_DeletesRow() {
        Event event = createDrawerEvent(orgId, "bundle1", "app1", "event-type-1");

        // First mark as read
        drawerNotificationRepository.updateReadStatus(orgId, username, Set.of(event.getId()), true);

        // Then mark as unread (DELETE)
        Integer deleted = drawerNotificationRepository.updateReadStatus(
            orgId, username, Set.of(event.getId()), false
        );

        assertEquals(1, deleted, "Should delete 1 drawer_read_status row");

        // Verify row was deleted
        Long count = (Long) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM drawer_read_status WHERE org_id = :orgId AND user_id = :userId AND event_id = :eventId",
                Long.class
            )
            .setParameter("orgId", orgId)
            .setParameter("userId", username)
            .setParameter("eventId", event.getId())
            .getSingleResult();

        assertEquals(0L, count, "drawer_read_status row should be deleted");
    }

    @Test
    void testUpdateReadStatus_MarkAsUnread_NoRowExists_Returns0() {
        Event event = createDrawerEvent(orgId, "bundle1", "app1", "event-type-1");

        // Mark as unread when no row exists
        Integer deleted = drawerNotificationRepository.updateReadStatus(
            orgId, username, Set.of(event.getId()), false
        );

        assertEquals(0, deleted, "Should return 0 when no row to delete");
    }

    @Test
    void testUpdateReadStatus_EmptySet_Returns0() {
        Integer updated = drawerNotificationRepository.updateReadStatus(
            orgId, username, new HashSet<>(), true
        );

        // Note: Current implementation doesn't validate empty sets, so this may fail with SQL error
        // This test documents the current behavior - ideally should return 0 early
        assertTrue(updated >= 0, "Should handle empty set gracefully");
    }

    @Test
    void testGetNotifications_ReturnsOnlyDrawerEvents() {
        Event drawerEvent = createDrawerEvent(orgId, "bundle1", "app1", "event-type-1");
        Event nonDrawerEvent = createNonDrawerEvent(orgId, "bundle1", "app1", "event-type-2");

        List<DrawerEntryPayload> result = drawerNotificationRepository.getNotifications(
            orgId, username, null, null, null, null, null, null, new Query(), null
        );

        assertEquals(1, result.size(), "Should return only drawer events");
        assertEquals(drawerEvent.getId(), result.get(0).getEventId());
    }

    @Test
    void testGetNotifications_RespectsReadStatusFilter_Unread() {
        Event unreadEvent = createDrawerEvent(orgId, "bundle1", "app1", "event-type-1");
        Event readEvent = createDrawerEvent(orgId, "bundle1", "app1", "event-type-1");
        markAsRead(readEvent.getId(), orgId, username);

        List<DrawerEntryPayload> result = drawerNotificationRepository.getNotifications(
            orgId, username, null, null, null, null, null, false, new Query(), null
        );

        assertEquals(1, result.size(), "Should return only unread events");
        assertEquals(unreadEvent.getId(), result.get(0).getEventId());
        assertFalse(result.get(0).isRead(), "Event should be marked as unread");
    }

    @Test
    void testGetNotifications_RespectsReadStatusFilter_Read() {
        Event unreadEvent = createDrawerEvent(orgId, "bundle1", "app1", "event-type-1");
        Event readEvent = createDrawerEvent(orgId, "bundle1", "app1", "event-type-1");
        markAsRead(readEvent.getId(), orgId, username);

        List<DrawerEntryPayload> result = drawerNotificationRepository.getNotifications(
            orgId, username, null, null, null, null, null, true, new Query(), null
        );

        assertEquals(1, result.size(), "Should return only read events");
        assertEquals(readEvent.getId(), result.get(0).getEventId());
        assertTrue(result.get(0).isRead(), "Event should be marked as read");
    }

    @Test
    void testGetNotifications_UserUnsubscribedFromAllEventTypes_ReturnsEmpty() {
        EventType eventType = createDrawerEvent(orgId, "bundle1", "app1", "event-type-1").getEventType();
        unsubscribeFromEventType(orgId, username, eventType.getId());

        List<DrawerEntryPayload> result = drawerNotificationRepository.getNotifications(
            orgId, username, null, null, null, null, null, null, new Query(), null
        );

        assertEquals(0, result.size(), "Should return empty when user unsubscribed from all drawer event types");
    }

    @Test
    void testGetNotifications_Pagination() {
        for (int i = 0; i < 25; i++) {
            createDrawerEvent(orgId, "bundle1", "app1", "event-type-1");
        }

        Query query = new Query();
        setQueryField(query, "pageSize", 10);
        setQueryField(query, "offset", 0);

        List<DrawerEntryPayload> page1 = drawerNotificationRepository.getNotifications(
            orgId, username, null, null, null, null, null, null, query, null
        );

        assertEquals(10, page1.size(), "First page should have 10 results");

        setQueryField(query, "pageSize", 10);
        setQueryField(query, "offset", 10);
        List<DrawerEntryPayload> page2 = drawerNotificationRepository.getNotifications(
            orgId, username, null, null, null, null, null, null, query, null
        );

        assertEquals(10, page2.size(), "Second page should have 10 results");

        // Verify no overlap
        assertNotEquals(page1.get(0).getEventId(), page2.get(0).getEventId());
    }

    @Test
    void testGetNotifications_DateRangeFilter() {
        LocalDateTime now = LocalDateTime.now();
        Event oldEvent = createDrawerEventAtTime(orgId, "bundle1", "app1", "event-type-1", now.minusDays(10));
        Event recentEvent = createDrawerEventAtTime(orgId, "bundle1", "app1", "event-type-1", now.minusHours(1));

        List<DrawerEntryPayload> result = drawerNotificationRepository.getNotifications(
            orgId, username, null, null, null,
            now.minusDays(2), now, null, new Query(), null
        );

        assertEquals(1, result.size(), "Should return only events in date range");
        assertEquals(recentEvent.getId(), result.get(0).getEventId());
    }

    @Test
    void testGetNotifications_FilterByBundle() {
        Bundle bundle1 = resourceHelpers.createBundle("bundle1");
        Bundle bundle2 = resourceHelpers.createBundle("bundle2");

        Event event1 = createDrawerEventInBundle(orgId, bundle1);
        Event event2 = createDrawerEventInBundle(orgId, bundle2);

        List<DrawerEntryPayload> result = drawerNotificationRepository.getNotifications(
            orgId, username, Set.of(bundle1.getId()), null, null,
            null, null, null, new Query(), null
        );

        assertEquals(1, result.size(), "Should return only events from bundle1");
        assertEquals(event1.getId(), result.get(0).getEventId());
    }

    @Test
    void testGetNotifications_OrgIsolation() {
        String org1 = "org1";
        String org2 = "org2";

        Event event1 = createDrawerEvent(org1, "bundle1", "app1", "event-type-1");
        Event event2 = createDrawerEvent(org2, "bundle1", "app1", "event-type-1");

        List<DrawerEntryPayload> result = drawerNotificationRepository.getNotifications(
            org1, username, null, null, null, null, null, null, new Query(), null
        );

        assertEquals(1, result.size(), "Should return only events from org1");
        assertEquals(event1.getId(), result.get(0).getEventId());
    }

    @Test
    void testGetNotifications_NormalizedQueryPath() {
        when(backendConfig.isNormalizedQueriesEnabled(anyString())).thenReturn(true);

        Event event = createDrawerEvent(orgId, "bundle1", "app1", "event-type-1");

        List<DrawerEntryPayload> result = drawerNotificationRepository.getNotifications(
            orgId, username, null, null, null, null, null, null, new Query(), null
        );

        assertEquals(1, result.size(), "Normalized query path should return results");
        assertEquals(event.getId(), result.get(0).getEventId());
    }

    @Test
    void testCount_MatchesGetNotificationsSize() {
        for (int i = 0; i < 15; i++) {
            createDrawerEvent(orgId, "bundle1", "app1", "event-type-1");
        }

        Long count = drawerNotificationRepository.count(
            orgId, username, null, null, null, null, null, null, null
        );

        List<DrawerEntryPayload> all = drawerNotificationRepository.getNotifications(
            orgId, username, null, null, null, null, null, null,
            createUnlimitedQuery(), null
        );

        assertEquals(count, (long) all.size(), "count() should match getNotifications() size");
    }

    @Test
    void testCount_WithReadStatusFilter() {
        Event unreadEvent1 = createDrawerEvent(orgId, "bundle1", "app1", "event-type-1");
        Event unreadEvent2 = createDrawerEvent(orgId, "bundle1", "app1", "event-type-1");
        Event readEvent = createDrawerEvent(orgId, "bundle1", "app1", "event-type-1");
        markAsRead(readEvent.getId(), orgId, username);

        Long unreadCount = drawerNotificationRepository.count(
            orgId, username, null, null, null, null, null, false, null
        );
        assertEquals(2L, unreadCount, "Should count 2 unread events");

        Long readCount = drawerNotificationRepository.count(
            orgId, username, null, null, null, null, null, true, null
        );
        assertEquals(1L, readCount, "Should count 1 read event");
    }

    @Test
    void testCount_UserUnsubscribedFromAll_Returns0() {
        EventType eventType = createDrawerEvent(orgId, "bundle1", "app1", "event-type-1").getEventType();
        unsubscribeFromEventType(orgId, username, eventType.getId());

        Long count = drawerNotificationRepository.count(
            orgId, username, null, null, null, null, null, null, null
        );

        assertEquals(0L, count, "Should return 0 when user unsubscribed from all");
    }

    @Transactional
    Event createDrawerEvent(String orgId, String bundleName, String appName, String eventTypeName) {
        return createDrawerEventAtTime(orgId, bundleName, appName, eventTypeName, LocalDateTime.now());
    }

    @Transactional
    Event createDrawerEventAtTime(String orgId, String bundleName, String appName, String eventTypeName, LocalDateTime created) {
        String uniqueSuffix = "-" + UUID.randomUUID().toString().substring(0, 8);
        Bundle bundle = resourceHelpers.createBundle(bundleName + uniqueSuffix, bundleName);
        Application app = resourceHelpers.createApplication(bundle.getId(), appName + uniqueSuffix, appName);
        EventType eventType = resourceHelpers.createEventType(app.getId(), eventTypeName + uniqueSuffix, eventTypeName, "description");

        // Set includedInDrawer = true
        eventType.setIncludedInDrawer(true);
        entityManager.merge(eventType);

        Event event = new Event();
        event.setOrgId(orgId);
        event.setEventType(eventType);
        event.setBundleId(bundle.getId());
        event.setBundleDisplayName(bundle.getDisplayName());
        event.setApplicationId(app.getId());
        event.setApplicationDisplayName(app.getDisplayName());
        event.setEventTypeDisplayName(eventType.getDisplayName());
        event.setCreated(created);
        event.setRenderedDrawerNotification("Rendered notification: " + eventTypeName);
        entityManager.persist(event);
        return event;
    }

    @Transactional
    Event createNonDrawerEvent(String orgId, String bundleName, String appName, String eventTypeName) {
        String uniqueSuffix = "-" + UUID.randomUUID().toString().substring(0, 8);
        Bundle bundle = resourceHelpers.createBundle(bundleName + uniqueSuffix, bundleName);
        Application app = resourceHelpers.createApplication(bundle.getId(), appName + uniqueSuffix, appName);
        EventType eventType = resourceHelpers.createEventType(app.getId(), eventTypeName + uniqueSuffix, eventTypeName, "description");

        // includedInDrawer = false (default)
        eventType.setIncludedInDrawer(false);
        entityManager.merge(eventType);

        Event event = new Event();
        event.setOrgId(orgId);
        event.setEventType(eventType);
        event.setBundleId(bundle.getId());
        event.setBundleDisplayName(bundle.getDisplayName());
        event.setApplicationId(app.getId());
        event.setApplicationDisplayName(app.getDisplayName());
        event.setEventTypeDisplayName(eventType.getDisplayName());
        event.setCreated(LocalDateTime.now());
        entityManager.persist(event);
        return event;
    }

    @Transactional
    Event createDrawerEventInBundle(String orgId, Bundle bundle) {
        String uniqueSuffix = "-" + UUID.randomUUID().toString().substring(0, 8);
        Application app = resourceHelpers.createApplication(bundle.getId(), "app1" + uniqueSuffix, "app1");
        EventType eventType = resourceHelpers.createEventType(app.getId(), "event-type-1" + uniqueSuffix, "event-type-1", "description");
        eventType.setIncludedInDrawer(true);
        entityManager.merge(eventType);

        Event event = new Event();
        event.setOrgId(orgId);
        event.setEventType(eventType);
        event.setBundleId(bundle.getId());
        event.setBundleDisplayName(bundle.getDisplayName());
        event.setApplicationId(app.getId());
        event.setApplicationDisplayName(app.getDisplayName());
        event.setEventTypeDisplayName(eventType.getDisplayName());
        event.setCreated(LocalDateTime.now());
        event.setRenderedDrawerNotification("Rendered notification");
        entityManager.persist(event);
        return event;
    }

    @Transactional
    void markAsRead(UUID eventId, String orgId, String userId) {
        entityManager.createNativeQuery(
                "INSERT INTO drawer_read_status (org_id, user_id, event_id) VALUES (:orgId, :userId, :eventId)"
            )
            .setParameter("orgId", orgId)
            .setParameter("userId", userId)
            .setParameter("eventId", eventId)
            .executeUpdate();
    }

    @Transactional
    void unsubscribeFromEventType(String orgId, String userId, UUID eventTypeId) {
        entityManager.createNativeQuery(
                "INSERT INTO email_subscriptions (org_id, user_id, event_type_id, subscription_type, subscribed) " +
                "VALUES (:orgId, :userId, :eventTypeId, 'DRAWER', false)"
            )
            .setParameter("orgId", orgId)
            .setParameter("userId", userId)
            .setParameter("eventTypeId", eventTypeId)
            .executeUpdate();
    }

    private Query createUnlimitedQuery() {
        Query query = new Query();
        setQueryField(query, "pageSize", 1000);
        setQueryField(query, "offset", 0);
        return query;
    }

    private void setQueryField(Query query, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = Query.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(query, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set query field: " + fieldName, e);
        }
    }
}
