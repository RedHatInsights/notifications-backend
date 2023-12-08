package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.models.EventTypeKey;
import com.redhat.cloud.notifications.models.KafkaMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class KafkaMessageDeduplicator {

    public static final String MESSAGE_ID_HEADER = "rh-message-id";
    public static final String MESSAGE_ID_VALID_COUNTER_NAME = "kafka-message-id.valid";
    public static final String MESSAGE_ID_INVALID_COUNTER_NAME = "kafka-message-id.invalid";
    public static final String MESSAGE_ID_MISSING_COUNTER_NAME = "kafka-message-id.missing";

    private static final String ACCEPTED_UUID_VERSION = "4";

    @Inject
    EntityManager entityManager;

    @Inject
    MeterRegistry meterRegistry;

    private Counter validMessageIdCounter;
    private Counter invalidMessageIdCounter;
    private Counter missingMessageIdCounter;

    @PostConstruct
    void initCounters() {
        validMessageIdCounter = meterRegistry.counter(MESSAGE_ID_VALID_COUNTER_NAME);
        invalidMessageIdCounter = meterRegistry.counter(MESSAGE_ID_INVALID_COUNTER_NAME);
        missingMessageIdCounter = meterRegistry.counter(MESSAGE_ID_MISSING_COUNTER_NAME);
    }

    /**
     * Validates the message ID retrieved from the Kafka message headers. If multiple header values are available, the first one
     * will be used and the other ones will be ignored. An invalid header value will be counted and logged, but won't
     * interrupt the message processing: the deduplication will be disabled for the message.
     * @deprecated The rh-message-id header will be replaced by the cloud events or actions id field soon.
     */
    @Deprecated(forRemoval = true)
    public UUID validateMessageId(EventTypeKey eventTypeKey, Optional<String> messageIdHeader) {
        if (messageIdHeader.isPresent()) {
            try {
                UUID messageId = UUID.fromString(messageIdHeader.get());
                // If the UUID version is 4, then its 15th character has to be "4".
                if (!messageIdHeader.get().substring(14, 15).equals(ACCEPTED_UUID_VERSION)) {
                    throw new IllegalArgumentException("Wrong UUID version received");
                }
                validMessageIdCounter.increment();
                Log.tracef("Application sent an EventType(%s) with a valid Kafka header [%s=%s]",
                        eventTypeKey, MESSAGE_ID_HEADER, messageIdHeader.get());
                return messageId;
            } catch (IllegalArgumentException e) {
                invalidMessageIdCounter.increment();
                Log.warnf("Application sent an EventType(%s) with an invalid Kafka header [%s=%s]. They must change their " +
                        "integration and send a valid UUID (version 4).", eventTypeKey, MESSAGE_ID_HEADER, messageIdHeader.get());
            }
        } else {
            missingMessageIdCounter.increment();
            Log.tracef("Application sent an EventType(%s) but did not send any Kafka header [%s]",
                    eventTypeKey, MESSAGE_ID_HEADER);
        }
        // This will be returned if the message ID is either invalid or not found.
        return null;
    }

    /**
     * Verifies that another Kafka consumer didn't already process the given message and then failed to commit its
     * offset. Such failure can happen when a consumer is kicked out of its consumer group because it didn't poll new
     * messages fast enough. We experienced that already on production.
     */
    public boolean isDuplicate(UUID messageId) {
        if (messageId == null) {
            /*
             * For now, messages without an ID are always considered new. This is necessary to give the onboarded apps
             * time to change their integration and start sending the new header. The message ID may become mandatory later.
             */
            return false;
        } else {
            String hql = "SELECT TRUE FROM KafkaMessage WHERE id = :messageId";
            try {
                return entityManager.createQuery(hql, Boolean.class)
                        .setParameter("messageId", messageId)
                        .getSingleResult();
            } catch (NoResultException e) {
                return false;
            }
        }
    }

    @Transactional
    public void registerMessageId(UUID messageId) {
        if (messageId != null) {
            KafkaMessage kafkaMessage = new KafkaMessage(messageId);
            entityManager.persist(kafkaMessage);
        }
    }
}
