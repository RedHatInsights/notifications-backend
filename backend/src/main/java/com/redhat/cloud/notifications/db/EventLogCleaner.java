package com.redhat.cloud.notifications.db;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.ConfigProvider;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static java.util.concurrent.TimeUnit.MINUTES;

@ApplicationScoped
public class EventLogCleaner {

    public static final String EVENT_LOG_CLEANER_DELETE_AFTER_CONF_KEY = "event-log-cleaner.delete-after";

    private static final Logger LOGGER = Logger.getLogger(EventLogCleaner.class);
    private static final Duration DEFAULT_DELETE_DELAY = Duration.ofDays(31L);

    @Inject
    Mutiny.SessionFactory sessionFactory;

    /**
     * The event log entries are stored in the database until their retention time is reached.
     * This scheduled job deletes from the database the expired event log entries.
     */
    @Scheduled(identity = "EventLogCleaner", every = "${event-log-cleaner.period}", delay = 5L, delayUnit = MINUTES)
    public void clean() {
        testableClean().await().indefinitely();
    }

    Uni<Integer> testableClean() {
        Duration deleteDelay = ConfigProvider.getConfig().getOptionalValue(EVENT_LOG_CLEANER_DELETE_AFTER_CONF_KEY, Duration.class)
                .orElse(DEFAULT_DELETE_DELAY);
        LocalDateTime deleteBefore = now().minus(deleteDelay);
        LOGGER.infof("Event log purge starting. Entries older than %s will be deleted.", deleteBefore.toString());
        return sessionFactory.withStatelessSession(statelessSession -> {
            return statelessSession.createQuery("DELETE FROM Event WHERE created < :deleteBefore")
                    .setParameter("deleteBefore", deleteBefore)
                    .executeUpdate()
                    .invoke(deleted -> LOGGER.infof("Event log purge ended. %d entries were deleted from the database.", deleted));
        });
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
