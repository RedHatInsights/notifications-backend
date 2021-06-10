package com.redhat.cloud.notifications.models.validation;

import com.redhat.cloud.notifications.models.CurrentStatus;
import com.redhat.cloud.notifications.models.Status;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Checks that {@link CurrentStatus#startTime} and {@link CurrentStatus#endTime} are not {@code null} and valid when
 * {@link CurrentStatus#status CurrentStatus#status} is equal to {@link Status#MAINTENANCE MAINTENANCE}.
 */
@Retention(RUNTIME)
@Target(TYPE)
@Constraint(validatedBy = MaintenanceWithTimeIntervalValidator.class)
@Documented
public @interface MaintenanceWithTimeInterval {

    String message() default "A valid time interval is mandatory for the maintenance status";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
