package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.events.EventConsumer;
import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.smallrye.reactive.messaging.ChannelRegistry;
import io.smallrye.reactive.messaging.PausableChannel;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages graceful shutdown of Kafka consumers and async processing to ensure
 * all in-flight messages are processed before the EntityManager is closed.
 *
 * Shutdown sequence:
 * 1. Pause all Kafka consumers (stop fetching new messages)
 * 2. Drain the EventConsumer's ThreadPoolExecutor (wait for in-flight tasks)
 * 3. @Blocking consumers naturally complete due to POST_PROCESSING acknowledgment
 * 4. Only then does Quarkus shut down EntityManager/datasource
 */
@ApplicationScoped
public class GracefulShutdownManager {

    /**
     * Maximum time (in seconds) to wait for executor tasks to complete.
     * This should be less than quarkus.shutdown.timeout to leave buffer time.
     */
    private static final int EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 30;

    /**
     * Maximum time (in seconds) to forcefully terminate remaining tasks.
     */
    private static final int EXECUTOR_FORCE_SHUTDOWN_TIMEOUT_SECONDS = 5;

    @Inject
    ChannelRegistry channelRegistry;

    @Inject
    EventConsumer eventConsumer;

    @Inject
    EngineConfig engineConfig;

    /**
     * High-priority shutdown observer that runs BEFORE @PreDestroy methods
     * and BEFORE Quarkus infrastructure (EntityManager, datasource) shutdown.
     *
     * The priority value of 100 ensures this executes early in the shutdown
     * sequence (lower numbers = higher priority). Default is 2500.
     */
    void onShutdown(@Observes @Priority(100) ShutdownEvent event) {
        Log.info("=== Starting graceful shutdown sequence ===");

        // Step 1: Stop all Kafka consumers from fetching new messages
        pauseAllKafkaConsumers();

        // Step 2: Wait for EventConsumer's executor to drain (if async processing is enabled)
        // This handles both EventConsumer and ReplayEventConsumer (which delegates to EventConsumer)
        if (engineConfig.isAsyncEventProcessing()) {
            Log.info("Async event processing is ENABLED - draining executor");
            drainEventConsumerExecutor();
        } else {
            Log.info("Async event processing is DISABLED - EventConsumer processes synchronously");
            Log.info("  Messages are fully processed before acknowledgment (already safe)");
        }

        // Step 3: @Blocking consumers (ConnectorReceiver, ExportEventListener) use POST_PROCESSING
        // acknowledgment, so Quarkus will naturally wait for them to complete their current message
        // No additional action needed - they're handled by the framework

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

        // Get only emitter names (these are outgoing)
        Set<String> emitterNames = channelRegistry.getEmitterNames();

        // Filter incoming names by excluding emitters
        Set<String> incomingChannels = channelRegistry.getIncomingNames();
        incomingChannels.removeAll(emitterNames);

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

        // Give Kafka a brief moment to stop in-flight fetch requests
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Drains the EventConsumer's ThreadPoolExecutor, waiting for all submitted
     * tasks to complete before allowing the shutdown to proceed.
     *
     * This is critical in async mode because EventConsumer acknowledges Kafka
     * messages immediately after submitting them to the executor, so tasks may
     * still be running even after Kafka thinks the messages are processed.
     *
     * Note: This method is only called when async processing is enabled.
     */
    private void drainEventConsumerExecutor() {
        ExecutorService executor = eventConsumer.getExecutor();

        if (executor == null) {
            Log.warn("  ✗ EventConsumer executor is null (unexpected), skipping drain");
            return;
        }

        Log.infof("  Draining EventConsumer executor (timeout: %ds)...", EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS);

        // Prevent executor from accepting new tasks
        executor.shutdown();

        try {
            // Wait for existing tasks to complete
            boolean terminated = executor.awaitTermination(
                EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            );

            if (terminated) {
                Log.info("  ✓ EventConsumer executor drained successfully - all tasks completed");
            } else {
                // Timeout reached, force shutdown
                Log.warnf("  ✗ Executor did not terminate within %ds, forcing shutdown...",
                    EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS);

                executor.shutdownNow();

                // Wait a bit more for tasks to respond to interruption
                boolean forcedTermination = executor.awaitTermination(
                    EXECUTOR_FORCE_SHUTDOWN_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                );

                if (forcedTermination) {
                    Log.info("  ✓ Executor terminated after forced shutdown");
                } else {
                    Log.error("  ✗ Executor did not terminate even after shutdownNow()");
                    Log.error("  Some tasks may be left running - potential data inconsistency risk");
                }
            }
        } catch (InterruptedException e) {
            Log.error("  ✗ Shutdown interrupted, forcing immediate termination", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
