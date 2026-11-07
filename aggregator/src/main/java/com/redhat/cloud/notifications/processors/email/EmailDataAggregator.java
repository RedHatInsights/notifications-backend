package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.db.EventAggregationQueryRepository;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventAggregationCriterion;
import com.redhat.cloud.notifications.processors.email.aggregators.AbstractEmailPayloadAggregator;
import com.redhat.cloud.notifications.processors.email.aggregators.EmailPayloadAggregatorFactory;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import com.redhat.cloud.notifications.utils.RecipientsAuthorizationCriterionExtractor;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Single-pass counterpart to engine's per-recipient {@code EmailAggregator}: folds every event in
 * the window into one shared context per org+bundle+app, regardless of recipient. Recipient
 * resolution and severity-based personalization stay engine's job, applied later as a trim over the
 * already-folded result.
 */
@ApplicationScoped
public class EmailDataAggregator {

    // This is manually read from the raw event's JSON instead of converting it to an Action and using
    // getRecipients(), matching engine's EmailAggregator.RECIPIENTS_KEY usage.
    private static final String RECIPIENTS_KEY = "recipients";

    @Inject
    EventAggregationQueryRepository eventAggregationQueryRepository;

    @Inject
    BaseTransformer baseTransformer;

    @Inject
    RecipientsAuthorizationCriterionExtractor recipientsAuthorizationCriterionExtractor;

    @ConfigProperty(name = "notifications.aggregation.max-page-size", defaultValue = "100")
    int maxPageSize;

    /**
     * Folds every event for the given app into one shared context.
     *
     * @return the folded context, or {@code null} if the app has no fold-only aggregator yet (see
     *         {@link EmailPayloadAggregatorFactory}) or there was nothing to fold.
     */
    public Map<String, Object> getAggregatedData(EventAggregationCriterion aggregationKey, LocalDateTime start, LocalDateTime end) {
        AbstractEmailPayloadAggregator aggregator = EmailPayloadAggregatorFactory.by(aggregationKey);
        if (aggregator == null) {
            Log.debugf("No fold-only aggregator available yet for %s, skipping", aggregationKey);
            return null;
        }

        int offset = 0;
        int totalAggregatedElements = 0;
        List<Event> events;
        do {
            events = eventAggregationQueryRepository.getEventsForAggregation(aggregationKey, start, end, offset, maxPageSize);
            offset += maxPageSize;

            for (Event event : events) {
                // Event.eventWrapper is @Transient -- not persisted, so a freshly loaded Event has it
                // null. extract() hydrates it as a side effect (same reliance engine's own
                // EmailAggregator.resolveRecipients() has on this call) before toJsonObject() needs it.
                recipientsAuthorizationCriterionExtractor.extract(event);

                EmailAggregation data = new EmailAggregation(
                    event.getOrgId(),
                    aggregationKey.getBundle(),
                    aggregationKey.getApplication(),
                    baseTransformer.toJsonObject(event),
                    event.getSeverity(),
                    event.getEventType().getId());
                data.setEventTypeName(event.getEventType().getName());
                data.setSubscribedByDefault(event.getEventType().isSubscribedByDefault());
                data.setRecipientsOverride(extractRecipientsOverride(event));
                aggregator.aggregate(data);
            }
            totalAggregatedElements += events.size();
        } while (maxPageSize == events.size());

        Log.infof("%d elements were aggregated for key %s", totalAggregatedElements, aggregationKey);

        return aggregator.isEmpty() ? null : aggregator.getContext();
    }

    /**
     * Note: {@code event.getPayload()} is the raw, full Action-level JSON string as originally
     * published (see {@code EventConsumer.process()} -- {@code event = new Event(eventType,
     * message.getPayload(), ...)}), not {@link BaseTransformer#toJsonObject(Event)}'s reconstructed
     * shape, which doesn't carry the top-level {@code recipients} field at all. Engine's own
     * {@code EmailAggregator.getActionRecipientSettings()} reads from the same raw source for the same
     * reason.
     */
    private JsonArray extractRecipientsOverride(Event event) {
        JsonArray recipients = new JsonObject(event.getPayload()).getJsonArray(RECIPIENTS_KEY);
        return (recipients == null || recipients.isEmpty()) ? null : recipients;
    }
}
