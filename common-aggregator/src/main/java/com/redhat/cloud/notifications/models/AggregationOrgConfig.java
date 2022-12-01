package com.redhat.cloud.notifications.models;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;


@Entity
@Table(name = "aggregation_org_config")
public class AggregationOrgConfig {

    @Id
    @NotNull
    @Size(max = 50)
    public String orgId;

    private LocalTime scheduledExecutionTime;

    private LocalDateTime lastRun;

    public AggregationOrgConfig() {
    }

    public AggregationOrgConfig(String orgId, LocalTime scheduledExecutionTime) {
        this.orgId = orgId;
        this.scheduledExecutionTime = scheduledExecutionTime;
    }

    public AggregationOrgConfig(String orgId, LocalTime scheduledExecutionTime, LocalDateTime lastRun) {
        this.orgId = orgId;
        this.scheduledExecutionTime = scheduledExecutionTime;
        this.lastRun = lastRun;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AggregationOrgConfig that = (AggregationOrgConfig) o;
        return Objects.equals(orgId, that.orgId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId);
    }
}
