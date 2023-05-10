package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EventType;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
import javax.validation.Valid;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static javax.persistence.LockModeType.PESSIMISTIC_WRITE;
import static org.hibernate.jpa.QueryHints.HINT_PASS_DISTINCT_THROUGH;

@ApplicationScoped
public class BehaviorGroupRepository {

    /**
     * Represents the maximum number of behavior groups that can be created for
     * a tenant. Comes from ticket <a href="https://issues.redhat.com/browse/RHCLOUD-21842">RHCLOUD-21842</a>.
     */
    public static final long MAXIMUM_NUMBER_BEHAVIOR_GROUPS = 64;
    private static final ZoneId UTC = ZoneId.of("UTC");

    @Inject
    EntityManager entityManager;

    @Inject
    FeatureFlipper featureFlipper;

    public BehaviorGroup createFull(String accountId, String orgId, @Valid BehaviorGroup behaviorGroup, List<UUID> endpoints, Set<UUID> eventTypes) {
        BehaviorGroup saved = create(accountId, orgId, behaviorGroup);
        if (endpoints != null) {
            updateBehaviorGroupActions(orgId, saved.getId(), endpoints);
        }

        if (eventTypes != null) {
            updateBehaviorEventTypes(orgId, saved.getId(), eventTypes);
        }

        // The previous updates execute native SQL - hibernate is not aware of the changes it made on the behavior group
        // so we need to refresh to ensure we have the actions and event types.
        entityManager.flush();
        entityManager.refresh(saved);

        return saved;
    }

    public BehaviorGroup create(String accountId, String orgId, @Valid BehaviorGroup behaviorGroup) {
        return this.create(accountId, orgId, behaviorGroup, false);
    }

    public BehaviorGroup createDefault(BehaviorGroup behaviorGroup) {
        return this.create(null, null, behaviorGroup, true);
    }

    public UUID getBundleId(String orgId, UUID behaviorGroupId) {
        try {
            return entityManager.createQuery("SELECT bundle.id FROM BehaviorGroup WHERE id = :id and orgId = :orgId", UUID.class)
                    .setParameter("id", behaviorGroupId)
                    .setParameter("orgId", orgId)
                    .getSingleResult();
        } catch (NoResultException nre) {
            throw new NotFoundException("Behavior group not found");
        }
    }

    @Transactional
    BehaviorGroup create(String accountId, String orgId, BehaviorGroup behaviorGroup, boolean isDefaultBehaviorGroup) {
        // The organization ID might be null if a system behavior group is being
        // created, that is why the check is only forced for actual tenants.
        if (orgId != null && !orgId.isBlank() && !this.isAllowedToCreateMoreBehaviorGroups(orgId)) {
            throw new BadRequestException("behavior group creation limit reached. Please consider deleting unused behavior groups before creating more.");
        }

        checkBehaviorGroupDisplayNameDuplicate(orgId, behaviorGroup, isDefaultBehaviorGroup);

        behaviorGroup.setAccountId(accountId);
        behaviorGroup.setOrgId(orgId);
        if (isDefaultBehaviorGroup != behaviorGroup.isDefaultBehavior()) {
            throw new BadRequestException(String.format(
                    "Unexpected default behavior group status: Expected [%s] found: [%s]",
                    isDefaultBehaviorGroup, behaviorGroup.isDefaultBehavior()
            ));
        }

        Bundle bundle = entityManager.find(Bundle.class, behaviorGroup.getBundleId());
        if (bundle == null) {
            throw new NotFoundException("bundle_id not found");
        } else {
            behaviorGroup.setBundle(bundle);
            entityManager.persist(behaviorGroup);
            behaviorGroup.filterOutBundle();
            return behaviorGroup;
        }
    }

    public List<BehaviorGroup> findDefaults() {
        String query = "SELECT DISTINCT b FROM BehaviorGroup b LEFT JOIN FETCH b.actions a " +
                "WHERE b.orgId IS NULL " +
                "ORDER BY b.created DESC, a.position ASC";

        List<BehaviorGroup> behaviorGroups = entityManager.createQuery(query, BehaviorGroup.class)
                .getResultList();
        for (BehaviorGroup behaviorGroup : behaviorGroups) {
            behaviorGroup.filterOutBundle();
        }
        return behaviorGroups;
    }

