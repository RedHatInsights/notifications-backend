package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.db.converters.NotificationHistoryDetailsConverter;

import javax.persistence.Convert;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY;
import static javax.persistence.FetchType.LAZY;

@Entity
@Table(name = "notification_history")
public class NotificationHistory extends CreationTimestamped {

    public static final Map<String, String> SORT_FIELDS = Map.of(
            "created", "nh.created",
            "invocation_time", "nh.invocationTime",
            "invocation_result", "nh.invocationResult",
            "status", "nh.status"
    );

    @Id
    // We can not use @GeneratedValue as the ID needs to be sent over to Camel
    @JsonProperty(access = READ_ONLY)
    private UUID id;

    @NotNull
    private Long invocationTime;

    @NotNull
    @Deprecated
    private Boolean invocationResult;

    @NotNull
    @Enumerated(EnumType.STRING)
    private NotificationStatus status;

    @NotNull
    @ManyToOne(fetch = LAZY, optional = false)
    @JoinColumn(name = "event_id")
    @JsonIgnore
    private Event event;

    @Transient
    private UUID endpointId;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "endpoint_id")
    @JsonIgnore
    private Endpoint endpoint;

    /*
     * This is a duplicate of the Endpoint#type and Endpoint#subType field. We need it to guarantee that the endpoint type will remain
     * available for the event log even if the endpoint is deleted by an org admin.
     */
    @NotNull
    @Embedded
    @JsonIgnore
    private final CompositeEndpointType compositeEndpointType = new CompositeEndpointType();

    @Convert(converter = NotificationHistoryDetailsConverter.class)
    private Map<String, Object> details;

    public NotificationHistory() {
    }

    public NotificationHistory(UUID id, Long invocationTime, Boolean invocationResult, NotificationStatus status, Endpoint endpoint, LocalDateTime created) {
        this.id = id;
        this.invocationTime = invocationTime;
        this.status = status;
        this.invocationResult = invocationResult;
        this.endpoint = endpoint;
        setCreated(created);
    }

    public NotificationHistory(UUID id, Long invocationTime, Boolean invocationResult, NotificationStatus status, Endpoint endpoint, LocalDateTime created, Map<String, Object> details) {
        this(id, invocationTime, invocationResult, status, endpoint, created);
        this.details = details;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Long getInvocationTime() {
        return invocationTime;
    }

    public void setInvocationTime(Long invocationTime) {
        this.invocationTime = invocationTime;
    }

    public Boolean isInvocationResult() {
        return invocationResult;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationStatus status) {
        this.status = status;
        this.invocationResult = invocationResultByStatus(status);
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public UUID getEndpointId() {
        if (endpointId == null && endpoint != null) {
            endpointId = endpoint.getId();
        }
        return endpointId;
    }

    public void setEndpointId(UUID endpointId) {
        this.endpointId = endpointId;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    public EndpointType getEndpointType() {
        return compositeEndpointType.getType();
    }

    public void setEndpointType(EndpointType endpointType) {
        this.compositeEndpointType.setType(endpointType);
    }

    public String getEndpointSubType() {
        return this.compositeEndpointType.getSubType();
    }

    public void setEndpointSubType(String endpointSubType) {
        this.compositeEndpointType.setSubType(endpointSubType);
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public void setDetails(Map<String, Object> details) {
        this.details = details;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof NotificationHistory) {
            NotificationHistory other = (NotificationHistory) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public static NotificationHistory getHistoryStub(Endpoint endpoint, Event event, long invocationTime, UUID historyId) {
        NotificationHistory history = new NotificationHistory();
        history.setInvocationTime(invocationTime);
        history.setEndpoint(endpoint);
        history.setEndpointType(endpoint.getType());
        history.setEndpointSubType(endpoint.getSubType());
        history.setEvent(event);
        history.setId(historyId);
        return history;
    }

    private Boolean invocationResultByStatus(NotificationStatus status) {
        return status != null ? status.toInvocationResult() : Boolean.FALSE;
    }

}
