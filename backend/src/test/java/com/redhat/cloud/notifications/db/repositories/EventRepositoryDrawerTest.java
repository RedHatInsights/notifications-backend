package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.EventPayloadTestHelper;
import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.auth.kessel.KesselCheckClient;
import com.redhat.cloud.notifications.auth.kessel.KesselTestHelper;
import com.redhat.cloud.notifications.auth.rbac.workspace.WorkspaceUtils;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.RecipientsAuthorizationCriterion;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.routers.handlers.event.EventAuthorizationCriterion;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.project_kessel.api.inventory.v1beta2.CheckForUpdateRequest;
import org.project_kessel.api.inventory.v1beta2.CheckRequest;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.project_kessel.api.inventory.v1beta2.Allowed.ALLOWED_FALSE;

/**
 * Tests for EventRepository.getDrawerEventsWithCriterion() method.
 * This method is used by DrawerNotificationRepository to fetch events that have
 * authorization criteria for Kessel RBAC checks.
 */
@QuarkusTest
public class EventRepositoryDrawerTest extends DbIsolatedTest {

    private static final String DEFAULT_ORG_ID = "test-org-123";

    @Inject
    EventRepository eventRepository;

    @Inject
    EntityManager entityManager;

    @Inject
    ResourceHelpers resourceHelpers;

    @InjectMock
    BackendConfig backendConfig;

    /**
     * Mocked Kessel's check client so that KesselTestHelper can be used.
     */
    @InjectMock
    KesselCheckClient kesselCheckClient;

    @Inject
    KesselTestHelper kesselTestHelper;

    /**
     * Mocked RBAC's workspace utilities so that KesselTestHelper can be used.
     */
    @InjectMock
    WorkspaceUtils workspaceUtils;

    @BeforeEach
    void setUp() {
        // Enable RBAC so ConsoleIdentityProvider doesn't build principal with all privileges
        Mockito.when(backendConfig.isRBACEnabled()).thenReturn(true);
        when(workspaceUtils.getDefaultWorkspaceId(DEFAULT_ORG_ID)).thenReturn(KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID);

        // Default: deny all Kessel permissions (tests will override as needed)
        mockKesselDenyAll();

        // Default configs
        when(backendConfig.isNormalizedQueriesEnabled(anyString())).thenReturn(false);
    }

    @Test
    void testGetDrawerEventsWithCriterion_ReturnsOnlyEventsWithAuthCriteria() {
        // Create event WITH authorization criterion
        Event eventWithAuth = createDrawerEventWithAuthCriterion(DEFAULT_ORG_ID);

        // Create event WITHOUT authorization criterion
        Event eventWithoutAuth = createDrawerEventNoAuthCriterion(DEFAULT_ORG_ID);

        // Get subscribed event types (all drawer types in this test)
        Set<UUID> subscribedEventTypes = new HashSet<>();
        subscribedEventTypes.add(eventWithAuth.getEventType().getId());
        subscribedEventTypes.add(eventWithoutAuth.getEventType().getId());

        List<EventAuthorizationCriterion> result = eventRepository.getDrawerEventsWithCriterion(
            DEFAULT_ORG_ID, false, subscribedEventTypes, null, null
        );

        // Should return ONLY the event with authorization criterion
        assertEquals(1, result.size(), "Should return only events with hasAuthorizationCriterion = true");
        assertEquals(eventWithAuth.getId(), result.get(0).id());
        assertNotNull(result.get(0).authorizationCriterion(), "Authorization criterion should not be null");
    }

    @Test
    void testGetDrawerEventsWithCriterion_FiltersDrawerEventTypesOnly() {
        // Create drawer event with auth criterion
        Event drawerEvent = createDrawerEventWithAuthCriterion(DEFAULT_ORG_ID);

        // Create non-drawer event with auth criterion
        Event nonDrawerEvent = createNonDrawerEventWithAuthCriterion(DEFAULT_ORG_ID);

        Set<UUID> allEventTypes = new HashSet<>();
        allEventTypes.add(drawerEvent.getEventType().getId());
        allEventTypes.add(nonDrawerEvent.getEventType().getId());

        List<EventAuthorizationCriterion> result = eventRepository.getDrawerEventsWithCriterion(
            DEFAULT_ORG_ID, false, allEventTypes, null, null
        );

        // Should return ONLY the drawer event
        assertEquals(1, result.size(), "Should return only drawer events (includedInDrawer = true)");
        assertEquals(drawerEvent.getId(), result.get(0).id());
    }

