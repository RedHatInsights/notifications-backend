package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.BehaviorGroupAction;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeBehavior;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.models.EndpointType.DRAWER;
import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class BehaviorGroupRepositoryTest extends DbIsolatedTest {

    private final String NOT_USED = "not-used";

    @Inject
    EntityManager entityManager;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    BehaviorGroupRepository behaviorGroupRepository;

    @InjectMock
    BackendConfig backendConfig;

    @Test
    void shouldThrowExceptionWhenCreatingWithExistingDisplayNameAndSameOrgId() {
        when(backendConfig.isUniqueBgNameEnabled()).thenReturn(true);
        Bundle bundle = resourceHelpers.createBundle();
        BehaviorGroup behaviorGroup1 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "displayName", bundle.getId());

        BehaviorGroup behaviorGroup2 = new BehaviorGroup();
        behaviorGroup2.setAccountId(behaviorGroup1.getAccountId());
        behaviorGroup2.setOrgId(behaviorGroup1.getOrgId());
        behaviorGroup2.setDisplayName(behaviorGroup1.getDisplayName());
        behaviorGroup2.setBundleId(bundle.getId());

        BadRequestException e = assertThrows(BadRequestException.class, () -> {
            behaviorGroupRepository.create(behaviorGroup2.getAccountId(), behaviorGroup2.getOrgId(), behaviorGroup2);
        });
        assertEquals("A behavior group with display name [" + behaviorGroup1.getDisplayName() + "] already exists", e.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenCreatingDefaultWithExistingDisplayName() {
        when(backendConfig.isUniqueBgNameEnabled()).thenReturn(true);
        Bundle bundle = resourceHelpers.createBundle();
        BehaviorGroup behaviorGroup1 = resourceHelpers.createDefaultBehaviorGroup("displayName", bundle.getId());

        BehaviorGroup behaviorGroup2 = new BehaviorGroup();
        behaviorGroup2.setDisplayName(behaviorGroup1.getDisplayName());
        behaviorGroup2.setBundleId(bundle.getId());

        BadRequestException e = assertThrows(BadRequestException.class, () -> {
            behaviorGroupRepository.createDefault(behaviorGroup2);
        });
        assertEquals("A behavior group with display name [" + behaviorGroup1.getDisplayName() + "] already exists", e.getMessage());
    }

    @Test
    void shouldNotThrowExceptionWhenCreatingWithExistingDisplayNameButDifferentOrgId() {
        Bundle bundle = resourceHelpers.createBundle();
        BehaviorGroup behaviorGroup1 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "displayName", bundle.getId());

        BehaviorGroup behaviorGroup2 = new BehaviorGroup();
        behaviorGroup2.setAccountId("other-account-id");
        behaviorGroup2.setOrgId("other-org-id");
        behaviorGroup2.setDisplayName(behaviorGroup1.getDisplayName());
        behaviorGroup2.setBundleId(bundle.getId());

        behaviorGroupRepository.create(behaviorGroup2.getAccountId(), behaviorGroup2.getOrgId(), behaviorGroup2);
    }

    @Test
    void shouldThrowExceptionWhenUpdatingToExistingDisplayNameAndSameOrgId() {
        when(backendConfig.isUniqueBgNameEnabled()).thenReturn(true);
        Bundle bundle = resourceHelpers.createBundle("name", "displayName");
        BehaviorGroup behaviorGroup1 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "displayName1", bundle.getId());
        BehaviorGroup behaviorGroup2 = resourceHelpers.createBehaviorGroup(behaviorGroup1.getAccountId(), behaviorGroup1.getOrgId(), "displayName2", bundle.getId());
        behaviorGroup2.setDisplayName(behaviorGroup1.getDisplayName());

        BadRequestException e = assertThrows(BadRequestException.class, () -> {
            behaviorGroupRepository.update(behaviorGroup2.getOrgId(), behaviorGroup2);
        });
        assertEquals("A behavior group with display name [" + behaviorGroup1.getDisplayName() + "] already exists", e.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenUpdatingDefaultToExistingDisplayName() {
        when(backendConfig.isUniqueBgNameEnabled()).thenReturn(true);
        Bundle bundle = resourceHelpers.createBundle("name", "displayName");
        BehaviorGroup behaviorGroup1 = resourceHelpers.createDefaultBehaviorGroup("displayName1", bundle.getId());
        BehaviorGroup behaviorGroup2 = resourceHelpers.createDefaultBehaviorGroup("displayName2", bundle.getId());
        behaviorGroup2.setDisplayName(behaviorGroup1.getDisplayName());

        BadRequestException e = assertThrows(BadRequestException.class, () -> {
            behaviorGroupRepository.updateDefault(behaviorGroup2);
        });
        assertEquals("A behavior group with display name [" + behaviorGroup1.getDisplayName() + "] already exists", e.getMessage());
    }

    @Test
    void shouldNotThrowExceptionWhenUpdatingToExistingDisplayNameButDifferentOrgId() {
        Bundle bundle = resourceHelpers.createBundle("name", "displayName");
        BehaviorGroup behaviorGroup1 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "displayName1", bundle.getId());
        BehaviorGroup behaviorGroup2 = resourceHelpers.createBehaviorGroup("other-account-id", "other-org-id", "displayName2", bundle.getId());
        behaviorGroup2.setDisplayName(behaviorGroup1.getDisplayName());
        behaviorGroupRepository.update(behaviorGroup2.getOrgId(), behaviorGroup2);
    }

    @Test
    void shouldNotThrowExceptionWhenSelfUpdatingWithSameDisplayName() {
        Bundle bundle = resourceHelpers.createBundle("name", "displayName");
        BehaviorGroup behaviorGroup = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "displayName1", bundle.getId());
        behaviorGroupRepository.update(behaviorGroup.getOrgId(), behaviorGroup);
    }

    @Test
    void shouldNotThrowExceptionWhenSelfUpdatingDefaultWithSameDisplayName() {
        Bundle bundle = resourceHelpers.createBundle("name", "displayName");
        BehaviorGroup behaviorGroup = resourceHelpers.createDefaultBehaviorGroup("displayName1", bundle.getId());
        behaviorGroupRepository.updateDefault(behaviorGroup);
    }

    @Test
    void testCreateAndUpdateAndDeleteBehaviorGroup() {
        String newDisplayName = "newDisplayName";

        Bundle bundle = resourceHelpers.createBundle();

        // Create behavior group.
        BehaviorGroup behaviorGroup = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "displayName", bundle.getId());
        List<BehaviorGroup> behaviorGroups = behaviorGroupRepository.findByBundleId(DEFAULT_ORG_ID, bundle.getId());
        assertEquals(1, behaviorGroups.size());
        assertEquals(behaviorGroup, behaviorGroups.get(0));
        assertEquals(behaviorGroup.getDisplayName(), behaviorGroups.get(0).getDisplayName());
        assertEquals(bundle.getId(), behaviorGroups.get(0).getBundle().getId());
        assertNotNull(bundle.getCreated());

        // Update behavior group.
        assertDoesNotThrow(() -> updateBehaviorGroup(behaviorGroup.getId(), newDisplayName));
        entityManager.clear(); // We need to clear the session L1 cache before checking the update result.

        behaviorGroups = behaviorGroupRepository.findByBundleId(DEFAULT_ORG_ID, bundle.getId());
        assertEquals(1, behaviorGroups.size());
        assertEquals(behaviorGroup.getId(), behaviorGroups.get(0).getId());
        assertEquals(newDisplayName, behaviorGroups.get(0).getDisplayName());
        assertEquals(bundle.getId(), behaviorGroups.get(0).getBundle().getId());

        // Delete behavior group.
        assertTrue(resourceHelpers.deleteBehaviorGroup(behaviorGroup.getId()));

        behaviorGroups = behaviorGroupRepository.findByBundleId(DEFAULT_ORG_ID, bundle.getId());
        assertTrue(behaviorGroups.isEmpty());
    }

    @Test
    void testCreateAndUpdateAndDeleteDefaultBehaviorGroup() {
        String newDisplayName = "newDisplayName";

        Bundle bundle = resourceHelpers.createBundle();

        // Create behavior group.
        BehaviorGroup behaviorGroup = resourceHelpers.createDefaultBehaviorGroup("displayName", bundle.getId());

        List<BehaviorGroup> behaviorGroups = behaviorGroupRepository.findByBundleId(DEFAULT_ORG_ID, bundle.getId());
        assertEquals(1, behaviorGroups.size());
        assertEquals(behaviorGroup, behaviorGroups.get(0));
        assertEquals(behaviorGroup.getDisplayName(), behaviorGroups.get(0).getDisplayName());
        assertEquals(bundle.getId(), behaviorGroups.get(0).getBundle().getId());
        assertNotNull(bundle.getCreated());

        // Update behavior group.
        assertDoesNotThrow(() -> updateDefaultBehaviorGroup(behaviorGroup.getId(), newDisplayName));
        entityManager.clear(); // We need to clear the session L1 cache before checking the update result.

        behaviorGroups = behaviorGroupRepository.findByBundleId(DEFAULT_ORG_ID, bundle.getId());
        assertEquals(1, behaviorGroups.size());
        assertEquals(behaviorGroup.getId(), behaviorGroups.get(0).getId());
        assertEquals(newDisplayName, behaviorGroups.get(0).getDisplayName());
        assertEquals(bundle.getId(), behaviorGroups.get(0).getBundle().getId());

        // Delete behavior group.
        assertTrue(resourceHelpers.deleteDefaultBehaviorGroup(behaviorGroup.getId()));

        behaviorGroups = behaviorGroupRepository.findByBundleId(DEFAULT_ORG_ID, bundle.getId());
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
            resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "displayName", null);
        });
        assertTrue(Pattern.compile("property path: [a-zA-Z0-9.]+bundleId").matcher(e.getMessage()).find());
    }

    @Test
    void testCreateBehaviorGroupWithUnknownBundleId() {
        NotFoundException e = assertThrows(NotFoundException.class, () -> {
            resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "displayName", UUID.randomUUID());
        });
        assertEquals("bundle_id not found", e.getMessage());
    }

    @Test
    void testfindByBundleIdOrdering() {
        Bundle bundle = resourceHelpers.createBundle();
        BehaviorGroup behaviorGroup1 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "displayName1", bundle.getId());
        BehaviorGroup behaviorGroup2 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "displayName2", bundle.getId());
        BehaviorGroup behaviorGroup3 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "displayName3", bundle.getId());
        List<BehaviorGroup> behaviorGroups = behaviorGroupRepository.findByBundleId(DEFAULT_ORG_ID, bundle.getId());
        assertEquals(3, behaviorGroups.size());
        // Behavior groups should be sorted on descending creation date.
        assertEquals(behaviorGroup3, behaviorGroups.get(0));
        assertEquals(behaviorGroup2, behaviorGroups.get(1));
        assertEquals(behaviorGroup1, behaviorGroups.get(2));
    }

    @Test
    void testAddAndDeleteEventTypeBehavior() {
        Bundle bundle = resourceHelpers.createBundle();
        Application app = resourceHelpers.createApplication(bundle.getId());
        EventType eventType = createEventType(app);
        BehaviorGroup behaviorGroup1 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "Behavior group 1", bundle.getId());
        BehaviorGroup behaviorGroup2 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "Behavior group 2", bundle.getId());
        BehaviorGroup behaviorGroup3 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "Behavior group 3", bundle.getId());
        updateAndCheckEventTypeBehaviors(DEFAULT_ORG_ID, eventType.getId(), behaviorGroup1.getId());
        updateAndCheckEventTypeBehaviors(DEFAULT_ORG_ID, eventType.getId(), behaviorGroup1.getId());
        updateAndCheckEventTypeBehaviors(DEFAULT_ORG_ID, eventType.getId(), behaviorGroup1.getId(), behaviorGroup2.getId());
        updateAndCheckEventTypeBehaviors(DEFAULT_ORG_ID, eventType.getId(), behaviorGroup2.getId());
        updateAndCheckEventTypeBehaviors(DEFAULT_ORG_ID, eventType.getId(), behaviorGroup1.getId(), behaviorGroup2.getId(), behaviorGroup3.getId());
        updateAndCheckEventTypeBehaviors(DEFAULT_ORG_ID, eventType.getId());
    }

    @Test
    void testAddEventTypeBehaviorWithBundleIntegrityCheckFailure() {
        /*
         * Bundle 1 objects hierarchy.
         */
        Bundle bundle1 = resourceHelpers.createBundle("bundle-1", "Bundle 1");
        Application app = resourceHelpers.createApplication(bundle1.getId());
        // 'eventType' is a child of 'bundle1'.
        EventType eventType = createEventType(app);
        // 'behaviorGroup1' is a child of 'bundle1'.
        BehaviorGroup behaviorGroup1 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "Behavior group 1", bundle1.getId());

        /*
         * Bundle 2 objects hierarchy.
         */
        Bundle bundle2 = resourceHelpers.createBundle("bundle-2", "Bundle 2");
        // 'behaviorGroup2' is a child of 'bundle2'.
        BehaviorGroup behaviorGroup2 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "Behavior group 2", bundle2.getId());

        // 'behaviorGroup2' should not be added to 'eventType' behaviors because it comes from a different bundle.
        BadRequestException e = assertThrows(BadRequestException.class, () -> {
            behaviorGroupRepository.updateEventTypeBehaviors(DEFAULT_ORG_ID, eventType.getId(), Set.of(behaviorGroup1.getId(), behaviorGroup2.getId()));
        });
        assertTrue(e.getMessage().startsWith("Some behavior groups can't be linked"));
    }

    @Test
    void testFindEventTypesByBehaviorGroupId() {
        Bundle bundle = resourceHelpers.createBundle();
        Application app = resourceHelpers.createApplication(bundle.getId());
        EventType eventType = createEventType(app);
        BehaviorGroup behaviorGroup = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "displayName", bundle.getId());
        updateAndCheckEventTypeBehaviors(DEFAULT_ORG_ID, eventType.getId(), behaviorGroup.getId());
        List<EventType> eventTypes = resourceHelpers.findEventTypesByBehaviorGroupId(behaviorGroup.getId());
        assertEquals(1, eventTypes.size());
        assertEquals(eventType.getId(), eventTypes.get(0).getId());
    }

    @Test
    void testFindBehaviorGroupsByEventTypeId() {
        Bundle bundle = resourceHelpers.createBundle();
        Application app = resourceHelpers.createApplication(bundle.getId());
        EventType eventType = createEventType(app);
        BehaviorGroup behaviorGroup = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "displayName", bundle.getId());
        updateAndCheckEventTypeBehaviors(DEFAULT_ORG_ID, eventType.getId(), behaviorGroup.getId());
        List<BehaviorGroup> behaviorGroups = resourceHelpers.findBehaviorGroupsByEventTypeId(eventType.getId());
        assertEquals(1, behaviorGroups.size());
        assertEquals(behaviorGroup.getId(), behaviorGroups.get(0).getId());
    }

    @Test
    void testAddAndDeleteBehaviorGroupAction() {
        Bundle bundle = resourceHelpers.createBundle();
        BehaviorGroup behaviorGroup1 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "Behavior group 1", bundle.getId());
        BehaviorGroup behaviorGroup2 = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "Behavior group 2", bundle.getId());
        Endpoint endpoint1 = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, WEBHOOK);
        Endpoint endpoint2 = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, WEBHOOK);
        Endpoint endpoint3 = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, WEBHOOK);

        // At the beginning of the test, endpoint1 shouldn't be linked with any behavior group.
        findBehaviorGroupsByEndpointId(endpoint1.getId());
        updateAndCheckBehaviorGroupActions(DEFAULT_ORG_ID, bundle.getId(), behaviorGroup1.getId(), endpoint1.getId());
        updateAndCheckBehaviorGroupActions(DEFAULT_ORG_ID, bundle.getId(), behaviorGroup1.getId(), endpoint1.getId());
        updateAndCheckBehaviorGroupActions(DEFAULT_ORG_ID, bundle.getId(), behaviorGroup1.getId(), endpoint1.getId(), endpoint2.getId());

        // Now, endpoint1 should be linked with behaviorGroup1.
        findBehaviorGroupsByEndpointId(endpoint1.getId(), behaviorGroup1.getId());
        updateAndCheckBehaviorGroupActions(DEFAULT_ORG_ID, bundle.getId(), behaviorGroup2.getId(), endpoint1.getId());

        // Then, endpoint1 should be linked with both behavior groups.
        findBehaviorGroupsByEndpointId(endpoint1.getId(), behaviorGroup1.getId(), behaviorGroup2.getId());
        updateAndCheckBehaviorGroupActions(DEFAULT_ORG_ID, bundle.getId(), behaviorGroup1.getId(), endpoint2.getId());
        updateAndCheckBehaviorGroupActions(DEFAULT_ORG_ID, bundle.getId(), behaviorGroup1.getId(), endpoint3.getId(), endpoint2.getId(), endpoint1.getId());
        updateAndCheckBehaviorGroupActions(DEFAULT_ORG_ID, bundle.getId(), behaviorGroup1.getId());

        // The link between endpoint1 and behaviorGroup1 was removed. Let's check it is still linked with behaviorGroup2.
        findBehaviorGroupsByEndpointId(endpoint1.getId(), behaviorGroup2.getId());
    }

    @Test
    void testAddMultipleEmailSubscriptionBehaviorGroupActions() {
        Bundle bundle = resourceHelpers.createBundle();
        BehaviorGroup behaviorGroup = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "displayName", bundle.getId());
        Endpoint endpoint1 = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EMAIL_SUBSCRIPTION);
        Endpoint endpoint2 = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EMAIL_SUBSCRIPTION);
        updateAndCheckBehaviorGroupActions(DEFAULT_ORG_ID, bundle.getId(), behaviorGroup.getId(), endpoint1.getId(), endpoint2.getId());
    }

    @Test
    void testAddMultipleDrawerSubscriptionBehaviorGroupActions() {
        Bundle bundle = resourceHelpers.createBundle();
        BehaviorGroup behaviorGroup = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "displayName", bundle.getId());
        Endpoint endpoint1 = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DRAWER);
        Endpoint endpoint2 = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DRAWER);
        updateAndCheckBehaviorGroupActions(DEFAULT_ORG_ID, bundle.getId(), behaviorGroup.getId(), endpoint1.getId(), endpoint2.getId());
    }

    @Test
    void testUpdateBehaviorGroupActionsWithWrongOrgId() {
        Bundle bundle = resourceHelpers.createBundle();
        BehaviorGroup behaviorGroup = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "displayName", bundle.getId());
        Endpoint endpoint = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, WEBHOOK);
        assertThrows(NotFoundException.class, () -> {
            updateAndCheckBehaviorGroupActions("unknownOrgId", bundle.getId(), behaviorGroup.getId(), endpoint.getId());
        });
    }

    @Test
    void shouldSortByDisplayName() {
        Bundle bundle = resourceHelpers.createBundle();
        Application application = resourceHelpers.createApplication(bundle.getId());
        EventType eventType = resourceHelpers.createEventType(application.getId(), NOT_USED, NOT_USED, NOT_USED);

        for (int i = 0; i < 10; i++) {
            BehaviorGroup behaviorGroup = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, String.format("%d", i), bundle.getId());
            behaviorGroupRepository.updateBehaviorEventTypes(DEFAULT_ORG_ID, behaviorGroup.getId(), Set.of(eventType.getId()));
        }

        TestHelpers.testSorting(
                "display_name",
                query -> behaviorGroupRepository.findBehaviorGroupsByEventTypeId(DEFAULT_ORG_ID, eventType.getId(), query),
                behaviorGroups -> behaviorGroups.stream().map(BehaviorGroup::getDisplayName).collect(Collectors.toList()),
                Query.Sort.Order.ASC,
                IntStream.range(0, 10).boxed().map(Object::toString).collect(Collectors.toList())
        );
    }

    /**
     * Tests that a bad request exception is raised when attempting to create more behavior groups than the allowed
     * maximum number.
     */
    @Test
    void testNotAllowedCreateMoreBehaviorGroups() {
        final Bundle bundle = this.resourceHelpers.createBundle();

        for (long i = 0; i < BehaviorGroupRepository.MAXIMUM_NUMBER_BEHAVIOR_GROUPS; i++) {
            this.resourceHelpers.createBehaviorGroup(
                DEFAULT_ACCOUNT_ID,
                DEFAULT_ORG_ID,
                String.format("display-name-%s", i),
                bundle.getId()
            );
        }

        final BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setAccountId(DEFAULT_ACCOUNT_ID);
        behaviorGroup.setBundleId(bundle.getId());
        behaviorGroup.setDisplayName("behavior-group-should-not-be-created");
        behaviorGroup.setOrgId(DEFAULT_ORG_ID);

        final BadRequestException ex = Assertions.assertThrows(
            BadRequestException.class,
                () -> this.behaviorGroupRepository.create(
                DEFAULT_ACCOUNT_ID,
                DEFAULT_ORG_ID,
                behaviorGroup
            )
        );

        Assertions.assertEquals("behavior group creation limit reached. Please consider deleting unused behavior groups before creating more.", ex.getMessage());
    }

    /**
     * Tests that when a non-existent {@link EventType} UUID is provided, a
     * "Not Found" exception is thrown.
     */
    @Test
    void testDeleteBehaviorGroupFromEventTypeEventTypeNotFound() {
        final Bundle bundle = this.resourceHelpers.createBundle();
        final BehaviorGroup behaviorGroup = this.resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "display-name", bundle.getId());

        // Call the function under test.
        final NotFoundException ex = Assertions.assertThrows(
            NotFoundException.class,
            () -> this.behaviorGroupRepository.deleteBehaviorGroupFromEventType(UUID.randomUUID(), behaviorGroup.getId(), DEFAULT_ORG_ID)
        );

        Assertions.assertEquals("the specified behavior group was not found for the given event type", ex.getMessage(), "unexpected exception message");
    }

    /**
     * Tests that when a non-existent {@link BehaviorGroup} UUID is provided, a
     * "Not Found" exception is thrown.
     */
    @Test
    void testDeleteBehaviorGroupFromEventTypeBehaviorGroupNotFound() {
        final Bundle bundle = this.resourceHelpers.createBundle();

        final Application application = this.resourceHelpers.createApplication(bundle.getId());
        final EventType eventType = this.resourceHelpers.createEventType(application.getId(), "name", "display-name", "description");

        // Call the function under test.
        final NotFoundException ex = Assertions.assertThrows(
            NotFoundException.class,
            () -> this.behaviorGroupRepository.deleteBehaviorGroupFromEventType(eventType.getId(), UUID.randomUUID(), DEFAULT_ORG_ID)
        );

        Assertions.assertEquals("the specified behavior group was not found for the given event type", ex.getMessage(), "unexpected exception message");
    }

    /**
     * Tests that when both the {@link EventType} and the {@link BehaviorGroup}
     * exist, but when they are not linked together, a not found exception is
     * thrown.
     */
    @Test
    void testDeleteBehaviorGroupFromEventTypeNotFound() {
        final Bundle bundle = this.resourceHelpers.createBundle();
        final BehaviorGroup behaviorGroup = this.resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "display-name", bundle.getId());

        final Application application = this.resourceHelpers.createApplication(bundle.getId());
        final EventType eventType = this.resourceHelpers.createEventType(application.getId(), "name", "display-name", "description");

        // Call the function under test.
        final NotFoundException ex = Assertions.assertThrows(
            NotFoundException.class,
            () -> this.behaviorGroupRepository.deleteBehaviorGroupFromEventType(eventType.getId(), behaviorGroup.getId(), DEFAULT_ORG_ID)
        );

        Assertions.assertEquals("the specified behavior group was not found for the given event type", ex.getMessage(), "unexpected exception message");
    }

    /**
     * Tests that when a {@link BehaviorGroup} is removed from the behavior
     * groups collection of an @{@link EventType}, no error is raised.
     */
    @Test
    void testDeleteBehaviorGroupFromEventType() {
        final Bundle bundle = this.resourceHelpers.createBundle();
        final BehaviorGroup behaviorGroup = this.resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "display-name", bundle.getId());

        final Application application = this.resourceHelpers.createApplication(bundle.getId());
        final EventType eventType = this.resourceHelpers.createEventType(application.getId(), "name", "display-name", "description");

        // First insert the behavior group as part of the even type's behavior groups.
        this.behaviorGroupRepository.updateEventTypeBehaviors(DEFAULT_ORG_ID, eventType.getId(), Set.of(behaviorGroup.getId()));

        // Then call the function under test.
        this.behaviorGroupRepository.deleteBehaviorGroupFromEventType(eventType.getId(), behaviorGroup.getId(), DEFAULT_ORG_ID);
    }

    /**
     * Tests that when a non-existing UUID is given for an event type, a
     * {@link NotFoundException} is raised with the proper error message.
     */
    @Test
    void testAppendBehaviorGroupEventTypeNotExists() {
        final Bundle bundle = this.resourceHelpers.createBundle();
        final BehaviorGroup behaviorGroup = this.resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "behavior-group-display-name", bundle.getId());

        final NotFoundException exception = Assertions.assertThrows(
            NotFoundException.class,
            () -> this.behaviorGroupRepository.appendBehaviorGroupToEventType(
                DEFAULT_ORG_ID,
                behaviorGroup.getId(),
                UUID.randomUUID()
            )
        );

        Assertions.assertEquals("the specified behavior group doesn't exist or the specified event type doesn't belong to the same bundle as the behavior group", exception.getMessage(), "unexpected exception received for a non existing behavior group");
    }

    /**
     * Tests that when an existing UUID is given for a behavior group, but with
     * a different organization ID with the one expected, which simulates a
     * different tenant, a {@link NotFoundException} is raised with the proper
     * error message.
     */
    @Test
    void testAppendBehaviorGroupEventTypeNotExistsOrgId() {
        final Bundle bundle = this.resourceHelpers.createBundle();
        final BehaviorGroup behaviorGroup = this.resourceHelpers.createBehaviorGroup("account-id", "org-id", "display-name", bundle.getId());

        final NotFoundException exception = Assertions.assertThrows(
            NotFoundException.class,
            () -> this.behaviorGroupRepository.appendBehaviorGroupToEventType(
                UUID.randomUUID().toString(),
                behaviorGroup.getId(),
                UUID.randomUUID()
            )
        );

        Assertions.assertEquals("the specified behavior group doesn't exist or the specified event type doesn't belong to the same bundle as the behavior group", exception.getMessage(), "unexpected exception received for a non existing behavior group");
    }

    /**
     * Tests that a {@link NotFoundException} is returned when a behavior group
     * that doesn't share a bundle with an event type is tried to be appended
     * to the latter.
     */
    @Test
    void testAppendBehaviorGroupEventTypeNotCompatible() {
        final String orgId = "append-behavior-group-event-type-not-compatible";

        // Create the application and event types belonging to one bundle...
        final Bundle bundle = this.resourceHelpers.createBundle();
        final Application application = this.resourceHelpers.createApplication(bundle.getId());
        final EventType eventType = this.resourceHelpers.createEventType(application.getId(), "name", "display-name", "description");

        // ... and a behavior group for a completely different bundle!
        final Bundle differentBundle = this.resourceHelpers.createBundle("different-name", "different-display-name");
        final BehaviorGroup behaviorGroup = this.resourceHelpers.createBehaviorGroup("account-id", orgId, "display-name", differentBundle.getId());

        final NotFoundException exception = Assertions.assertThrows(
            NotFoundException.class,
            () -> this.behaviorGroupRepository.appendBehaviorGroupToEventType(
                orgId,
                behaviorGroup.getId(),
                eventType.getId()
            )
        );

        Assertions.assertEquals("the specified behavior group doesn't exist or the specified event type doesn't belong to the same bundle as the behavior group", exception.getMessage(), "unexpected exception received for a non existing behavior group");
    }

    /**
     * Tests that appending a behavior group to an event type inserts the join
     * row in the join table.
     */
    @Test
    void testAppendBehaviorGroupToEventType() {
        final String orgId = "append-behavior-group-event-type";
        final String name = String.format("%s-name", orgId);
        final String displayName = String.format("%s-display-name", orgId);
        final String description = String.format("%s-description", orgId);

        final Bundle bundle = this.resourceHelpers.createBundle();
        final Application application = this.resourceHelpers.createApplication(bundle.getId());
        final EventType eventType = this.resourceHelpers.createEventType(application.getId(), name, displayName, description);
        final BehaviorGroup behaviorGroup = this.resourceHelpers.createBehaviorGroup("account-id", orgId, displayName, bundle.getId());

        // Call the function under test.
        this.behaviorGroupRepository.appendBehaviorGroupToEventType(
            orgId,
            behaviorGroup.getId(),
            eventType.getId()
        );

        final String checkInsertionQuery =
            "SELECT " +
                "1 " +
            "FROM " +
                "EventTypeBehavior AS etb " +
            "WHERE " +
                "etb.behaviorGroup.id = :behaviorGroupUuid " +
            "AND " +
                "etb.eventType.id = :eventTypeUuid";

        try {
            this.entityManager.createQuery(checkInsertionQuery)
                .setParameter("behaviorGroupUuid", behaviorGroup.getId())
                .setParameter("eventTypeUuid", eventType.getId())
                .getSingleResult();
        } catch (final NotFoundException e) {
            Assertions.fail(
                String.format(
                    "the relation between the behavior group '%s' and the event type '%s' was not found in the 'event_type_behavior' table",
                    behaviorGroup.getId(),
                    eventType.getId()
                )
            );
        }
    }

    /**
     * <p>Tests that the "count" function works as expected. For that, a specific bundle an application are created, and
     * then:
     *
     *  <ul>
     *      <li>Five behavior-group/event-type/event-type-behavior tuples are created, which are expected to be counted
     *      as "1" each.</li>
     *      <li>Five behavior-group/event-type-behavior tuples are created for a specific event type, which are
     *      expected to be counted as "5" total.</li>
     *  </ul>
     * </p>
     */
    @Test
    @Transactional
    void countBehaviorsGroupsByEventTypeIdTest() {
        final String accountId = "account-count-behavior-groups-by-event-type-id";
        final String orgId = "orgid-count-behavior-groups-by-event-type-id;";

        final Bundle bundle = resourceHelpers.createBundle();
        final Application application = this.resourceHelpers.createApplication(bundle.getId());

        // By creating a behavior group - event type couple every time, the "count" query should only return "1"
        // element every time.
        for (int i = 0; i < 5; i++) {
            final EventType eventType = this.resourceHelpers.createEventType(
                application.getId(),
                String.format("count-behavior-groups-by-event-type-id-test-%s", i),
                String.format("count behavior groups by event type id test %s", i),
                String.format("test event type %s", i)
            );

            final BehaviorGroup behaviorGroup = this.resourceHelpers.createBehaviorGroup(
                accountId,
                orgId,
                String.format("count behavior groups by event type id %s", i),
                bundle.getId()
            );

            final var eventTypeBehavior = new EventTypeBehavior(eventType, behaviorGroup);
            this.entityManager.persist(eventTypeBehavior);

            final long result = this.behaviorGroupRepository.countByEventTypeId(orgId, eventType.getId());
            Assertions.assertEquals(1, result, "unexpected number of behavior groups counted");
        }

        // Now let's create a single event type...
        final EventType eventType = this.resourceHelpers.createEventType(
            application.getId(),
            "count-behavior-groups-by-event-type-id-test-multiple",
            "count behavior groups by event type id test multiple",
            "test event type multiple"
        );

        // ... and multiple behavior groups for it.
        final int numberBehaviorGroupsForEventType = 5;
        for (int i = 0; i < numberBehaviorGroupsForEventType; i++) {
            final BehaviorGroup behaviorGroup = this.resourceHelpers.createBehaviorGroup(
                accountId,
                orgId,
                String.format("count behavior groups by event type id %s", i),
                bundle.getId()
            );

            EventTypeBehavior eventTypeBehavior = new EventTypeBehavior(eventType, behaviorGroup);
            this.entityManager.persist(eventTypeBehavior);
        }

        // Since the even type was the same for the different behavior groups, we should get the number of behavior
        // groups created for that event type.
        final long result = this.behaviorGroupRepository.countByEventTypeId(orgId, eventType.getId());
        Assertions.assertEquals(numberBehaviorGroupsForEventType, result, "unexpected number of behavior groups counted for a single event type");
    }

    @Transactional
    EventType createEventType(Application app) {
        EventType eventType = new EventType();
        eventType.setApplication(app);
        eventType.setApplicationId(app.getId());
        eventType.setName("name");
        eventType.setDisplayName("displayName");
        entityManager.persist(eventType);
        return eventType;
    }

    private void createBehaviorGroupWithIllegalDisplayName(String displayName) {
        Bundle bundle = resourceHelpers.createBundle();
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> {
            resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, displayName, bundle.getId());
        });
        assertTrue(Pattern.compile("property path: [a-zA-Z0-9.]+displayName").matcher(e.getMessage()).find());
    }

    private void updateBehaviorGroup(UUID behaviorGroupId, String displayName) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setId(behaviorGroupId);
        behaviorGroup.setDisplayName(displayName);
        behaviorGroup.setBundleId(UUID.randomUUID()); // This should not have any effect, the bundle is not updatable.
        resourceHelpers.updateBehaviorGroup(behaviorGroup);
    }

    private void updateDefaultBehaviorGroup(UUID behaviorGroupId, String displayName) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setId(behaviorGroupId);
        behaviorGroup.setDisplayName(displayName);
        behaviorGroup.setBundleId(UUID.randomUUID()); // This should not have any effect, the bundle is not updatable.
        resourceHelpers.updateDefaultBehaviorGroup(behaviorGroup);
    }

    @Transactional
    void updateAndCheckEventTypeBehaviors(String orgId, UUID eventTypeId, UUID... behaviorGroupIds) {
        behaviorGroupRepository.updateEventTypeBehaviors(orgId, eventTypeId, Set.of(behaviorGroupIds));
        entityManager.clear(); // We need to clear the session L1 cache before checking the update result.
        // If we expected a success, the event type behaviors should match in any order the given behavior groups IDs.
        List<EventTypeBehavior> behaviors = findEventTypeBehaviorByEventTypeId(eventTypeId);
        assertEquals(behaviorGroupIds.length, behaviors.size());
        for (UUID behaviorGroupId : behaviorGroupIds) {
            assertEquals(1L, behaviors.stream().filter(behavior -> behavior.getBehaviorGroup().getId().equals(behaviorGroupId)).count());
        }
    }

    private List<EventTypeBehavior> findEventTypeBehaviorByEventTypeId(UUID eventTypeId) {
        String query = "FROM EventTypeBehavior WHERE eventType.id = :eventTypeId";
        return entityManager.createQuery(query, EventTypeBehavior.class)
                .setParameter("eventTypeId", eventTypeId)
                .getResultList();
    }

    @Transactional
    void updateAndCheckBehaviorGroupActions(String orgId, UUID bundleId, UUID behaviorGroupId, UUID... endpointIds) {
        behaviorGroupRepository.updateBehaviorGroupActions(orgId, behaviorGroupId, Arrays.asList(endpointIds));
        entityManager.clear(); // We need to clear the session L1 cache before checking the update result.
        // If we expected a success, the behavior group actions should match exactly the given endpoint IDs.
        List<BehaviorGroupAction> actions = findBehaviorGroupActions(orgId, bundleId, behaviorGroupId);
        assertEquals(endpointIds.length, actions.size());
        for (int i = 0; i < endpointIds.length; i++) {
            assertEquals(endpointIds[i], actions.get(i).getEndpoint().getId());
        }
    }

    private List<BehaviorGroupAction> findBehaviorGroupActions(String orgId, UUID bundleId, UUID behaviorGroupId) {
        return behaviorGroupRepository.findByBundleId(orgId, bundleId)
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
