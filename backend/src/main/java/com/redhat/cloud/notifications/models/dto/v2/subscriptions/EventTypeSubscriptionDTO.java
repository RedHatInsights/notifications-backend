package com.redhat.cloud.notifications.models.dto.v2.subscriptions;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;

import java.util.List;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@JsonNaming(SnakeCaseStrategy.class)
public class EventTypeSubscriptionDTO {

    @NotNull
    private String eventType;

    @NotNull
    private String displayName;

    @NotNull
    private List<SeverityDTO> availableSeverities;

    @NotNull
    private List<SubscriptionChannelDTO> subscriptions;

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public List<SeverityDTO> getAvailableSeverities() {
        return availableSeverities;
    }

    public void setAvailableSeverities(List<SeverityDTO> availableSeverities) {
        this.availableSeverities = availableSeverities;
    }

    public List<SubscriptionChannelDTO> getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(List<SubscriptionChannelDTO> subscriptions) {
        this.subscriptions = subscriptions;
    }
}
