package com.redhat.cloud.notifications.connector;

public class ExchangeProperty {

    public static final String ID = "id";
    /**
     * Specifies the ID of the event for which its payload was fetched. Signals
     * that the engine must be notified about deleting the stored payload.
     */
    public static final String DATABASE_PAYLOAD_EVENT_ID = "databasePayloadEventId";
    /**
     * Specifies the number of times the message was reinjected to the
     * "incoming" Kafka topic.
     */
    public static final String KAFKA_REINJECTION_COUNT = "kafkaReinjectionCount";
    /**
     * Specifies the delay we want to apply for reinjecting the Kafka message.
     */
    public static final String KAFKA_REINJECTION_DELAY = "kafkaReinjectionDelay";
    public static final String ORG_ID = "orgId";
    /**
     * Holds the original Cloud Event as received from the "incoming" Kafka
     * topic.
     */
    public static final String ORIGINAL_CLOUD_EVENT = "originalCloudEvent";
    public static final String OUTCOME = "outcome";
    public static final String REDELIVERY_ATTEMPTS = "redeliveryAttempts";
    public static final String RETURN_SOURCE = "source";
    public static final String START_TIME = "startTime";
    public static final String SUCCESSFUL = "successful";
    public static final String TARGET_URL = "targetUrl";
    public static final String TYPE = "type";
}
