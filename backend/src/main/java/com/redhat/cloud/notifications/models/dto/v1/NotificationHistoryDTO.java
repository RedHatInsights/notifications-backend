package com.redhat.cloud.notifications.models.dto.v1;

import com.redhat.cloud.notifications.models.CreationTimestamped;
import com.redhat.cloud.notifications.models.NotificationStatus;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointTypeDTO;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

public class NotificationHistoryDTO extends CreationTimestamped {

    public UUID id;

    @NotNull
    public Long invocationTime;

    @NotNull
    public Boolean invocationResult;

    @NotNull
    @Enumerated(EnumType.STRING)
    private NotificationStatus status;

    public UUID endpointId;

    public EndpointTypeDTO endpointType;

    public Map<String, Object> details;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public @NotNull Long getInvocationTime() {
        return invocationTime;
    }

    public void setInvocationTime(@NotNull Long invocationTime) {
        this.invocationTime = invocationTime;
    }

    public @NotNull Boolean getInvocationResult() {
        return invocationResult;
    }

    public void setInvocationResult(@NotNull Boolean invocationResult) {
        this.invocationResult = invocationResult;
    }

    public @NotNull NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(@NotNull NotificationStatus status) {
        this.status = status;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(UUID endpointId) {
        this.endpointId = endpointId;
    }

    public EndpointTypeDTO getEndpointType() {
        return endpointType;
    }

    public void setEndpointType(EndpointTypeDTO endpointType) {
        this.endpointType = endpointType;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }
}
