package com.redhat.cloud.notifications.ephemeral;

import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Set;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@JsonNaming(SnakeCaseStrategy.class)
public class Application {
    public String name;
    public String displayName;
    public Set<EventType> eventTypes;
}
