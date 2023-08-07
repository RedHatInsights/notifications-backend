package com.redhat.cloud.notifications.connector;

import io.quarkus.arc.DefaultBean;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Collections;
import java.util.Map;

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

    private static final String HTTPS_CONNECT_TIMEOUT_MS = "notifications.connector.https.connect-timeout-ms";
    private static final String HTTPS_SOCKET_TIMEOUT_MS = "notifications.connector.https.socket-timeout-ms";

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

    @ConfigProperty(name = HTTPS_CONNECT_TIMEOUT_MS, defaultValue = "2500")
    int httpsConnectTimeout;

    @ConfigProperty(name = HTTPS_SOCKET_TIMEOUT_MS, defaultValue = "2500")
    int httpsSocketTimeout;

    public void log() {
        log(Collections.emptyMap());
    }

    protected void log(Map<String, Object> additionalEntries) {
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
        Log.infof("%s=%s", HTTPS_CONNECT_TIMEOUT_MS, httpsConnectTimeout);
        Log.infof("%s=%s", HTTPS_SOCKET_TIMEOUT_MS, httpsSocketTimeout);
        if (additionalEntries != null) {
            for (Entry<String, Object> additionalEntry : additionalEntries.entrySet()) {
                Log.infof("%s=%s", additionalEntry.getKey(), additionalEntry.getValue());
            }
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

    public int getHttpsConnectTimeout() {
        return httpsConnectTimeout;
    }

    public int getHttpsSocketTimeout() {
        return httpsSocketTimeout;
    }
}
