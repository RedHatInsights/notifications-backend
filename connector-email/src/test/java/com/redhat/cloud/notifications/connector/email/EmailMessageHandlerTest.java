package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.email.engine.InternalEngine;
import com.redhat.cloud.notifications.connector.email.model.EmailNotification;
import com.redhat.cloud.notifications.connector.email.model.HandledEmailMessageDetails;
import com.redhat.cloud.notifications.connector.email.payload.PayloadDetails;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class EmailMessageHandlerTest {

    @Inject
    EmailMessageHandler emailMessageHandler;

    @InjectMock
    @RestClient
    InternalEngine internalEngine;

    // --- extractPayload tests ---

    @Test
    void testExtractPayloadWithInvalidUUID() {
        JsonObject cloudEventData = new JsonObject()
            .put(PayloadDetails.PAYLOAD_DETAILS_ID_KEY, "not-a-uuid");
        HandledEmailMessageDetails details = new HandledEmailMessageDetails();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> emailMessageHandler.extractPayload(cloudEventData, details));
        assertTrue(ex.getMessage().contains("Invalid payload ID format"));
        assertTrue(ex.getMessage().contains("not-a-uuid"));
    }

    @Test
    void testExtractPayloadWithNullResponseFromEngine() {
        String payloadId = UUID.randomUUID().toString();
        when(internalEngine.getPayloadDetails(payloadId)).thenReturn(null);

        JsonObject cloudEventData = new JsonObject()
            .put(PayloadDetails.PAYLOAD_DETAILS_ID_KEY, payloadId);
        HandledEmailMessageDetails details = new HandledEmailMessageDetails();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> emailMessageHandler.extractPayload(cloudEventData, details));
        assertTrue(ex.getMessage().contains("Engine returned null payload"));
    }

    @Test
    void testExtractPayloadWithNullContentsFromEngine() {
        String payloadId = UUID.randomUUID().toString();
        when(internalEngine.getPayloadDetails(payloadId)).thenReturn(new PayloadDetails(null));

        JsonObject cloudEventData = new JsonObject()
            .put(PayloadDetails.PAYLOAD_DETAILS_ID_KEY, payloadId);
        HandledEmailMessageDetails details = new HandledEmailMessageDetails();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> emailMessageHandler.extractPayload(cloudEventData, details));
        assertTrue(ex.getMessage().contains("Engine returned null payload"));
    }

    @Test
    void testExtractPayloadWithInvalidJsonFromEngine() {
        String payloadId = UUID.randomUUID().toString();
        when(internalEngine.getPayloadDetails(payloadId)).thenReturn(new PayloadDetails("not valid json"));

        JsonObject cloudEventData = new JsonObject()
            .put(PayloadDetails.PAYLOAD_DETAILS_ID_KEY, payloadId);
        HandledEmailMessageDetails details = new HandledEmailMessageDetails();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> emailMessageHandler.extractPayload(cloudEventData, details));
        assertTrue(ex.getMessage().contains("Engine returned invalid JSON payload"));
    }

    @Test
    void testExtractPayloadWithMappingFailure() {
        JsonObject cloudEventData = new JsonObject()
            .put("recipient_settings", "not-an-array");
        HandledEmailMessageDetails details = new HandledEmailMessageDetails();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> emailMessageHandler.extractPayload(cloudEventData, details));
        assertTrue(ex.getMessage().contains("Failed to parse email notification"));
    }

    @Test
    void testExtractPayloadSetsPayloadIdOnDetails() {
        String payloadId = UUID.randomUUID().toString();

        JsonObject innerPayload = buildMinimalEmailNotificationJson();
        when(internalEngine.getPayloadDetails(payloadId)).thenReturn(new PayloadDetails(innerPayload.encode()));

        JsonObject cloudEventData = new JsonObject()
            .put(PayloadDetails.PAYLOAD_DETAILS_ID_KEY, payloadId);
        HandledEmailMessageDetails details = new HandledEmailMessageDetails();

        EmailNotification result = emailMessageHandler.extractPayload(cloudEventData, details);
        assertNotNull(result);
        assertEquals(payloadId, details.payloadId);
    }

    @Test
    void testExtractPayloadWithoutPayloadId() {
        JsonObject cloudEventData = buildMinimalEmailNotificationJson();
        HandledEmailMessageDetails details = new HandledEmailMessageDetails();

        EmailNotification result = emailMessageHandler.extractPayload(cloudEventData, details);
        assertNotNull(result);
        assertNull(details.payloadId);
    }

    private static JsonObject buildMinimalEmailNotificationJson() {
        return new JsonObject()
            .put("recipient_settings", List.of())
            .put("subscribers", List.of())
            .put("unsubscribers", List.of())
            .put("subscribed_by_default", false)
            .put("event_data", Map.of())
            .put("id_daily_digest", false);
    }

    // --- requireEventDataField tests ---

    @Test
    void testExtractPayloadWithMissingBundleField() {
        Map<String, Object> eventData = Map.of("application", "patch", "event_type", "new-advisory");
        JsonObject cloudEventData = buildEmailNotificationJson(eventData);
        HandledEmailMessageDetails details = new HandledEmailMessageDetails();

        EmailNotification result = emailMessageHandler.extractPayload(cloudEventData, details);
        assertNotNull(result);
        assertNull(result.eventData().get("bundle"));
    }

    @Test
    void testExtractPayloadWithMissingApplicationField() {
        Map<String, Object> eventData = Map.of("bundle", "rhel", "event_type", "new-advisory");
        JsonObject cloudEventData = buildEmailNotificationJson(eventData);
        HandledEmailMessageDetails details = new HandledEmailMessageDetails();

        EmailNotification result = emailMessageHandler.extractPayload(cloudEventData, details);
        assertNotNull(result);
        assertNull(result.eventData().get("application"));
    }

    @Test
    void testExtractPayloadWithMissingEventTypeField() {
        Map<String, Object> eventData = Map.of("bundle", "rhel", "application", "patch");
        JsonObject cloudEventData = buildEmailNotificationJson(eventData);
        HandledEmailMessageDetails details = new HandledEmailMessageDetails();

        EmailNotification result = emailMessageHandler.extractPayload(cloudEventData, details);
        assertNotNull(result);
        assertNull(result.eventData().get("event_type"));
    }

    private static JsonObject buildEmailNotificationJson(Map<String, Object> eventData) {
        return new JsonObject()
            .put("recipient_settings", List.of())
            .put("subscribers", List.of())
            .put("unsubscribers", List.of())
            .put("subscribed_by_default", false)
            .put("event_data", eventData)
            .put("id_daily_digest", false);
    }

    // --- partition tests ---

    @Test
    void testPartitionSingleBatch() {
        Set<String> input = Set.of("a", "b", "c");
        List<List<String>> result = EmailMessageHandler.partition(input, 5);
        assertEquals(1, result.size());
        assertEquals(3, result.getFirst().size());
    }

    @Test
    void testPartitionMultipleBatches() {
        Set<String> input = Set.of("a", "b", "c", "d", "e");
        List<List<String>> result = EmailMessageHandler.partition(input, 2);
        assertEquals(3, result.size());
        int total = result.stream().mapToInt(List::size).sum();
        assertEquals(5, total);
        Set<String> flattened = result.stream().flatMap(List::stream).collect(Collectors.toSet());
        assertEquals(input, flattened);
    }

    @Test
    void testPartitionExactFit() {
        Set<String> input = Set.of("a", "b", "c", "d");
        List<List<String>> result = EmailMessageHandler.partition(input, 4);
        assertEquals(1, result.size());
        assertEquals(4, result.getFirst().size());
    }

    @Test
    void testPartitionSingleElement() {
        Set<String> input = Set.of("a");
        List<List<String>> result = EmailMessageHandler.partition(input, 3);
        assertEquals(1, result.size());
        assertEquals(1, result.getFirst().size());
    }

    @Test
    void testPartitionEmpty() {
        Set<String> input = Set.of();
        List<List<String>> result = EmailMessageHandler.partition(input, 3);
        assertTrue(result.isEmpty());
    }

    @Test
    void testPartitionByOne() {
        Set<String> input = Set.of("a", "b", "c");
        List<List<String>> result = EmailMessageHandler.partition(input, 1);
        assertEquals(3, result.size());
        result.forEach(batch -> assertEquals(1, batch.size()));
        Set<String> flattened = result.stream().flatMap(List::stream).collect(Collectors.toSet());
        assertEquals(input, flattened);
    }

    @Test
    void testPartitionByZeroThrows() {
        Set<String> input = Set.of("a");
        assertThrows(ArithmeticException.class, () -> EmailMessageHandler.partition(input, 0));
    }
}
