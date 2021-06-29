package com.redhat.cloud.notifications.models.validation;

import com.redhat.cloud.notifications.models.CurrentStatus;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import static com.redhat.cloud.notifications.models.Status.MAINTENANCE;

public class MaintenanceWithTimeIntervalValidator implements ConstraintValidator<MaintenanceWithTimeInterval, CurrentStatus> {

    @Override
    public boolean isValid(CurrentStatus value, ConstraintValidatorContext context) {
        if (value.getStatus() == MAINTENANCE) {
            // The time interval is mandatory for maintenance.
            if (value.getStartTime() == null || value.getEndTime() == null) {
                return false;
            }
            // The time interval must be valid.
            if (value.getStartTime().isAfter(value.getEndTime())) {
                return false;
            }
        }
        return true;
    }
}
