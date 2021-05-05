package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeBehavior;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import javax.ws.rs.NotFoundException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class BehaviorGroupResources {

    private static final Logger LOGGER = Logger.getLogger(BehaviorGroupResources.class.getName());
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
                .replaceWith(behaviorGroup);
    }

    public Uni<List<BehaviorGroup>> findByBundleId(String accountId, UUID bundleId) {
        return session.createNamedQuery("findByBundleId", BehaviorGroup.class)
                .setParameter("accountId", accountId)
                .setParameter("bundleId", bundleId)
                .getResultList();
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

    public Uni<Boolean> addEventTypeBehavior(String accountId, UUID eventTypeId, UUID behaviorGroupId) {
        String query = "SELECT COUNT(*) FROM BehaviorGroup WHERE accountId = :accountId AND id = :id";
        return session.createQuery(query, Long.class)
                .setParameter("accountId", accountId)
                .setParameter("id", behaviorGroupId)
                .getSingleResult()
                .onItem().transform(count -> {
                    if (count == 0L) {
                        throw new NotFoundException("Behavior group not found: " + behaviorGroupId);
                    } else {
                        EventType eventType = session.getReference(EventType.class, eventTypeId);
                        BehaviorGroup behaviorGroup = session.getReference(BehaviorGroup.class, behaviorGroupId);
                        return new EventTypeBehavior(eventType, behaviorGroup);
                    }
                })
                .onItem().transformToUni(session::persist)
                .onItem().call(session::flush)
                .replaceWith(Boolean.TRUE)
                .onFailure().recoverWithItem(failure -> {
                    LOGGER.log(Level.WARNING, "Event type behavior addition failed", failure);
                    return Boolean.FALSE;
                });
    }

    public Uni<Boolean> deleteEventTypeBehavior(String accountId, UUID eventTypeId, UUID behaviorGroupId) {
        String query = "DELETE FROM EventTypeBehavior WHERE eventType.id = :eventTypeId AND behaviorGroup.id = :behaviorGroupId " +
                "AND behaviorGroup.id IN (SELECT id FROM BehaviorGroup WHERE accountId = :accountId)";
        return session.createQuery(query)
                .setParameter("eventTypeId", eventTypeId)
                .setParameter("behaviorGroupId", behaviorGroupId)
                .setParameter("accountId", accountId)
                .executeUpdate()
                .call(session::flush)
                .onItem().transform(rowCount -> rowCount > 0);
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
                .onItem().invoke(behaviorGroups -> behaviorGroups.forEach(BehaviorGroup::filterOutActions));
    }

    /*
     * Returns Boolean.TRUE if the behavior group was found and successfully updated.
     * Returns Boolean.FALSE if the behavior group was not found.
     * If an exception other than NoResultException is thrown during the update, the DB transaction will be rolled back.
     */
    public Uni<Boolean> updateBehaviorGroupActions(String accountId, UUID behaviorGroupId, List<UUID> endpointIds) {
        return session.withTransaction(tx -> {

            // First, let's make sure the behavior group exists and is owned by the current account.
            String checkQuery = "SELECT id FROM BehaviorGroup WHERE accountId = :accountId AND id = :id";
            return session.createQuery(checkQuery, UUID.class)
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
                                "VALUES (:behaviorGroupId, :endpointId, :position, :created) " +
                                "ON CONFLICT (behavior_group_id, endpoint_id) DO UPDATE SET position = :position";
                        return session.createNativeQuery(upsertQuery)
                                .setParameter("behaviorGroupId", behaviorGroupId)
                                .setParameter("endpointId", endpointId)
                                .setParameter("position", endpointIds.indexOf(endpointId))
                                .setParameter("created", LocalDateTime.now(UTC))
                                .executeUpdate();

                    })
                    .collect().asList()
                    .replaceWith(Boolean.TRUE)
                    // The following exception will be thrown if the behavior group is not found with the first query.
                    .onFailure(NoResultException.class).recoverWithItem(Boolean.FALSE);
        });
    }

    // This should only be called from an internal API. That's why we don't have to validate the accountId.
    public Uni<Integer> setDefaultBehaviorGroup(UUID bundleId, UUID behaviorGroupId) {
        String query = "UPDATE BehaviorGroup SET defaultBehavior = (CASE WHEN id = :behaviorGroupId THEN TRUE ELSE FALSE END) " +
                "WHERE bundle.id = :bundleId";
        return session.createQuery(query)
                .setParameter("behaviorGroupId", behaviorGroupId)
                .setParameter("bundleId", bundleId)
                .executeUpdate()
                .call(session::flush);
    }

    public Uni<Boolean> muteEventType(String accountId, UUID eventTypeId) {
        String query = "DELETE FROM EventTypeBehavior b " +
                "WHERE b.behaviorGroup.id IN (SELECT id FROM BehaviorGroup WHERE accountId = :accountId) AND b.eventType.id = :eventTypeId";
        return session.createQuery(query)
                .setParameter("accountId", accountId)
                .setParameter("eventTypeId", eventTypeId)
                .executeUpdate()
                .call(session::flush)
                .onItem().transform(rowCount -> rowCount > 0);
    }
}
