package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.vertx.core.json.JsonObject;

import java.util.Date;
import java.util.UUID;

public class NotificationHistory {
    private Integer id;
    private UUID endpointId;

    @JsonIgnore
    private String tenant;

    private long invocationTime;
    private boolean invocationResult;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Date created;
    private JsonObject details;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(UUID endpointId) {
        this.endpointId = endpointId;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public long getInvocationTime() {
        return invocationTime;
    }

    public void setInvocationTime(long invocationTime) {
        this.invocationTime = invocationTime;
    }

    public boolean isInvocationResult() {
        return invocationResult;
    }

    public void setInvocationResult(boolean invocationResult) {
        this.invocationResult = invocationResult;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public JsonObject getDetails() {
        return details;
    }

    public void setDetails(JsonObject details) {
        this.details = details;
    }
}
