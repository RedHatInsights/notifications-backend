package com.redhat.cloud.notifications.routers.internal.models;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AddApplicationRequest {
    @NotNull
    @Pattern(regexp = "[a-z][a-z_0-9-]*")
    @Size(max = 255)
    public String name;

    @NotNull
    public String displayName;

    @NotNull
    public UUID bundleId;

    @Size(max = 200)
    public String ownerRole;
}
