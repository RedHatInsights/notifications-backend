package com.redhat.cloud.notifications.db.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.Objects;

@Entity
@Table(name = "email_aggregation")
public class EmailAggregationEntity extends CreationTimestampedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO, generator = "email_aggregation_id_seq")
    @NotNull
    public Integer id;

    @Column(name = "account_id")
    @NotNull
    @Size(max = 50)
    public String accountId;

    @NotNull
    public String payload;

    @Column(name = "application")
    @NotNull
    @Size(max = 255)
    public String applicationName;

    @Column(name = "bundle")
    @NotNull
    @Size(max = 255)
    public String bundleName;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o instanceof EmailAggregationEntity) {
            EmailAggregationEntity other = (EmailAggregationEntity) o;
            return Objects.equals(id, other.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
