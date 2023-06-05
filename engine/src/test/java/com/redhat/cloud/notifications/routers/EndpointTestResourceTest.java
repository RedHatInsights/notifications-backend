package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.events.ConnectorReceiver;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.event.TestEventHelper;
import com.redhat.cloud.notifications.routers.endpoints.EndpointTestRequest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.reactive.messaging.providers.connectors.InMemoryConnector;
import io.smallrye.reactive.messaging.providers.connectors.InMemorySink;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.enterprise.inject.Any;
import javax.inject.Inject;
import java.util.Map;

import static io.restassured.RestAssured.given;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EndpointTestResourceTest {

    @Any
    @Inject
    InMemoryConnector inMemoryConnector;

    @Inject
    ResourceHelpers resourceHelpers;

    /**
     * Tests that when a "Test Endpoint" request is received, a "test
     * endpoint integration" event is posted to the egress channel.
     */
    @Test
    void testEndpoint() {
        final String orgId = "test-endpoint-engine-test";
        final Endpoint createdEndpoint = this.resourceHelpers.createEndpoint(EndpointType.CAMEL, "slack", true, 0);

        final EndpointTestRequest endpointTestRequest = new EndpointTestRequest(createdEndpoint.getId(), orgId);

        // Call the endpoint under test.
        given()
            .when()
            .contentType(ContentType.JSON)
            .body(Json.encode(endpointTestRequest))
            .post("/internal/endpoints/test")
            .then()
            .statusCode(204);

        // We should receive the action triggered by the REST call.
        InMemorySink<String> actionsOut = this.inMemoryConnector.sink(ConnectorReceiver.EGRESS_CHANNEL);

        // Make sure that the message was received before continuing.
        Awaitility.await().until(
            () -> actionsOut.received().size() == 1
        );

        final var actionsList = actionsOut.received();

        // Only one test action should have been sent to Kafka.
        final var expectedActionsCount = 1;
        Assertions.assertEquals(expectedActionsCount, actionsList.size(), "unexpected number of actions sent to Kafka");

        final String kafkaActionRaw = actionsList.get(0).getPayload();
        final Action kafkaAction = Parser.decode(kafkaActionRaw);

        // Check that the top level values coincide.
        Assertions.assertEquals(TestEventHelper.TEST_ACTION_BUNDLE, kafkaAction.getBundle(), "unexpected bundle in the test action");
        Assertions.assertEquals(TestEventHelper.TEST_ACTION_APPLICATION, kafkaAction.getApplication(), "unexpected application in the test action");
        Assertions.assertEquals(TestEventHelper.TEST_ACTION_EVENT_TYPE, kafkaAction.getEventType(), "unexpected event type in the test action");
        Assertions.assertEquals(orgId, kafkaAction.getOrgId(), "unexpected org id in the test action");

        final Context context = kafkaAction.getContext();
        Map<String, Object> contextProperties = context.getAdditionalProperties();
        Assertions.assertEquals(createdEndpoint.getId().toString(), contextProperties.get(TestEventHelper.TEST_ACTION_CONTEXT_ENDPOINT_ID), "unexpected endpoint ID received in the action's context");

        // Check the events, their metadata and their payload.
        final var events = kafkaAction.getEvents();

        final var expectedEventsCount = 1;
        Assertions.assertEquals(expectedEventsCount, events.size(), "unexpected number of test action events");

        final Event event = events.get(0);
        final Metadata metadata = event.getMetadata();

        final Payload payload = event.getPayload();
        final Map<String, Object> payloadAdditionalProperties = payload.getAdditionalProperties();

        final var expectedPayloadAdditionalPropertiesCount = 1;
        Assertions.assertEquals(expectedPayloadAdditionalPropertiesCount, payloadAdditionalProperties.size(), "unexpected number of payload additional properties");

        final String payloadValue = (String) payload.getAdditionalProperties().get(TestEventHelper.TEST_ACTION_PAYLOAD_KEY);

        Assertions.assertEquals(TestEventHelper.TEST_ACTION_PAYLOAD_VALUE, payloadValue, "unexpected event payload value");
    }
}
