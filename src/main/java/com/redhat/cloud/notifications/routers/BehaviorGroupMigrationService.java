package com.redhat.cloud.notifications.routers;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.BehaviorGroupAction;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointTarget;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeBehavior;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.logging.Logger;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@RequestScoped
@Path("/internal/behaviorGroups/migrate")
public class BehaviorGroupMigrationService {

    public static final String CONFIRMATION_TOKEN = "ready-set-go";

    private static final Logger LOGGER = Logger.getLogger(BehaviorGroupMigrationService.class.getName());

    /*
     * This is used to dynamically create behavior groups names at persistence time.
     * See calling point for more details.
     */
    private final Map<String, AtomicInteger> accountBehaviorGroupIndexes = new ConcurrentHashMap<>();

    private volatile MigrationReport report = new MigrationReport();

    @Inject
    Mutiny.Session session;

    @GET
    @Produces(APPLICATION_JSON)
    public Uni<MigrationReport> migrate(@QueryParam("confirmation-token") String confirmationToken) {
        long start = System.currentTimeMillis();
        if (!CONFIRMATION_TOKEN.equals(confirmationToken)) {
            throw new BadRequestException("You forgot to confirm the migration!");
        }
        // All DB changes will be rolled-back if anything goes wrong.
        return session.withTransaction(tx -> {
            LOGGER.debug("[BG MIGRATION] Start");
            return Uni.createFrom().item(new AccountBehaviorGroupsAggregator())
                    .onItem().transformToUni(aggregator -> {
                        LOGGER.debug("[BG MIGRATION] Migrating global default actions");
                        // Let's iterate over all accounts that saved default actions.
                        return session.createQuery("SELECT DISTINCT id.accountId FROM EndpointDefault", String.class)
                                .getResultList()
                                .onItem().invoke(accounts -> LOGGER.debugf("[BG MIGRATION] __ %d account(s) with global default actions found", accounts.size()))
                                .onItem().transformToMulti(Multi.createFrom()::iterable)
                                .onItem().transformToUniAndConcatenate(accountId -> {
                                    report.accountsWithDefaults.incrementAndGet();
                                    LOGGER.debugf("[BG MIGRATION] __ Account '%s'", accountId);
                                    /*
                                     * The current default actions are not bound to a specific bundle, so we have to create a
                                     * behavior group that will contain the default actions for each existing bundle.
                                     */
                                    return session.createQuery("FROM Bundle", Bundle.class)
                                            .getResultList()
                                            .onItem().invoke(bundles -> LOGGER.debugf("[BG MIGRATION] ____ %d bundle(s) found", bundles.size()))
                                            .onItem().transformToMulti(Multi.createFrom()::iterable)
                                            .onItem().transformToUniAndConcatenate(bundle -> {
                                                LOGGER.debugf("[BG MIGRATION] ____ Building behavior group for bundle '%s'", bundle.getName());
                                                MigrationBehaviorGroup mBehaviorGroup = new MigrationBehaviorGroup();
                                                mBehaviorGroup.accountId = accountId;
                                                mBehaviorGroup.bundle = bundle;
                                                // Let's iterate over the current global default actions of the account.
                                                return session.createQuery("FROM Endpoint e WHERE e.accountId = :accountId AND EXISTS(FROM EndpointDefault WHERE endpoint = e AND id.accountId = :accountId)", Endpoint.class)
                                                        .setParameter("accountId", accountId)
                                                        .getResultList()
                                                        .onItem().invoke(endpoints -> {
                                                            for (Endpoint endpoint : endpoints) {
                                                                mBehaviorGroup.actions.add(endpoint);
                                                            }
                                                            LOGGER.debugf("[BG MIGRATION] ______ %d action(s) added", endpoints.size());
                                                        })
                                                        // Let's iterate over the event types from the current bundle that are linked with the global default actions.
                                                        .replaceWith(session.createQuery("FROM EventType et WHERE et.application.bundle = :bundle AND EXISTS(FROM EndpointTarget WHERE eventType = et AND id.accountId = :accountId AND endpoint.type = :endpointType)", EventType.class)
                                                                .setParameter("bundle", bundle)
                                                                .setParameter("accountId", accountId)
                                                                .setParameter("endpointType", EndpointType.DEFAULT)
                                                                .getResultList()
                                                                .onItem().invoke(eventTypes -> {
                                                                    if (eventTypes.isEmpty()) {
                                                                        aggregator.aggregate(mBehaviorGroup, Optional.empty(), report);
                                                                        LOGGER.debug("[BG MIGRATION] ______ 0 event type linked, the behavior group will be created anyway");
                                                                    } else {
                                                                        for (EventType eventType : eventTypes) {
                                                                            aggregator.aggregate(mBehaviorGroup, Optional.of(eventType), report);
                                                                        }
                                                                        LOGGER.debugf("[BG MIGRATION] ______ %d event type(s) added to the links set", eventTypes.size());
                                                                    }
                                                                })
                                                        )
                                                        .onItem().invoke(ignored -> LOGGER.debug("[BG MIGRATION] ____ Done"));
                                            })
                                            .onItem().ignoreAsUni()
                                            .onItem().invoke(ignored -> LOGGER.debug("[BG MIGRATION] __ Done"));
                                })
                                .onItem().ignoreAsUni()
                                .onItem().invoke(ignored -> {
                                    LOGGER.debug("[BG MIGRATION] Done");
                                    LOGGER.debug("[BG MIGRATION] Migrating non-default actions");
                                })
                                // Let's iterate over all accounts that saved non-default actions.
                                .replaceWith(session.createQuery("SELECT DISTINCT id.accountId FROM EndpointTarget WHERE endpoint.type <> :endpointType", String.class)
                                        .setParameter("endpointType", EndpointType.DEFAULT)
                                        .getResultList()
                                        .onItem().invoke(accounts -> LOGGER.debugf("[BG MIGRATION] __ %d account(s) with non-default actions found", accounts.size()))
                                        .onItem().transformToMulti(Multi.createFrom()::iterable)
                                        .onItem().transformToUniAndConcatenate(accountId -> {
                                            report.accountsWithNonDefaults.incrementAndGet();
                                            LOGGER.debugf("[BG MIGRATION] __ Account '%s'", accountId);
                                            /*
                                             * Let's iterate over the event types that are linked with non-default actions.
                                             * We can't iterate directly over the EndpointTarget records because we need to group the actions by event type for the behavior group creation.
                                             */
                                            return session.createQuery("FROM EventType et WHERE EXISTS(FROM EndpointTarget WHERE eventType = et AND id.accountId = :accountId AND endpoint.type <> :endpointType)", EventType.class)
                                                    .setParameter("accountId", accountId)
                                                    .setParameter("endpointType", EndpointType.DEFAULT)
                                                    .getResultList()
                                                    .onItem().invoke(eventTypes -> LOGGER.debugf("[BG MIGRATION] ____ %d event type(s) linked with non-default actions found", eventTypes.size()))
                                                    .onItem().transformToMulti(Multi.createFrom()::iterable)
                                                    .onItem().transformToUniAndConcatenate(eventType -> {
                                                        LOGGER.debugf("[BG MIGRATION] ____ Building behavior group for event type '%s'", eventType.getName());
                                                        MigrationBehaviorGroup mBehaviorGroup = new MigrationBehaviorGroup();
                                                        mBehaviorGroup.accountId = accountId;
                                                        // Fetching the bundle from the event type does not work for a mysterious reason (probably an Hibernate Reactive bug) so let's retrieve it manually...
                                                        return session.createQuery("FROM Bundle b WHERE EXISTS(FROM EventType et JOIN et.application a WHERE et = :eventType AND a.bundle = b)", Bundle.class)
                                                                .setParameter("eventType", eventType)
                                                                .getSingleResult()
                                                                .onItem().transformToUni(bundle -> {
                                                                    mBehaviorGroup.bundle = bundle;
                                                                    // We can now iterate over the links for the current event type.
                                                                    return session.createQuery("FROM EndpointTarget t LEFT JOIN FETCH t.endpoint e WHERE t.eventType = :eventType AND t.id.accountId = :accountId AND e.type <> :endpointType", EndpointTarget.class)
                                                                            .setParameter("eventType", eventType)
                                                                            .setParameter("accountId", accountId)
                                                                            .setParameter("endpointType", EndpointType.DEFAULT)
                                                                            .getResultList()
                                                                            .onItem().invoke(targets -> {
                                                                                for (EndpointTarget target : targets) {
                                                                                    mBehaviorGroup.actions.add(target.getEndpoint());
                                                                                }
                                                                                LOGGER.debugf("[BG MIGRATION] ______ %d action(s) added", targets.size());
                                                                                aggregator.aggregate(mBehaviorGroup, Optional.of(eventType), report);
                                                                                LOGGER.debugf("[BG MIGRATION] ______ 1 event type added to the links set", targets.size());
                                                                            });
                                                                });
                                                    })
                                                    .onItem().ignoreAsUni()
                                                    .onItem().invoke(ignored -> LOGGER.debug("[BG MIGRATION] ____ Done"));
                                        })
                                        .onItem().ignoreAsUni()
                                        .onItem().invoke(ignored -> LOGGER.debug("[BG MIGRATION] __ Done"))
                                )
                                .onItem().invoke(ignored -> {
                                    LOGGER.debug("[BG MIGRATION] Done");
                                    LOGGER.debug("[BG MIGRATION] Starting DB behavior groups creation");
                                })
                                /*
                                 * Now we'll iterate over the aggregator entries.
                                 * Each entry key is a behavior group and its value is a Set containing all event types that should be linked with the behavior group.
                                 */
                                .replaceWith(aggregator.entrySet())
                                .onItem().transformToMulti(aggregatorEntries -> Multi.createFrom().iterable(aggregatorEntries))
                                .onItem().transformToUniAndConcatenate(aggregatorEntry -> {
                                    // It's time to finally persist a behavior group.
                                    BehaviorGroup behaviorGroup = new BehaviorGroup();
                                    behaviorGroup.setAccountId(aggregatorEntry.getKey().accountId);
                                    behaviorGroup.setBundle(aggregatorEntry.getKey().bundle);
                                    behaviorGroup.setBundleId(aggregatorEntry.getKey().bundle.getId()); // Yes, we need to set that even if the bundle was already set.
                                    // We have to generate a dynamic display name for the behavior group.
                                    int displayNameIndex = accountBehaviorGroupIndexes.computeIfAbsent(behaviorGroup.getAccountId(), key -> new AtomicInteger()).incrementAndGet();
                                    behaviorGroup.setDisplayName("Behavior group " + displayNameIndex);
                                    // Endpoints need to be ordered to determine the actions positions in the UI.
                                    List<Endpoint> orderedEndpoints = new ArrayList<>(aggregatorEntry.getKey().actions);
                                    LOGGER.debugf("[BG MIGRATION] __ Persisting BehaviorGroup[account=%s, bundle=%s, displayName=%s] created", behaviorGroup.getAccountId(), behaviorGroup.getBundle().getName(), behaviorGroup.getDisplayName());
                                    return session.persist(behaviorGroup)
                                            .onItem().invoke(ignored -> report.behaviorGroupsPersisted.incrementAndGet())
                                            .onItem().transformToMulti(ignored -> Multi.createFrom().iterable(orderedEndpoints))
                                            .onItem().transformToUniAndConcatenate(endpoint -> {
                                                BehaviorGroupAction action = new BehaviorGroupAction(behaviorGroup, endpoint);
                                                action.setPosition(orderedEndpoints.indexOf(endpoint));
                                                return session.persist(action)
                                                        .onItem().invoke(ignored -> {
                                                            report.actionsPersisted.incrementAndGet();
                                                            LOGGER.debugf("[BG MIGRATION] ____ BehaviorGroupAction[endpoint=%s, position=%s] persisted", endpoint.getName(), action.getPosition());
                                                        });
                                            })
                                            .onItem().ignoreAsUni()
                                            .onItem().transformToMulti(ignored -> Multi.createFrom().iterable(aggregatorEntry.getValue()))
                                            .onItem().transformToUniAndConcatenate(eventType -> {
                                                EventTypeBehavior behavior = new EventTypeBehavior(eventType, behaviorGroup);
                                                return session.persist(behavior)
                                                        .onItem().invoke(ignored -> {
                                                            report.behaviorsPersisted.incrementAndGet();
                                                            LOGGER.debugf("[BG MIGRATION] ____ EventTypeBehavior[eventType=%s] persisted", eventType.getName());
                                                        });
                                            })
                                            .onItem().ignoreAsUni()
                                            .onItem().invoke(ignored -> LOGGER.debug("[BG MIGRATION] __ Done"));
                                })
                                .onItem().ignoreAsUni()
                                .onItem().invoke(ignored -> LOGGER.debug("[BG MIGRATION] Done"));
                    });
        })
        .onItem().invoke(ignored -> {
            report.durationInMs.set(System.currentTimeMillis() - start);
            LOGGER.debug("[BG MIGRATION] End");
        })
        .replaceWith(report);
    }

