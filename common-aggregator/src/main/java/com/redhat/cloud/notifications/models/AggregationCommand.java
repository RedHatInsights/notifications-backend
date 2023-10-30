package com.redhat.cloud.notifications.models;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class AggregationCommand {

    @NotNull
    private final EmailAggregationKey aggregationKey;

    @NotNull
    private final LocalDateTime start;

    @NotNull
    private final LocalDateTime end;

    @NotNull
    private final SubscriptionType subscriptionType;

    public AggregationCommand(EmailAggregationKey aggregationKey, LocalDateTime start, LocalDateTime end, SubscriptionType subscriptionType) {
        this.aggregationKey = aggregationKey;
        this.start = start;
        this.end = end;
        this.subscriptionType = subscriptionType;
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

    @Override
    public String toString() {
        return "AggregationCommand{" +
                "aggregationKey=" + aggregationKey +
                ", start=" + start +
                ", end=" + end +
                ", subscriptionType=" + subscriptionType +
                '}';
    }
}