    @Test
    void testGetDrawerEventsWithCriterion_RespectsSubscribedEventTypes() {
        Event event1 = createDrawerEventWithAuthCriterion(DEFAULT_ORG_ID, "bundle1", "app1", "event-type-1");
        Event event2 = createDrawerEventWithAuthCriterion(DEFAULT_ORG_ID, "bundle1", "app1", "event-type-2");

        // User only subscribed to event-type-1
        Set<UUID> subscribedEventTypes = Set.of(event1.getEventType().getId());

        List<EventAuthorizationCriterion> result = eventRepository.getDrawerEventsWithCriterion(
            DEFAULT_ORG_ID, false, subscribedEventTypes, null, null
        );

        assertEquals(1, result.size(), "Should return only events from subscribed event types");
        assertEquals(event1.getId(), result.get(0).id());
    }

    @Test
    @Transactional
    void testGetDrawerEventsWithCriterion_DateRangeFilter() {
        LocalDateTime now = LocalDateTime.now();

        // Create shared bundle/app/eventType first
        String uniqueSuffix = "-" + UUID.randomUUID().toString().substring(0, 8);
        Bundle bundle = resourceHelpers.createBundle("bundle1" + uniqueSuffix, "bundle1");
        Application app = resourceHelpers.createApplication(bundle.getId(), "app1" + uniqueSuffix, "app1");
        EventType eventType = resourceHelpers.createEventType(app.getId(), "event-type-1" + uniqueSuffix, "event-type-1", "description");
        eventType.setIncludedInDrawer(true);
        entityManager.merge(eventType);

        // Create old event
        Event oldEvent = createEventWithSharedEventType(DEFAULT_ORG_ID, bundle, app, eventType, now.minusDays(10));

        // Create recent event with same event type
        Event recentEvent = createEventWithSharedEventType(DEFAULT_ORG_ID, bundle, app, eventType, now.minusHours(1));

        Set<UUID> subscribedEventTypes = Set.of(eventType.getId());

        // Filter for events in last 2 days
        LocalDateTime startDate = now.minusDays(2);
        LocalDateTime endDate = now;

        List<EventAuthorizationCriterion> result = eventRepository.getDrawerEventsWithCriterion(
            DEFAULT_ORG_ID, false, subscribedEventTypes, startDate, endDate
        );

        assertEquals(1, result.size(), "Should return only events in date range");
        assertEquals(recentEvent.getId(), result.get(0).id());
    }

    @Test
    void testGetDrawerEventsWithCriterion_OrgIsolation() {
        String org1 = "org1";
        String org2 = "org2";

        Event event1 = createDrawerEventWithAuthCriterion(org1);
        Event event2 = createDrawerEventWithAuthCriterion(org2);

        Set<UUID> subscribedEventTypes = Set.of(event1.getEventType().getId(), event2.getEventType().getId());

        // Query for org1
        List<EventAuthorizationCriterion> result = eventRepository.getDrawerEventsWithCriterion(
            org1, false, subscribedEventTypes, null, null
        );

        assertEquals(1, result.size(), "Should return only events from org1");
        assertEquals(event1.getId(), result.get(0).id());
    }

    @Test
    void testGetDrawerEventsWithCriterion_NormalizedQueryPath() {
        Event event = createDrawerEventWithAuthCriterion(DEFAULT_ORG_ID);
        Set<UUID> subscribedEventTypes = Set.of(event.getEventType().getId());

        List<EventAuthorizationCriterion> result = eventRepository.getDrawerEventsWithCriterion(
            DEFAULT_ORG_ID, true, subscribedEventTypes, null, null
        );

        assertEquals(1, result.size(), "Normalized query path should work");
        assertEquals(event.getId(), result.get(0).id());

        // Verify denormalized display names were populated (from JOINed entities)
        assertNotNull(event.getBundleDisplayName());
        assertNotNull(event.getApplicationDisplayName());
        assertNotNull(event.getEventTypeDisplayName());
    }

