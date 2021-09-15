package com.redhat.cloud.notifications.events;

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

// TODO Replace this with an OpenShift cronjob.
@ApplicationScoped
public class KafkaMessagesCleaner {

    public static final String KAFKA_MESSAGES_CLEANER_DELETE_AFTER_CONF_KEY = "kafka-messages-cleaner.delete-after";

    private static final Logger LOGGER = Logger.getLogger(KafkaMessagesCleaner.class);
    private static final Duration DEFAULT_DELETE_DELAY = Duration.ofDays(1L);

    @Inject
    Mutiny.StatelessSession statelessSession;

    /**
     * The Kafka messages identifiers are stored in the database until their retention time is reached.
     * This scheduled job deletes from the database the expired Kafka messages identifiers.
     */
    /*
     * TODO The scheduling is delayed to prevent an unwanted execution during tests. Remove the delay and set the period
     * to `disabled` after the Quarkus 2 bump. See https://quarkus.io/guides/scheduler-reference for more details.
     */
    @Scheduled(identity = "KafkaMessagesCleaner", delay = 10L, delayUnit = MINUTES, every = "{kafka-messages-cleaner.period}")
    public void clean() {
        testableClean().await().indefinitely();
    }

    Uni<Integer> testableClean() {
        Duration deleteDelay = ConfigProvider.getConfig().getOptionalValue(KAFKA_MESSAGES_CLEANER_DELETE_AFTER_CONF_KEY, Duration.class)
                .orElse(DEFAULT_DELETE_DELAY);
        LocalDateTime deleteBefore = now().minus(deleteDelay);
        LOGGER.infof("Kafka messages purge starting. Entries older than %s will be deleted.", deleteBefore.toString());
        return statelessSession.createQuery("DELETE FROM KafkaMessage WHERE created < :deleteBefore")
                .setParameter("deleteBefore", deleteBefore)
                .executeUpdate()
                .invoke(deleted -> LOGGER.infof("Kafka messages purge ended. %d entries were deleted from the database.", deleted));
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
