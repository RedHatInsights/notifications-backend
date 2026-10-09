package com.redhat.cloud.notifications.routers.handlers.orgconfig;

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

public final class DigestCronUtils {

    private static final CronParser CRON_PARSER = new CronParser(
        CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
    );

    private DigestCronUtils() {
    }

    public static String buildCronExpression(SubscriptionType subscriptionType, LocalTime time, DayOfWeek day) {
        int minute = time.getMinute();
        int hour = time.getHour();
        if (subscriptionType == SubscriptionType.WEEKLY) {
            if (day == null) {
                throw new IllegalArgumentException("day is required for WEEKLY subscriptions");
            }
            return minute + " " + hour + " * * " + toCronDay(day);
        } else if (subscriptionType != SubscriptionType.DAILY) {
            throw new IllegalArgumentException("Unsupported subscription type for digest scheduling: " + subscriptionType);
        }
        return minute + " " + hour + " * * *";
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
