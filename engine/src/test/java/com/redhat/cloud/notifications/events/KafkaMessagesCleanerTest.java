package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.KafkaMessage;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class KafkaMessagesCleanerTest {

    @Inject
    EntityManager entityManager;

    @Test
    @Transactional
    void testPostgresStoredProcedure() {
        deleteAllKafkaMessages();
        createKafkaMessage(now().minus(Duration.ofHours(13L)));
        createKafkaMessage(now().minus(Duration.ofDays(2L)));
        assertEquals(2L, count());
        entityManager.createNativeQuery("CALL cleanKafkaMessagesIds()").executeUpdate();
        assertEquals(1L, count());
    }

    private Integer deleteAllKafkaMessages() {
        return entityManager.createQuery("DELETE FROM KafkaMessage")
                .executeUpdate();
    }

    private void createKafkaMessage(LocalDateTime created) {
        KafkaMessage kafkaMessage = new KafkaMessage(UUID.randomUUID());
        kafkaMessage.setCreated(created);
        entityManager.persist(kafkaMessage);
    }

    private Long count() {
        return entityManager.createQuery("SELECT COUNT(*) FROM KafkaMessage", Long.class)
                .getSingleResult();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
