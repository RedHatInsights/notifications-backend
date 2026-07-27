package com.redhat.cloud.notifications.models.dto.v2.subscriptions;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;

import java.util.List;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@JsonNaming(SnakeCaseStrategy.class)
public class SubscriptionChannelDTO {

    @NotNull
    private SubscriptionTypeDTO subscriptionType;

    @NotNull
    private List<SeverityDTO> subscribedSeverities;

    public SubscriptionChannelDTO() {
    }

    public SubscriptionChannelDTO(SubscriptionTypeDTO subscriptionType, List<SeverityDTO> subscribedSeverities) {
        this.subscriptionType = subscriptionType;
        this.subscribedSeverities = subscribedSeverities;
    }

    public SubscriptionTypeDTO getSubscriptionType() {
        return subscriptionType;
    }

    public void setSubscriptionType(SubscriptionTypeDTO subscriptionType) {
        this.subscriptionType = subscriptionType;
    }

    public List<SeverityDTO> getSubscribedSeverities() {
        return subscribedSeverities;
    }

    public void setSubscribedSeverities(List<SeverityDTO> subscribedSeverities) {
        this.subscribedSeverities = subscribedSeverities;
    }
}
