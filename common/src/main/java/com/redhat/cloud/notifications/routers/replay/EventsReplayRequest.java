package com.redhat.cloud.notifications.routers.replay;

import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.models.EndpointType;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

/**
 * Represents the internal request that will be sent from the backend to the
 * engine in order to replay events processing in case of outage.
 */
@JsonNaming(SnakeCaseStrategy.class)
public class EventsReplayRequest {

    @NotNull
    public LocalDateTime startDate;

    @NotNull
    public LocalDateTime endDate;

    @NotNull
    public EndpointType endpointType;

    public String endpointSubType;

    public String orgId;
}