    public List<BehaviorGroup> findByBundleId(String orgId, UUID bundleId) {
        Bundle bundle = entityManager.find(Bundle.class, bundleId);
        if (bundle == null) {
            throw new NotFoundException("Bundle not found");
        }

        /*
         * When PostgreSQL sorts a BOOLEAN column in DESC order, true comes first. That's not true for all DBMS.
         *
         * When QueryHints.HINT_PASS_DISTINCT_THROUGH is set to false, Hibernate returns distinct results without passing the
         * DISTINCT keyword to the DBMS. This is better for performances.
         * See https://in.relation.to/2016/08/04/introducing-distinct-pass-through-query-hint/ for more details about that hint.
         */
        String query = "SELECT DISTINCT b FROM BehaviorGroup b LEFT JOIN FETCH b.actions a " +
                "WHERE (b.orgId = :orgId OR b.orgId IS NULL) AND b.bundle.id = :bundleId " +
                "ORDER BY b.created DESC, a.position ASC";
        List<BehaviorGroup> behaviorGroups = entityManager.createQuery(query, BehaviorGroup.class)
                .setParameter("orgId", orgId)
                .setParameter("bundleId", bundleId)
                .setHint(HINT_PASS_DISTINCT_THROUGH, false)
                .getResultList();
        for (BehaviorGroup behaviorGroup : behaviorGroups) {
            behaviorGroup.filterOutBundle();
        }
        return behaviorGroups;
    }

    public void update(String orgId, BehaviorGroup behaviorGroup) {
        this.update(orgId, behaviorGroup, false);
    }

    public void updateDefault(BehaviorGroup behaviorGroup) {
        this.update(null, behaviorGroup, true);
    }

    @Transactional
    void update(String orgId, BehaviorGroup behaviorGroup, boolean isDefaultBehavior) {
        checkBehaviorGroupDisplayNameDuplicate(orgId, behaviorGroup, isDefaultBehavior);

        checkBehaviorGroup(behaviorGroup.getId(), isDefaultBehavior);
        String query = "UPDATE BehaviorGroup SET displayName = :displayName WHERE id = :id";

        if (orgId == null) {
            query += " AND orgId IS NULL";
        } else {
            query += " AND orgId = :orgId";
        }

        javax.persistence.Query q = entityManager.createQuery(query)
                .setParameter("displayName", behaviorGroup.getDisplayName())
                .setParameter("id", behaviorGroup.getId());

        if (orgId != null) {
            q = q.setParameter("orgId", orgId);
        }

        q.executeUpdate();
    }

    private void checkBehaviorGroupDisplayNameDuplicate(String orgId, BehaviorGroup behaviorGroup, boolean isDefaultBehaviorGroup) {
        if (!featureFlipper.isEnforceBehaviorGroupNameUnicity()) {
            // The check is disabled from configuration.
            return;
        }

        String hql = "SELECT COUNT(*) FROM BehaviorGroup WHERE displayName = :displayName AND bundle.id = :bundleId";
        if (behaviorGroup.getId() != null) { // The behavior group already exists in the DB, it's being updated.
            hql += " AND id != :behaviorGroupId";
        }
        if (isDefaultBehaviorGroup) {
            hql += " AND orgId IS NULL";
        } else {
            hql += " AND orgId = :orgId";
        }

        TypedQuery<Long> query = entityManager.createQuery(hql, Long.class)
                .setParameter("displayName", behaviorGroup.getDisplayName())
                .setParameter("bundleId", behaviorGroup.getBundleId());

        if (behaviorGroup.getId() != null) {
            query.setParameter("behaviorGroupId", behaviorGroup.getId());
        }
        if (!isDefaultBehaviorGroup) {
            query.setParameter("orgId", orgId);
        }
        if (query.getSingleResult() > 0) {
            throw new BadRequestException("A behavior group with display name [" + behaviorGroup.getDisplayName() + "] already exists");
        }
    }

    public boolean delete(String orgId, UUID behaviorGroupId) {
        return this.delete(orgId, behaviorGroupId, false);
    }

    public boolean deleteDefault(UUID behaviorGroupId) {
        return this.delete(null, behaviorGroupId, true);
    }

