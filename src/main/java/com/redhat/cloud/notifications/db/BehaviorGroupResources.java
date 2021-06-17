package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class BehaviorGroupResources {

    private static final ZoneId UTC = ZoneId.of("UTC");

    @Inject
    Mutiny.Session session;

    public Uni<BehaviorGroup> create(String accountId, BehaviorGroup behaviorGroup) {
        return Uni.createFrom().item(behaviorGroup)
                .onItem().transform(bg -> {
                    bg.setAccountId(accountId);
                    Bundle bundle = session.getReference(Bundle.class, bg.getBundleId());
                    bg.setBundle(bundle);
                    return bg;
                })
                .onItem().transformToUni(session::persist)
                .call(session::flush)
                .replaceWith(behaviorGroup)
                .onItem().invoke(BehaviorGroup::filterOutBundle);
    }

    public Uni<List<BehaviorGroup>> findByBundleId(String accountId, UUID bundleId) {
        return session.createNamedQuery("findByBundleId", BehaviorGroup.class)
                .setParameter("accountId", accountId)
                .setParameter("bundleId", bundleId)
                .getResultList()
                .onItem().invoke(behaviorGroups -> behaviorGroups.forEach(BehaviorGroup::filterOutBundle));
    }

    // TODO Should this be forbidden for default behavior groups?
    public Uni<Boolean> update(String accountId, BehaviorGroup behaviorGroup) {
        String query = "UPDATE BehaviorGroup SET displayName = :displayName WHERE accountId = :accountId AND id = :id";
        return session.createQuery(query)
                .setParameter("displayName", behaviorGroup.getDisplayName())
                .setParameter("accountId", accountId)
                .setParameter("id", behaviorGroup.getId())
                .executeUpdate()
                .call(session::flush)
                .onItem().transform(rowCount -> rowCount > 0);
    }

    // TODO Should this be forbidden for default behavior groups?
    public Uni<Boolean> delete(String accountId, UUID behaviorGroupId) {
        String query = "DELETE FROM BehaviorGroup WHERE accountId = :accountId AND id = :id";
        return session.createQuery(query)
                .setParameter("accountId", accountId)
                .setParameter("id", behaviorGroupId)
                .executeUpdate()
                .call(session::flush)
                .onItem().transform(rowCount -> rowCount > 0);
    }

    /*
     * Returns Boolean.TRUE if the event type was found and successfully updated.
     * Returns Boolean.FALSE if the event type was not found.
     * If an exception other than NoResultException is thrown during the update, the DB transaction will be rolled back.
     */
    public Uni<Boolean> updateEventTypeBehaviors(String accountId, UUID eventTypeId, Set<UUID> behaviorGroupIds) {
        return session.withTransaction(tx -> {

            // First, let's make sure the event type exists.
            return session.find(EventType.class, eventTypeId)
                    .onItem().ifNull().failWith(new NoResultException())
                    .onItem().call(() -> {

                        /*
                         * All event type behaviors that should no longer exist must be deleted.
                         * Deleted event type behaviors must obviously be owned by the current account.
                         */
                        String deleteQuery = "DELETE FROM EventTypeBehavior b " +
                                "WHERE eventType.id = :eventTypeId " +
                                "AND EXISTS (SELECT 1 FROM BehaviorGroup WHERE accountId = :accountId AND id = b.behaviorGroup.id)";
                        if (!behaviorGroupIds.isEmpty()) {
                            deleteQuery += " AND behaviorGroup.id NOT IN (:behaviorGroupIds)";
                        }
                        Mutiny.Query mutinyQuery = session.createQuery(deleteQuery)
                                .setParameter("accountId", accountId)
                                .setParameter("eventTypeId", eventTypeId);
                        if (!behaviorGroupIds.isEmpty()) {
                            mutinyQuery = mutinyQuery.setParameter("behaviorGroupIds", behaviorGroupIds);
                        }
                        return mutinyQuery.executeUpdate();

                    })
                    .onItem().transformToMulti(ignored -> Multi.createFrom().iterable(behaviorGroupIds))
                    .onItem().transformToUniAndConcatenate(behaviorGroupId -> {

                        /*
                         * Then, we'll insert all event type behaviors from the given behaviorGroupIds list.
                         * If an event type behavior already exists, nothing will happen (no exception will be thrown).
                         */
                        String insertQuery = "INSERT INTO event_type_behavior (event_type_id, behavior_group_id, created) " +
                                "SELECT :eventTypeId, :behaviorGroupId, :created " +
                                "WHERE EXISTS (SELECT 1 FROM behavior_group WHERE account_id = :accountId AND id = :behaviorGroupId) " +
                                "ON CONFLICT (event_type_id, behavior_group_id) DO NOTHING";
                        return session.createNativeQuery(insertQuery)
                                .setParameter("eventTypeId", eventTypeId)
                                .setParameter("behaviorGroupId", behaviorGroupId)
                                .setParameter("created", LocalDateTime.now(UTC))
                                .setParameter("accountId", accountId)
                                .executeUpdate();

                    })
                    .collect().asList()
                    .replaceWith(Boolean.TRUE)
                    // The following exception will be thrown if the event type is not found with the first query.
                    .onFailure(NoResultException.class).recoverWithItem(Boolean.FALSE);
        });
    }

    public Uni<List<EventType>> findEventTypesByBehaviorGroupId(String accountId, UUID behaviorGroupId) {
        String query = "SELECT e FROM EventType e LEFT JOIN FETCH e.application JOIN e.behaviors b " +
                "WHERE b.behaviorGroup.accountId = :accountId AND b.behaviorGroup.id = :behaviorGroupId";
        return session.createQuery(query, EventType.class)
                .setParameter("accountId", accountId)
                .setParameter("behaviorGroupId", behaviorGroupId)
                .getResultList();
    }

    public Uni<List<BehaviorGroup>> findBehaviorGroupsByEventTypeId(String accountId, UUID eventTypeId, Query limiter) {
        String query = "SELECT bg FROM BehaviorGroup bg JOIN bg.behaviors b WHERE bg.accountId = :accountId AND b.eventType.id = :eventTypeId";

        if (limiter != null) {
            query = limiter.getModifiedQuery(query);
        }

        Mutiny.Query<BehaviorGroup> mutinyQuery = session.createQuery(query, BehaviorGroup.class)
                .setParameter("accountId", accountId)
                .setParameter("eventTypeId", eventTypeId);

        if (limiter != null && limiter.getLimit() != null && limiter.getLimit().getLimit() > 0) {
            mutinyQuery = mutinyQuery.setMaxResults(limiter.getLimit().getLimit())
                    .setFirstResult(limiter.getLimit().getOffset());
        }

        return mutinyQuery.getResultList()
                .onItem().invoke(behaviorGroups ->
                        behaviorGroups.forEach(behaviorGroup -> behaviorGroup.filterOutBundle().filterOutActions())
                );
    }

    /*
     * Returns Boolean.TRUE if the behavior group was found and successfully updated.
     * Returns Boolean.FALSE if the behavior group was not found.
     * If an exception other than NoResultException is thrown during the update, the DB transaction will be rolled back.
     */
    public Uni<Boolean> updateBehaviorGroupActions(String accountId, UUID behaviorGroupId, List<UUID> endpointIds) {
        return session.withTransaction(tx -> {

            // First, let's make sure the behavior group exists and is owned by the current account.
            String checkQuery = "SELECT 1 FROM BehaviorGroup WHERE accountId = :accountId AND id = :id";
            return session.createQuery(checkQuery)
                    .setParameter("accountId", accountId)
                    .setParameter("id", behaviorGroupId)
                    .getSingleResult()
                    .onItem().call(() -> {

                        // All behavior group actions that should no longer exist must be deleted.
                        String deleteQuery = "DELETE FROM BehaviorGroupAction WHERE behaviorGroup.id = :behaviorGroupId";
                        if (!endpointIds.isEmpty()) {
                            deleteQuery += " AND endpoint.id NOT IN (:endpointIds)";
                        }
                        Mutiny.Query mutinyQuery = session.createQuery(deleteQuery)
                                .setParameter("behaviorGroupId", behaviorGroupId);
                        if (!endpointIds.isEmpty()) {
                            mutinyQuery = mutinyQuery.setParameter("endpointIds", endpointIds);
                        }
                        return mutinyQuery.executeUpdate();

                    })
                    .onItem().transformToMulti(ignored -> Multi.createFrom().iterable(endpointIds))
                    .onItem().transformToUniAndConcatenate(endpointId -> {

                        /*
                         * Then, we'll execute an "upsert" based on the given endpointIds list:
                         * - if an action already exists, its position will be updated
                         * - otherwise, the action will be inserted into the database
                         * In the end, all inserted or updated actions will have the same position than the endpointIds list order.
                         */
                        String upsertQuery = "INSERT INTO behavior_group_action (behavior_group_id, endpoint_id, position, created) " +
                                "SELECT :behaviorGroupId, :endpointId, :position, :created " +
                                "WHERE EXISTS (SELECT 1 FROM endpoints WHERE account_id = :accountId AND id = :endpointId) " +
                                "ON CONFLICT (behavior_group_id, endpoint_id) DO UPDATE SET position = :position";
                        return session.createNativeQuery(upsertQuery)
                                .setParameter("behaviorGroupId", behaviorGroupId)
                                .setParameter("endpointId", endpointId)
                                .setParameter("position", endpointIds.indexOf(endpointId))
                                .setParameter("created", LocalDateTime.now(UTC))
                                .setParameter("accountId", accountId)
                                .executeUpdate();

                    })
                    .collect().asList()
                    .replaceWith(Boolean.TRUE)
                    // The following exception will be thrown if the behavior group is not found with the first query.
                    .onFailure(NoResultException.class).recoverWithItem(Boolean.FALSE);
        });
    }

    public Uni<List<BehaviorGroup>> findBehaviorGroupsByEndpointId(String accountId, UUID endpointId) {
        String query = "SELECT bg FROM BehaviorGroup bg LEFT JOIN FETCH bg.bundle JOIN bg.actions a WHERE bg.accountId = :accountId AND a.endpoint.id = :endpointId";
        return session.createQuery(query, BehaviorGroup.class)
                .setParameter("accountId", accountId)
                .setParameter("endpointId", endpointId)
                .getResultList()
                .onItem().invoke(behaviorGroups -> behaviorGroups.forEach(BehaviorGroup::filterOutActions));
    }
}
