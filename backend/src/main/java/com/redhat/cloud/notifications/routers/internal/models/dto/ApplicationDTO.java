package com.redhat.cloud.notifications.routers.internal.models.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ApplicationDTO {
    public UUID id;

    @NotNull
    @Pattern(regexp = "[a-z][a-z_0-9-]*")
    public String name;

    @NotNull
    public String displayName;

    @NotNull
    public UUID bundleId;

    public String ownerRole;

    public String created;
}
