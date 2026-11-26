package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.db.repositories.EmailAggregationRepository;
import com.redhat.cloud.notifications.db.repositories.PayloadDetailsRepository;
import com.redhat.cloud.notifications.models.AggregatedPayloadEntry;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.PayloadDetails;
import com.redhat.cloud.notifications.processors.email.connector.dto.EmailNotification;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The new aggregation pipeline: builds the aggregated emails of an org and bundle, saves each one to
 * {@code payload_details} and returns one {@link AggregatedPayloadEntry} per email for the trigger.
 */
@ApplicationScoped
public class AggregatedPayloadBuilder {

    @Inject
    AggregatedDigestBuilder aggregatedDigestBuilder;

    @Inject
    EmailAggregationRepository emailAggregationRepository;

    @Inject
    PayloadDetailsRepository payloadDetailsRepository;

    public List<Map<String, Object>> build(List<AggregationCommand> aggregationCommands) {
        // Everything is built before anything is saved, and all rows are saved in one transaction, so a failure
        // leaves no rows behind.
        AggregatedDigestBuilder.Digests digests = aggregatedDigestBuilder.build(aggregationCommands);
        if (digests.notifications().isEmpty()) {
            return List.of();
        }

        Event anchorEvent = findAnchorEvent(aggregationCommands);
        Endpoint endpoint = digests.endpoint();

        List<PayloadDetails> payloadDetailsList = new ArrayList<>();
        for (EmailNotification notification : digests.notifications()) {
            JsonObject contents = JsonObject.mapFrom(notification);
            // The same two keys the engine's ConnectorSender adds to the payload: the email connector restores them from
            // the stored contents.
            contents.put("org_id", aggregationCommands.getFirst().getOrgId());
            contents.put("endpoint_id", endpoint.getId());

            payloadDetailsList.add(new PayloadDetails(anchorEvent, contents));
        }
        payloadDetailsRepository.saveAll(payloadDetailsList);

        // The ids are generated when the rows are persisted.
        List<Map<String, Object>> entries = payloadDetailsList.stream()
            .map(payloadDetails -> new AggregatedPayloadEntry(payloadDetails.getId(), endpoint.getId()).toMap())
            .toList();
        Log.debugf("[org_id: %s] Saved %d aggregated payloads", aggregationCommands.getFirst().getOrgId(), entries.size());
        return entries;
    }

    /*
     * payload_details.event_id is only the anchor of the ON DELETE CASCADE: any already persisted event works, so the
     * first event of the first command that has one is used.
     */
    private Event findAnchorEvent(List<AggregationCommand> aggregationCommands) {
        for (AggregationCommand command : aggregationCommands) {
            Optional<Event> event = emailAggregationRepository
                .getEmailAggregationBasedOnEvent(command.getAggregationKey(), command.getStart(), command.getEnd(), 0, 1)
                .stream().findFirst();
            if (event.isPresent()) {
                return event.get();
            }
        }
        throw new IllegalStateException("No event found to anchor the aggregated payloads of org " + aggregationCommands.getFirst().getOrgId());
    }
}
