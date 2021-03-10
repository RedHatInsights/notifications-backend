package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class NotificationHistory {
    private Integer id;
    private UUID endpointId;

    // Internally we use Endpoint, but for the REST interface only endpointId is returned
    @JsonIgnore
    private Endpoint endpoint;

    @JsonIgnore
    private String tenant;

    private long invocationTime;
    private boolean invocationResult;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Date created;
    private Map<String, Object> details;

    private String eventId;

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

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
    }
}
