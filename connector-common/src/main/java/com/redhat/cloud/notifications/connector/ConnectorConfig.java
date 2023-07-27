package com.redhat.cloud.notifications.connector;

import io.quarkus.logging.Log;
import io.quarkus.runtime.configuration.ProfileManager;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;

import static io.quarkus.runtime.LaunchMode.TEST;

@ApplicationScoped
public class ConnectorConfig {

    private static final String ENDPOINT_CACHE_MAX_SIZE = "notifications.connector.endpoint-cache-max-size";
    private static final String KAFKA_INCOMING_GROUP_ID = "notifications.connector.kafka.incoming.group-id";
    private static final String KAFKA_INCOMING_MAX_POLL_INTERVAL_MS = "notifications.connector.kafka.incoming.max-poll-interval-ms";
    private static final String KAFKA_INCOMING_MAX_POLL_RECORDS = "notifications.connector.kafka.incoming.max-poll-records";
    private static final String KAFKA_INCOMING_POLL_ON_ERROR = "notifications.connector.kafka.incoming.poll-on-error";
    private static final String KAFKA_INCOMING_TOPIC = "notifications.connector.kafka.incoming.topic";
    private static final String KAFKA_OUTGOING_TOPIC = "notifications.connector.kafka.outgoing.topic";
    private static final String NAME = "notifications.connector.name";
    private static final String REDELIVERY_COUNTER_NAME = "notifications.connector.redelivery.counter-name";
    private static final String REDELIVERY_DELAY = "notifications.connector.redelivery.delay";
    private static final String REDELIVERY_MAX_ATTEMPTS = "notifications.connector.redelivery.max-attempts";

    @ConfigProperty(name = ENDPOINT_CACHE_MAX_SIZE, defaultValue = "100")
    int endpointCacheMaxSize;

    @ConfigProperty(name = KAFKA_INCOMING_GROUP_ID)
    String incomingKafkaGroupId;

    // https://docs.confluent.io/platform/current/installation/configuration/consumer-configs.html#max-poll-interval-ms
    @ConfigProperty(name = KAFKA_INCOMING_MAX_POLL_INTERVAL_MS, defaultValue = "300000")
    long incomingKafkaMaxPollIntervalMs;

    // https://docs.confluent.io/platform/current/installation/configuration/consumer-configs.html#max-poll-records
    @ConfigProperty(name = KAFKA_INCOMING_MAX_POLL_RECORDS, defaultValue = "500")
    int incomingKafkaMaxPollRecords;

    @ConfigProperty(name = KAFKA_INCOMING_POLL_ON_ERROR, defaultValue = "RECONNECT")
    String incomingKafkaPollOnError;

    @ConfigProperty(name = KAFKA_INCOMING_TOPIC)
    String incomingKafkaTopic;

    @ConfigProperty(name = KAFKA_OUTGOING_TOPIC)
    String outgoingKafkaTopic;

    @ConfigProperty(name = NAME)
    String connectorName;

    @ConfigProperty(name = REDELIVERY_COUNTER_NAME)
    String redeliveryCounterName;

    @ConfigProperty(name = REDELIVERY_DELAY, defaultValue = "1000")
    long redeliveryDelay;

    @ConfigProperty(name = REDELIVERY_MAX_ATTEMPTS, defaultValue = "2")
    int redeliveryMaxAttempts;

    public void log() {
        Log.info("=== Connector configuration ===");
        Log.infof("%s=%s", ENDPOINT_CACHE_MAX_SIZE, endpointCacheMaxSize);
        Log.infof("%s=%s", KAFKA_INCOMING_GROUP_ID, incomingKafkaGroupId);
        Log.infof("%s=%s", KAFKA_INCOMING_MAX_POLL_INTERVAL_MS, incomingKafkaMaxPollIntervalMs);
        Log.infof("%s=%s", KAFKA_INCOMING_MAX_POLL_RECORDS, incomingKafkaMaxPollRecords);
        Log.infof("%s=%s", KAFKA_INCOMING_POLL_ON_ERROR, incomingKafkaPollOnError);
        Log.infof("%s=%s", KAFKA_INCOMING_TOPIC, incomingKafkaTopic);
        Log.infof("%s=%s", KAFKA_OUTGOING_TOPIC, outgoingKafkaTopic);
        Log.infof("%s=%s", NAME, connectorName);
        Log.infof("%s=%s", REDELIVERY_COUNTER_NAME, redeliveryCounterName);
        Log.infof("%s=%s", REDELIVERY_DELAY, redeliveryDelay);
        Log.infof("%s=%s", REDELIVERY_MAX_ATTEMPTS, redeliveryMaxAttempts);
    }

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

    public String getIncomingKafkaPollOnError() {
        return incomingKafkaPollOnError;
    }

    public void setIncomingKafkaPollOnError(String incomingKafkaPollOnError) {
        checkTestLaunchMode();
        this.incomingKafkaPollOnError = incomingKafkaPollOnError;
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
