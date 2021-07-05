package com.redhat.cloud.notifications.routers;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.models.BehaviorGroupAction;
import com.redhat.cloud.notifications.models.BehaviorGroupActionId;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.Constants.INTERNAL;

// Todo: Delete after migration
@Path(INTERNAL + "/email_endpoint/migrate")
public class EmailEndpointMigrationService {

    public static final String CONFIRMATION_TOKEN = "ready-set-go";

    private static final Logger LOGGER = Logger.getLogger(EmailEndpointMigrationService.class.getName());

    @Inject
    Mutiny.Session session;

    @GET
    public Uni<MigrationReport> migrateEmailEndpoint(@QueryParam("confirmation-token") String confirmationToken) {
        // For every account we can only have a single Endpoint.type = EmailSubscription (as of today).
        // Fetch every account and ensure this
        long start = System.currentTimeMillis();
        if (!CONFIRMATION_TOKEN.equals(confirmationToken)) {
            throw new BadRequestException("You forgot to confirm the migration!");
        }
        MigrationReport report = new MigrationReport();

        return session.withTransaction(transaction -> {
            LOGGER.debugf("[NOTIF-240] Starting");
            return session.createQuery("SELECT DISTINCT accountId from Endpoint WHERE type = :endpointType", String.class)
                    .setParameter("endpointType", EndpointType.EMAIL_SUBSCRIPTION)
                    .getResultList()
                    .onItem().invoke(accounts -> LOGGER.debugf("[NOTIF-240] Collapsing Email endpoints"))
                    .onItem().transformToMulti(Multi.createFrom()::iterable)
                    .invoke(s -> report.updatedAccounts.incrementAndGet())
                    .onItem().transformToUniAndConcatenate(accountId -> session.createQuery("FROM Endpoint WHERE type = :endpointType AND accountId = :accountId", Endpoint.class)
                            .setParameter("endpointType", EndpointType.EMAIL_SUBSCRIPTION)
                            .setParameter("accountId", accountId)
                            .getResultList()
                            .onItem().transformToUni(endpoints -> {
                                if (endpoints.size() > 1) {
                                    Endpoint defaultEmailEndpoint = endpoints.remove(0);
                                    LOGGER.debugf("[NOTIF-240] Account [%s] has [%d] EmailEndpoints. Using [%s] as the default", accountId, endpoints.size() + 1, defaultEmailEndpoint.getId());
                                    Set<UUID> endpointIds = endpoints.stream().map(Endpoint::getId).collect(Collectors.toSet());

                                    return session.createQuery("FROM BehaviorGroupAction bga WHERE bga.id.endpointId IN (:endpointIds)", BehaviorGroupAction.class)
                                            .setParameter("endpointIds", endpointIds)
                                            .getResultList()
                                            .onItem().transformToMulti(Multi.createFrom()::iterable)
                                            .invoke(behaviorGroupAction -> {
                                                LOGGER.debugf(
                                                        "[NOTIF-240] Updating BehaviorGroup [%s] to use default EmailEndpoint [%s]",
                                                        behaviorGroupAction.getId().behaviorGroupId,
                                                        defaultEmailEndpoint.getId()
                                                );
                                            })
                                            .onItem().transformToUniAndConcatenate(behaviorGroupAction -> session.remove(behaviorGroupAction)
                                                    .onItem().transformToUni(_unused -> {
                                                        BehaviorGroupActionId bgaId = new BehaviorGroupActionId();
                                                        bgaId.endpointId = defaultEmailEndpoint.getId();
                                                        bgaId.behaviorGroupId = behaviorGroupAction.getId().behaviorGroupId;

                                                        return session.find(BehaviorGroupAction.class, bgaId)
                                                                .onItem().transformToUni(bgaFound -> {
                                                                    if (bgaFound == null) {
                                                                        BehaviorGroupAction replacement = new BehaviorGroupAction(
                                                                                behaviorGroupAction.getBehaviorGroup(),
                                                                                defaultEmailEndpoint
                                                                        );
                                                                        replacement.setPosition(behaviorGroupAction.getPosition());
                                                                        return session.persist(replacement)
                                                                                .invoke(_unused2 -> report.updatedBehaviorGroupActions.incrementAndGet());
                                                                    }

                                                                    return Uni.createFrom().voidItem();
                                                                });
                                                    })).collect().asList()
                                            .invoke(_unused -> {
                                                report.deletedEndpoints.addAndGet(endpoints.size());
                                                LOGGER.debugf(
                                                        "[NOTIF-240 Deleting [%d] endpoints from account: [%s]",
                                                        endpoints.size(),
                                                        accountId
                                                );
                                            })
                                            .onItem().transformToUni(_unused -> {
                                                endpoints.forEach(endpoint -> {
                                                    endpoint.setBehaviorGroupActions(Set.of());
                                                });

                                                return session.removeAll(endpoints.toArray());
                                            });
                                }

                                LOGGER.debugf("[NOTIF-240] Account [%s] only has one EmailEndpoint - skipping", accountId);
                                return Uni.createFrom().voidItem();
                            })).collect().asList();
        })
        .onItem().invoke(_unused -> {
            report.durationInMs.set(System.currentTimeMillis() - start);
            LOGGER.debugf("[NOTIF-240] End");
        })
        .onItem().transform(_unused -> report);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    static class MigrationReport {
        private final AtomicLong durationInMs = new AtomicLong();
        private final AtomicLong deletedEndpoints = new AtomicLong();
        private final AtomicLong updatedBehaviorGroupActions = new AtomicLong();
        private final AtomicLong updatedAccounts = new AtomicLong();


        public AtomicLong getDurationInMs() {
            return durationInMs;
        }

        public AtomicLong getDeletedEndpoints() {
            return deletedEndpoints;
        }

        public AtomicLong getUpdatedBehaviorGroupActions() {
            return updatedBehaviorGroupActions;
        }

        public AtomicLong getUpdatedAccounts() {
            return updatedAccounts;
        }
    }
}
