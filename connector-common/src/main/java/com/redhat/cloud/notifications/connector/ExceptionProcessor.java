package com.redhat.cloud.notifications.connector;

import io.quarkus.arc.DefaultBean;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;

/**
 * Extend this class in an {@link ApplicationScoped} bean from a connector Maven module to change the
 * behavior of the connector in case of failure while calling an external service (e.g. Slack, Splunk...).
 * If this class is not extended, then the default implementation below will be used.
 *
 * This is the new version that replaces the Camel-based ExceptionProcessor.
 */
@DefaultBean
@ApplicationScoped
public class ExceptionProcessor {

    private static final String DEFAULT_LOG_MSG = "Message sending failed on %s: [orgId=%s, historyId=%s, targetUrl=%s]";

    @Inject
    ConnectorConfig connectorConfig;

    @Inject
    ConnectorMessagingService messagingService;

    public Uni<Void> process(Throwable failure, Message<String> originalMessage, ProcessingContext context) {

        context.setSuccessful(false);
        context.setOutcome(failure.getMessage());

        process(failure, context);

        // Handle reinjection logic (replaces Camel's redelivery mechanism)
        if (context.getKafkaReinjectionCount() < connectorConfig.getKafkaMaximumReinjections()) {
            // Reinject to Kafka for retry
            return messagingService.reinjectMessage(originalMessage, context)
                    .onFailure().invoke(t -> Log.error("Failed to reinject message", t))
                    .replaceWithVoid();
        } else {
            // Send failure result to engine
            ConnectorProcessor.ConnectorResult result = new ConnectorProcessor.ConnectorResult(
                    false,
                    failure.getMessage(),
                    context.getId(),
                    context.getOrgId(),
                    context.getOriginalCloudEvent()
            );
            return messagingService.sendToEngine(result)
                    .onFailure().invoke(t -> Log.error("Failed to send failure result to engine", t))
                    .replaceWithVoid();
        }
    }

    protected final void logDefault(Throwable t, ProcessingContext context) {
        Log.errorf(
                t,
                DEFAULT_LOG_MSG,
                context.getRouteId(),
                context.getOrgId(),
                context.getId(),
                context.getTargetUrl()
        );
    }

    protected void process(Throwable t, ProcessingContext context) {
        logDefault(t, context);
    }

    /**
     * Context object that holds processing state, replacing Camel Exchange properties
     */
    public static class ProcessingContext {
        private String id;
        private String orgId;
        private String targetUrl;
        private String routeId;
        private boolean successful = true;
        private String outcome;
        private int kafkaReinjectionCount = 0;
        private JsonObject originalCloudEvent;
        private long startTime = System.currentTimeMillis();
        private java.util.Map<String, Object> additionalProperties = new java.util.HashMap<>();

        // Getters and setters
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getOrgId() {
            return orgId;
        }

        public void setOrgId(String orgId) {
            this.orgId = orgId;
        }

        public String getTargetUrl() {
            return targetUrl;
        }

        public void setTargetUrl(String targetUrl) {
            this.targetUrl = targetUrl;
        }

        public String getRouteId() {
            return routeId;
        }

        public void setRouteId(String routeId) {
            this.routeId = routeId;
        }

        public boolean isSuccessful() {
            return successful;
        }

        public void setSuccessful(boolean successful) {
            this.successful = successful;
        }

        public String getOutcome() {
            return outcome;
        }

        public void setOutcome(String outcome) {
            this.outcome = outcome;
        }

        public int getKafkaReinjectionCount() {
            return kafkaReinjectionCount;
        }

        public void setKafkaReinjectionCount(int kafkaReinjectionCount) {
            this.kafkaReinjectionCount = kafkaReinjectionCount;
        }

        public JsonObject getOriginalCloudEvent() {
            return originalCloudEvent;
        }

        public void setOriginalCloudEvent(JsonObject originalCloudEvent) {
            this.originalCloudEvent = originalCloudEvent;
        }

        public long getStartTime() {
            return startTime;
        }

        public void setStartTime(long startTime) {
            this.startTime = startTime;
        }

        // Additional properties for connector-specific data
        public void setAdditionalProperty(String key, Object value) {
            additionalProperties.put(key, value);
        }

        public Object getAdditionalProperty(String key) {
            return additionalProperties.get(key);
        }

        @SuppressWarnings("unchecked")
        public <T> T getAdditionalProperty(String key, Class<T> type) {
            return (T) additionalProperties.get(key);
        }
    }
}
