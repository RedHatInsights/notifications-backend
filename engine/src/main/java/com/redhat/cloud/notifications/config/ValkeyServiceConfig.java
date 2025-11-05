package com.redhat.cloud.notifications.config;

import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.properties.UnlessBuildProperty;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.UUID;

public class ValkeyServiceConfig {

    /**
     * Created if {@code quarkus.redis.hosts} is created and not empty.
     */
    @UnlessBuildProperty(name = "quarkus.redis.hosts", stringValue = "")
    @ApplicationScoped
    public ValkeyService activeValkeyService(RedisDataSource ds) {
        return new ActiveValkeyService(ds);
    }

    @DefaultBean
    @ApplicationScoped
    public ValkeyService noopValkeyService() {
        return new NoopValkeyService();
    }
}

class ActiveValkeyService implements ValkeyService {

    private static final String KAFKA_MESSAGE_KEY = "engine:kafka-message:";
    private static final String NOT_USED = "";

    @ConfigProperty(name = "valkey-service.ttl", defaultValue = "PT24H")
    Duration ttl;

    private final ValueCommands<String, String> kafkaMessageCommands;

    ActiveValkeyService(RedisDataSource ds) {
        kafkaMessageCommands = ds.value(String.class);
    }

    @Override
    public boolean isNewMessageId(UUID messageId) {
        String key = KAFKA_MESSAGE_KEY + messageId;
        boolean isNew = kafkaMessageCommands.setnx(key, NOT_USED);
        if (isNew) {
            kafkaMessageCommands.setex(key, ttl.toSeconds(), NOT_USED);
        }

        return isNew;
    }
}

class NoopValkeyService implements ValkeyService {

    @Override
    public boolean isNewMessageId(UUID messageId) {
        throw new RuntimeException("Valkey data source was not configured");
    }
}
