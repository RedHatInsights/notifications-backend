package com.redhat.cloud.notifications.routers.handlers.orgconfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class WeeklyDigestPreference {

    @NotNull
    @JsonProperty("scheduled_execution_time")
    private LocalTime scheduledExecutionTime;

    @NotNull
    @JsonProperty("preferred_day")
    private DayOfWeek preferredDay;

    public WeeklyDigestPreference() {
    }

    public WeeklyDigestPreference(LocalTime scheduledExecutionTime, DayOfWeek preferredDay) {
        this.scheduledExecutionTime = scheduledExecutionTime;
        this.preferredDay = preferredDay;
    }

    public LocalTime getScheduledExecutionTime() {
        return scheduledExecutionTime;
    }

    public void setScheduledExecutionTime(LocalTime scheduledExecutionTime) {
        this.scheduledExecutionTime = scheduledExecutionTime;
    }

    public DayOfWeek getPreferredDay() {
        return preferredDay;
    }

    public void setPreferredDay(DayOfWeek preferredDay) {
        this.preferredDay = preferredDay;
    }
}
