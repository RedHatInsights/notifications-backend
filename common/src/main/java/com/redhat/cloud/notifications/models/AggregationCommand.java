package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AggregationCommand {

    /**
     * Key, in the trigger Action's context, of the {@code payload_details} row ID holding the whole
     * bundle's combined pre-folded digest content, used instead of inline {@link #aggregatedData}
     * when that content is too large to send inline.
     */
    public static final String AGGREGATED_DATA_PAYLOAD_DETAILS_ID_CONTEXT_KEY = "aggregated_data_payload_details_id";

    @NotNull
    private EventAggregationCriterion aggregationKey;

    @NotNull
    private final LocalDateTime start;

    @NotNull
    private final LocalDateTime end;

    @NotNull
    private final SubscriptionType subscriptionType;

    /**
     * The aggregator's pre-folded digest content for this app, when the org is flagged into the
     * fold-only path and a fold-only aggregator exists for this app. {@code null} otherwise, in which
     * case engine falls back to folding the raw events itself. Additive field, set via setter only --
     * no constructor change, so existing callers are unaffected. When the bundle's combined content is
     * too large to send inline, this stays {@code null} and the Action's context carries a reference
     * instead, under {@link #AGGREGATED_DATA_PAYLOAD_DETAILS_ID_CONTEXT_KEY}.
     */
    private Map<String, Object> aggregatedData;

    public AggregationCommand(EventAggregationCriterion aggregationKey, LocalDateTime start, LocalDateTime end, SubscriptionType subscriptionType) {
        this.aggregationKey = aggregationKey;
        this.start = start;
        this.end = end;
        this.subscriptionType = subscriptionType;
    }

    public void setAggregationKey(@NotNull EventAggregationCriterion aggregationKey) {
        this.aggregationKey = aggregationKey;
    }

    public EventAggregationCriterion getAggregationKey() {
        return aggregationKey;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public SubscriptionType getSubscriptionType() {
        return subscriptionType;
    }

    public Map<String, Object> getAggregatedData() {
        return aggregatedData;
    }

    public void setAggregatedData(Map<String, Object> aggregatedData) {
        this.aggregatedData = aggregatedData;
    }

    public String getOrgId() {
        return aggregationKey.getOrgId();
    }

    public UUID getBundleId() {
        return aggregationKey.getBundleId();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AggregationCommand that = (AggregationCommand) o;
        return Objects.equals(aggregationKey, that.aggregationKey) && Objects.equals(start, that.start) && Objects.equals(end, that.end) && subscriptionType == that.subscriptionType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(aggregationKey, start, end, subscriptionType);
    }

    public String toString() {
        return "AggregationCommand{" +
                "aggregationKey=" + aggregationKey +
                ", start=" + start +
                ", end=" + end +
                ", subscriptionType=" + subscriptionType +
                '}';
    }
}
