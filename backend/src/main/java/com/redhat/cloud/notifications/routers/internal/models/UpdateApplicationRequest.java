package com.redhat.cloud.notifications.routers.internal.models;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Represents the expected payload for an "update application" request.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UpdateApplicationRequest {
    public String name;
    public String displayName;
    public String ownerRole;
}
