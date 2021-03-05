package com.redhat.cloud.notifications.db.entities;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "endpoints")
public class EndpointEntity extends CreationUpdateTimestampedEntity {

    @Id
    @GeneratedValue
    @NotNull
    public UUID id;

    @Column(name = "account_id")
    @NotNull
    @Size(max = 50)
    public String accountId;

    @Column(name = "endpoint_type")
    @NotNull
    public Integer endpointType;

    @NotNull
    public Boolean enabled;

    @NotNull
    @Size(max = 255)
    public String name;

    public String description;

    @OneToOne(mappedBy = "endpoint", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    public EndpointWebhookEntity webhook;

    @OneToMany(mappedBy = "endpoint", cascade = CascadeType.ALL)
    public Set<EndpointTargetEntity> targets;

    @OneToMany(mappedBy = "endpoint", cascade = CascadeType.ALL)
    public Set<EndpointDefaultEntity> defaults;

    @OneToMany(mappedBy = "endpoint", cascade = CascadeType.ALL)
    public Set<NotificationHistoryEntity> notificationHistories;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EndpointEntity) {
            EndpointEntity other = (EndpointEntity) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