    @Test
    void testGetDrawerEventsWithCriterion_ExtractsAuthorizationCriterion() {
        Event event = createDrawerEventWithAuthCriterion(DEFAULT_ORG_ID);
        Set<UUID> subscribedEventTypes = Set.of(event.getEventType().getId());

        List<EventAuthorizationCriterion> result = eventRepository.getDrawerEventsWithCriterion(
            DEFAULT_ORG_ID, false, subscribedEventTypes, null, null
        );

        assertEquals(1, result.size());

        RecipientsAuthorizationCriterion criterion = result.get(0).authorizationCriterion();
        assertNotNull(criterion, "Authorization criterion should be extracted");
        assertEquals("rel1", criterion.getRelation(), "Should extract relation");
        assertEquals("id1", criterion.getId(), "Should extract id");
        assertNotNull(criterion.getType(), "Should extract type");
        assertEquals("host", criterion.getType().getName(), "Should extract type name");
    }

    @Test
    void testGetDrawerEventsWithCriterion_EmptyWhenNoMatchingEvents() {
        // Use a unique org ID to avoid test data pollution
        String uniqueOrgId = "unique-org-" + UUID.randomUUID().toString().substring(0, 8);

        // Create event but don't pass its event type ID
        Event event = createDrawerEventWithAuthCriterion(uniqueOrgId);

        // Pass a non-existent event type ID (user subscribed to different event types)
        Set<UUID> nonMatchingEventTypeIds = Set.of(UUID.randomUUID());

        List<EventAuthorizationCriterion> result = eventRepository.getDrawerEventsWithCriterion(
            uniqueOrgId, false, nonMatchingEventTypeIds, null, null
        );

        assertEquals(0, result.size(), "Should return empty list when no matching events");
    }

    @Transactional
    Event createDrawerEventWithAuthCriterion(String orgId) {
        return createDrawerEventWithAuthCriterion(orgId, "bundle1", "app1", "event-type-1");
    }

    @Transactional
    Event createDrawerEventWithAuthCriterion(String orgId, String bundleName, String appName, String eventTypeName) {
        return createDrawerEventWithAuthCriterionAtTime(orgId, bundleName, appName, eventTypeName, LocalDateTime.now());
    }

    @Transactional
    Event createDrawerEventWithAuthCriterionAtTime(String orgId, String bundleName, String appName, String eventTypeName, LocalDateTime created) {
        String uniqueSuffix = "-" + UUID.randomUUID().toString().substring(0, 8);
        Bundle bundle = resourceHelpers.createBundle(bundleName + uniqueSuffix, bundleName);
        Application app = resourceHelpers.createApplication(bundle.getId(), appName + uniqueSuffix, appName);
        EventType eventType = resourceHelpers.createEventType(app.getId(), eventTypeName + uniqueSuffix, eventTypeName, "description");

        // Set as drawer event type
        eventType.setIncludedInDrawer(true);
        entityManager.merge(eventType);

        // Build action with authorization criterion (using EventPayloadTestHelper pattern)
        Action action = EventPayloadTestHelper.buildValidAction(orgId, bundle.getName(), app.getName(), eventType.getName());
        action.setSeverity(Severity.MODERATE.name());

        // Add authorization criterion
        RecipientsAuthorizationCriterion authCriterion = EventPayloadTestHelper.buildRecipientsAuthorizationCriterion();
        action.setRecipientsAuthorizationCriterion(authCriterion);

        String payload = Parser.encode(action);

        Event event = new Event();
        event.setOrgId(orgId);
        event.setEventType(eventType);
        event.setBundleId(bundle.getId());
        event.setBundleDisplayName(bundle.getDisplayName());
        event.setApplicationId(app.getId());
        event.setApplicationDisplayName(app.getDisplayName());
        event.setEventTypeDisplayName(eventType.getDisplayName());
        event.setCreated(created);
        event.setPayload(payload);
        event.setRenderedDrawerNotification("Rendered notification: " + eventTypeName);
        event.setEventWrapper(new EventWrapperAction(action));
        event.setHasAuthorizationCriterion(true); // CRITICAL!

        entityManager.persist(event);
        return event;
    }

