package com.redhat.cloud.notifications.routers;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationHistory;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import java.util.concurrent.atomic.AtomicLong;

import static com.redhat.cloud.notifications.Constants.INTERNAL;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

// TODO [Event log phase 2] Delete this class after the data migration.
@Path(INTERNAL + "/event_log/migrate")
public class EventLogMigrationService {

    public static final String CONFIRMATION_TOKEN = "ready-set-go";
    public static final String BATCH_SIZE_KEY = "event-log.migration-service.batch-size";

    private static final Logger LOGGER = Logger.getLogger(EventLogMigrationService.class);

    @Inject
    Mutiny.Session session;

    @ConfigProperty(name = BATCH_SIZE_KEY, defaultValue = "1000")
    int batchSize;

    @GET
    @Produces(APPLICATION_JSON)
    public Uni<MigrationReport> migrate(@QueryParam("confirmation-token") String confirmationToken) {
        long start = System.currentTimeMillis();
        if (!CONFIRMATION_TOKEN.equals(confirmationToken)) {
            throw new BadRequestException("You forgot to confirm the migration!");
        }
        MigrationReport report = new MigrationReport();
        // All DB changes will be rolled-back if anything goes wrong.
        return session.withTransaction(tx -> {
            LOGGER.debug("[NOTIF-291] Start");
            return Multi.createBy().repeating()
                    .uni(() -> new AtomicLong(), counter -> {
                        LOGGER.infof("[NOTIF-291] Migrating batch #%d", counter.getAndIncrement());
                        return session.createQuery("FROM NotificationHistory WHERE event IS NULL", NotificationHistory.class)
                                .setMaxResults(batchSize)
                                .getResultList()
                                .onItem().transformToUni(historyEntries ->
                                        Multi.createFrom().iterable(historyEntries)
                                                .onItem().transformToUniAndConcatenate(history -> {
                                                    Event event = new Event();
                                                    event.setAccountId(history.getAccountId());
                                                    return session.persist(event)
                                                            .onItem().call(session::flush)
                                                            .onItem().transformToUni(ignored ->
                                                                    session.createQuery("UPDATE NotificationHistory SET event = :event WHERE id = :id")
                                                                            .setParameter("event", event)
                                                                            .setParameter("id", history.getId())
                                                                            .executeUpdate()
                                                                            .onItem().invoke(() -> report.getUpdatedHistoryRecords().incrementAndGet())
                                                            );
                                                })
                                                .onItem().ignoreAsUni()
                                                .replaceWith(historyEntries.isEmpty())
                                );
                    })
                    .until(isEmpty -> isEmpty)
                    .onItem().ignoreAsUni();
        })
        .onItem().invoke(ignored -> {
            report.durationInMs.set(System.currentTimeMillis() - start);
            LOGGER.debugf("[NOTIF-291] End");
        })
        .replaceWith(report);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    static class MigrationReport {

        private final AtomicLong durationInMs = new AtomicLong();
        private final AtomicLong updatedHistoryRecords = new AtomicLong();

        public AtomicLong getDurationInMs() {
            return durationInMs;
        }

        public AtomicLong getUpdatedHistoryRecords() {
            return updatedHistoryRecords;
        }
    }
}
