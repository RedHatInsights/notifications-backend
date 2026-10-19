package com.redhat.cloud.notifications.helpers;

import java.time.LocalDateTime;

import static java.time.ZoneOffset.UTC;

/**
 * Helper class for computing aggregation reference times in tests.
 * This mirrors the logic in NotificationsApp.computeReferenceTime().
 */
public final class AggregationTimeHelper {

    private AggregationTimeHelper() {
    }

    /**
     * Compute reference time with 15-minute rounding.
     * Users can schedule execution time every 15 minutes.
     * If the job executes at 10:26, we round to the scheduled time of 10:15.
     *
     * @return the rounded reference time
     */
    public static LocalDateTime computeReferenceTime() {
        return computeReferenceTime(LocalDateTime.now(UTC));
    }

    /**
     * Compute reference time with 15-minute rounding from a given time.
     *
     * @param currentTime the base time to round
     * @return the rounded reference time
     */
    public static LocalDateTime computeReferenceTime(LocalDateTime currentTime) {
        LocalDateTime rounded = currentTime.withSecond(0).withNano(0);
        if (rounded.getMinute() < 15) {
            return rounded.withMinute(0);
        } else if (rounded.getMinute() < 30) {
            return rounded.withMinute(15);
        } else if (rounded.getMinute() < 45) {
            return rounded.withMinute(30);
        } else {
            return rounded.withMinute(45);
        }
    }
}
