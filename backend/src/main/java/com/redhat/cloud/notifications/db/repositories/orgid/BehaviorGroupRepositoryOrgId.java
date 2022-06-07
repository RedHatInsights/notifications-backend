package com.redhat.cloud.notifications.db.repositories.orgid;

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
import javax.ws.rs.core.Response.Status;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;

@ApplicationScoped
public class BehaviorGroupRepositoryOrgId {

    private static final ZoneId UTC = ZoneId.of("UTC");

    @Inject
    EntityManager entityManager;

    public BehaviorGroup create(String orgId, @Valid BehaviorGroup behaviorGroup) {
        return this.create(orgId, behaviorGroup, false);
    }

    public BehaviorGroup createDefault(BehaviorGroup behaviorGroup) {
        return this.create(null, behaviorGroup, true);
    }

    @Transactional
    BehaviorGroup create(String orgId, BehaviorGroup behaviorGroup, boolean isDefaultBehaviorGroup) {
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
        final String query = "SELECT DISTINCT b FROM BehaviorGroup b LEFT JOIN FETCH b.actions a " +
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

        List<BehaviorGroup> behaviorGroups = entityManager.createNamedQuery("findByBundleIdWithOrgId", BehaviorGroup.class)
                .setParameter("orgId", orgId)
                .setParameter("bundleId", bundleId)
                .getResultList();
        for (BehaviorGroup behaviorGroup : behaviorGroups) {
            behaviorGroup.filterOutBundle();
        }
        return behaviorGroups;
    }

    public boolean update(String orgId, BehaviorGroup behaviorGroup) {
        return this.update(orgId, behaviorGroup, false);
    }

    public boolean updateDefault(BehaviorGroup behaviorGroup) {
        return this.update(null, behaviorGroup, true);
    }

    @Transactional
    boolean update(String orgId, BehaviorGroup behaviorGroup, boolean isDefaultBehavior) {
        checkBehavior(behaviorGroup.getId(), isDefaultBehavior);
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

        return q.executeUpdate() > 0;
    }

    public boolean delete(String orgId, UUID behaviorGroupId) {
        return this.delete(orgId, behaviorGroupId, false);
    }

    public boolean deleteDefault(UUID behaviorGroupId) {
        return this.delete(null, behaviorGroupId, true);
    }

    @Transactional
    public boolean delete(String orgId, UUID behaviorGroupId, boolean isDefaultBehavior) {
        checkBehavior(behaviorGroupId, isDefaultBehavior);
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
        checkBehavior(behaviorGroupId, true);
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
        checkBehavior(behaviorGroupId, true);
        String deleteQuery = "DELETE FROM EventTypeBehavior b " +
                "WHERE eventType.id = :eventTypeId " +
                "AND id.behaviorGroupId = :behaviorGroupId";
        entityManager.createQuery(deleteQuery)
                .setParameter("eventTypeId", eventTypeId)
                .setParameter("behaviorGroupId", behaviorGroupId)
                .executeUpdate();
        return true;
    }

    /*
     * Returns true if the event type was found and successfully updated.
     * Returns false if the event type was not found.
     * If an exception other than NoResultException is thrown during the update, the DB transaction will be rolled back.
     */
    @Transactional
    public boolean updateEventTypeBehaviors(String orgId, UUID eventTypeId, Set<UUID> behaviorGroupIds) {
        // First, let's make sure the event type exists.
        EventType eventType = entityManager.find(EventType.class, eventTypeId);
        if (eventType == null) {
            throw new NotFoundException("Event type not found");
        } else {

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

            /*
             * All event type behaviors that should no longer exist must be deleted.
             * Deleted event type behaviors must obviously be owned by the current account.
             */
            String deleteQuery = "DELETE FROM EventTypeBehavior b " +
                    "WHERE b.eventType.id = :eventTypeId " +
                    "AND EXISTS (SELECT 1 FROM BehaviorGroup WHERE orgId = :orgId AND id = b.behaviorGroup.id)";
            if (!behaviorGroupIds.isEmpty()) {
                deleteQuery += " AND b.behaviorGroup.id NOT IN (:behaviorGroupIds)";
            }
            javax.persistence.Query q = entityManager.createQuery(deleteQuery)
                    .setParameter("orgId", orgId)
                    .setParameter("eventTypeId", eventTypeId);
            if (!behaviorGroupIds.isEmpty()) {
                q = q.setParameter("behaviorGroupIds", behaviorGroupIds);
            }
            q.executeUpdate();

            for (UUID behaviorGroupId : behaviorGroupIds) {
                /*
                 * Then, we'll insert all event type behaviors from the given behaviorGroupIds list.
                 * If an event type behavior already exists, nothing will happen (no exception will be thrown).
                 */
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
            return true;
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
            behaviorGroup.filterOutBundle().filterOutActions();
        }
        return behaviorGroups;
    }

    /*
     * Returns Status.OK if the behavior group was found and successfully updated.
     * Returns Status.NOT_FOUND if the behavior group was not found.
     * If an exception other than NoResultException is thrown during the update, the DB transaction will be rolled back.
     */
    @Transactional
    public Status updateBehaviorGroupActions(String orgId, UUID behaviorGroupId, List<UUID> endpointIds) {

        // First, let's make sure the behavior group exists and is owned by the current account.
        String checkBehaviorGroupQuery = "SELECT 1 FROM BehaviorGroup WHERE id = :id AND orgId ";
        if (orgId == null) {
            checkBehaviorGroupQuery += "IS NULL";
        } else {
            checkBehaviorGroupQuery += "= :orgId";
        }

        javax.persistence.Query query = entityManager.createQuery(checkBehaviorGroupQuery, Integer.class)
                .setParameter("id", behaviorGroupId);

        if (orgId != null) {
            query = query.setParameter("orgId", orgId);
        }

        try {
            query.getSingleResult();
        } catch (NoResultException e) {
            return NOT_FOUND;
        }

        // All behavior group actions that should no longer exist must be deleted.
        String deleteQuery = "DELETE FROM BehaviorGroupAction WHERE behaviorGroup.id = :behaviorGroupId";
        if (!endpointIds.isEmpty()) {
            deleteQuery += " AND endpoint.id NOT IN (:endpointIds)";
        }
        javax.persistence.Query q = entityManager.createQuery(deleteQuery)
                .setParameter("behaviorGroupId", behaviorGroupId);
        if (!endpointIds.isEmpty()) {
            q = q.setParameter("endpointIds", endpointIds);
        }
        q.executeUpdate();

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
        return OK;
    }

    public Status updateDefaultBehaviorGroupActions(UUID behaviorGroupId, List<UUID> endpointIds) {
        return updateBehaviorGroupActions(null, behaviorGroupId, endpointIds);
    }

    public List<BehaviorGroup> findBehaviorGroupsByEndpoint(String orgId, UUID endpointId) {
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
            behaviorGroup.filterOutActions();
        }
        return behaviorGroups;
    }

    private void checkBehavior(UUID behaviorGroupId, boolean isDefaultBehaviorGroup) {
        BehaviorGroup behaviorGroup = entityManager.find(BehaviorGroup.class, behaviorGroupId);
        if (behaviorGroup == null) {
            throw new NotFoundException("Behavior group not found");
        } else {
            if (isDefaultBehaviorGroup) {
                if (behaviorGroup.getOrgId() != null) {
                    throw new BadRequestException("Default behavior groups must have a null orgId");
                }
            } else {
                if (behaviorGroup.getOrgId() == null) {
                    throw new BadRequestException("Only default behavior groups have a null orgId");
                }
            }
        }
    }
}
