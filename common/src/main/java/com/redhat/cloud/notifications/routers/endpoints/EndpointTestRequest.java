package com.redhat.cloud.notifications.routers.endpoints;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;

public class EndpointTestRequest {

    @NotNull
    public UUID endpointUuid;

    @NotBlank
    public String orgId;

    public EndpointTestRequest(final UUID endpointUuid, final String orgId) {
        this.endpointUuid = endpointUuid;
        this.orgId = orgId;
    }
}
