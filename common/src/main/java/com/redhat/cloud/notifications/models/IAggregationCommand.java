package com.redhat.cloud.notifications.models;

import java.time.LocalDateTime;

public interface IAggregationCommand<T extends EmailAggregationKey> {
    LocalDateTime getStart();

    LocalDateTime getEnd();

    SubscriptionType getSubscriptionType();

    String getOrgId();

    T getAggregationKey();
}
