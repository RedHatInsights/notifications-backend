package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One entry of the aggregated payloads that the aggregator hands to the engine in the Action context of the
 * aggregation trigger, under {@link #CONTEXT_KEY}. An entry points at ONE ready-to-send email: the
 * {@code payload_details} row that holds the complete email notification, and the email endpoint it is sent through.
 * <p>
 * Contract between the aggregator (producer) and the engine (consumer):
 * <ul>
 *   <li>The context property is present only when there is at least one entry. An absent property means that the
 *   aggregator did not produce any aggregated data, and the engine aggregates the events itself.</li>
 *   <li>The property value is a list of entries, serialized as plain JSON objects with the keys
 *   {@code payload_details_id} and {@code endpoint_id}.</li>
 *   <li>Both fields are mandatory: an entry missing one of them cannot be built, so a consumer treats it as unusable.</li>
 * </ul>
 *
 * @param payloadDetailsId the {@code payload_details} row that holds the stored email notification
 * @param endpointId       the email endpoint the stored notification is sent through
 */
public record AggregatedPayloadEntry(
    @JsonProperty("payload_details_id") UUID payloadDetailsId,
    @JsonProperty("endpoint_id") UUID endpointId
) {

    /**
     * Key of the Action context property that carries the list of entries.
     */
    public static final String CONTEXT_KEY = "aggregated_payloads";

    public AggregatedPayloadEntry {
        Objects.requireNonNull(payloadDetailsId, "payload_details_id is mandatory");
        Objects.requireNonNull(endpointId, "endpoint_id is mandatory");
    }

    /**
     * @return the entry as plain values (UUIDs as strings), ready to be put into the Action context.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("payload_details_id", payloadDetailsId.toString());
        map.put("endpoint_id", endpointId.toString());
        return map;
    }
}
