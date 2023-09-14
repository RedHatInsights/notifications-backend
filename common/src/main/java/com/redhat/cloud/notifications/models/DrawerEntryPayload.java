package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

import static com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING;
import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@JsonNaming(SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DrawerEntryPayload {

    private UUID id;

    private String description;

    private String title;

    @JsonFormat(shape = STRING)
    private LocalDateTime created;

    @NotNull
    private boolean read;

    private String source;

    public DrawerEntryPayload() {
    }

    public DrawerEntryPayload(Object[] rawDrawerEntry) {
        id = (UUID) rawDrawerEntry[0];
        read = (boolean) rawDrawerEntry[1];
        source = String.format("%s - %s", rawDrawerEntry[2], rawDrawerEntry[3]);
        title = (String) rawDrawerEntry[4];
        created = (LocalDateTime) rawDrawerEntry[5];
        description = (String) rawDrawerEntry[6];
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