    private static class MigrationBehaviorGroup {

        private String accountId;
        private Bundle bundle;
        private Set<Endpoint> actions = new HashSet<>();

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o instanceof MigrationBehaviorGroup) {
                MigrationBehaviorGroup other = (MigrationBehaviorGroup) o;
                return Objects.equals(accountId, other.accountId) &&
                        Objects.equals(bundle, other.bundle) &&
                        Objects.equals(actions, other.actions);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(accountId, bundle, actions);
        }
    }

    private static class AccountBehaviorGroupsAggregator extends ConcurrentHashMap<MigrationBehaviorGroup, Set<EventType>> {

        public void aggregate(MigrationBehaviorGroup behaviorGroup, Optional<EventType> eventType, MigrationReport report) {
            Set<EventType> singletonOrEmptySet = eventType.isPresent() ? Set.of(eventType.get()) : Collections.emptySet();
            merge(behaviorGroup, singletonOrEmptySet, (existingSet, newSet) -> {
                report.behaviorGroupsAggregated.incrementAndGet();
                LOGGER.debug("[BG MIGRATION] ______ Reusing existing behavior group");
                Set<EventType> eventTypes = new HashSet<>(existingSet);
                eventTypes.addAll(newSet);
                return eventTypes;
            });
        }
    }

    @JsonNaming(SnakeCaseStrategy.class)
    private static class MigrationReport {
        AtomicLong durationInMs = new AtomicLong();
        AtomicLong accountsWithDefaults = new AtomicLong();
        AtomicLong accountsWithNonDefaults = new AtomicLong();
        AtomicLong behaviorGroupsAggregated = new AtomicLong();
        AtomicLong behaviorGroupsPersisted = new AtomicLong();
        AtomicLong actionsPersisted = new AtomicLong();
        AtomicLong behaviorsPersisted = new AtomicLong();

        public AtomicLong getDurationInMs() {
            return durationInMs;
        }

        public AtomicLong getAccountsWithDefaults() {
            return accountsWithDefaults;
        }

        public AtomicLong getAccountsWithNonDefaults() {
            return accountsWithNonDefaults;
        }

        public AtomicLong getBehaviorGroupsAggregated() {
            return behaviorGroupsAggregated;
        }

        public AtomicLong getBehaviorGroupsPersisted() {
            return behaviorGroupsPersisted;
        }

        public AtomicLong getActionsPersisted() {
            return actionsPersisted;
        }

        public AtomicLong getBehaviorsPersisted() {
            return behaviorsPersisted;
        }
    }
}
