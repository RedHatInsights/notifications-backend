package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING;
import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@Entity
@Table(name = "aggregation_cronjob_parameter")
@JsonNaming(SnakeCaseStrategy.class)
public class AggregationCronjobParameters {

    @Id
    @NotNull
    @Size(max = 50)
    public String orgId;

    @JsonFormat(shape = STRING)
    private LocalTime expectedRunningTime;

    @JsonFormat(shape = STRING)
    private LocalDateTime lastRun;

    public AggregationCronjobParameters() {
    }

    public AggregationCronjobParameters(String orgId, LocalTime expectedRunningTime) {
        this.orgId = orgId;
        this.expectedRunningTime = expectedRunningTime;
    }

    public AggregationCronjobParameters(String orgId, LocalTime expectedRunningTime, LocalDateTime lastRun) {
        this.orgId = orgId;
        this.expectedRunningTime = expectedRunningTime;
        this.lastRun = lastRun;
    }

    public String getOrgId() {
        return orgId;
    }

    public void setOrgId(String orgId) {
        this.orgId = orgId;
    }

    public LocalTime getExpectedRunningTime() {
        return expectedRunningTime;
    }

    public void setExpectedRunningTime(LocalTime expectedRunningTime) {
        this.expectedRunningTime = expectedRunningTime;
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
        AggregationCronjobParameters that = (AggregationCronjobParameters) o;
        return Objects.equals(orgId, that.orgId) && Objects.equals(expectedRunningTime, that.expectedRunningTime) && Objects.equals(lastRun, that.lastRun);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, expectedRunningTime, lastRun);
    }
}
