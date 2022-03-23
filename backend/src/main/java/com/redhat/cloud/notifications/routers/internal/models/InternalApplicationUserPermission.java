package com.redhat.cloud.notifications.routers.internal.models;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.validation.constraints.NotNull;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InternalApplicationUserPermission {

    @NotNull
    public UUID applicationId;
    @NotNull
    public String applicationDisplayName;
    @NotNull
    public String role;

}