    @Transactional
    public boolean delete(String orgId, UUID behaviorGroupId, boolean isDefaultBehavior) {
        checkBehaviorGroup(behaviorGroupId, isDefaultBehavior);
        String query = "DELETE FROM BehaviorGroup WHERE id = :id";

        if (orgId == null) {
            query += " AND orgId IS NULL";
        } else {
            query += " AND orgId = :orgId";
        }

        javax.persistence.Query q = entityManager.createQuery(query)
                .setParameter("id", behaviorGroupId);

        if (orgId != null) {
            q = q.setParameter("orgId", orgId);
        }

        return q.executeUpdate() > 0;
    }

    @Transactional
    public boolean linkEventTypeDefaultBehavior(UUID eventTypeId, UUID behaviorGroupId) {
        checkBehaviorGroup(behaviorGroupId, true);
        String insertQuery = "INSERT INTO event_type_behavior (event_type_id, behavior_group_id, created) " +
                "VALUES (:eventTypeId, :behaviorGroupId, :created) " +
                "ON CONFLICT (event_type_id, behavior_group_id) DO NOTHING";
        entityManager.createNativeQuery(insertQuery)
                .setParameter("eventTypeId", eventTypeId)
                .setParameter("behaviorGroupId", behaviorGroupId)
                .setParameter("created", LocalDateTime.now(UTC))
                .executeUpdate();
        return true;
    }

    @Transactional
    public boolean unlinkEventTypeDefaultBehavior(UUID eventTypeId, UUID behaviorGroupId) {
        checkBehaviorGroup(behaviorGroupId, true);
        String deleteQuery = "DELETE FROM EventTypeBehavior " +
                "WHERE eventType.id = :eventTypeId " +
                "AND id.behaviorGroupId = :behaviorGroupId";
        entityManager.createQuery(deleteQuery)
                .setParameter("eventTypeId", eventTypeId)
                .setParameter("behaviorGroupId", behaviorGroupId)
                .executeUpdate();
        return true;
    }

    /**
     * Sets the event types of a behavior group
     * @param behaviorGroupId Id of the behavior group
     * @param eventTypeIds List of the event type ids that we want to set
     */
    @Transactional
    public void updateBehaviorEventTypes(String orgId, UUID behaviorGroupId, Set<UUID> eventTypeIds) {
        BehaviorGroup behaviorGroup = entityManager.find(BehaviorGroup.class, behaviorGroupId);
        if (behaviorGroup == null || !behaviorGroup.getOrgId().equals(orgId)) {
            throw new NotFoundException("Behavior group not found in the org");
        }

        if (!eventTypeIds.isEmpty()) {
            // Behavior group can only be linked to event types in the same bundle
            String integrityCheckHql = "SELECT id from EventType WHERE id IN (:eventTypeIds) "
                    + "AND application.bundle.id != :bundleId";
            List<UUID> differentBundle = entityManager.createQuery(integrityCheckHql, UUID.class)
                    .setParameter("eventTypeIds", eventTypeIds)
                    .setParameter("bundleId", behaviorGroup.getBundleId())
                    .getResultList();
            if (!differentBundle.isEmpty()) {
                throw new BadRequestException("Some event types can't be linked to the behavior group because they " +
                        "belong to a different bundle: " + differentBundle);
            }
        }

        /*
         * Lock related rows to avoid deadlocks
         */
        String lockQuery = "SELECT id.eventTypeId FROM EventTypeBehavior " +
                "WHERE id.behaviorGroupId = :behaviorGroupId";
        List<UUID> eventTypesFromDb = entityManager.createQuery(lockQuery, UUID.class)
                .setParameter("behaviorGroupId", behaviorGroupId)
                .setLockMode(PESSIMISTIC_WRITE)
                .getResultList();

        /*
        * Delete the event type links that were not provided
        */
        List<UUID> eventTypesToDelete = new ArrayList<>(eventTypesFromDb);
        eventTypesToDelete.removeAll(eventTypeIds);

        if (!eventTypesToDelete.isEmpty()) {
            String deleteQuery = "DELETE FROM EventTypeBehavior " +
                    "WHERE id.eventTypeId IN (:eventTypeIds) AND id.behaviorGroupId = :behaviorGroupId";
            entityManager.createQuery(deleteQuery)
                    .setParameter("eventTypeIds", eventTypesToDelete)
                    .setParameter("behaviorGroupId", behaviorGroupId)
                    .executeUpdate();
        }

        List<UUID> eventTypesToAdd = new ArrayList<>(eventTypeIds);
        eventTypesToAdd.removeAll(eventTypesFromDb);

        if (!eventTypesToAdd.isEmpty()) {
            for (UUID eventTypeId : eventTypesToAdd) {
                String insertQuery = "INSERT INTO event_type_behavior (event_type_id, behavior_group_id, created) " +
                        "SELECT :eventTypeId, :behaviorGroupId, :created";
                entityManager.createNativeQuery(insertQuery)
                        .setParameter("eventTypeId", eventTypeId)
                        .setParameter("behaviorGroupId", behaviorGroupId)
                        .setParameter("created", LocalDateTime.now(UTC))
                        .executeUpdate();
            }
        }
    }

