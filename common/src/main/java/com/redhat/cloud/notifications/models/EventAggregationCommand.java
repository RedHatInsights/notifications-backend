package com.redhat.cloud.notifications.models;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class EventAggregationCommand implements IAggregationCommand {

    @NotNull
    private final EventAggregationCriteria aggregationKey;

    @NotNull
    private final LocalDateTime start;

    @NotNull
    private final LocalDateTime end;

    @NotNull
    private final SubscriptionType subscriptionType;

    public EventAggregationCommand(EventAggregationCriteria aggregationKey, LocalDateTime start, LocalDateTime end, SubscriptionType subscriptionType) {
        this.aggregationKey = aggregationKey;
        this.start = start;
        this.end = end;
        this.subscriptionType = subscriptionType;
    }

    public EventAggregationCriteria getAggregationKey() {
        return aggregationKey;
    }

    @Override
    public LocalDateTime getStart() {
        return start;
    }

    @Override
    public LocalDateTime getEnd() {
        return end;
    }

    @Override
    public SubscriptionType getSubscriptionType() {
        return subscriptionType;
    }

    @Override
    public String getOrgId() {
        return aggregationKey.getOrgId();
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
