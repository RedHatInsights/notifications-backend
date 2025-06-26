package com.redhat.cloud.notifications.models;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Objects;

public class AggregationCommand {

    @NotNull
    private EventAggregationCriterion aggregationKey;

    @NotNull
    private final LocalDateTime start;

    @NotNull
    private final LocalDateTime end;

    @NotNull
    private final SubscriptionType subscriptionType;

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

    public String getOrgId() {
        return aggregationKey.getOrgId();
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
