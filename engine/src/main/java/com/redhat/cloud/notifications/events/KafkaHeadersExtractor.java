package com.redhat.cloud.notifications.events;

import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.kafka.api.KafkaMessageMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.kafka.common.header.Header;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;

@ApplicationScoped
public class KafkaHeadersExtractor {

    public <T> Map<String, Optional<String>> extract(Message<T> message, String... headerKeys) {
        Map<String, Optional<String>> headers = new HashMap<>();
        if (headerKeys.length > 0) {
            Optional<KafkaMessageMetadata> metadata = message.getMetadata(KafkaMessageMetadata.class);
            if (metadata.isPresent()) {
                for (String headerKey : headerKeys) {
                    Iterator<Header> headerValues = metadata.get().getHeaders().headers(headerKey).iterator();
                    if (headerValues.hasNext()) {
                        Header header = headerValues.next();
                        if (header.value() != null) {
                            String headerValue = new String(header.value(), UTF_8);
                            headers.put(headerKey, Optional.of(headerValue));
                        }
                        if (headerValues.hasNext()) {
                            Log.warnf(
                                    "Processed a Kafka payload with multiple [%s] header values. The emitter of that payload must change their integration and send only one value. Payload: %s",
                                    headerKey,
                                    message.getPayload());
                        }
                    }
                }
            }
        }
        // The returned Map always contains all header keys to prevent NPE.
        for (String headerKey : headerKeys) {
            headers.putIfAbsent(headerKey, Optional.empty());
        }
        return headers;
    }
}
