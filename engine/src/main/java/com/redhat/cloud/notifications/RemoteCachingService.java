package com.redhat.cloud.notifications;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores and retrieves data from remote cache (i.e. Redis or Valkey).
 */
@ApplicationScoped
public class RemoteCachingService {

    private static final String KAFKA_MESSAGE_KEY = "engine:kafka-message";

    // Key, message ID, creation timestamp
    private final HashCommands<String, UUID, LocalDateTime> kafkaMessageCommands;

    public RemoteCachingService(RedisDataSource ds) {
        kafkaMessageCommands = ds.hash(String.class, UUID.class, LocalDateTime.class);
    }

    /**
     * Verifies that another Kafka consumer didn't already process the given message and then failed to commit its
     * offset. Such failure can happen when a consumer is kicked out of its consumer group because it didn't poll new
     * messages fast enough. We experienced that already in production.
     *
     * @param messageId ID of an incoming message
     * @return true if the message has not been processed yet
     */
    public boolean isNewMessageId(UUID messageId) {
        return kafkaMessageCommands.hsetnx(KAFKA_MESSAGE_KEY, messageId, LocalDateTime.now(Clock.systemUTC()));
    }
}
