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
    Mutiny.SessionFactory sessionFactory;

    /**
     * The Kafka messages identifiers are stored in the database until their retention time is reached. This scheduled
     * job deletes from the database the expired Kafka messages identifiers.
     */
    @Scheduled(identity = "KafkaMessagesCleaner", every = "${notifications.kafka-messages-cleaner.period}", delay = 5L, delayUnit = MINUTES)
    public void clean() {
        testableClean().await().indefinitely();
    }

    Uni<Integer> testableClean() {
        Duration deleteDelay = ConfigProvider.getConfig()
                .getOptionalValue(KAFKA_MESSAGES_CLEANER_DELETE_AFTER_CONF_KEY, Duration.class)
                .orElse(DEFAULT_DELETE_DELAY);
        LocalDateTime deleteBefore = now().minus(deleteDelay);
        LOGGER.infof("Kafka messages purge starting. Entries older than %s will be deleted.", deleteBefore.toString());
        return sessionFactory.withStatelessSession(statelessSession -> {
            return statelessSession.createQuery("DELETE FROM KafkaMessage WHERE created < :deleteBefore")
                    .setParameter("deleteBefore", deleteBefore).executeUpdate().invoke(deleted -> LOGGER
                            .infof("Kafka messages purge ended. %d entries were deleted from the database.", deleted));
        });
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
