package com.redhat.cloud.notifications.connector.drawer.config;

import com.redhat.cloud.notifications.connector.ConnectorConfig;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class DrawerConnectorConfig extends ConnectorConfig {

    private static final String DRAWER_TOPIC = "notifications.connector.kafka.outgoing.drawer.topic";
    public static final String NOTIFICATIONS_RECIPIENTS_RESOLVER_RETRY_MAX_ATTEMPTS = "notifications.recipients-resolver.retry.max-attempts";
    public static final String NOTIFICATIONS_RECIPIENTS_RESOLVER_RETRY_INITIAL_BACKOFF = "notifications.recipients-resolver.retry.initial-backoff";
    public static final String NOTIFICATIONS_RECIPIENTS_RESOLVER_RETRY_MAX_BACKOFF = "notifications.recipients-resolver.retry.max-backoff";

    @ConfigProperty(name = NOTIFICATIONS_RECIPIENTS_RESOLVER_RETRY_MAX_ATTEMPTS, defaultValue = "3")
    int maxRetryAttempts;

    @ConfigProperty(name = NOTIFICATIONS_RECIPIENTS_RESOLVER_RETRY_INITIAL_BACKOFF, defaultValue = "0.1S")
    Duration initialRetryBackoff;

    @ConfigProperty(name = NOTIFICATIONS_RECIPIENTS_RESOLVER_RETRY_MAX_BACKOFF, defaultValue = "1S")
    Duration maxRetryBackoff;

    @ConfigProperty(name = DRAWER_TOPIC)
    String outgoingDrawerTopic;

    @Override
    public void log() {
        final Map<String, Object> additionalEntries = new HashMap<>();
        additionalEntries.put(NOTIFICATIONS_RECIPIENTS_RESOLVER_RETRY_MAX_ATTEMPTS, outgoingDrawerTopic);
        additionalEntries.put(NOTIFICATIONS_RECIPIENTS_RESOLVER_RETRY_INITIAL_BACKOFF, initialRetryBackoff);
        additionalEntries.put(NOTIFICATIONS_RECIPIENTS_RESOLVER_RETRY_MAX_BACKOFF, maxRetryBackoff);
        additionalEntries.put(DRAWER_TOPIC, outgoingDrawerTopic);
        log(additionalEntries);
    }

    public String getOutgoingDrawerTopic() {
        return outgoingDrawerTopic;
    }

    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public Duration getInitialRetryBackoff() {
        return initialRetryBackoff;
    }

    public Duration getMaxRetryBackoff() {
        return maxRetryBackoff;
    }
}
