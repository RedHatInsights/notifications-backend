package com.redhat.cloud.notifications.models.dto.v2.subscriptions;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;

import java.util.List;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@JsonNaming(SnakeCaseStrategy.class)
public class ApplicationSubscriptionDTO {

    @NotNull
    private String application;

    @NotNull
    private String applicationDisplayName;

    @NotNull
    private List<EventTypeSubscriptionDTO> eventTypes;

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public String getApplicationDisplayName() {
        return applicationDisplayName;
    }

    public void setApplicationDisplayName(String applicationDisplayName) {
        this.applicationDisplayName = applicationDisplayName;
    }

    public List<EventTypeSubscriptionDTO> getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(List<EventTypeSubscriptionDTO> eventTypes) {
        this.eventTypes = eventTypes;
    }
}
