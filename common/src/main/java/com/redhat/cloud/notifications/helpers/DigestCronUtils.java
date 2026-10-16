package com.redhat.cloud.notifications.helpers;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.redhat.cloud.notifications.models.SubscriptionType;
import io.quarkus.logging.Log;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

public final class DigestCronUtils {

    private static final CronParser CRON_PARSER = new CronParser(
        CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
    );

    private DigestCronUtils() {
    }

    public static String buildCronExpression(SubscriptionType subscriptionType, LocalTime time, DayOfWeek day) {
        int minute = time.getMinute();
        int hour = time.getHour();

        return switch (subscriptionType) {
            case WEEKLY -> {
                if (day == null) {
                    throw new IllegalArgumentException("day is required for WEEKLY subscriptions");
                }
                yield minute + " " + hour + " * * " + toCronDay(day);
            }
            case DAILY -> minute + " " + hour + " * * *";
            default -> throw new IllegalArgumentException("Unsupported subscription type for digest scheduling: " + subscriptionType);
        };
    }

    public static LocalTime parseTimeFromCron(String cronExpression) {
        String[] parts = validateCronParts(cronExpression);
        return LocalTime.of(Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
    }

    public static DayOfWeek parseDayFromCron(String cronExpression) {
        String[] parts = validateCronParts(cronExpression);
        String dayField = parts[4];
        if ("*".equals(dayField)) {
            return null;
        }
        return fromCronDay(Integer.parseInt(dayField));
    }

    /**
     * Compute the next execution time for a cron expression from the current time.
     *
     * @param cronExpression the cron expression (UNIX format)
     * @return the next execution time
     * @throws IllegalStateException if next execution cannot be computed
     */
    public static LocalDateTime computeNextRun(String cronExpression) {
        ExecutionTime executionTime = ExecutionTime.forCron(
            CRON_PARSER.parse(cronExpression).validate()
        );
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        return executionTime.nextExecution(now)
            .map(ZonedDateTime::toLocalDateTime)
            .orElseThrow(() -> {
                Log.errorf("Could not compute next execution for cron expression '%s'", cronExpression);
                return new IllegalStateException("Failed to compute next execution for cron expression: " + cronExpression);
            });
    }

    /**
     * Calculate the next execution time for a cron expression starting from the given time.
     * This method returns null instead of throwing an exception if the calculation fails.
     *
     * @param cronExpression the cron expression (UNIX format: minute hour day-of-month month day-of-week)
     * @param from the starting time
     * @return the next execution time, or null if the expression is invalid or no next execution exists
     */
    public static LocalDateTime calculateNextRun(String cronExpression, LocalDateTime from) {
        if (cronExpression == null || cronExpression.isBlank()) {
            Log.warnf("Invalid cron expression: null or blank");
            return null;
        }

        try {
            Cron cron = CRON_PARSER.parse(cronExpression);
            ExecutionTime executionTime = ExecutionTime.forCron(cron);

            ZonedDateTime fromZoned = from.atZone(ZoneOffset.UTC);
            Optional<ZonedDateTime> nextExecution = executionTime.nextExecution(fromZoned);

            if (nextExecution.isPresent()) {
                return nextExecution.get().withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            } else {
                Log.warnf("No next execution found for cron expression: %s from time: %s",
                    cronExpression, from);
                return null;
            }
        } catch (Exception e) {
            Log.errorf(e, "Failed to parse or calculate next run for cron expression: %s", cronExpression);
            return null;
        }
    }

    /**
     * Validate if a cron expression is valid.
     *
     * @param cronExpression the cron expression to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidCronExpression(String cronExpression) {
        if (cronExpression == null || cronExpression.isBlank()) {
            return false;
        }

        try {
            CRON_PARSER.parse(cronExpression);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String[] validateCronParts(String cronExpression) {
        String[] parts = cronExpression.split(" ");
        if (parts.length < 5) {
            throw new IllegalArgumentException("Malformed cron expression (expected 5 fields): " + cronExpression);
        }
        return parts;
    }

    private static int toCronDay(DayOfWeek day) {
        return day.getValue() % 7;
    }

    private static DayOfWeek fromCronDay(int cronDay) {
        if (cronDay == 0) {
            return DayOfWeek.SUNDAY;
        }
        return DayOfWeek.of(cronDay);
    }
}
