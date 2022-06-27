package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.FeatureFlipper;
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

    private static final ZoneId UTC = ZoneId.of("UTC");

    @Inject
    EntityManager entityManager;

    @Inject
    FeatureFlipper featureFlipper;

    public BehaviorGroup create(String accountId, @Valid BehaviorGroup behaviorGroup) {
        return this.create(accountId, behaviorGroup, false);
    }

    public BehaviorGroup createDefault(BehaviorGroup behaviorGroup) {
        return this.create(null, behaviorGroup, true);
    }

    @Transactional
    BehaviorGroup create(String accountId, BehaviorGroup behaviorGroup, boolean isDefaultBehaviorGroup) {
        checkBehaviorGroupDisplayNameDuplicate(accountId, behaviorGroup, isDefaultBehaviorGroup);

        behaviorGroup.setAccountId(accountId);
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
        final String query = "SELECT DISTINCT b FROM BehaviorGroup b LEFT JOIN FETCH b.actions a " +
                "WHERE b.accountId IS NULL " +
                "ORDER BY b.created DESC, a.position ASC";

        List<BehaviorGroup> behaviorGroups = entityManager.createQuery(query, BehaviorGroup.class)
                .getResultList();
        for (BehaviorGroup behaviorGroup : behaviorGroups) {
            behaviorGroup.filterOutBundle();
        }
        return behaviorGroups;
    }

    public List<BehaviorGroup> findByBundleId(String accountId, UUID bundleId) {
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
                "WHERE (b.accountId = :accountId OR b.accountId IS NULL) AND b.bundle.id = :bundleId " +
                "ORDER BY b.created DESC, a.position ASC";
        List<BehaviorGroup> behaviorGroups = entityManager.createQuery(query, BehaviorGroup.class)
                .setParameter("accountId", accountId)
                .setParameter("bundleId", bundleId)
                .setHint(HINT_PASS_DISTINCT_THROUGH, false)
                .getResultList();
        for (BehaviorGroup behaviorGroup : behaviorGroups) {
            behaviorGroup.filterOutBundle();
        }
        return behaviorGroups;
    }

    public boolean update(String accountId, BehaviorGroup behaviorGroup) {
        return this.update(accountId, behaviorGroup, false);
    }

    public boolean updateDefault(BehaviorGroup behaviorGroup) {
        return this.update(null, behaviorGroup, true);
    }

    @Transactional
    boolean update(String accountId, BehaviorGroup behaviorGroup, boolean isDefaultBehavior) {
        checkBehaviorGroupDisplayNameDuplicate(accountId, behaviorGroup, isDefaultBehavior);

        checkBehaviorGroup(behaviorGroup.getId(), isDefaultBehavior);
        String query = "UPDATE BehaviorGroup SET displayName = :displayName WHERE id = :id";

        if (accountId == null) {
            query += " AND accountId IS NULL";
        } else {
            query += " AND accountId = :accountId";
        }

        javax.persistence.Query q = entityManager.createQuery(query)
                .setParameter("displayName", behaviorGroup.getDisplayName())
                .setParameter("id", behaviorGroup.getId());

        if (accountId != null) {
            q = q.setParameter("accountId", accountId);
        }

        return q.executeUpdate() > 0;
    }

    private void checkBehaviorGroupDisplayNameDuplicate(String accountId, BehaviorGroup behaviorGroup, boolean isDefaultBehaviorGroup) {
        if (!featureFlipper.isEnforceBehaviorGroupNameUnicity()) {
            // The check is disabled from configuration.
            return;
        }

        String hql = "SELECT COUNT(*) FROM BehaviorGroup WHERE displayName = :displayName";
        if (behaviorGroup.getId() != null) { // The behavior group already exists in the DB, it's being updated.
            hql += " AND id != :behaviorGroupId";
        }
        if (isDefaultBehaviorGroup) {
            hql += " AND accountId IS NULL";
        } else {
            hql += " AND accountId = :accountId";
        }

        TypedQuery<Long> query = entityManager.createQuery(hql, Long.class)
                .setParameter("displayName", behaviorGroup.getDisplayName());
        if (behaviorGroup.getId() != null) {
            query.setParameter("behaviorGroupId", behaviorGroup.getId());
        }
        if (!isDefaultBehaviorGroup) {
            query.setParameter("accountId", accountId);
        }
        if (query.getSingleResult() > 0) {
            throw new BadRequestException("A behavior group with display name [" + behaviorGroup.getDisplayName() + "] already exists");
        }
    }

    public boolean delete(String accountId, UUID behaviorGroupId) {
        return this.delete(accountId, behaviorGroupId, false);
    }

    public boolean deleteDefault(UUID behaviorGroupId) {
        return this.delete(null, behaviorGroupId, true);
    }

    @Transactional
    public boolean delete(String accountId, UUID behaviorGroupId, boolean isDefaultBehavior) {
        checkBehaviorGroup(behaviorGroupId, isDefaultBehavior);
        String query = "DELETE FROM BehaviorGroup WHERE id = :id";

        if (accountId == null) {
            query += " AND accountId IS NULL";
        } else {
            query += " AND accountId = :accountId";
        }

        javax.persistence.Query q = entityManager.createQuery(query)
                .setParameter("id", behaviorGroupId);

        if (accountId != null) {
            q = q.setParameter("accountId", accountId);
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
        String deleteQuery = "DELETE FROM EventTypeBehavior b " +
                "WHERE eventType.id = :eventTypeId " +
                "AND id.behaviorGroupId = :behaviorGroupId";
        entityManager.createQuery(deleteQuery)
                .setParameter("eventTypeId", eventTypeId)
                .setParameter("behaviorGroupId", behaviorGroupId)
                .executeUpdate();
        return true;
    }

    @Transactional
    public void updateEventTypeBehaviors(String accountId, UUID eventTypeId, Set<UUID> behaviorGroupIds) {
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
                    "WHERE behaviorGroup.accountId = :accountId AND eventType.id = :eventTypeId";
            List<UUID> behaviorsFromDb = entityManager.createQuery(lockQuery, UUID.class)
                    .setParameter("accountId", accountId)
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

    public List<EventType> findEventTypesByBehaviorGroupId(String accountId, UUID behaviorGroupId) {
        BehaviorGroup behaviorGroup = entityManager.find(BehaviorGroup.class, behaviorGroupId);
        if (behaviorGroup == null) {
            throw new NotFoundException("Behavior group not found");
        }

        String query = "SELECT e FROM EventType e LEFT JOIN FETCH e.application JOIN e.behaviors b " +
                "WHERE (b.behaviorGroup.accountId = :accountId OR b.behaviorGroup.accountId IS NULL) AND b.behaviorGroup.id = :behaviorGroupId";
        return entityManager.createQuery(query, EventType.class)
                .setParameter("accountId", accountId)
                .setParameter("behaviorGroupId", behaviorGroupId)
                .getResultList();
    }

    public List<BehaviorGroup> findBehaviorGroupsByEventTypeId(String accountId, UUID eventTypeId, Query limiter) {
        EventType eventType = entityManager.find(EventType.class, eventTypeId);
        if (eventType == null) {
            throw new NotFoundException("Event type not found");
        }

        String query = "SELECT bg FROM BehaviorGroup bg JOIN bg.behaviors b WHERE (bg.accountId = :accountId OR bg.accountId IS NULL) AND b.eventType.id = :eventTypeId";

        if (limiter != null) {
            query = limiter.getModifiedQuery(query);
        }

        TypedQuery<BehaviorGroup> typedQuery = entityManager.createQuery(query, BehaviorGroup.class)
                .setParameter("accountId", accountId)
                .setParameter("eventTypeId", eventTypeId);

        if (limiter != null && limiter.getLimit() != null && limiter.getLimit().getLimit() > 0) {
            typedQuery = typedQuery.setMaxResults(limiter.getLimit().getLimit())
                    .setFirstResult(limiter.getLimit().getOffset());
        }

        List<BehaviorGroup> behaviorGroups = typedQuery.getResultList();
        for (BehaviorGroup behaviorGroup : behaviorGroups) {
            behaviorGroup.filterOutBundle().filterOutActions();
        }
        return behaviorGroups;
    }

    @Transactional
    public void updateBehaviorGroupActions(String accountId, UUID behaviorGroupId, List<UUID> endpointIds) {

        // First, let's make sure the behavior group exists and is owned by the current account.
        String checkBehaviorGroupQuery = "SELECT 1 FROM BehaviorGroup WHERE id = :id AND accountId ";
        if (accountId == null) {
            checkBehaviorGroupQuery += "IS NULL";
        } else {
            checkBehaviorGroupQuery += "= :accountId";
        }

        TypedQuery<Integer> query = entityManager.createQuery(checkBehaviorGroupQuery, Integer.class)
                .setParameter("id", behaviorGroupId);

        if (accountId != null) {
            query = query.setParameter("accountId", accountId);
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
        String lockQuery = "SELECT endpoint.id FROM BehaviorGroupAction " +
                "WHERE behaviorGroup.accountId = :accountId AND behaviorGroup.id = :behaviorGroupId";
        List<UUID> actionsFromDb = entityManager.createQuery(lockQuery, UUID.class)
                .setParameter("accountId", accountId)
                .setParameter("behaviorGroupId", behaviorGroupId)
                .setLockMode(PESSIMISTIC_WRITE)
                .getResultList();

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
                    "WHERE EXISTS (SELECT 1 FROM endpoints WHERE account_id " +
                    (accountId == null ? "IS NULL" : "= :accountId") +
                    " AND id = :endpointId) " +
                    "ON CONFLICT (behavior_group_id, endpoint_id) DO UPDATE SET position = :position";

            var sessionQuery = entityManager.createNativeQuery(upsertQuery)
                    .setParameter("behaviorGroupId", behaviorGroupId)
                    .setParameter("endpointId", endpointId)
                    .setParameter("position", endpointIds.indexOf(endpointId))
                    .setParameter("created", LocalDateTime.now(UTC));

            if (accountId != null) {
                sessionQuery = sessionQuery.setParameter("accountId", accountId);
            }

            sessionQuery.executeUpdate();
        }
    }

    public void updateDefaultBehaviorGroupActions(UUID behaviorGroupId, List<UUID> endpointIds) {
        updateBehaviorGroupActions(null, behaviorGroupId, endpointIds);
    }

    public List<BehaviorGroup> findBehaviorGroupsByEndpointId(String accountId, UUID endpointId) {
        Endpoint endpoint = entityManager.find(Endpoint.class, endpointId);
        if (endpoint == null) {
            throw new NotFoundException("Endpoint not found");
        }

        String query = "SELECT bg FROM BehaviorGroup bg LEFT JOIN FETCH bg.bundle JOIN bg.actions a WHERE bg.accountId = :accountId AND a.endpoint.id = :endpointId";
        List<BehaviorGroup> behaviorGroups = entityManager.createQuery(query, BehaviorGroup.class)
                .setParameter("accountId", accountId)
                .setParameter("endpointId", endpointId)
                .getResultList();
        for (BehaviorGroup behaviorGroup : behaviorGroups) {
            behaviorGroup.filterOutActions();
        }
        return behaviorGroups;
    }

    private void checkBehaviorGroup(UUID behaviorGroupId, boolean isDefaultBehaviorGroup) {
        BehaviorGroup behaviorGroup = entityManager.find(BehaviorGroup.class, behaviorGroupId);
        if (behaviorGroup == null) {
            throw new NotFoundException("Behavior group not found");
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
