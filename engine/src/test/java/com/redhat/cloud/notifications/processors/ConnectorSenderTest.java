package com.redhat.cloud.notifications.processors;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.PayloadDetailsRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.processors.payload.PayloadDetails;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.kafka.common.header.Header;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ConnectorSenderTest {
    @Inject
    ConnectorSender connectorSender;

    @InjectMock
    EngineConfig engineConfig;

    @Any
    @Inject
    InMemoryConnector inMemoryConnector;

    @InjectMock
    PayloadDetailsRepository payloadDetailsRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    /**
     * Tests that when the payload of the message is over the configured
     * maximum Kafka message size, then a header specifying the event ID is
     * added to the Kafka message, and that the payload is replaced with an
     * empty JSON object.
     */
    @Test
    @Transactional
    void testHeavyPayloadGetsStoredInDatabase() {
        // Prepare the fixtures for our function.
        final Bundle bundle = this.resourceHelpers.createBundle("bundle-test-heavy-payload");
        final Application application = this.resourceHelpers.createApp(bundle.getId(), "app-test-heavy-payload");
        final EventType eventType = this.resourceHelpers.createEventType(application.getId(), "event-test-heavy-payload");
        final Event event = this.resourceHelpers.createEvent(eventType);
        final Endpoint endpoint = this.resourceHelpers.createEndpoint(EndpointType.WEBHOOK, null, true, 0);
        final JsonObject payload = new JsonObject("{\"flavor\": \"Red Hat Enterprise Linux\"}");

        // Simulate that the payload exceeds the limit that we have set for the
        // Kafka message size.
        Mockito.when(this.engineConfig.getKafkaToCamelMaximumRequestSize()).thenReturn(0);

        // Call the function under test.
        this.connectorSender.send(event, endpoint, payload);

        // Assert that the repository for saving the payloads was called.
        Mockito.verify(this.payloadDetailsRepository, Mockito.times(1)).save(Mockito.any());

        // Get the Kafka sink.
        final InMemorySink<JsonObject> messages = this.inMemoryConnector.sink(ConnectorSender.TOCAMEL_CHANNEL);

        // Wait until we receive the message.
        Awaitility.await().until(
            () -> messages.received().size() == 1
        );

        // Check that the "event ID" header is present in the message.
        final Message<JsonObject> message = messages.received().getFirst();
        final Optional<OutgoingKafkaRecordMetadata> metadataOptional = message.getMetadata(OutgoingKafkaRecordMetadata.class);

        if (metadataOptional.isEmpty()) {
            Assertions.fail("no headers are present in the message's metadata, and at least the one about the event ID was expected");
        }

        final OutgoingKafkaRecordMetadata metadata = metadataOptional.get();
        boolean headerFound = false;
        for (final Header header : metadata.getHeaders().headers(PayloadDetails.X_RH_NOTIFICATIONS_CONNECTOR_PAYLOAD_HEADER)) {
            if (header.key().equals(PayloadDetails.X_RH_NOTIFICATIONS_CONNECTOR_PAYLOAD_HEADER)) {
                headerFound = true;
                Assertions.assertEquals(event.getId().toString(), new String(header.value(), StandardCharsets.UTF_8));
            }
        }

        if (!headerFound) {
            Assertions.fail("the Kafka header with the event's ID was not found in the Kafka message");
        }

        // Assert that we have replaced the contents of the messdage with an
        // empty payload.
        Assertions.assertEquals(new JsonObject().encode(), message.getPayload().encode());
    }
}
