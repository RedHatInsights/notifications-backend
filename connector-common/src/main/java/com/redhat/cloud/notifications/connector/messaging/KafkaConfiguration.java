package com.redhat.cloud.notifications.connector.messaging;

import io.smallrye.config.ConfigMapping;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Configuration for Kafka messaging channels.
 * Replaces Camel Kafka component configuration.
 */
@ApplicationScoped
@ConfigMapping(prefix = "connector.kafka")
public interface KafkaConfiguration {

    /**
     * Incoming Kafka topic name (from engine to connector)
     */
    String incomingTopic();

    /**
     * Outgoing Kafka topic name (from connector to engine)
     */
    String outgoingTopic();

    /**
     * Kafka consumer group ID
     */
    String groupId();

    /**
     * Bootstrap servers
     */
    String bootstrapServers();

    /**
     * Consumer configuration
     */
    ConsumerConfig consumer();

    /**
     * Producer configuration
     */
    ProducerConfig producer();

    interface ConsumerConfig {
        /**
         * Auto offset reset strategy
         */
        String autoOffsetReset();

        /**
         * Enable auto commit
         */
        boolean enableAutoCommit();

        /**
         * Max poll records
         */
        int maxPollRecords();
    }

    interface ProducerConfig {
        /**
         * Acks configuration
         */
        String acks();

        /**
         * Retries
         */
        int retries();

        /**
         * Idempotence
         */
        boolean enableIdempotence();
    }
}