    @Transactional
    public void updateEventTypeBehaviors(String orgId, UUID eventTypeId, Set<UUID> behaviorGroupIds) {
        // First, let's make sure the event type exists.
        EventType eventType = entityManager.find(EventType.class, eventTypeId);
        if (eventType == null) {
            throw new NotFoundException("Event type not found");
        } else {

            if (!behaviorGroupIds.isEmpty()) {
                // An event type should only be linked to behavior groups from the same bundle.
                String integrityCheckHql = "SELECT id FROM BehaviorGroup WHERE id IN (:behaviorGroupIds) " +
                        "AND bundle != (SELECT application.bundle FROM EventType WHERE id = :eventTypeId)";
                List<UUID> differentBundle = entityManager.createQuery(integrityCheckHql, UUID.class)
                        .setParameter("behaviorGroupIds", behaviorGroupIds)
                        .setParameter("eventTypeId", eventTypeId)
                        .getResultList();
                if (!differentBundle.isEmpty()) {
                    throw new BadRequestException("Some behavior groups can't be linked to the event " +
                            "type because they belong to a different bundle: " + differentBundle);
                }
            }

            /*
             * Before changing the DB data, we need to lock the existing rows "for update" to prevent them
             * from being modified or deleted by other transactions. Otherwise, DB deadlocks could happen.
             */
            String lockQuery = "SELECT behaviorGroup.id FROM EventTypeBehavior " +
                    "WHERE behaviorGroup.orgId = :orgId AND eventType.id = :eventTypeId";
            List<UUID> behaviorsFromDb = entityManager.createQuery(lockQuery, UUID.class)
                    .setParameter("orgId", orgId)
                    .setParameter("eventTypeId", eventTypeId)
                    .setLockMode(PESSIMISTIC_WRITE)
                    .getResultList();

            /*
             * All event type behaviors that should no longer exist must be deleted.
             */
            List<UUID> behaviorsToDelete = new ArrayList<>(behaviorsFromDb);
            behaviorsToDelete.removeAll(behaviorGroupIds);
            if (!behaviorsToDelete.isEmpty()) {
                String deleteQuery = "DELETE FROM EventTypeBehavior " +
                        "WHERE eventType.id = :eventTypeId AND behaviorGroup.id IN (:behaviorsToDelete)";
                entityManager.createQuery(deleteQuery)
                        .setParameter("eventTypeId", eventTypeId)
                        .setParameter("behaviorsToDelete", behaviorsToDelete)
                        .executeUpdate();
            }

            if (!behaviorGroupIds.isEmpty()) {
                /*
                 * Then, we'll insert all event type behaviors from the given behaviorGroupIds list that do not already exist in the DB.
                 * If an event type behavior already exists (because of another transaction), nothing will happen (no exception will be thrown).
                 */
                List<UUID> behaviorsToInsert = new ArrayList<>(behaviorGroupIds);
                behaviorsToInsert.removeAll(behaviorsFromDb);
                for (UUID behaviorGroupId : behaviorsToInsert) {
                    String insertQuery = "INSERT INTO event_type_behavior (event_type_id, behavior_group_id, created) " +
                            "SELECT :eventTypeId, :behaviorGroupId, :created " +
                            "WHERE EXISTS (SELECT 1 FROM behavior_group WHERE org_id = :orgId AND id = :behaviorGroupId) " +
                            "ON CONFLICT (event_type_id, behavior_group_id) DO NOTHING";
                    entityManager.createNativeQuery(insertQuery)
                            .setParameter("eventTypeId", eventTypeId)
                            .setParameter("behaviorGroupId", behaviorGroupId)
                            .setParameter("created", LocalDateTime.now(UTC))
                            .setParameter("orgId", orgId)
                            .executeUpdate();
                }
            }
        }
    }

