package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.db.EventAggregationQueryRepository;
import com.redhat.cloud.notifications.db.PayloadDetailsRepository;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventAggregationCriterion;
import com.redhat.cloud.notifications.processors.payload.PayloadDetails;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Guards against a pre-folded digest payload being too large for the {@code egress} Kafka producer to
 * send inline, offloading it to the shared {@code payload_details} table instead. Unlike today's bare
 * {@code AggregationCommand} pointer, the pre-folded payload scales with the org's event volume, so
 * this has no analogue in the pre-fold-only codebase.
 */
@ApplicationScoped
public class AggregatedDataOffloader {

    @Inject
    EventAggregationQueryRepository eventAggregationQueryRepository;

    @Inject
    PayloadDetailsRepository payloadDetailsRepository;

    @ConfigProperty(name = "mp.messaging.outgoing.egress.max.request.size", defaultValue = "1048576")
    int maxRequestSize;

    /**
     * Zero-margin check against the real final encoded message, matching the existing precedent in
     * engine's {@code ConnectorSender} -- checking the real artifact against the real limit removes
     * the need for an arbitrary safety-margin percentage.
     */
    public boolean exceedsMaxRequestSize(String encodedAction) {
        return encodedAction.getBytes(StandardCharsets.UTF_8).length >= maxRequestSize;
    }

    /**
     * Saves every app's folded data for this bundle as one combined {@code payload_details} row,
     * anchored to any already-persisted event matching the given criterion/window. Engine has to
     * reconstruct/merge all apps' data into one digest anyway, so there's no benefit to a per-app
     * surgical offload once a row is being written.
     *
     * @return the new row's ID, to be sent as a reference in place of the inline payload.
     */
    public UUID offload(Map<String, Map<String, Object>> aggregatedDataByApplication, EventAggregationCriterion anyAggregationKey, LocalDateTime start, LocalDateTime end) {
        Event anchorEvent = eventAggregationQueryRepository.findAnyEvent(anyAggregationKey, start, end)
            .orElseThrow(() -> new IllegalStateException("No persisted event found to anchor the offloaded payload for " + anyAggregationKey));

        PayloadDetails payloadDetails = new PayloadDetails(anchorEvent, JsonObject.mapFrom(aggregatedDataByApplication));
        payloadDetailsRepository.save(payloadDetails);
        return payloadDetails.getId();
    }
}
