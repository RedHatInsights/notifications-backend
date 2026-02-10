package com.redhat.cloud.notifications.config;

import io.quarkus.logging.Log;
import io.smallrye.common.annotation.Identifier;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.KafkaAdminClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Produces a Kafka AdminClient bean for consumer group offset management.
 * Uses Quarkus's default-kafka-broker configuration which automatically
 * includes all kafka.* properties.
 */
@ApplicationScoped
public class KafkaAdminClientProducer {

    @Inject
    @Identifier("default-kafka-broker")
    Map<String, Object> kafkaConfig;

    private AdminClient adminClient;

    @Produces
    @ApplicationScoped
    public AdminClient produceAdminClient() {
        Map<String, Object> adminConfig = new HashMap<>();
        for (Map.Entry<String, Object> entry : kafkaConfig.entrySet()) {
            if (AdminClientConfig.configNames().contains(entry.getKey())) {
                adminConfig.put(entry.getKey(), entry.getValue());
            }
        }

        Log.infof("Creating Kafka AdminClient with bootstrap servers: %s",
                adminConfig.get(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG));
        adminClient = KafkaAdminClient.create(adminConfig);
        return adminClient;
    }

    @PreDestroy
    public void close() {
        if (adminClient != null) {
            Log.info("Closing Kafka AdminClient");
            adminClient.close();
        }
    }
}