    /**
     * Appends the behavior group to the event type. It checks that the
     * behavior group exists in the tenant, and it makes sure that the behavior
     * group is in the same bundle as the event type, since otherwise we would
     * be appending behavior groups to event types when those associations are,
     * in essence, incompatible.
     * @param orgId the tenant to run the checks against.
     * @param behaviorGroupUuid the UUID of the behavior group to be appended.
     * @param eventTypeUuid the UUID the event type the behavior group will be
     *                      appended to.
     */
    @Transactional
    public void appendBehaviorGroupToEventType(final String orgId, final UUID behaviorGroupUuid, final UUID eventTypeUuid) {
        final String appendBehaviorGroupQuery =
            "INSERT INTO " +
                "event_type_behavior(behavior_group_id, event_type_id, created) " +
            "SELECT " +
                "bg.id, et.id, :created " +
            "FROM " +
                "event_type AS et " +
                "INNER JOIN " +
                    "applications AS a " +
                    "ON a.id = et.application_id " +
                "INNER JOIN " +
                    "behavior_group AS bg " +
                        "ON bg.bundle_id = a.bundle_id " +
            "WHERE " +
                "et.id = :eventTypeUuid " +
            "AND " +
                "bg.id = :behaviorGroupUuid " +
            "AND " +
                "bg.org_id = :orgId " +
            "ON CONFLICT " +
                "(behavior_group_id, event_type_id) DO NOTHING";

        final javax.persistence.Query query = this.entityManager.createNativeQuery(appendBehaviorGroupQuery)
            .setParameter("behaviorGroupUuid", behaviorGroupUuid)
            .setParameter("eventTypeUuid", eventTypeUuid)
            .setParameter("created", LocalDateTime.now(UTC))
            .setParameter("orgId", orgId);

        final int affectedRows = query.executeUpdate();
        if (affectedRows == 0) {
            throw new NotFoundException("the specified behavior group doesn't exist or the specified event type doesn't belong to the same bundle as the behavior group");
        }
    }

    /**
     * Deletes the specified behavior group from the event type, by deleting the relation in the "event_type_behavior"
     * table.
     * @param eventTypeUuid the event type to remove the behavior group from.
     * @param behaviorGroupUuid the behavior group to remove.
     * @param orgId the org id to make sure that the caller has visibility on that behavior group, so that they don't
     *              delete unowned relations.
     */
    @Transactional
    public void deleteBehaviorGroupFromEventType(final UUID eventTypeUuid, final UUID behaviorGroupUuid, final String orgId) {
        final var deleteFromEventTypeQuery =
            "DELETE FROM " +
                "EventTypeBehavior AS etb " +
            "WHERE " +
                "etb.behaviorGroup.id = :behaviorGroupUuid " +
            "AND " +
                "etb.eventType.id = :eventTypeUuid " +
            "AND EXISTS (" +
                "SELECT " +
                    "1 " +
                "FROM " +
                    "BehaviorGroup AS bg " +
                "WHERE " +
                    "bg.id = :behaviorGroupUuid " +
                "AND " +
                    "bg.orgId = :orgId" +
                ")";

        final javax.persistence.Query query = this.entityManager.createQuery(deleteFromEventTypeQuery);
        query.setParameter("behaviorGroupUuid", behaviorGroupUuid)
            .setParameter("eventTypeUuid", eventTypeUuid)
            .setParameter("orgId", orgId);

        final int affectedRows = query.executeUpdate();
        if (affectedRows == 0) {
            throw new NotFoundException("the specified behavior group was not found for the given event type");
        }
    }

