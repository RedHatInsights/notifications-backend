package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.db.converters.StatusConverter;
import com.redhat.cloud.notifications.models.validation.MaintenanceWithTimeInterval;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

import static com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;

@Entity
@Table(name = "status")
@JsonNaming(SnakeCaseStrategy.class)
@MaintenanceWithTimeInterval
public class CurrentStatus {

    /*
     * We don't need this field in the Java code but Hibernate needs each @Entity to have a field annotated with @Id.
     * This field has a real purpose in the database though: it is used to guarantee that the status table will never
     * contain more than one row. This is done with a combination of PK and CHECK SQL constraints.
     */
    @Id
    @JsonIgnore
    private boolean preventMultipleRows = true;

    @NotNull
    @Convert(converter = StatusConverter.class)
    @Column(name = "value")
    public Status status;

    @JsonInclude(NON_NULL)
    @JsonFormat(shape = STRING)
    @Schema(name = "start_time")
    private LocalDateTime startTime;

    @JsonInclude(NON_NULL)
    @JsonFormat(shape = STRING)
    @Schema(name = "end_time")
    private LocalDateTime endTime;

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }
}
