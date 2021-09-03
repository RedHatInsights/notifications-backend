package com.redhat.cloud.notifications.models.aggregation;

import com.redhat.cloud.notifications.EmailSubscriptionType;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class AggregationCommand {

    @NotNull
    private final EmailAggregationKey aggregationKey;

    @NotNull
    private final LocalDateTime start;

    @NotNull
    private final LocalDateTime end;

    @NotNull
    private final EmailSubscriptionType subscriptionType;

    public AggregationCommand(EmailAggregationKey aggregationKey, LocalDateTime start, LocalDateTime end, EmailSubscriptionType subscriptionType) {
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

    public EmailSubscriptionType getSubscriptionType() {
        return subscriptionType;
    }
}
