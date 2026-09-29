package com.redhat.cloud.notifications.models;

import com.redhat.cloud.notifications.db.converters.DayOfWeekConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;


@Entity
@Table(name = "aggregation_org_config")
public class AggregationOrgConfig {

    @EmbeddedId
    private AggregationOrgConfigId id;

    private LocalTime scheduledExecutionTime;

    private LocalDateTime lastRun;

    @Convert(converter = DayOfWeekConverter.class)
    private DayOfWeek preferredDay;

    public AggregationOrgConfig() {
    }

    public AggregationOrgConfig(String orgId, LocalTime scheduledExecutionTime) {
        this.id = new AggregationOrgConfigId(orgId, DAILY);
        this.scheduledExecutionTime = scheduledExecutionTime;
    }

    public AggregationOrgConfig(String orgId, LocalTime scheduledExecutionTime, LocalDateTime lastRun) {
        this.id = new AggregationOrgConfigId(orgId, DAILY);
        this.scheduledExecutionTime = scheduledExecutionTime;
        this.lastRun = lastRun;
    }

    public AggregationOrgConfig(String orgId, SubscriptionType subscriptionType, LocalTime scheduledExecutionTime, DayOfWeek preferredDay) {
        this.id = new AggregationOrgConfigId(orgId, subscriptionType);
        this.scheduledExecutionTime = scheduledExecutionTime;
        this.preferredDay = preferredDay;
    }

    public AggregationOrgConfigId getId() {
        return id;
    }

    public void setId(AggregationOrgConfigId id) {
        this.id = id;
    }

    public String getOrgId() {
        return id.orgId;
    }

    public SubscriptionType getSubscriptionType() {
        return id.subscriptionType;
    }

    public LocalTime getScheduledExecutionTime() {
        return scheduledExecutionTime;
    }

    public void setScheduledExecutionTime(LocalTime expectedRunningTime) {
        this.scheduledExecutionTime = expectedRunningTime;
    }

    public LocalDateTime getLastRun() {
        return lastRun;
    }

    public void setLastRun(LocalDateTime lastRun) {
        this.lastRun = lastRun;
    }

    public DayOfWeek getPreferredDay() {
        return preferredDay;
    }

    public void setPreferredDay(DayOfWeek preferredDay) {
        this.preferredDay = preferredDay;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AggregationOrgConfig that = (AggregationOrgConfig) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
