package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.KafkaMessage;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static com.redhat.cloud.notifications.db.EventLogCleaner.now;
import static com.redhat.cloud.notifications.events.KafkaMessagesCleaner.KAFKA_MESSAGES_CLEANER_DELETE_AFTER_CONF_KEY;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class KafkaMessagesCleanerTest {

    /*
     * The KafkaMessage#created field is automatically set because of the @PrePersist annotation in CreationTimestamped
     * when a KafkaMessage is persisted using a stateful session. @PrePersist does not work with a stateless session. We
     * need to set KafkaMessage#created manually to a past date in the tests below, that's why these tests are run using
     * a stateless session.
     */
    @Inject
    Mutiny.StatelessSession statelessSession;

    @Inject
    KafkaMessagesCleaner kafkaMessagesCleaner;

    @BeforeEach
    void beforeEach() {
        statelessSession.createQuery("DELETE FROM KafkaMessage")
                .executeUpdate()
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .await()
                .assertCompleted();
    }

    @Test
    void testWithDefaultConfiguration() {
        createKafkaMessage(now().minus(Duration.ofHours(13L)))
                .chain(() -> createKafkaMessage(now().minus(Duration.ofDays(2L))))
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .await()
                .assertCompleted();
        assertCount(2L);
        kafkaMessagesCleaner.clean();
        assertCount(1L);
    }

    @Test
    void testWithCustomConfiguration() {
        System.setProperty(KAFKA_MESSAGES_CLEANER_DELETE_AFTER_CONF_KEY, "12h");
        createKafkaMessage(now().minus(Duration.ofHours(13L)))
                .chain(() -> createKafkaMessage(now().minus(Duration.ofDays(2L))))
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .await()
                .assertCompleted();
        assertCount(2L);
        kafkaMessagesCleaner.clean();
        assertCount(0L);
        System.clearProperty(KAFKA_MESSAGES_CLEANER_DELETE_AFTER_CONF_KEY);
    }

    private Uni<Void> createKafkaMessage(LocalDateTime created) {
        KafkaMessage kafkaMessage = new KafkaMessage(UUID.randomUUID());
        kafkaMessage.setCreated(created);
        return statelessSession.insert(kafkaMessage);
    }

    private void assertCount(long expectedCount) {
        statelessSession.createQuery("SELECT COUNT(*) FROM KafkaMessage", Long.class)
                .getSingleResult()
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .await()
                .assertItem(expectedCount)
                .assertCompleted();
    }
}
