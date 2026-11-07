package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AggregationCommand {

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
     * no constructor change, so existing callers are unaffected. Mutually exclusive with
     * {@link #aggregatedDataPayloadDetailsId}.
     */
    private Map<String, Object> aggregatedData;

    /**
     * Reference to a {@code payload_details} row holding this bundle's combined pre-folded digest
     * content, used instead of {@link #aggregatedData} when that content is too large to send inline.
     * Additive, see {@link #aggregatedData}.
     */
    private UUID aggregatedDataPayloadDetailsId;

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

    public UUID getAggregatedDataPayloadDetailsId() {
        return aggregatedDataPayloadDetailsId;
    }

    public void setAggregatedDataPayloadDetailsId(UUID aggregatedDataPayloadDetailsId) {
        this.aggregatedDataPayloadDetailsId = aggregatedDataPayloadDetailsId;
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
