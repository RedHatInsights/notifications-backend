package com.redhat.cloud.notifications.routers.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.models.EndpointType;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@JsonNaming(SnakeCaseStrategy.class)
public class EventLogEntryAction {

    @NotNull
    private UUID id;

    @NotNull
    private EndpointType endpointType;

    private String endpointSubType;

    @NotNull
    @Deprecated
    private Boolean invocationResult;

    @NotNull
    private EventLogEntryActionStatus status;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private UUID endpointId;

    @Nullable
    private Map<String, Object> details;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public EndpointType getEndpointType() {
        return endpointType;
    }

    public void setEndpointType(EndpointType endpointType) {
        this.endpointType = endpointType;
    }

    public String getEndpointSubType() {
        return endpointSubType;
    }

    public void setEndpointSubType(String endpointSubType) {
        this.endpointSubType = endpointSubType;
    }

    public Boolean getInvocationResult() {
        return invocationResult;
    }

    public void setInvocationResult(Boolean invocationResult) {
        this.invocationResult = invocationResult;
    }

    public EventLogEntryActionStatus getStatus() {
        return status;
    }

    public void setStatus(EventLogEntryActionStatus status) {
        this.status = status;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(UUID endpointId) {
        this.endpointId = endpointId;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    @Nullable
    public Map<String, Object> getDetails() {
        return details;
    }
}
