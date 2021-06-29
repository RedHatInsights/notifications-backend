package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.db.converters.NotificationHistoryDetailsConverter;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY;

@Entity
@Table(name = "notification_history")
public class NotificationHistory extends CreationTimestamped {

    @Id
    // We can not use @GeneratedValue as the ID needs to be sent over to Camel
    @JsonProperty(access = READ_ONLY)
    private UUID id;

    @NotNull
    @Size(max = 50)
    @JsonIgnore
    private String accountId;

    @NotNull
    private Long invocationTime;

    @NotNull
    private Boolean invocationResult;

    private String eventId;

    @Transient
    private UUID endpointId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_id")
    @JsonIgnore
    private Endpoint endpoint;

    @Convert(converter = NotificationHistoryDetailsConverter.class)
    private Map<String, Object> details;

    public NotificationHistory() {
    }

    public NotificationHistory(UUID id, String accountId, Long invocationTime, Boolean invocationResult, String eventId, Endpoint endpoint, LocalDateTime created) {
        this.id = id;
        this.accountId = accountId;
        this.invocationTime = invocationTime;
        this.invocationResult = invocationResult;
        this.eventId = eventId;
        this.endpoint = endpoint;
        setCreated(created);
    }

    public NotificationHistory(UUID id, String accountId, Long invocationTime, Boolean invocationResult, String eventId, Endpoint endpoint, LocalDateTime created, Map<String, Object> details) {
        this(id, accountId, invocationTime, invocationResult, eventId, endpoint, created);
        this.details = details;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
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

    public void setInvocationResult(Boolean invocationResult) {
        this.invocationResult = invocationResult;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
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


    public static NotificationHistory getHistoryStub(Notification item, long invocationTime, UUID historyId) {
        NotificationHistory history = new NotificationHistory();
        history.setInvocationTime(invocationTime);
        history.setEndpoint(item.getEndpoint());
        history.setAccountId(item.getTenant());
        history.setEventId("");
        history.setInvocationResult(false);
        history.setId(historyId);
        return history;
    }

}
