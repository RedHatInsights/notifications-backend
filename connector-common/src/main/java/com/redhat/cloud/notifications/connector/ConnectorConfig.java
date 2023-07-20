package com.redhat.cloud.notifications.connector;

import io.quarkus.runtime.configuration.ProfileManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;

import static io.quarkus.runtime.LaunchMode.TEST;

@ApplicationScoped
public class ConnectorConfig {

    @ConfigProperty(name = "notifications.connector.endpoint-cache-max-size", defaultValue = "100")
    int endpointCacheMaxSize;

    @ConfigProperty(name = "notifications.connector.kafka.incoming.group-id")
    String incomingKafkaGroupId;

    // https://docs.confluent.io/platform/current/installation/configuration/consumer-configs.html#max-poll-interval-ms
    @ConfigProperty(name = "notifications.connector.kafka.incoming.max-poll-interval-ms", defaultValue = "300000")
    long incomingKafkaMaxPollIntervalMs;

    // https://docs.confluent.io/platform/current/installation/configuration/consumer-configs.html#max-poll-records
    @ConfigProperty(name = "notifications.connector.kafka.incoming.max-poll-records", defaultValue = "500")
    int incomingKafkaMaxPollRecords;

    @ConfigProperty(name = "notifications.connector.kafka.incoming.topic")
    String incomingKafkaTopic;

    @ConfigProperty(name = "notifications.connector.kafka.outgoing.topic")
    String outgoingKafkaTopic;

    @ConfigProperty(name = "notifications.connector.name")
    String connectorName;

    @ConfigProperty(name = "notifications.connector.redelivery.counter-name")
    String redeliveryCounterName;

    @ConfigProperty(name = "notifications.connector.redelivery.delay", defaultValue = "1000")
    long redeliveryDelay;

    @ConfigProperty(name = "notifications.connector.redelivery.max-attempts", defaultValue = "2")
    int redeliveryMaxAttempts;

    public int getEndpointCacheMaxSize() {
        return endpointCacheMaxSize;
    }

    public void setEndpointCacheMaxSize(int endpointCacheMaxSize) {
        checkTestLaunchMode();
        this.endpointCacheMaxSize = endpointCacheMaxSize;
    }

    public String getIncomingKafkaGroupId() {
        return incomingKafkaGroupId;
    }

    public void setIncomingKafkaGroupId(String incomingKafkaGroupId) {
        checkTestLaunchMode();
        this.incomingKafkaGroupId = incomingKafkaGroupId;
    }

    public long getIncomingKafkaMaxPollIntervalMs() {
        return incomingKafkaMaxPollIntervalMs;
    }

    public void setIncomingKafkaMaxPollIntervalMs(long incomingKafkaMaxPollIntervalMs) {
        checkTestLaunchMode();
        this.incomingKafkaMaxPollIntervalMs = incomingKafkaMaxPollIntervalMs;
    }

    public int getIncomingKafkaMaxPollRecords() {
        return incomingKafkaMaxPollRecords;
    }

    public void setIncomingKafkaMaxPollRecords(int incomingKafkaMaxPollRecords) {
        checkTestLaunchMode();
        this.incomingKafkaMaxPollRecords = incomingKafkaMaxPollRecords;
    }

    public String getIncomingKafkaTopic() {
        return incomingKafkaTopic;
    }

    public void setIncomingKafkaTopic(String incomingKafkaTopic) {
        checkTestLaunchMode();
        this.incomingKafkaTopic = incomingKafkaTopic;
    }

    public String getOutgoingKafkaTopic() {
        return outgoingKafkaTopic;
    }

    public void setOutgoingKafkaTopic(String outgoingKafkaTopic) {
        checkTestLaunchMode();
        this.outgoingKafkaTopic = outgoingKafkaTopic;
    }

    public String getConnectorName() {
        return connectorName;
    }

    public void setConnectorName(String connectorName) {
        checkTestLaunchMode();
        this.connectorName = connectorName;
    }

    public String getRedeliveryCounterName() {
        return redeliveryCounterName;
    }

    public void setRedeliveryCounterName(String redeliveryCounterName) {
        checkTestLaunchMode();
        this.redeliveryCounterName = redeliveryCounterName;
    }

    public long getRedeliveryDelay() {
        return redeliveryDelay;
    }

    public void setRedeliveryDelay(long redeliveryDelay) {
        checkTestLaunchMode();
        this.redeliveryDelay = redeliveryDelay;
    }

    public int getRedeliveryMaxAttempts() {
        return redeliveryMaxAttempts;
    }

    public void setRedeliveryMaxAttempts(int redeliveryMaxAttempts) {
        checkTestLaunchMode();
        this.redeliveryMaxAttempts = redeliveryMaxAttempts;
    }

    /**
     * This method throws an {@link IllegalStateException} if it is invoked with a launch mode different from
     * {@link io.quarkus.runtime.LaunchMode#TEST TEST}. It should be added to methods that allow overriding a
     * config value from tests only, preventing doing so from runtime code.
     */
    private static void checkTestLaunchMode() {
        if (ProfileManager.getLaunchMode() != TEST) {
            throw new IllegalStateException("Illegal config value override detected");
        }
    }
}
