package com.redhat.cloud.notifications.helpers;

import java.time.LocalDateTime;

import static java.time.ZoneOffset.UTC;

/**
 * Helper class for computing aggregation reference times with 15-minute rounding.
 * Users can schedule digest execution time every 15 minutes (00, 15, 30, 45).
 * If the job executes at 10:26, we round to the scheduled time of 10:15.
 */
public final class AggregationTimeHelper {

    private AggregationTimeHelper() {
    }

    /**
     * Compute reference time with 15-minute rounding from the current time.
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

    /**
     * Compute the aggregation start time based on last run and current time.
     * Handles initial runs and outage recovery scenarios.
     *
     * @param lastRunFromDb the last recorded run time, may be null
     * @param now the current time
     * @return the computed start time for aggregation
     */
    public static LocalDateTime computeStartDateTime(LocalDateTime lastRunFromDb, LocalDateTime now) {
        LocalDateTime yesterday = now.minusDays(1);
        if (lastRunFromDb == null) {
            // No previous run - start from yesterday for initial run
            return yesterday;
        }

        LocalDateTime twoDaysAgo = now.minusDays(2);

        if (lastRunFromDb.isBefore(twoDaysAgo)) {
            // Last run is old (outage scenario) - start from yesterday to catch up
            return yesterday;
        }

        // Last run is recent - use it
        return lastRunFromDb;
    }
}
