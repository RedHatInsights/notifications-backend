package com.redhat.cloud.notifications.routers.handlers.orgconfig;

import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

@JsonNaming(SnakeCaseStrategy.class)
public class DigestTriggerRequest {

    @NotNull
    private LocalTime scheduledExecutionTime;

    private DayOfWeek scheduledExecutionDay;

    public LocalTime getScheduledExecutionTime() {
        return scheduledExecutionTime;
    }

    public void setScheduledExecutionTime(LocalTime scheduledExecutionTime) {
        this.scheduledExecutionTime = scheduledExecutionTime;
    }

    public DayOfWeek getScheduledExecutionDay() {
        return scheduledExecutionDay;
    }

    public void setScheduledExecutionDay(DayOfWeek scheduledExecutionDay) {
        this.scheduledExecutionDay = scheduledExecutionDay;
    }
}
