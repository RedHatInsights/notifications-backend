package com.redhat.cloud.notifications.connector;

import io.quarkus.arc.DefaultBean;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import static java.util.Map.Entry;

@ApplicationScoped
@DefaultBean
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
    int incomingKafkaMaxPollIntervalMs;

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
        log(Collections.emptyMap());
    }

    protected void log(Map<String, Object> additionalConfig) {

        Map<String, Object> config = new TreeMap<>();
        config.put(ENDPOINT_CACHE_MAX_SIZE, endpointCacheMaxSize);
        config.put(KAFKA_INCOMING_GROUP_ID, incomingKafkaGroupId);
        config.put(KAFKA_INCOMING_MAX_POLL_INTERVAL_MS, incomingKafkaMaxPollIntervalMs);
        config.put(KAFKA_INCOMING_MAX_POLL_RECORDS, incomingKafkaMaxPollRecords);
        config.put(KAFKA_INCOMING_POLL_ON_ERROR, incomingKafkaPollOnError);
        config.put(KAFKA_INCOMING_TOPIC, incomingKafkaTopic);
        config.put(KAFKA_OUTGOING_TOPIC, outgoingKafkaTopic);
        config.put(NAME, connectorName);
        config.put(REDELIVERY_COUNTER_NAME, redeliveryCounterName);
        config.put(REDELIVERY_DELAY, redeliveryDelay);
        config.put(REDELIVERY_MAX_ATTEMPTS, redeliveryMaxAttempts);
        config.putAll(additionalConfig);

        Log.info("=== Connector configuration ===");
        for (Entry<String, Object> configEntry : config.entrySet()) {
            Log.infof("%s=%s", configEntry.getKey(), configEntry.getValue());
        }
    }

    public int getEndpointCacheMaxSize() {
        return endpointCacheMaxSize;
    }

    public String getIncomingKafkaGroupId() {
        return incomingKafkaGroupId;
    }

    public int getIncomingKafkaMaxPollIntervalMs() {
        return incomingKafkaMaxPollIntervalMs;
    }

    public int getIncomingKafkaMaxPollRecords() {
        return incomingKafkaMaxPollRecords;
    }

    public String getIncomingKafkaPollOnError() {
        return incomingKafkaPollOnError;
    }

    public String getIncomingKafkaTopic() {
        return incomingKafkaTopic;
    }

    public String getOutgoingKafkaTopic() {
        return outgoingKafkaTopic;
    }

    public String getConnectorName() {
        return connectorName;
    }

    public String getRedeliveryCounterName() {
        return redeliveryCounterName;
    }

    public long getRedeliveryDelay() {
        return redeliveryDelay;
    }

    public int getRedeliveryMaxAttempts() {
        return redeliveryMaxAttempts;
    }
}
