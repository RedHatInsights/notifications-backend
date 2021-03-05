package com.redhat.cloud.notifications.db.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Objects;

@Entity
@Table(name = "notification_history")
public class NotificationHistoryEntity extends CreationTimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "notification_history_id_seq")
    @NotNull
    public Integer id;

    @Column(name = "account_id")
    @NotNull
    @Size(max = 50)
    public String accountId;

    @Column(name = "invocation_time")
    @NotNull
    public Long invocationTime;

    @Column(name = "invocation_result")
    @NotNull
    public Boolean invocationResult;

    public String details;

    @Column(name = "event_id")
    public String eventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "endpoint_id")
    @NotNull
    public EndpointEntity endpoint;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof NotificationHistoryEntity) {
            NotificationHistoryEntity other = (NotificationHistoryEntity) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
