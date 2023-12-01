package com.redhat.cloud.notifications.connector;

public class KafkaHeader {
    /**
     * Specifies the name of the header that we will use to hold the number of
     * times a message got reinjected in the "incoming" topic.
     */
    public static final String REINJECTION_COUNT = "notifications-connector-reinjections-count";
}
