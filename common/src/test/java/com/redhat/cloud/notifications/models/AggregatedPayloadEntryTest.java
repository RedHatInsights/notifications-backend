package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Payload;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AggregatedPayloadEntryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void contextKeyIsTheAgreedContract() {
        assertEquals("aggregated_payloads", AggregatedPayloadEntry.CONTEXT_KEY);
    }

    @Test
    void serializesWithSnakeCaseKeys() throws Exception {
        UUID payloadId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();

        Map<?, ?> json = objectMapper.readValue(objectMapper.writeValueAsString(new AggregatedPayloadEntry(payloadId, endpointId)), Map.class);

        assertEquals(Map.of("payload_details_id", payloadId.toString(), "endpoint_id", endpointId.toString()), json);
    }

    @Test
    void toMapUsesPlainStringValues() {
        UUID payloadId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();

        Map<String, Object> map = new AggregatedPayloadEntry(payloadId, endpointId).toMap();

        assertEquals(Map.of("payload_details_id", payloadId.toString(), "endpoint_id", endpointId.toString()), map);
    }

    @Test
    void deserializesFromTheMapProducedByToMap() {
        AggregatedPayloadEntry entry = new AggregatedPayloadEntry(UUID.randomUUID(), UUID.randomUUID());

        assertEquals(entry, objectMapper.convertValue(entry.toMap(), AggregatedPayloadEntry.class));
    }

    @Test
    void entryMissingAFieldIsRejected() {
        Map<String, Object> missingEndpoint = Map.of("payload_details_id", UUID.randomUUID().toString());
        Map<String, Object> missingPayload = Map.of("endpoint_id", UUID.randomUUID().toString());

        assertThrows(IllegalArgumentException.class, () -> objectMapper.convertValue(missingEndpoint, AggregatedPayloadEntry.class));
        assertThrows(IllegalArgumentException.class, () -> objectMapper.convertValue(missingPayload, AggregatedPayloadEntry.class));
        assertThrows(NullPointerException.class, () -> new AggregatedPayloadEntry(null, UUID.randomUUID()));
        assertThrows(NullPointerException.class, () -> new AggregatedPayloadEntry(UUID.randomUUID(), null));
    }

    /*
     * The entries travel inside the Action context of the aggregation trigger. This is the same Parser the aggregator
     * encodes with and the engine decodes with, so it settles that the ingress schema accepts a list of objects as an
     * additional context property and that the entries survive the trip.
     */
    @Test
    void entriesSurviveAnActionRoundTrip() {
        List<AggregatedPayloadEntry> entries = List.of(
            new AggregatedPayloadEntry(UUID.randomUUID(), UUID.randomUUID()),
            new AggregatedPayloadEntry(UUID.randomUUID(), UUID.randomUUID()));
        Action action = new Action.ActionBuilder()
            .withId(UUID.randomUUID())
            .withBundle("console")
            .withApplication("notifications")
            .withEventType("aggregation")
            .withOrgId("someOrgId")
            .withTimestamp(LocalDateTime.now())
            .withEvents(List.of(new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(new Payload.PayloadBuilder().build())
                .build()))
            .withContext(Context.builder()
                .withAdditionalProperty(AggregatedPayloadEntry.CONTEXT_KEY, entries.stream().map(AggregatedPayloadEntry::toMap).toList())
                .build())
            .build();

        Action decoded = Parser.decode(Parser.encode(action));

        Object value = decoded.getContext().getAdditionalProperties().get(AggregatedPayloadEntry.CONTEXT_KEY);
        assertEquals(entries, objectMapper.convertValue(value, new TypeReference<List<AggregatedPayloadEntry>>() { }));
    }
}
