package com.redhat.cloud.notifications.routers.internal.models.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SubscriptionUnsubscribeStatDTO {

    public UUID eventTypeId;
    public String eventTypeDisplayName;
    public long unsubscribeCount;
}
