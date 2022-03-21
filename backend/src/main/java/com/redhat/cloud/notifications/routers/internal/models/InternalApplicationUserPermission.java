package com.redhat.cloud.notifications.routers.internal.models;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.validation.constraints.NotNull;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class InternalApplicationUserPermission {

    @NotNull
    public String applicationId;
    @NotNull
    public String applicationDisplayName;
    @NotNull
    public String role;

}
