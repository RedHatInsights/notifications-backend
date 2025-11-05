package com.redhat.cloud.notifications.config;

import java.util.UUID;

/** Stores and retrieves data from remote cache (i.e. Valkey). */
public interface ValkeyService {
    /**
     * Verifies that another Kafka consumer didn't already process the given message and then failed to commit its
     * offset. Such failure can happen when a consumer is kicked out of its consumer group because it didn't poll new
     * messages fast enough. We experienced that already in production.
     *
     * @param messageId ID of an incoming message
     * @return true if the message has not been processed yet
     */
    boolean isNewMessageId(UUID messageId);
}
