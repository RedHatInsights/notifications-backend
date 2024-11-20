package com.redhat.cloud.notifications.models;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class AggregationCommand<T extends EmailAggregationKey> {

    @NotNull
    private T aggregationKey;

    @NotNull
    private final LocalDateTime start;

    @NotNull
    private final LocalDateTime end;

    @NotNull
    private final SubscriptionType subscriptionType;

    public AggregationCommand(T aggregationKey, LocalDateTime start, LocalDateTime end, SubscriptionType subscriptionType) {
        this.aggregationKey = aggregationKey;
        this.start = start;
        this.end = end;
        this.subscriptionType = subscriptionType;
    }

    public void setAggregationKey(@NotNull T aggregationKey) {
        this.aggregationKey = aggregationKey;
    }

    public EmailAggregationKey getAggregationKey() {
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

    public String toString() {
        return "AggregationCommand{" +
                "aggregationKey=" + aggregationKey +
                ", start=" + start +
                ", end=" + end +
                ", subscriptionType=" + subscriptionType +
                '}';
    }
}
