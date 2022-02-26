package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.BehaviorGroupAction;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeBehavior;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import javax.validation.ConstraintViolationException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response.Status;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class BehaviorGroupResourcesTest extends DbIsolatedTest {

    @Inject
    EntityManager entityManager;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    BehaviorGroupResources behaviorGroupResources;

    // A new instance is automatically created by JUnit before each test is executed.
    private ModelInstancesHolder model = new ModelInstancesHolder();

    @Test
    void testCreateAndUpdateAndDeleteBehaviorGroup() {
        String newDisplayName = "newDisplayName";

        model.bundles.add(resourceHelpers.createBundle());

        // Create behavior group.
        model.behaviorGroups.add(resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName", model.bundles.get(0).getId()));
        List<BehaviorGroup> behaviorGroups = behaviorGroupResources.findByBundleId(DEFAULT_ACCOUNT_ID, model.bundles.get(0).getId());
        assertEquals(1, behaviorGroups.size());
        assertEquals(model.behaviorGroups.get(0), behaviorGroups.get(0));
        assertEquals(model.behaviorGroups.get(0).getDisplayName(), behaviorGroups.get(0).getDisplayName());
        assertEquals(model.bundles.get(0).getId(), behaviorGroups.get(0).getBundle().getId());
        assertNotNull(model.bundles.get(0).getCreated());

        // Update behavior group.
        assertTrue(updateBehaviorGroup(model.behaviorGroups.get(0).getId(), newDisplayName));
        entityManager.clear(); // We need to clear the session L1 cache before checking the update result.

        behaviorGroups = behaviorGroupResources.findByBundleId(DEFAULT_ACCOUNT_ID, model.bundles.get(0).getId());
        assertEquals(1, behaviorGroups.size());
        assertEquals(model.behaviorGroups.get(0).getId(), behaviorGroups.get(0).getId());
        assertEquals(newDisplayName, behaviorGroups.get(0).getDisplayName());
        assertEquals(model.bundles.get(0).getId(), behaviorGroups.get(0).getBundle().getId());

        // Delete behavior group.
        assertTrue(resourceHelpers.deleteBehaviorGroup(model.behaviorGroups.get(0).getId()));

        behaviorGroups = behaviorGroupResources.findByBundleId(DEFAULT_ACCOUNT_ID, model.bundles.get(0).getId());
        assertTrue(behaviorGroups.isEmpty());
    }

    @Test
    void testCreateAndUpdateAndDeleteDefaultBehaviorGroup() {
        String newDisplayName = "newDisplayName";

        model.bundles.add(resourceHelpers.createBundle());

        // Create behavior group.
        model.behaviorGroups.add(resourceHelpers.createDefaultBehaviorGroup("displayName", model.bundles.get(0).getId()));

        List<BehaviorGroup> behaviorGroups = behaviorGroupResources.findByBundleId(DEFAULT_ACCOUNT_ID, model.bundles.get(0).getId());
        assertEquals(1, behaviorGroups.size());
        assertEquals(model.behaviorGroups.get(0), behaviorGroups.get(0));
        assertEquals(model.behaviorGroups.get(0).getDisplayName(), behaviorGroups.get(0).getDisplayName());
        assertEquals(model.bundles.get(0).getId(), behaviorGroups.get(0).getBundle().getId());
        assertNotNull(model.bundles.get(0).getCreated());

        // Update behavior group.
        assertTrue(updateDefaultBehaviorGroup(model.behaviorGroups.get(0).getId(), newDisplayName));
        entityManager.clear(); // We need to clear the session L1 cache before checking the update result.

        behaviorGroups = behaviorGroupResources.findByBundleId(DEFAULT_ACCOUNT_ID, model.bundles.get(0).getId());
        assertEquals(1, behaviorGroups.size());
        assertEquals(model.behaviorGroups.get(0).getId(), behaviorGroups.get(0).getId());
        assertEquals(newDisplayName, behaviorGroups.get(0).getDisplayName());
        assertEquals(model.bundles.get(0).getId(), behaviorGroups.get(0).getBundle().getId());

        // Delete behavior group.
        assertTrue(resourceHelpers.deleteDefaultBehaviorGroup(model.behaviorGroups.get(0).getId()));

        behaviorGroups = behaviorGroupResources.findByBundleId(DEFAULT_ACCOUNT_ID, model.bundles.get(0).getId());
        assertTrue(behaviorGroups.isEmpty());
    }

    @Test
    void testCreateBehaviorGroupWithNullDisplayName() {
        createBehaviorGroupWithIllegalDisplayName(null);
    }

    @Test
    void testCreateBehaviorGroupWithEmptyDisplayName() {
        createBehaviorGroupWithIllegalDisplayName("");
    }

    @Test
    void testCreateBehaviorGroupWithBlankDisplayName() {
        createBehaviorGroupWithIllegalDisplayName(" ");
    }

    @Test
    void testCreateBehaviorGroupWithNullBundleId() {
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> {
            resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName", null);
        });
        assertTrue(Pattern.compile("property path: [a-zA-Z0-9.]+bundleId").matcher(e.getMessage()).find());
    }

    @Test
    void testCreateBehaviorGroupWithUnknownBundleId() {
        NotFoundException e = assertThrows(NotFoundException.class, () -> {
            resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName", UUID.randomUUID());
        });
        assertEquals("bundle_id not found", e.getMessage());
    }

    @Test
    void testfindByBundleIdOrdering() {
        model.bundles.add(resourceHelpers.createBundle());
        model.behaviorGroups.add(resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName", model.bundles.get(0).getId()));
        model.behaviorGroups.add(resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName", model.bundles.get(0).getId()));
        model.behaviorGroups.add(resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName", model.bundles.get(0).getId()));
        List<BehaviorGroup> behaviorGroups = behaviorGroupResources.findByBundleId(DEFAULT_ACCOUNT_ID, model.bundles.get(0).getId());
        assertEquals(3, behaviorGroups.size());
        // Behavior groups should be sorted on descending creation date.
        assertEquals(model.behaviorGroups.get(2), behaviorGroups.get(0));
        assertEquals(model.behaviorGroups.get(1), behaviorGroups.get(1));
        assertEquals(model.behaviorGroups.get(0), behaviorGroups.get(2));
    }

    @Test
    void testAddAndDeleteEventTypeBehavior() {
        model.bundles.add(resourceHelpers.createBundle());
        model.applications.add(resourceHelpers.createApplication(model.bundles.get(0).getId()));
        model.eventTypes.add(createEventType(model.applications.get(0).getId()));
        model.behaviorGroups.add(resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "Behavior group 1", model.bundles.get(0).getId()));
        model.behaviorGroups.add(resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "Behavior group 2", model.bundles.get(0).getId()));
        model.behaviorGroups.add(resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "Behavior group 3", model.bundles.get(0).getId()));
        updateAndCheckEventTypeBehaviors(DEFAULT_ACCOUNT_ID, model.eventTypes.get(0).getId(), true, model.behaviorGroups.get(0).getId());
        updateAndCheckEventTypeBehaviors(DEFAULT_ACCOUNT_ID, model.eventTypes.get(0).getId(), true, model.behaviorGroups.get(0).getId());
        updateAndCheckEventTypeBehaviors(DEFAULT_ACCOUNT_ID, model.eventTypes.get(0).getId(), true, model.behaviorGroups.get(0).getId(), model.behaviorGroups.get(1).getId());
        updateAndCheckEventTypeBehaviors(DEFAULT_ACCOUNT_ID, model.eventTypes.get(0).getId(), true, model.behaviorGroups.get(1).getId());
        updateAndCheckEventTypeBehaviors(DEFAULT_ACCOUNT_ID, model.eventTypes.get(0).getId(), true, model.behaviorGroups.get(0).getId(), model.behaviorGroups.get(1).getId(), model.behaviorGroups.get(2).getId());
        updateAndCheckEventTypeBehaviors(DEFAULT_ACCOUNT_ID, model.eventTypes.get(0).getId(), true);
    }

    @Test
    void testFindEventTypesByBehaviorGroupId() {
        model.bundles.add(resourceHelpers.createBundle());
        model.applications.add(resourceHelpers.createApplication(model.bundles.get(0).getId()));
        model.eventTypes.add(createEventType(model.applications.get(0).getId()));
        model.behaviorGroups.add(resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName", model.bundles.get(0).getId()));
        updateAndCheckEventTypeBehaviors(DEFAULT_ACCOUNT_ID, model.eventTypes.get(0).getId(), true, model.behaviorGroups.get(0).getId());
        List<EventType> eventTypes = resourceHelpers.findEventTypesByBehaviorGroupId(model.behaviorGroups.get(0).getId());
        assertEquals(1, eventTypes.size());
        assertEquals(model.eventTypes.get(0).getId(), eventTypes.get(0).getId());
    }

    @Test
    void testFindBehaviorGroupsByEventTypeId() {
        model.bundles.add(resourceHelpers.createBundle());
        model.applications.add(resourceHelpers.createApplication(model.bundles.get(0).getId()));
        model.eventTypes.add(createEventType(model.applications.get(0).getId()));
        model.behaviorGroups.add(resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName", model.bundles.get(0).getId()));
        updateAndCheckEventTypeBehaviors(DEFAULT_ACCOUNT_ID, model.eventTypes.get(0).getId(), true, model.behaviorGroups.get(0).getId());
        List<BehaviorGroup> behaviorGroups = resourceHelpers.findBehaviorGroupsByEventTypeId(model.eventTypes.get(0).getId());
        assertEquals(1, behaviorGroups.size());
        assertEquals(model.behaviorGroups.get(0).getId(), behaviorGroups.get(0).getId());
    }

    @Test
    void testAddAndDeleteBehaviorGroupAction() {
        model.bundles.add(resourceHelpers.createBundle());
        model.behaviorGroups.add(resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "Behavior group 1", model.bundles.get(0).getId()));
        model.behaviorGroups.add(resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "Behavior group 2", model.bundles.get(0).getId()));
        model.endpoints.add(resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, WEBHOOK));
        model.endpoints.add(resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, WEBHOOK));
        model.endpoints.add(resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, WEBHOOK));

        // At the beginning of the test, endpoint1 shouldn't be linked with any behavior group.
        findBehaviorGroupsByEndpointId(model.endpoints.get(0).getId());
        updateAndCheckBehaviorGroupActions(DEFAULT_ACCOUNT_ID, model.bundles.get(0).getId(), model.behaviorGroups.get(0).getId(), OK, model.endpoints.get(0).getId());
        updateAndCheckBehaviorGroupActions(DEFAULT_ACCOUNT_ID, model.bundles.get(0).getId(), model.behaviorGroups.get(0).getId(), OK, model.endpoints.get(0).getId());
        updateAndCheckBehaviorGroupActions(DEFAULT_ACCOUNT_ID, model.bundles.get(0).getId(), model.behaviorGroups.get(0).getId(), OK, model.endpoints.get(0).getId(), model.endpoints.get(1).getId());

        // Now, endpoint1 should be linked with behaviorGroup1.
        findBehaviorGroupsByEndpointId(model.endpoints.get(0).getId(), model.behaviorGroups.get(0).getId());
        updateAndCheckBehaviorGroupActions(DEFAULT_ACCOUNT_ID, model.bundles.get(0).getId(), model.behaviorGroups.get(1).getId(), OK, model.endpoints.get(0).getId());

        // Then, endpoint1 should be linked with both behavior groups.
        findBehaviorGroupsByEndpointId(model.endpoints.get(0).getId(), model.behaviorGroups.get(0).getId(), model.behaviorGroups.get(1).getId());
        updateAndCheckBehaviorGroupActions(DEFAULT_ACCOUNT_ID, model.bundles.get(0).getId(), model.behaviorGroups.get(0).getId(), OK, model.endpoints.get(1).getId());
        updateAndCheckBehaviorGroupActions(DEFAULT_ACCOUNT_ID, model.bundles.get(0).getId(), model.behaviorGroups.get(0).getId(), OK, model.endpoints.get(2).getId(), model.endpoints.get(1).getId(), model.endpoints.get(0).getId());
        updateAndCheckBehaviorGroupActions(DEFAULT_ACCOUNT_ID, model.bundles.get(0).getId(), model.behaviorGroups.get(0).getId(), OK);

        // The link between endpoint1 and behaviorGroup1 was removed. Let's check it is still linked with behaviorGroup2.
        findBehaviorGroupsByEndpointId(model.endpoints.get(0).getId(), model.behaviorGroups.get(1).getId());
    }

    @Test
    void testAddMultipleEmailSubscriptionBehaviorGroupActions() {
        model.bundles.add(resourceHelpers.createBundle());
        model.behaviorGroups.add(resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName", model.bundles.get(0).getId()));
        model.endpoints.add(resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, EMAIL_SUBSCRIPTION));
        model.endpoints.add(resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, EMAIL_SUBSCRIPTION));
        updateAndCheckBehaviorGroupActions(DEFAULT_ACCOUNT_ID, model.bundles.get(0).getId(), model.behaviorGroups.get(0).getId(), OK, model.endpoints.get(0).getId(), model.endpoints.get(1).getId());
    }

    @Test
    void testUpdateBehaviorGroupActionsWithWrongAccountId() {
        model.bundles.add(resourceHelpers.createBundle());
        model.behaviorGroups.add(resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, "displayName", model.bundles.get(0).getId()));
        model.endpoints.add(resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, WEBHOOK));
        updateAndCheckBehaviorGroupActions("unknownAccountId", model.bundles.get(0).getId(), model.behaviorGroups.get(0).getId(), NOT_FOUND, model.endpoints.get(0).getId());
    }

    @Transactional
    EventType createEventType(UUID appID) {
        EventType eventType = new EventType();
        eventType.setApplicationId(appID);
        eventType.setName("name");
        eventType.setDisplayName("displayName");
        entityManager.persist(eventType);
        return eventType;
    }

    private void createBehaviorGroupWithIllegalDisplayName(String displayName) {
        model.bundles.add(resourceHelpers.createBundle());
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> {
            resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, displayName, model.bundles.get(0).getId());
        });
        assertTrue(Pattern.compile("property path: [a-zA-Z0-9.]+displayName").matcher(e.getMessage()).find());
    }

    private Boolean updateBehaviorGroup(UUID behaviorGroupId, String displayName) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setId(behaviorGroupId);
        behaviorGroup.setDisplayName(displayName);
        behaviorGroup.setBundleId(UUID.randomUUID()); // This should not have any effect, the bundle is not updatable.
        return resourceHelpers.updateBehaviorGroup(behaviorGroup);
    }

    private Boolean updateDefaultBehaviorGroup(UUID behaviorGroupId, String displayName) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setId(behaviorGroupId);
        behaviorGroup.setDisplayName(displayName);
        behaviorGroup.setBundleId(UUID.randomUUID()); // This should not have any effect, the bundle is not updatable.
        return resourceHelpers.updateDefaultBehaviorGroup(behaviorGroup);
    }

    @Transactional
    void updateAndCheckEventTypeBehaviors(String accountId, UUID eventTypeId, boolean expectedResult, UUID... behaviorGroupIds) {
        boolean updated = behaviorGroupResources.updateEventTypeBehaviors(accountId, eventTypeId, Set.of(behaviorGroupIds));
        // Is the update result the one we expected?
        assertEquals(expectedResult, updated);
        if (expectedResult) {
            entityManager.clear(); // We need to clear the session L1 cache before checking the update result.
            // If we expected a success, the event type behaviors should match in any order the given behavior groups IDs.
            List<EventTypeBehavior> behaviors = findEventTypeBehaviorByEventTypeId(eventTypeId);
            assertEquals(behaviorGroupIds.length, behaviors.size());
            for (UUID behaviorGroupId : behaviorGroupIds) {
                assertEquals(1L, behaviors.stream().filter(behavior -> behavior.getBehaviorGroup().getId().equals(behaviorGroupId)).count());
            }
        }
    }

    private List<EventTypeBehavior> findEventTypeBehaviorByEventTypeId(UUID eventTypeId) {
        String query = "FROM EventTypeBehavior WHERE eventType.id = :eventTypeId";
        return entityManager.createQuery(query, EventTypeBehavior.class)
                .setParameter("eventTypeId", eventTypeId)
                .getResultList();
    }

    @Transactional
    void updateAndCheckBehaviorGroupActions(String accountId, UUID bundleId, UUID behaviorGroupId, Status expectedResult, UUID... endpointIds) {
        Status status = behaviorGroupResources.updateBehaviorGroupActions(accountId, behaviorGroupId, Arrays.asList(endpointIds));
        // Is the update result the one we expected?
        assertEquals(expectedResult, status);
        if (expectedResult == Status.OK) {
            entityManager.clear(); // We need to clear the session L1 cache before checking the update result.
            // If we expected a success, the behavior group actions should match exactly the given endpoint IDs.
            List<BehaviorGroupAction> actions = findBehaviorGroupActions(accountId, bundleId, behaviorGroupId);
            assertEquals(endpointIds.length, actions.size());
            for (int i = 0; i < endpointIds.length; i++) {
                assertEquals(endpointIds[i], actions.get(i).getEndpoint().getId());
            }
        }
    }

    private List<BehaviorGroupAction> findBehaviorGroupActions(String accountId, UUID bundleId, UUID behaviorGroupId) {
        return behaviorGroupResources.findByBundleId(accountId, bundleId)
                .stream().filter(behaviorGroup -> behaviorGroup.getId().equals(behaviorGroupId))
                .findFirst().get().getActions();
    }

    private void findBehaviorGroupsByEndpointId(UUID endpointId, UUID... expectedBehaviorGroupIds) {
        List<BehaviorGroup> behaviorGroups = resourceHelpers.findBehaviorGroupsByEndpointId(endpointId);
        List<UUID> actualBehaviorGroupIds = behaviorGroups.stream().map(BehaviorGroup::getId).collect(Collectors.toList());
        assertEquals(expectedBehaviorGroupIds.length, actualBehaviorGroupIds.size());
        assertTrue(actualBehaviorGroupIds.containsAll(Arrays.asList(expectedBehaviorGroupIds)));
    }
}