    public List<EventType> findEventTypesByBehaviorGroupId(String orgId, UUID behaviorGroupId) {
        BehaviorGroup behaviorGroup = entityManager.find(BehaviorGroup.class, behaviorGroupId);
        if (behaviorGroup == null) {
            throw new NotFoundException("Behavior group not found");
        }

        String query = "SELECT e FROM EventType e LEFT JOIN FETCH e.application JOIN e.behaviors b " +
                "WHERE (b.behaviorGroup.orgId = :orgId OR b.behaviorGroup.orgId IS NULL) AND b.behaviorGroup.id = :behaviorGroupId";
        return entityManager.createQuery(query, EventType.class)
                .setParameter("orgId", orgId)
                .setParameter("behaviorGroupId", behaviorGroupId)
                .getResultList();
    }

    public List<BehaviorGroup> findBehaviorGroupsByEventTypeId(String orgId, UUID eventTypeId, Query limiter) {
        EventType eventType = entityManager.find(EventType.class, eventTypeId);
        if (eventType == null) {
            throw new NotFoundException("Event type not found");
        }

        String query = "SELECT bg FROM BehaviorGroup bg JOIN bg.behaviors b WHERE (bg.orgId = :orgId OR bg.orgId IS NULL) AND b.eventType.id = :eventTypeId";

        if (limiter != null) {
            limiter.setSortFields(BehaviorGroup.SORT_FIELDS);
            query = limiter.getModifiedQuery(query);
        }

        TypedQuery<BehaviorGroup> typedQuery = entityManager.createQuery(query, BehaviorGroup.class)
                .setParameter("orgId", orgId)
                .setParameter("eventTypeId", eventTypeId);

        if (limiter != null && limiter.getLimit() != null && limiter.getLimit().getLimit() > 0) {
            typedQuery = typedQuery.setMaxResults(limiter.getLimit().getLimit())
                    .setFirstResult(limiter.getLimit().getOffset());
        }

        List<BehaviorGroup> behaviorGroups = typedQuery.getResultList();
        for (BehaviorGroup behaviorGroup : behaviorGroups) {
            behaviorGroup.filterOutBundle().filterOutActions().filterOutBehaviors();
        }
        return behaviorGroups;
    }

    @Transactional
    public void updateBehaviorGroupActions(String orgId, UUID behaviorGroupId, List<UUID> endpointIds) {

        // First, let's make sure the behavior group exists and is owned by the current organization.
        String checkBehaviorGroupQuery = "SELECT 1 FROM BehaviorGroup WHERE id = :id AND ";
        if (orgId == null) {
            checkBehaviorGroupQuery += "orgId IS NULL";
        } else {
            checkBehaviorGroupQuery += "orgId = :orgId";
        }

        TypedQuery<Integer> query = entityManager.createQuery(checkBehaviorGroupQuery, Integer.class)
                .setParameter("id", behaviorGroupId);

        if (orgId != null) {
            query = query.setParameter("orgId", orgId);
        }

        try {
            query.getSingleResult();
        } catch (NoResultException e) {
            throw new NotFoundException("Behavior group not found");
        }

        /*
         * Before changing the DB data, we need to lock the existing rows "for update" to prevent them
         * from being modified or deleted by other transactions. Otherwise, DB deadlocks could happen.
         */
        String lockHql = "SELECT endpoint.id FROM BehaviorGroupAction " +
                "WHERE behaviorGroup.orgId " + (orgId == null ? "IS NULL" : "= :orgId") + " AND behaviorGroup.id = :behaviorGroupId";
        TypedQuery<UUID> lockQuery = entityManager.createQuery(lockHql, UUID.class)
                .setParameter("behaviorGroupId", behaviorGroupId)
                .setLockMode(PESSIMISTIC_WRITE);
        if (orgId != null) {
            lockQuery = lockQuery.setParameter("orgId", orgId);
        }
        List<UUID> actionsFromDb = lockQuery.getResultList();

        /*
         * All behavior group actions that should no longer exist must be deleted.
         */
        List<UUID> actionsToDelete = new ArrayList<>(actionsFromDb);
        actionsToDelete.removeAll(endpointIds);
        if (!actionsToDelete.isEmpty()) {
            String deleteQuery = "DELETE FROM BehaviorGroupAction " +
                    "WHERE behaviorGroup.id = :behaviorGroupId AND endpoint.id IN (:actionsToDelete)";
            entityManager.createQuery(deleteQuery)
                    .setParameter("behaviorGroupId", behaviorGroupId)
                    .setParameter("actionsToDelete", actionsToDelete)
                    .executeUpdate();
        }

        for (UUID endpointId : endpointIds) {
            /*
             * Then, we'll execute an "upsert" based on the given endpointIds list:
             * - if an action already exists, its position will be updated
             * - otherwise, the action will be inserted into the database
             * In the end, all inserted or updated actions will have the same position than the endpointIds list order.
             */
            String upsertQuery = "INSERT INTO behavior_group_action (behavior_group_id, endpoint_id, position, created) " +
                    "SELECT :behaviorGroupId, :endpointId, :position, :created " +
                    "WHERE EXISTS (SELECT 1 FROM endpoints WHERE org_id " +
                    (orgId == null ? "IS NULL" : "= :orgId") +
                    " AND id = :endpointId) " +
                    "ON CONFLICT (behavior_group_id, endpoint_id) DO UPDATE SET position = :position";

            var sessionQuery = entityManager.createNativeQuery(upsertQuery)
                    .setParameter("behaviorGroupId", behaviorGroupId)
                    .setParameter("endpointId", endpointId)
                    .setParameter("position", endpointIds.indexOf(endpointId))
                    .setParameter("created", LocalDateTime.now(UTC));

            if (orgId != null) {
                sessionQuery = sessionQuery.setParameter("orgId", orgId);
            }

            sessionQuery.executeUpdate();
        }
    }

