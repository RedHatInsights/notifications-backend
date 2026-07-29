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
public class ApplicationSubscriptionUpdateDTO {

    @NotBlank
    @Pattern(regexp = "[a-z][a-z_0-9-]*")
    private String application;

    @NotEmpty
    @Valid
    private List<@NotNull EventTypeSubscriptionUpdateDTO> eventTypes;

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public List<EventTypeSubscriptionUpdateDTO> getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(List<EventTypeSubscriptionUpdateDTO> eventTypes) {
        this.eventTypes = eventTypes;
    }
}
