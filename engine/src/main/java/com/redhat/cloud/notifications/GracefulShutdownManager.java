package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.events.ConnectorReceiver;
import com.redhat.cloud.notifications.events.EventConsumer;
import com.redhat.cloud.notifications.events.ReplayEventConsumer;
import com.redhat.cloud.notifications.exports.ExportEventListener;
import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.smallrye.reactive.messaging.ChannelRegistry;
import io.smallrye.reactive.messaging.PausableChannel;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.List;
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
     */
    private void pauseAllKafkaConsumers() {
        Log.info("Pausing all Kafka consumer channels...");

        /**
         * All pausable Kafka consumer channels in the application.
         */
        List<String> KAFKA_CHANNELS = List.of(
            EventConsumer.INGRESS_CHANNEL,              // "ingress"
            ReplayEventConsumer.INGRESS_REPLAY_CHANNEL, // "ingressreplay"
            ConnectorReceiver.FROMCAMEL_CHANNEL,        // "fromcamel"
            ExportEventListener.EXPORT_CHANNEL          // Export requests
        );

        int pausedCount = 0;
        for (String channelName : KAFKA_CHANNELS) {
            try {
                PausableChannel channel = channelRegistry.getPausable(channelName);
                if (channel != null) {
                    if (!channel.isPaused()) {
                        channel.pause();
                        pausedCount++;
                        Log.infof("  ✓ Paused channel: %s", channelName);
                    } else {
                        Log.debugf("  - Channel already paused: %s", channelName);
                    }
                } else {
                    Log.debugf("  - Channel not found or not pausable: %s", channelName);
                }
            } catch (Exception e) {
                Log.warnf(e, "  ✗ Failed to pause channel: %s", channelName);
            }
        }

        Log.infof("Paused %d Kafka channel(s)", pausedCount);

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
