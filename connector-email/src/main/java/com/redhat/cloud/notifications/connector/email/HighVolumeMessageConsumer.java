package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.v2.MessageConsumer;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.reactive.messaging.annotations.Blocking;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class HighVolumeMessageConsumer {

    @Inject
    EmailConnectorConfig emailConnectorConfig;

    @Inject
    MessageConsumer messageConsumer;

    @Incoming("highvolumemessages")
    @Blocking("high-volume-thread-pool")
    @RunOnVirtualThread
    public CompletionStage<Void> processMessage(Message<JsonObject> message) {
        if (!emailConnectorConfig.isIncomingKafkaHighVolumeTopicEnabled()) {
            Log.warnf("Kafka message rejected because high-volume topic is disabled on this connector");
            return message.ack();
        }
        // Delegates to processMessage() which includes connector header filtering (defense-in-depth)
        return messageConsumer.processMessage(message);
    }
}
