package com.redhat.cloud.notifications;

import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownDelayInitiated;
import io.smallrye.reactive.messaging.ChannelRegistry;
import io.smallrye.reactive.messaging.PausableChannel;
import io.smallrye.reactive.messaging.kafka.KafkaClientService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Set;

/**
 * Manages graceful shutdown of Kafka consumers and async processing to ensure
 * all in-flight messages are processed before the EntityManager is closed.
 *
 * Shutdown sequence:
 * 1. Pause all Kafka consumers (stop fetching new messages)
 * 2. @Blocking consumers naturally complete due to POST_PROCESSING acknowledgment
 * 3. Only then does Quarkus shut down EntityManager/datasource
 */
@ApplicationScoped
public class GracefulShutdownManager {

    @Inject
    ChannelRegistry channelRegistry;

    @Inject
    KafkaClientService kafkaClientService;

    @ShutdownDelayInitiated
    void onShutdown() {
        Log.info("=== Starting graceful shutdown sequence ===");

        pauseAllKafkaConsumers();

        Log.info("=== Graceful shutdown sequence completed ===");
        Log.info("EntityManager and datasource can now safely shut down");
    }

    /**
     * Pauses all Kafka consumer channels to prevent fetching new messages.
     * Automatically discovers all incoming channels using the ChannelRegistry,
     * but only pauses those that are actually pausable (Kafka consumers, not emitters).
     */
    private void pauseAllKafkaConsumers() {
        Log.info("Pausing all Kafka consumer channels...");

        // Filter incoming names by excluding emitters
        Set<String> incomingChannels = kafkaClientService.getConsumerChannels();
        Log.infof("  Found %d incoming channel(s) to process", incomingChannels.size());

        int pausedCount = 0;
        int skippedCount = 0;
        for (String channelName : incomingChannels) {
            try {
                PausableChannel channel = channelRegistry.getPausable(channelName);
                if (channel != null) {
                    // This is an actual pausable Kafka consumer channel
                    if (!channel.isPaused()) {
                        channel.pause();
                        pausedCount++;
                        Log.infof("  ✓ Paused Kafka consumer: %s", channelName);
                    } else {
                        Log.infof("  - Channel already paused: %s", channelName);
                    }
                } else {
                    // Not a pausable channel (e.g., emitter-based channels like toCamel)
                    // These don't consume from Kafka, so no need to pause
                    skippedCount++;
                    Log.infof("  - Skipped non-pausable channel: %s (not configured as pausable in application.properties file)", channelName);
                }
            } catch (Exception e) {
                Log.warnf(e, "  ✗ Failed to pause channel: %s - Messages may be lost during shutdown!", channelName);
            }
        }

        Log.infof("Successfully paused %d Kafka consumer(s), skipped %d non-pausable channel(s)", pausedCount, skippedCount);

        // Give Kafka a brief moment to process in-flight fetch requests
        try {
            Log.infof("Waiting 5s to process already pulled messages", pausedCount, skippedCount);
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
