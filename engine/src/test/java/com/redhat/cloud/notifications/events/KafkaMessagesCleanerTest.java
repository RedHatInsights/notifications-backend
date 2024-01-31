package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.TestLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

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
        String sql = "DELETE FROM kafka_message";
        return entityManager.createNativeQuery(sql)
                .executeUpdate();
    }

    private void createKafkaMessage(LocalDateTime created) {
        String sql = "INSERT INTO kafka_message(id, created) " +
                "VALUES (:messageId, :created)";
        entityManager.createNativeQuery(sql)
                .setParameter("messageId", UUID.randomUUID())
                .setParameter("created", created)
                .executeUpdate();
    }

    private Long count() {
        String sql = "SELECT COUNT(*) FROM kafka_message";
        return (Long) entityManager.createNativeQuery(sql, Long.class)
                .getSingleResult();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
