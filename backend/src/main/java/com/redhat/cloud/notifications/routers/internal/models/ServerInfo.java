package com.redhat.cloud.notifications.routers.internal.models;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ServerInfo {
    public enum Environment {
        PROD,
        STAGE,
        EPHEMERAL,
        LOCAL_SERVER
    }

    public Environment environment;
}
