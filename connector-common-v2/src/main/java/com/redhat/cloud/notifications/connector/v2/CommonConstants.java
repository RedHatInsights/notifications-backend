package com.redhat.cloud.notifications.connector.v2;

public class CommonConstants {

    /**
     * Specifies the delay we want to apply for reinjecting the Kafka message.
     */
    public static final String ORG_ID = "orgId";
    public static final String ENDPOINT_ID = "endpointId";
    /**
     * Holds the original Cloud Event as received from the "incoming" Kafka
     * topic.
     */
    public static final String OUTCOME = "outcome";
    public static final String RETURN_SOURCE = "source";
    public static final String START_TIME = "startTime";
    public static final String SUCCESSFUL = "successful";
    public static final String TARGET_URL = "targetUrl";
}
