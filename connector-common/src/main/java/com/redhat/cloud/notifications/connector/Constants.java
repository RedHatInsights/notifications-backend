package com.redhat.cloud.notifications.connector;

public interface Constants {
    /**
     * Represents the Kafka header's name that will hold the payload's database
     * reference.
     */
    String X_RH_NOTIFICATIONS_CONNECTOR_PAYLOAD_ID_HEADER = "x-rh-notifications-payload-id";
}
