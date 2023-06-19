package com.redhat.cloud.notifications.models;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Entity
@Table(name = "cronjob_run")
public class CronJobRun {

    /*
     * We don't need this field in the Java code but Hibernate needs each @Entity to have a field annotated with @Id.
     * This field has a real purpose in the database though: it is used to guarantee that the status table will never
     * contain more than one row. This is done with a combination of PK and CHECK SQL constraints.
     */
    @Id
    private boolean preventMultipleRows = true;

    @NotNull
    private LocalDateTime lastRun;

    public LocalDateTime getLastRun() {
        return lastRun;
    }
}
