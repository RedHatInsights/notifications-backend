package com.redhat.cloud.notifications.ephemeral;

import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@JsonNaming(SnakeCaseStrategy.class)
public class EventType {
    public String name;
    public String displayName;
    public String description;
}
