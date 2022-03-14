package com.redhat.cloud.notifications.routers.internal.models;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.UUID;

public class AddApplicationRequest {
    @NotNull
    @Pattern(regexp = "[a-z][a-z_0-9-]*")
    public String name;

    @NotNull
    public String displayName;

    @NotNull
    public UUID bundleId;

    public String ownerRole;
}