    public void updateDefaultBehaviorGroupActions(UUID behaviorGroupId, List<UUID> endpointIds) {
        updateBehaviorGroupActions(null, behaviorGroupId, endpointIds);
    }

    public List<BehaviorGroup> findBehaviorGroupsByEndpointId(String orgId, UUID endpointId) {
        Endpoint endpoint = entityManager.find(Endpoint.class, endpointId);
        if (endpoint == null) {
            throw new NotFoundException("Endpoint not found");
        }

        String query = "SELECT bg FROM BehaviorGroup bg LEFT JOIN FETCH bg.bundle JOIN bg.actions a WHERE bg.orgId = :orgId AND a.endpoint.id = :endpointId";
        List<BehaviorGroup> behaviorGroups = entityManager.createQuery(query, BehaviorGroup.class)
                .setParameter("orgId", orgId)
                .setParameter("endpointId", endpointId)
                .getResultList();
        for (BehaviorGroup behaviorGroup : behaviorGroups) {
            behaviorGroup.filterOutActions().filterOutBehaviors();
        }
        return behaviorGroups;
    }

    private void checkBehaviorGroup(UUID behaviorGroupId, boolean isDefaultBehaviorGroup) {
        BehaviorGroup behaviorGroup = entityManager.find(BehaviorGroup.class, behaviorGroupId);
        if (behaviorGroup == null) {
            throw new NotFoundException("Behavior group not found");
        } else {
            if (isDefaultBehaviorGroup) {
                if (behaviorGroup.getOrgId() != null) {
                    throw new BadRequestException("Default behavior groups must have a null org id");
                }
            } else {
                if (behaviorGroup.getOrgId() == null) {
                    throw new BadRequestException("Only default behavior groups have a null org id");
                }
            }
        }
    }

    /**
     * Checks if it is allowed to create more behavior groups for the given tenant.
     *
     * @param orgId the account number to filter the behavior groups by.
     * @return true if the number of behavior groups of the tenant is lower than the allowed
     * {@link BehaviorGroupRepository#MAXIMUM_NUMBER_BEHAVIOR_GROUPS}.
     */
    private boolean isAllowedToCreateMoreBehaviorGroups(final String orgId) {
        final String countQuery =
            "SELECT " +
                "COUNT(bg) " +
            "FROM " +
                "BehaviorGroup AS bg " +
            "WHERE " +
                "bg.orgId = :org_id";

        final long count = this.entityManager.createQuery(countQuery, Long.class)
            .setParameter("org_id", orgId)
            .getSingleResult();

        return count < MAXIMUM_NUMBER_BEHAVIOR_GROUPS;
    }
}