    @Transactional
    Event createDrawerEventNoAuthCriterion(String orgId) {
        String uniqueSuffix = "-" + UUID.randomUUID().toString().substring(0, 8);
        Bundle bundle = resourceHelpers.createBundle("bundle-no-auth" + uniqueSuffix, "Bundle No Auth");
        Application app = resourceHelpers.createApplication(bundle.getId(), "app-no-auth" + uniqueSuffix, "App No Auth");
        EventType eventType = resourceHelpers.createEventType(app.getId(), "event-type-no-auth" + uniqueSuffix, "EventType No Auth", "description");

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
        event.setPayload("{}");
        event.setRenderedDrawerNotification("Rendered notification");
        event.setHasAuthorizationCriterion(false); // No auth criterion

        entityManager.persist(event);
        return event;
    }

    @Transactional
    Event createNonDrawerEventWithAuthCriterion(String orgId) {
        String uniqueSuffix = "-" + UUID.randomUUID().toString().substring(0, 8);
        Bundle bundle = resourceHelpers.createBundle("bundle-non-drawer" + uniqueSuffix, "Bundle Non Drawer");
        Application app = resourceHelpers.createApplication(bundle.getId(), "app-non-drawer" + uniqueSuffix, "App Non Drawer");
        EventType eventType = resourceHelpers.createEventType(app.getId(), "event-type-non-drawer" + uniqueSuffix, "EventType Non Drawer", "description");

        eventType.setIncludedInDrawer(false); // NOT a drawer event type
        entityManager.merge(eventType);

        Action action = EventPayloadTestHelper.buildValidAction(orgId, bundle.getName(), app.getName(), eventType.getName());
        RecipientsAuthorizationCriterion authCriterion = EventPayloadTestHelper.buildRecipientsAuthorizationCriterion();
        action.setRecipientsAuthorizationCriterion(authCriterion);
        String payload = Parser.encode(action);

        Event event = new Event();
        event.setOrgId(orgId);
        event.setEventType(eventType);
        event.setBundleId(bundle.getId());
        event.setBundleDisplayName(bundle.getDisplayName());
        event.setApplicationId(app.getId());
        event.setApplicationDisplayName(app.getDisplayName());
        event.setEventTypeDisplayName(eventType.getDisplayName());
        event.setCreated(LocalDateTime.now());
        event.setPayload(payload);
        event.setEventWrapper(new EventWrapperAction(action));
        event.setHasAuthorizationCriterion(true);

        entityManager.persist(event);
        return event;
    }

    @Transactional
    Event createEventWithSharedEventType(String orgId, Bundle bundle, Application app, EventType eventType, LocalDateTime created) {
        Action action = EventPayloadTestHelper.buildValidAction(orgId, bundle.getName(), app.getName(), eventType.getName());
        action.setSeverity(Severity.MODERATE.name());

        RecipientsAuthorizationCriterion authCriterion = EventPayloadTestHelper.buildRecipientsAuthorizationCriterion();
        action.setRecipientsAuthorizationCriterion(authCriterion);

        String payload = Parser.encode(action);

        Event event = new Event();
        event.setOrgId(orgId);
        event.setEventType(eventType);
        event.setBundleId(bundle.getId());
        event.setBundleDisplayName(bundle.getDisplayName());
        event.setApplicationId(app.getId());
        event.setApplicationDisplayName(app.getDisplayName());
        event.setEventTypeDisplayName(eventType.getDisplayName());
        event.setCreated(created);
        event.setPayload(payload);
        event.setRenderedDrawerNotification("Rendered notification: " + eventType.getName());
        event.setEventWrapper(new EventWrapperAction(action));
        event.setHasAuthorizationCriterion(true);

        entityManager.persist(event);
        return event;
    }

    private void mockKesselDenyAll() {
        when(kesselCheckClient
            .check(any(CheckRequest.class)))
            .thenReturn(kesselTestHelper.buildCheckResponse(ALLOWED_FALSE));
        when(kesselCheckClient
            .checkForUpdate(any(CheckForUpdateRequest.class)))
            .thenReturn(kesselTestHelper.buildCheckForUpdateResponse(ALLOWED_FALSE));
    }
}
