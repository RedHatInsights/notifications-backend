package com.redhat.cloud.notifications;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.UUID;

/** Stores and retrieves data from remote cache (i.e. Valkey). */
@ApplicationScoped
public class ValkeyService {

    private static final String KAFKA_MESSAGE_KEY = "engine:kafka-message:";
    private static final String NOT_USED = "";

    @ConfigProperty(name = "valkey-service.ttl", defaultValue = "PT24H")
    Duration ttl;

    private final ValueCommands<String, String> kafkaMessageCommands;

    public ValkeyService(RedisDataSource ds) {
        kafkaMessageCommands = ds.value(String.class);
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
        String key = KAFKA_MESSAGE_KEY + messageId;
        boolean isNew = kafkaMessageCommands.setnx(key, NOT_USED);
        if (isNew) {
            kafkaMessageCommands.setex(key, ttl.toSeconds(), NOT_USED);
        }

        return isNew;
    }
}
