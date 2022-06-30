package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.models.KafkaMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.kafka.api.KafkaMessageMetadata;
import org.apache.kafka.common.header.Header;
import org.eclipse.microprofile.reactive.messaging.Message;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

@ApplicationScoped
public class KafkaMessageDeduplicator {

    public static final String MESSAGE_ID_HEADER = "rh-message-id";
    public static final String MESSAGE_ID_VALID_COUNTER_NAME = "kafka-message-id.valid";
    public static final String MESSAGE_ID_INVALID_COUNTER_NAME = "kafka-message-id.invalid";
    public static final String MESSAGE_ID_MISSING_COUNTER_NAME = "kafka-message-id.missing";

    private static final String ACCEPTED_UUID_VERSION = "4";

    @Inject
    StatelessSessionFactory statelessSessionFactory;

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
     * Extracts the message ID from a Kafka message header value. If multiple header values are available, the first one
     * will be used and the other ones will be ignored. An invalid header value will be counted and logged, but won't
     * interrupt the message processing: the deduplication will be disabled for the message.
     */
    public UUID findMessageId(String bundleName, String applicationName, Message<String> message) {
        boolean found = false;
        Optional<KafkaMessageMetadata> metadata = message.getMetadata(KafkaMessageMetadata.class);
        if (metadata.isPresent()) {
            Iterator<Header> headers = metadata.get().getHeaders().headers(MESSAGE_ID_HEADER).iterator();
            if (headers.hasNext()) {
                found = true;
                Header header = headers.next();
                if (header.value() == null) {
                    invalidMessageIdCounter.increment();
                    Log.warnf("Application %s/%s sent an invalid Kafka header [%s=null]. They must change their " +
                                    "integration and send a non-null value.", bundleName, applicationName, MESSAGE_ID_HEADER);
                } else {
                    String headerValue = new String(header.value(), UTF_8);
                    try {
                        UUID messageId = UUID.fromString(headerValue);
                        // If the UUID version is 4, then its 15th character has to be "4".
                        if (!headerValue.substring(14, 15).equals(ACCEPTED_UUID_VERSION)) {
                            throw new IllegalArgumentException("Wrong UUID version received");
                        }
                        validMessageIdCounter.increment();
                        Log.tracef("Application %s/%s sent a valid Kafka header [%s=%s]",
                                bundleName, applicationName, MESSAGE_ID_HEADER, headerValue);
                        return messageId;
                    } catch (IllegalArgumentException e) {
                        invalidMessageIdCounter.increment();
                        Log.warnf("Application %s/%s sent an invalid Kafka header [%s=%s]. They must change their " +
                                "integration and send a valid UUID (version 4).", bundleName, applicationName, MESSAGE_ID_HEADER, headerValue);
                    }
                }
            }
            if (headers.hasNext()) {
                Log.warnf("Application %s/%s sent multiple Kafka headers [%s]. They must change their " +
                                "integration and send only one value.", bundleName, applicationName, MESSAGE_ID_HEADER);
            }
        }
        if (!found) {
            missingMessageIdCounter.increment();
            Log.tracef("Application %s/%s did not send any Kafka header [%s]",
                    bundleName, applicationName, MESSAGE_ID_HEADER);
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
                return statelessSessionFactory.getCurrentSession().createQuery(hql, Boolean.class)
                        .setParameter("messageId", messageId)
                        .getSingleResult();
            } catch (NoResultException e) {
                return false;
            }
        }
    }

    public void registerMessageId(UUID messageId) {
        if (messageId != null) {
            KafkaMessage kafkaMessage = new KafkaMessage(messageId);
            kafkaMessage.prePersist(); // This method must be called manually while using a StatelessSession.
            statelessSessionFactory.getCurrentSession().insert(kafkaMessage);
        }
    }
}
