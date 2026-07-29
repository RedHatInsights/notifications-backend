package com.redhat.cloud.notifications.models.dto.v2.subscriptions;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@JsonNaming(SnakeCaseStrategy.class)
public class EventTypeSubscriptionUpdateDTO {

    @NotBlank
    @Pattern(regexp = "[a-z][a-z_0-9-]*")
    private String eventType;

    @NotEmpty
    @Valid
    private List<@NotNull SubscriptionChannelDTO> subscriptions;

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public List<SubscriptionChannelDTO> getSubscriptions() {
        return subscriptions;
    }

    public void setSubscriptions(List<SubscriptionChannelDTO> subscriptions) {
        this.subscriptions = subscriptions;
    }
}
