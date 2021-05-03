package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.BehaviorGroupAction;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeBehavior;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.pgclient.PgException;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.PersistenceException;
import javax.validation.ConstraintViolationException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class BehaviorGroupResourcesTest extends DbIsolatedTest {

    private static final String ACCOUNT_ID = "root";

    @Inject
    Mutiny.Session session;

    @Inject
    BundleResources bundleResources;

    @Inject
    ApplicationResources appResources;

    @Inject
    EndpointResources endpointResources;

    @Inject
    BehaviorGroupResources behaviorGroupResources;

    @Test
    public void testCreateAndUpdateAndDeleteBehaviorGroup() {
        Bundle bundle = createBundle();

        // Create behavior group.
        BehaviorGroup behaviorGroup = createBehaviorGroup("name", "displayName", bundle.getId());
        List<BehaviorGroup> behaviorGroups = findBehaviorGroupsByBundleId(bundle.getId());
        assertEquals(1, behaviorGroups.size());
        assertEquals(behaviorGroup, behaviorGroups.get(0));
        assertEquals(behaviorGroup.getName(), behaviorGroups.get(0).getName());
        assertEquals(behaviorGroup.getDisplayName(), behaviorGroups.get(0).getDisplayName());
        assertEquals(bundle.getId(), behaviorGroups.get(0).getBundle().getId());
        assertNotNull(bundle.getCreated());

        // Update behavior group.
        String newName = "new-name";
        String newDisplayName = "newDisplayName";
        Boolean updated = updateBehaviorGroup(behaviorGroup.getId(), newName, newDisplayName, bundle.getId());
        assertTrue(updated);
        session.clear(); // We need to clear the session L1 cache before checking the update result.
        behaviorGroups = findBehaviorGroupsByBundleId(bundle.getId());
        assertEquals(1, behaviorGroups.size());
        assertEquals(behaviorGroup.getId(), behaviorGroups.get(0).getId());
        assertEquals(newName, behaviorGroups.get(0).getName());
        assertEquals(newDisplayName, behaviorGroups.get(0).getDisplayName());
        assertEquals(bundle.getId(), behaviorGroups.get(0).getBundle().getId());

        // Delete behavior group.
        Boolean deleted = deleteBehaviorGroup(behaviorGroup.getId());
        assertTrue(deleted);
        behaviorGroups = findBehaviorGroupsByBundleId(bundle.getId());
        assertTrue(behaviorGroups.isEmpty());
    }

    @Test
    public void testCreateBehaviorGroupWithNullName() {
        Bundle bundle = createBundle();
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            createBehaviorGroup(null, "displayName", bundle.getId());
        });
        assertSame(ConstraintViolationException.class, e.getCause().getCause().getClass());
        assertTrue(e.getCause().getCause().getMessage().contains("propertyPath=name"));
    }

    @Test
    public void testCreateBehaviorGroupWithInvalidName() {
        Bundle bundle = createBundle();
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            createBehaviorGroup("I am not valid", "displayName", bundle.getId());
        });
        assertSame(ConstraintViolationException.class, e.getCause().getCause().getClass());
        assertTrue(e.getCause().getCause().getMessage().contains("propertyPath=name"));
    }

    @Test
    public void testCreateBehaviorGroupWithNullDisplayName() {
        Bundle bundle = createBundle();
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            createBehaviorGroup("name", null, bundle.getId());
        });
        assertSame(ConstraintViolationException.class, e.getCause().getCause().getClass());
        assertTrue(e.getCause().getCause().getMessage().contains("propertyPath=displayName"));
    }

    @Test
    public void testCreateBehaviorGroupWithNullBundleId() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            createBehaviorGroup("name", "displayName", null);
        });
        assertSame(IllegalArgumentException.class, e.getCause().getCause().getClass());
    }

    @Test
    public void testCreateBehaviorGroupWithUnknownBundleId() {
        PersistenceException e = assertThrows(PersistenceException.class, () -> {
            createBehaviorGroup("name", "displayName", UUID.randomUUID());
        });
        assertSame(PgException.class, e.getCause().getCause().getClass()); // FK constraint violation
    }

    @Test
    public void testfindByBundleIdOrdering() {
        Bundle bundle = createBundle();
        BehaviorGroup behaviorGroup1 = createBehaviorGroup("name1", "displayName", bundle.getId());
        BehaviorGroup behaviorGroup2 = createBehaviorGroup("name2", "displayName", bundle.getId());
        BehaviorGroup behaviorGroup3 = createBehaviorGroup("name3", "displayName", bundle.getId());
        setDefaultBehaviorGroup(bundle.getId(), behaviorGroup2.getId());
        List<BehaviorGroup> behaviorGroups = findBehaviorGroupsByBundleId(bundle.getId());
        assertEquals(3, behaviorGroups.size());
        assertSame(behaviorGroup2, behaviorGroups.get(0), "Default behavior group should come first");
        assertSame(behaviorGroup3, behaviorGroups.get(1), "Non-default behavior groups should be sorted on descending creation date");
        assertSame(behaviorGroup1, behaviorGroups.get(2), "Non-default behavior groups should be sorted on descending creation date");
    }

    @Test
    public void testAddAndDeleteEventTypeBehaviorAndMuteEventType() {
        Bundle bundle = createBundle();
        Application app = createApp(bundle.getId());
        EventType eventType = createEventType(app.getId());

        // Add behaviorGroup1 to eventType behaviors.
        BehaviorGroup behaviorGroup1 = createBehaviorGroup("name1", "displayName", bundle.getId());
        Boolean added = addEventTypeBehavior(ACCOUNT_ID, eventType.getId(), behaviorGroup1.getId());
        assertTrue(added);

        // Doing it again should not throw any exception but return false.
        added = addEventTypeBehavior(ACCOUNT_ID, eventType.getId(), behaviorGroup1.getId());
        assertFalse(added);

        // Add behaviorGroup2 to eventType behaviors.
        BehaviorGroup behaviorGroup2 = createBehaviorGroup("name2", "displayName", bundle.getId());
        added = addEventTypeBehavior(ACCOUNT_ID, eventType.getId(), behaviorGroup2.getId());
        assertTrue(added);

        // Add behaviorGroup3 to eventType behaviors.
        BehaviorGroup behaviorGroup3 = createBehaviorGroup("name3", "displayName", bundle.getId());
        added = addEventTypeBehavior(ACCOUNT_ID, eventType.getId(), behaviorGroup3.getId());
        assertTrue(added);

        // Check all behaviors were correctly persisted.
        List<EventTypeBehavior> behaviors = findEventTypeBehaviorByEventTypeId(eventType.getId());
        assertEquals(3, behaviors.size());
        assertTrue(behaviors.stream().anyMatch(behavior -> behavior.getId().behaviorGroupId.equals(behaviorGroup1.getId())));
        assertTrue(behaviors.stream().anyMatch(behavior -> behavior.getId().behaviorGroupId.equals(behaviorGroup2.getId())));
        assertTrue(behaviors.stream().anyMatch(behavior -> behavior.getId().behaviorGroupId.equals(behaviorGroup3.getId())));

        // Remove behaviorGroup2 from eventType behaviors.
        Boolean deleted = deleteEventTypeBehavior(ACCOUNT_ID, eventType.getId(), behaviorGroup2.getId());
        assertTrue(deleted);
        behaviors = findEventTypeBehaviorByEventTypeId(eventType.getId());
        assertEquals(2, behaviors.size());
        assertTrue(behaviors.stream().noneMatch(action -> action.getId().behaviorGroupId.equals(behaviorGroup2.getId())));

        // Doing it again should not throw any exception but return false.
        deleted = deleteEventTypeBehavior(ACCOUNT_ID, eventType.getId(), behaviorGroup2.getId());
        assertFalse(deleted);
        behaviors = findEventTypeBehaviorByEventTypeId(eventType.getId());
        assertEquals(2, behaviors.size());

        // Mute eventType, removing all its behaviors.
        Boolean muted = muteEventType(ACCOUNT_ID, eventType.getId());
        assertTrue(muted);
        behaviors = findEventTypeBehaviorByEventTypeId(eventType.getId());
        assertTrue(behaviors.isEmpty());
    }

    @Test
    public void testAddEventTypeBehaviorWithWrongAccountId() {
        Bundle bundle = createBundle();
        Application app = createApp(bundle.getId());
        EventType eventType = createEventType(app.getId());
        BehaviorGroup behaviorGroup = createBehaviorGroup("name", "displayName", bundle.getId());
        Boolean added = addEventTypeBehavior("unknownAccountId", eventType.getId(), behaviorGroup.getId());
        assertFalse(added);
    }

    @Test
    public void testFindEventTypesByBehaviorGroupId() {
        Bundle bundle = createBundle();
        Application app = createApp(bundle.getId());
        EventType eventType = createEventType(app.getId());
        BehaviorGroup behaviorGroup = createBehaviorGroup("name", "displayName", bundle.getId());
        addEventTypeBehavior(ACCOUNT_ID, eventType.getId(), behaviorGroup.getId());
        List<EventType> eventTypes = findEventTypesByBehaviorGroupId(ACCOUNT_ID, behaviorGroup.getId());
        assertEquals(1, eventTypes.size());
        assertEquals(eventType.getId(), eventTypes.get(0).getId());
    }

    @Test
    public void testFindBehaviorGroupsByEventTypeId() {
        Bundle bundle = createBundle();
        Application app = createApp(bundle.getId());
        EventType eventType = createEventType(app.getId());
        BehaviorGroup behaviorGroup = createBehaviorGroup("name", "displayName", bundle.getId());
        addEventTypeBehavior(ACCOUNT_ID, eventType.getId(), behaviorGroup.getId());
        List<BehaviorGroup> behaviorGroups = findBehaviorGroupsByEventTypeId(ACCOUNT_ID, eventType.getId());
        assertEquals(1, behaviorGroups.size());
        assertEquals(behaviorGroup.getId(), behaviorGroups.get(0).getId());
    }

    @Test
    public void testAddAndDeleteBehaviorGroupAction() {
        Bundle bundle = createBundle();
        BehaviorGroup behaviorGroup = createBehaviorGroup("name", "displayName", bundle.getId());
        Endpoint endpoint1 = createEndpoint();
        Endpoint endpoint2 = createEndpoint();
        Endpoint endpoint3 = createEndpoint();

        updateAndCheckBehaviorGroupActions(ACCOUNT_ID, bundle.getId(), behaviorGroup.getId(), true, endpoint1.getId());
        updateAndCheckBehaviorGroupActions(ACCOUNT_ID, bundle.getId(), behaviorGroup.getId(), true, endpoint1.getId());
        updateAndCheckBehaviorGroupActions(ACCOUNT_ID, bundle.getId(), behaviorGroup.getId(), true, endpoint1.getId(), endpoint2.getId());
        updateAndCheckBehaviorGroupActions(ACCOUNT_ID, bundle.getId(), behaviorGroup.getId(), true, endpoint2.getId());
        updateAndCheckBehaviorGroupActions(ACCOUNT_ID, bundle.getId(), behaviorGroup.getId(), true, endpoint3.getId(), endpoint2.getId(), endpoint1.getId());
        updateAndCheckBehaviorGroupActions(ACCOUNT_ID, bundle.getId(), behaviorGroup.getId(), true);
    }

    @Test
    public void testUpdateBehaviorGroupActionsWithWrongAccountId() {
        Bundle bundle = createBundle();
        BehaviorGroup behaviorGroup = createBehaviorGroup("name", "displayName", bundle.getId());
        Endpoint endpoint = createEndpoint();
        updateAndCheckBehaviorGroupActions("unknownAccountId", bundle.getId(), behaviorGroup.getId(), false, endpoint.getId());
    }

    private Bundle createBundle() {
        Bundle bundle = new Bundle();
        bundle.setName("name");
        bundle.setDisplayName("displayName");
        return bundleResources.createBundle(bundle).await().indefinitely();
    }

    private Application createApp(UUID bundleId) {
        Application app = new Application();
        app.setBundleId(bundleId);
        app.setName("name");
        app.setDisplayName("displayName");
        return appResources.createApp(app).await().indefinitely();
    }

    private EventType createEventType(UUID appID) {
        EventType eventType = new EventType();
        eventType.setApplicationId(appID);
        eventType.setName("name");
        eventType.setDisplayName("displayName");
        return session.persist(eventType).call(session::flush).replaceWith(eventType).await().indefinitely();
    }

    private Endpoint createEndpoint() {
        Endpoint endpoint = new Endpoint();
        endpoint.setAccountId(ACCOUNT_ID);
        endpoint.setName("name");
        endpoint.setDescription("description");
        endpoint.setType(EndpointType.WEBHOOK);
        return endpointResources.createEndpoint(endpoint).await().indefinitely();
    }

    private BehaviorGroup createBehaviorGroup(String name, String displayName, UUID bundleId) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setName(name);
        behaviorGroup.setDisplayName(displayName);
        behaviorGroup.setBundleId(bundleId);
        return behaviorGroupResources.create(ACCOUNT_ID, behaviorGroup).await().indefinitely();
    }

    private Boolean updateBehaviorGroup(UUID behaviorGroupId, String name, String displayName, UUID bundleId) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setId(behaviorGroupId);
        behaviorGroup.setName(name);
        behaviorGroup.setDisplayName(displayName);
        behaviorGroup.setBundleId(UUID.randomUUID()); // This should not have any effect, the bundle is not updatable.
        return behaviorGroupResources.update(ACCOUNT_ID, behaviorGroup).await().indefinitely();
    }

    private Boolean deleteBehaviorGroup(UUID behaviorGroupId) {
        return behaviorGroupResources.delete(ACCOUNT_ID, behaviorGroupId).await().indefinitely();
    }

    private void setDefaultBehaviorGroup(UUID bundleId, UUID behaviorGroupId) {
        behaviorGroupResources.setDefaultBehaviorGroup(bundleId, behaviorGroupId).await().indefinitely();
    }

    private List<BehaviorGroup> findBehaviorGroupsByBundleId(UUID bundleId) {
        return behaviorGroupResources.findByBundleId(ACCOUNT_ID, bundleId).await().indefinitely();
    }

    private Boolean addEventTypeBehavior(String accountId, UUID eventTypeId, UUID behaviorGroupId) {
        return behaviorGroupResources.addEventTypeBehavior(accountId, eventTypeId, behaviorGroupId).await().indefinitely();
    }

    private List<EventTypeBehavior> findEventTypeBehaviorByEventTypeId(UUID eventTypeId) {
        String query = "FROM EventTypeBehavior WHERE eventType.id = :eventTypeId";
        return session.createQuery(query, EventTypeBehavior.class)
                .setParameter("eventTypeId", eventTypeId)
                .getResultList()
                .await().indefinitely();
    }

    private Boolean deleteEventTypeBehavior(String accountId, UUID eventTypeId, UUID behaviorGroupId) {
        return behaviorGroupResources.deleteEventTypeBehavior(accountId, eventTypeId, behaviorGroupId).await().indefinitely();
    }

    private Boolean muteEventType(String accountId, UUID eventTypeId) {
        return behaviorGroupResources.muteEventType(accountId, eventTypeId).await().indefinitely();
    }

    private List<EventType> findEventTypesByBehaviorGroupId(String accountId, UUID behaviorGroupId) {
        return behaviorGroupResources.findEventTypesByBehaviorGroupId(accountId, behaviorGroupId).await().indefinitely();
    }

    private List<BehaviorGroup> findBehaviorGroupsByEventTypeId(String accountId, UUID eventTypeId) {
        return behaviorGroupResources.findBehaviorGroupsByEventTypeId(accountId, eventTypeId, new Query()).await().indefinitely();
    }

    private void updateAndCheckBehaviorGroupActions(String accountId, UUID bundleId, UUID behaviorGroupId, boolean expectedResult, UUID... endpointIds) {
        Boolean updated = behaviorGroupResources.updateBehaviorGroupActions(accountId, behaviorGroupId, Arrays.asList(endpointIds)).await().indefinitely();
        // Is the update result the one we expected?
        assertEquals(expectedResult, updated);
        if (expectedResult) {
            session.clear(); // We need to clear the session L1 cache before checking the update result.
            // If we expected a success, the behavior group actions should match exactly the given endpoint IDs.
            List<BehaviorGroupAction> actions = findBehaviorGroupActions(accountId, bundleId, behaviorGroupId);
            assertEquals(endpointIds.length, actions.size());
            for (int i = 0; i < endpointIds.length; i++) {
                assertEquals(endpointIds[i], actions.get(i).getEndpoint().getId());
            }
        }
    }

    private List<BehaviorGroupAction> findBehaviorGroupActions(String accountId, UUID bundleId, UUID behaviorGroupId) {
        List<BehaviorGroup> behaviorGroups = behaviorGroupResources.findByBundleId(accountId, bundleId).await().indefinitely();
        assertEquals(1, behaviorGroups.size());
        return behaviorGroups
                .stream().filter(behaviorGroup -> behaviorGroup.getId().equals(behaviorGroupId))
                .findFirst().get().getActions();
    }
}
