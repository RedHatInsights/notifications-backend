package com.redhat.cloud.notifications.routers.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING;
import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@JsonNaming(SnakeCaseStrategy.class)
public class EventLogEntry {

    @NotNull
    private UUID id;

    @NotNull
    @JsonFormat(shape = STRING)
    private LocalDateTime created;

    @NotNull
    private String bundle;

    @NotNull
    private String application;

    @NotNull
    private String eventType;

    @NotNull
    private List<EventLogEntryAction> actions;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public String getBundle() {
        return bundle;
    }

    public void setBundle(String bundle) {
        this.bundle = bundle;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public List<EventLogEntryAction> getActions() {
        return actions;
    }

    public void setActions(List<EventLogEntryAction> actions) {
        this.actions = actions;
    }
}
