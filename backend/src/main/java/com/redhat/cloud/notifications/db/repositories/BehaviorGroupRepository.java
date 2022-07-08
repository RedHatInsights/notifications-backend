package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.OrgIdHelper;
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
import javax.validation.constraints.NotNull;
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

    private static final ZoneId UTC = ZoneId.of("UTC");

    @Inject
    EntityManager entityManager;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    OrgIdHelper orgIdHelper;

    public BehaviorGroup createFull(String accountId, String orgId, @Valid BehaviorGroup behaviorGroup, List<UUID> endpoints, Set<UUID> eventTypes) {
        BehaviorGroup saved = create(accountId, orgId, behaviorGroup);
        if (endpoints != null) {
            updateBehaviorGroupActions(accountId, orgId, saved.getId(), endpoints);
        }

        if (eventTypes != null) {
            updateBehaviorEventTypes(accountId, orgId, saved.getId(), eventTypes);
        }

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

    @Transactional
    BehaviorGroup create(String accountId, String orgId, BehaviorGroup behaviorGroup, boolean isDefaultBehaviorGroup) {
        checkBehaviorGroupDisplayNameDuplicate(accountId, orgId, behaviorGroup, isDefaultBehaviorGroup);

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
        String query;
        if (featureFlipper.isUseOrgId()) {
            query = "SELECT DISTINCT b FROM BehaviorGroup b LEFT JOIN FETCH b.actions a " +
                    "WHERE b.orgId IS NULL " +
                    "ORDER BY b.created DESC, a.position ASC";
        } else {
            query = "SELECT DISTINCT b FROM BehaviorGroup b LEFT JOIN FETCH b.actions a " +
                    "WHERE b.accountId IS NULL " +
                    "ORDER BY b.created DESC, a.position ASC";
        }

        List<BehaviorGroup> behaviorGroups = entityManager.createQuery(query, BehaviorGroup.class)
                .getResultList();
        for (BehaviorGroup behaviorGroup : behaviorGroups) {
            behaviorGroup.filterOutBundle();
        }
        return behaviorGroups;
    }

    public List<BehaviorGroup> findByBundleId(String accountId, String orgId, UUID bundleId) {
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
        List<BehaviorGroup> behaviorGroups;
        if (orgIdHelper.useOrgId(orgId)) {
            String query = "SELECT DISTINCT b FROM BehaviorGroup b LEFT JOIN FETCH b.actions a " +
                    "WHERE (b.orgId = :orgId OR b.orgId IS NULL) AND b.bundle.id = :bundleId " +
                    "ORDER BY b.created DESC, a.position ASC";
            behaviorGroups = entityManager.createQuery(query, BehaviorGroup.class)
                    .setParameter("orgId", orgId)
                    .setParameter("bundleId", bundleId)
                    .setHint(HINT_PASS_DISTINCT_THROUGH, false)
                    .getResultList();
        } else {
            String query = "SELECT DISTINCT b FROM BehaviorGroup b LEFT JOIN FETCH b.actions a " +
                    "WHERE (b.accountId = :accountId OR b.accountId IS NULL) AND b.bundle.id = :bundleId " +
                    "ORDER BY b.created DESC, a.position ASC";
            behaviorGroups = entityManager.createQuery(query, BehaviorGroup.class)
                    .setParameter("accountId", accountId)
                    .setParameter("bundleId", bundleId)
                    .setHint(HINT_PASS_DISTINCT_THROUGH, false)
                    .getResultList();
        }
        for (BehaviorGroup behaviorGroup : behaviorGroups) {
            behaviorGroup.filterOutBundle();
        }
        return behaviorGroups;
    }

    public boolean update(String accountId, String orgId, BehaviorGroup behaviorGroup) {
        return this.update(accountId, orgId, behaviorGroup, false);
    }

    public boolean updateDefault(BehaviorGroup behaviorGroup) {
        return this.update(null, null, behaviorGroup, true);
    }

    @Transactional
    boolean update(String accountId, String orgId, BehaviorGroup behaviorGroup, boolean isDefaultBehavior) {
        checkBehaviorGroupDisplayNameDuplicate(accountId, orgId, behaviorGroup, isDefaultBehavior);

        checkBehaviorGroup(behaviorGroup.getId(), isDefaultBehavior);
        String query = "UPDATE BehaviorGroup SET displayName = :displayName WHERE id = :id";

        if (featureFlipper.isUseOrgId()) {
            if (orgId == null) {
                query += " AND orgId IS NULL";
            } else {
                query += " AND orgId = :orgId";
            }
        } else {
            if (accountId == null) {
                query += " AND accountId IS NULL";
            } else {
                query += " AND accountId = :accountId";
            }
        }

        javax.persistence.Query q = entityManager.createQuery(query)
                .setParameter("displayName", behaviorGroup.getDisplayName())
                .setParameter("id", behaviorGroup.getId());

        if (featureFlipper.isUseOrgId()) {
            if (orgId != null) {
                q = q.setParameter("orgId", orgId);
            }
        } else {
            if (accountId != null) {
                q = q.setParameter("accountId", accountId);
            }
        }

        return q.executeUpdate() > 0;
    }

    private void checkBehaviorGroupDisplayNameDuplicate(String accountId, String orgId, BehaviorGroup behaviorGroup, boolean isDefaultBehaviorGroup) {
        if (!featureFlipper.isEnforceBehaviorGroupNameUnicity()) {
            // The check is disabled from configuration.
            return;
        }

        String hql = "SELECT COUNT(*) FROM BehaviorGroup WHERE displayName = :displayName";
        if (behaviorGroup.getId() != null) { // The behavior group already exists in the DB, it's being updated.
            hql += " AND id != :behaviorGroupId";
        }
        if (featureFlipper.isUseOrgId()) {
            if (isDefaultBehaviorGroup) {
                hql += " AND orgId IS NULL";
            } else {
                hql += " AND orgId = :orgId";
            }
        } else {
            if (isDefaultBehaviorGroup) {
                hql += " AND accountId IS NULL";
            } else {
                hql += " AND accountId = :accountId";
            }
        }

        TypedQuery<Long> query = entityManager.createQuery(hql, Long.class)
                .setParameter("displayName", behaviorGroup.getDisplayName());
        if (behaviorGroup.getId() != null) {
            query.setParameter("behaviorGroupId", behaviorGroup.getId());
        }
        if (featureFlipper.isUseOrgId()) {
            if (!isDefaultBehaviorGroup) {
                query.setParameter("orgId", orgId);
            }
        } else {
            if (!isDefaultBehaviorGroup) {
                query.setParameter("accountId", accountId);
            }
        }
        if (query.getSingleResult() > 0) {
            throw new BadRequestException("A behavior group with display name [" + behaviorGroup.getDisplayName() + "] already exists");
        }
    }

    public boolean delete(String accountId, String orgId, UUID behaviorGroupId) {
        return this.delete(accountId, orgId, behaviorGroupId, false);
    }

    public boolean deleteDefault(UUID behaviorGroupId) {
        return this.delete(null, null, behaviorGroupId, true);
    }

    @Transactional
    public boolean delete(String accountId, String orgId, UUID behaviorGroupId, boolean isDefaultBehavior) {
        checkBehaviorGroup(behaviorGroupId, isDefaultBehavior);
        String query = "DELETE FROM BehaviorGroup WHERE id = :id";

        if (featureFlipper.isUseOrgId()) {
            if (orgId == null) {
                query += " AND orgId IS NULL";
            } else {
                query += " AND orgId = :orgId";
            }
        } else {
            if (accountId == null) {
                query += " AND accountId IS NULL";
            } else {
                query += " AND accountId = :accountId";
            }
        }

        javax.persistence.Query q = entityManager.createQuery(query)
                .setParameter("id", behaviorGroupId);

        if (featureFlipper.isUseOrgId()) {
            if (orgId != null) {
                q = q.setParameter("orgId", orgId);
            }
        } else {
            if (accountId != null) {
                q = q.setParameter("accountId", accountId);
            }
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
     * @param accountId Account id of the behavior group
     * @param behaviorGroupId Id of the behavior group
     * @param eventTypeIds List of the event type ids that we want to set
     */
    @Transactional
    public void updateBehaviorEventTypes(String accountId, String orgId, UUID behaviorGroupId, Set<UUID> eventTypeIds) {
        BehaviorGroup behaviorGroup = entityManager.find(BehaviorGroup.class, behaviorGroupId);
        if (featureFlipper.isUseOrgId()) {
            if (behaviorGroup == null || !behaviorGroup.getOrgId().equals(orgId)) {
                throw new NotFoundException("Behavior group not found in the org");
            }
        } else {
            if (behaviorGroup == null || !behaviorGroup.getAccountId().equals(accountId)) {
                throw new NotFoundException("Behavior group not found in the account");
            }
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
    public void updateEventTypeBehaviors(String accountId, String orgId, UUID eventTypeId, Set<UUID> behaviorGroupIds) {
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
            List<UUID> behaviorsFromDb;
            if (orgIdHelper.useOrgId(orgId)) {
                String lockQuery = "SELECT behaviorGroup.id FROM EventTypeBehavior " +
                        "WHERE behaviorGroup.orgId = :orgId AND eventType.id = :eventTypeId";
                behaviorsFromDb = entityManager.createQuery(lockQuery, UUID.class)
                        .setParameter("orgId", orgId)
                        .setParameter("eventTypeId", eventTypeId)
                        .setLockMode(PESSIMISTIC_WRITE)
                        .getResultList();
            } else {
                String lockQuery = "SELECT behaviorGroup.id FROM EventTypeBehavior " +
                        "WHERE behaviorGroup.accountId = :accountId AND eventType.id = :eventTypeId";
                behaviorsFromDb = entityManager.createQuery(lockQuery, UUID.class)
                        .setParameter("accountId", accountId)
                        .setParameter("eventTypeId", eventTypeId)
                        .setLockMode(PESSIMISTIC_WRITE)
                        .getResultList();
            }

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
                    if (orgIdHelper.useOrgId(orgId)) {
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
                    } else {
                        String insertQuery = "INSERT INTO event_type_behavior (event_type_id, behavior_group_id, created) " +
                                "SELECT :eventTypeId, :behaviorGroupId, :created " +
                                "WHERE EXISTS (SELECT 1 FROM behavior_group WHERE account_id = :accountId AND id = :behaviorGroupId) " +
                                "ON CONFLICT (event_type_id, behavior_group_id) DO NOTHING";
                        entityManager.createNativeQuery(insertQuery)
                                .setParameter("eventTypeId", eventTypeId)
                                .setParameter("behaviorGroupId", behaviorGroupId)
                                .setParameter("created", LocalDateTime.now(UTC))
                                .setParameter("accountId", accountId)
                                .executeUpdate();
                    }
                }
            }
        }
    }

    public List<EventType> findEventTypesByBehaviorGroupId(String accountId, String orgId, UUID behaviorGroupId) {
        BehaviorGroup behaviorGroup = entityManager.find(BehaviorGroup.class, behaviorGroupId);
        if (behaviorGroup == null) {
            throw new NotFoundException("Behavior group not found");
        }

        if (orgIdHelper.useOrgId(orgId)) {
            String query = "SELECT e FROM EventType e LEFT JOIN FETCH e.application JOIN e.behaviors b " +
                    "WHERE (b.behaviorGroup.orgId = :orgId OR b.behaviorGroup.orgId IS NULL) AND b.behaviorGroup.id = :behaviorGroupId";
            return entityManager.createQuery(query, EventType.class)
                    .setParameter("orgId", orgId)
                    .setParameter("behaviorGroupId", behaviorGroupId)
                    .getResultList();
        } else {
            String query = "SELECT e FROM EventType e LEFT JOIN FETCH e.application JOIN e.behaviors b " +
                    "WHERE (b.behaviorGroup.accountId = :accountId OR b.behaviorGroup.accountId IS NULL) AND b.behaviorGroup.id = :behaviorGroupId";
            return entityManager.createQuery(query, EventType.class)
                    .setParameter("accountId", accountId)
                    .setParameter("behaviorGroupId", behaviorGroupId)
                    .getResultList();
        }
    }

    public List<BehaviorGroup> findBehaviorGroupsByEventTypeId(String accountId, String orgId, UUID eventTypeId, Query limiter) {
        EventType eventType = entityManager.find(EventType.class, eventTypeId);
        if (eventType == null) {
            throw new NotFoundException("Event type not found");
        }

        TypedQuery<BehaviorGroup> typedQuery;
        if (orgIdHelper.useOrgId(orgId)) {
            String query = "SELECT bg FROM BehaviorGroup bg JOIN bg.behaviors b WHERE (bg.orgId = :orgId OR bg.orgId IS NULL) AND b.eventType.id = :eventTypeId";

            if (limiter != null) {
                query = limiter.getModifiedQuery(query);
            }

            typedQuery = entityManager.createQuery(query, BehaviorGroup.class)
                    .setParameter("orgId", orgId)
                    .setParameter("eventTypeId", eventTypeId);
        } else {
            String query = "SELECT bg FROM BehaviorGroup bg JOIN bg.behaviors b WHERE (bg.accountId = :accountId OR bg.accountId IS NULL) AND b.eventType.id = :eventTypeId";

            if (limiter != null) {
                query = limiter.getModifiedQuery(query);
            }

            typedQuery = entityManager.createQuery(query, BehaviorGroup.class)
                    .setParameter("accountId", accountId)
                    .setParameter("eventTypeId", eventTypeId);
        }

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
    public void updateBehaviorGroupActions(String accountId, String orgId, UUID behaviorGroupId, List<UUID> endpointIds) {

        // First, let's make sure the behavior group exists and is owned by the current account.
        String checkBehaviorGroupQuery = "SELECT 1 FROM BehaviorGroup WHERE id = :id AND ";
        if (featureFlipper.isUseOrgId()) {
            if (orgId == null) {
                checkBehaviorGroupQuery += "orgId IS NULL";
            } else {
                checkBehaviorGroupQuery += "orgId = :orgId";
            }
        } else {
            if (accountId == null) {
                checkBehaviorGroupQuery += "accountId IS NULL";
            } else {
                checkBehaviorGroupQuery += "accountId = :accountId";
            }
        }

        TypedQuery<Integer> query = entityManager.createQuery(checkBehaviorGroupQuery, Integer.class)
                .setParameter("id", behaviorGroupId);

        if (featureFlipper.isUseOrgId()) {
            if (orgId != null) {
                query = query.setParameter("orgId", orgId);
            }
        } else {
            if (accountId != null) {
                query = query.setParameter("accountId", accountId);
            }
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
        TypedQuery<UUID> lockQuery;
        if (featureFlipper.isUseOrgId()) {
            String lockHql = "SELECT endpoint.id FROM BehaviorGroupAction " +
                    "WHERE behaviorGroup.orgId " + (orgId == null ? "IS NULL" : "= :orgId") + " AND behaviorGroup.id = :behaviorGroupId";
            lockQuery = entityManager.createQuery(lockHql, UUID.class)
                    .setParameter("behaviorGroupId", behaviorGroupId)
                    .setLockMode(PESSIMISTIC_WRITE);
            if (orgId != null) {
                lockQuery = lockQuery.setParameter("orgId", orgId);
            }
        } else {
            String lockHql = "SELECT endpoint.id FROM BehaviorGroupAction " +
                    "WHERE behaviorGroup.accountId " + (accountId == null ? "IS NULL" : "= :accountId") + " AND behaviorGroup.id = :behaviorGroupId";
            lockQuery = entityManager.createQuery(lockHql, UUID.class)
                    .setParameter("behaviorGroupId", behaviorGroupId)
                    .setLockMode(PESSIMISTIC_WRITE);
            if (accountId != null) {
                lockQuery = lockQuery.setParameter("accountId", accountId);
            }
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
            String upsertQuery;
            if (featureFlipper.isUseOrgId()) {
                upsertQuery = "INSERT INTO behavior_group_action (behavior_group_id, endpoint_id, position, created) " +
                        "SELECT :behaviorGroupId, :endpointId, :position, :created " +
                        "WHERE EXISTS (SELECT 1 FROM endpoints WHERE org_id " +
                        (orgId == null ? "IS NULL" : "= :orgId") +
                        " AND id = :endpointId) " +
                        "ON CONFLICT (behavior_group_id, endpoint_id) DO UPDATE SET position = :position";
            } else {
                upsertQuery = "INSERT INTO behavior_group_action (behavior_group_id, endpoint_id, position, created) " +
                        "SELECT :behaviorGroupId, :endpointId, :position, :created " +
                        "WHERE EXISTS (SELECT 1 FROM endpoints WHERE account_id " +
                        (accountId == null ? "IS NULL" : "= :accountId") +
                        " AND id = :endpointId) " +
                        "ON CONFLICT (behavior_group_id, endpoint_id) DO UPDATE SET position = :position";
            }

            var sessionQuery = entityManager.createNativeQuery(upsertQuery)
                    .setParameter("behaviorGroupId", behaviorGroupId)
                    .setParameter("endpointId", endpointId)
                    .setParameter("position", endpointIds.indexOf(endpointId))
                    .setParameter("created", LocalDateTime.now(UTC));

            if (featureFlipper.isUseOrgId()) {
                if (orgId != null) {
                    sessionQuery = sessionQuery.setParameter("orgId", orgId);
                }
            } else {
                if (accountId != null) {
                    sessionQuery = sessionQuery.setParameter("accountId", accountId);
                }
            }

            sessionQuery.executeUpdate();
        }
    }

    public void updateDefaultBehaviorGroupActions(UUID behaviorGroupId, List<UUID> endpointIds) {
        updateBehaviorGroupActions(null, null, behaviorGroupId, endpointIds);
    }

    public List<BehaviorGroup> findBehaviorGroupsByEndpointId(String accountId, String orgId, UUID endpointId) {
        Endpoint endpoint = entityManager.find(Endpoint.class, endpointId);
        if (endpoint == null) {
            throw new NotFoundException("Endpoint not found");
        }

        List<BehaviorGroup> behaviorGroups;
        if (orgIdHelper.useOrgId(orgId)) {
            String query = "SELECT bg FROM BehaviorGroup bg LEFT JOIN FETCH bg.bundle JOIN bg.actions a WHERE bg.orgId = :orgId AND a.endpoint.id = :endpointId";
            behaviorGroups = entityManager.createQuery(query, BehaviorGroup.class)
                    .setParameter("orgId", orgId)
                    .setParameter("endpointId", endpointId)
                    .getResultList();
        } else {
            String query = "SELECT bg FROM BehaviorGroup bg LEFT JOIN FETCH bg.bundle JOIN bg.actions a WHERE bg.accountId = :accountId AND a.endpoint.id = :endpointId";
            behaviorGroups = entityManager.createQuery(query, BehaviorGroup.class)
                    .setParameter("accountId", accountId)
                    .setParameter("endpointId", endpointId)
                    .getResultList();
        }
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
            if (featureFlipper.isUseOrgId()) {
                if (isDefaultBehaviorGroup) {
                    if (behaviorGroup.getOrgId() != null) {
                        throw new BadRequestException("Default behavior groups must have a null accountId");
                    }
                } else {
                    if (behaviorGroup.getOrgId() == null) {
                        throw new BadRequestException("Only default behavior groups have a null accountId");
                    }
                }
            } else {
                if (isDefaultBehaviorGroup) {
                    if (behaviorGroup.getAccountId() != null) {
                        throw new BadRequestException("Default behavior groups must have a null accountId");
                    }
                } else {
                    if (behaviorGroup.getAccountId() == null) {
                        throw new BadRequestException("Only default behavior groups have a null accountId");
                    }
                }
            }
        }
    }
}
