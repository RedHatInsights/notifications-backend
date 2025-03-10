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
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ConnectorSenderTest {
    @Inject
    ConnectorSender connectorSender;

    @InjectSpy
    EngineConfig engineConfig;

    @Any
    @Inject
    InMemoryConnector inMemoryConnector;

    @InjectMock
    PayloadDetailsRepository payloadDetailsRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    /**
     * Clear the Kafka topics so that each test can verify the exact number
     * of messages received, as otherwise the topics get not cleared until the
     * whole test class is run.
     */
    @AfterEach
    void cleanUp() {
        this.inMemoryConnector.sink(ConnectorSender.HIGH_VOLUME_CHANNEL).clear();
        this.inMemoryConnector.sink(ConnectorSender.TOCAMEL_CHANNEL).clear();
    }

    /**
     * Tests that events incoming from a high volume application are sent to
     * the high volume Kafka topic.
     */
    @Test
    @Transactional
    void testHighVolumeEventGetsSentToHighVolumeTopic() {
        // Prepare the fixtures for our function.
        final Bundle bundle = this.resourceHelpers.createBundle("bundle-test-high-volume");
        final Application application = this.resourceHelpers.createApp(bundle.getId(), ConnectorSender.HIGH_VOLUME_APPLICATION);
        final EventType eventType = this.resourceHelpers.createEventType(application.getId(), "event-test-high-volume");
        final Event event = this.resourceHelpers.createEvent(eventType);
        final Endpoint endpoint = this.resourceHelpers.createEndpoint(EndpointType.EMAIL_SUBSCRIPTION, null, true, 0);

        final JsonObject payload = new JsonObject();
        payload.put("Red Hat", "Red Hat Enterprise Linux");

        // Enable the high volume topic for the test.
        Mockito.when(this.engineConfig.isOutgoingKafkaHighVolumeTopicEnabled()).thenReturn(true);

        // Call the function under test.
        this.connectorSender.send(event, endpoint, payload);

        // Get the Kafka sink.
        final InMemorySink<JsonObject> highVolumeMessages = this.inMemoryConnector.sink(ConnectorSender.HIGH_VOLUME_CHANNEL);

        // Wait until we receive the message.
        Awaitility.await().until(
            () -> highVolumeMessages.received().size() == 1
        );

        final Message<JsonObject> message = highVolumeMessages.received().getFirst();
        final JsonObject receivedPayload = message.getPayload();

        Assertions.assertEquals(payload.encode(), receivedPayload.encode(), "the received payload does not match");

        // Assert that the regular "tocamel" topic did not receive the event.
        final InMemorySink<JsonObject> regularMessages = this.inMemoryConnector.sink(ConnectorSender.TOCAMEL_CHANNEL);

        // Verify that the regular channel did not receive any messages.
        Assertions.assertEquals(0, regularMessages.received().size(), "no messages should have been received in the \"tocamel\" topic");
    }

    /**
     * Tests that when the payload of the message is over the configured
     * maximum Kafka message size the payload is stored in the database, and
     * that the payload itself that gets sent to Kafka only contains the
     * payload's identifier.
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

        final JsonObject payload = new JsonObject();
        payload.put("flavor", "Red Hat Enterprise Linux");

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

        // Simply validate that there is a "payload id" key in the payload
        // the engine sends. The identifier gets generated by Hibernate, and
        // since we are mocking it, it will simply be null. However, having the
        // key there signals that we went through the correct branch in the
        // "if" statement.
        final Message<JsonObject> message = messages.received().getFirst();
        final JsonObject receivedPayload = message.getPayload();

        final JsonObject expectedPayload = new JsonObject();
        expectedPayload.put(PayloadDetails.PAYLOAD_DETAILS_ID_KEY, null);

        Assertions.assertEquals(expectedPayload.encode(), receivedPayload.encode(), "the received payload should only contain the payload's identifier");
    }
}
