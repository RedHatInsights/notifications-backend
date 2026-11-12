package com.redhat.cloud.notifications.utils;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.redhat.cloud.notifications.models.SubscriptionType;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

@ApplicationScoped
public class DigestCronUtils {

    private static final CronParser CRON_PARSER = new CronParser(
        CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
    );

    public static final String NOTIFICATIONS_DEFAULT_DAILY_DIGEST_CRON = "notifications.default.daily.digest.cron";
    public static final String NOTIFICATIONS_DEFAULT_WEEKLY_DIGEST_CRON = "notifications.default.weekly.digest.cron";

    @ConfigProperty(name = NOTIFICATIONS_DEFAULT_DAILY_DIGEST_CRON, defaultValue = "0 0 * * *")
    String defaultDailyDigestCron;

    @ConfigProperty(name = NOTIFICATIONS_DEFAULT_WEEKLY_DIGEST_CRON, defaultValue = "30 0 * * 1")
    String defaultWeeklyDigestCron;

    public String getDefaultDailyDigestCron() {
        return defaultDailyDigestCron;
    }

    public String getDefaultWeeklyDigestCron() {
        return defaultWeeklyDigestCron;
    }

    public String buildCronExpression(SubscriptionType subscriptionType, LocalTime time, DayOfWeek day) {
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

    public LocalTime parseTimeFromCron(String cronExpression) {
        String[] parts = validateCronParts(cronExpression);
        return LocalTime.of(Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
    }

    public DayOfWeek parseDayFromCron(String cronExpression) {
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
     * @throws IllegalArgumentException if next execution cannot be computed
     */
    public LocalDateTime computeNextRun(String cronExpression) {
        return calculateNextRun(cronExpression, LocalDateTime.now(ZoneOffset.UTC));
    }

    /**
     * Calculate the next execution time for a cron expression starting from the given time.
     *
     * @param cronExpression the cron expression (UNIX format: minute hour day-of-month month day-of-week)
     * @param from the starting time
     * @return the next execution time
     * @throws IllegalArgumentException if the cron expression is invalid or no next execution exists
     */
    public LocalDateTime calculateNextRun(String cronExpression, LocalDateTime from) {
        if (cronExpression == null || cronExpression.isBlank()) {
            throw new IllegalArgumentException("Cron expression cannot be null or blank");
        }

        Cron cron = CRON_PARSER.parse(cronExpression).validate();
        ExecutionTime executionTime = ExecutionTime.forCron(cron);

        ZonedDateTime fromZoned = from.atZone(ZoneOffset.UTC);
        Optional<ZonedDateTime> nextExecution = executionTime.nextExecution(fromZoned);

        return nextExecution
            .map(zonedDateTime -> zonedDateTime.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime())
            .orElseThrow(() -> new IllegalArgumentException("No next execution found for cron expression: " + cronExpression));
    }

    /**
     * Returns the most recent cron slot at or before {@code at}.
     * <p>
     * Use this — not {@code calculateNextRun(cron, lastRun)} — to compute the window end and the
     * new {@code last_run} after a successful digest run.  When the cron expression is changed (e.g.
     * Monday → Wednesday), {@code calculateNextRun(newCron, oldLastRun)} resolves to a slot that is
     * already in the past, causing every subsequent digest to be one week behind.
     * {@code calculateCurrentSlot} always anchors to {@code at}, so the window stays correct
     * regardless of schedule changes.
     *
     * @param cronExpression the cron expression (UNIX format)
     * @param at             the reference time (typically the job's execution time)
     * @return the last cron slot at or before {@code at}
     * @throws IllegalArgumentException if the expression is invalid or no previous execution exists
     */
    public LocalDateTime calculateCurrentSlot(String cronExpression, LocalDateTime at) {
        if (cronExpression == null || cronExpression.isBlank()) {
            throw new IllegalArgumentException("Cron expression cannot be null or blank");
        }

        Cron cron = CRON_PARSER.parse(cronExpression).validate();
        ExecutionTime executionTime = ExecutionTime.forCron(cron);

        // Add one second so that lastExecution includes the slot that falls exactly on `at`.
        // cron-utils lastExecution is strictly-before; without the nudge, an on-schedule run
        // (at == slot) would return the previous slot.
        ZonedDateTime atZoned = at.plusSeconds(1).atZone(ZoneOffset.UTC);
        Optional<ZonedDateTime> lastExecution = executionTime.lastExecution(atZoned);

        return lastExecution
            .map(zonedDateTime -> zonedDateTime.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime())
            .orElseThrow(() -> new IllegalArgumentException("No previous execution found for cron expression: " + cronExpression));
    }

    private String[] validateCronParts(String cronExpression) {
        String[] parts = cronExpression.split(" ");
        if (parts.length < 5) {
            throw new IllegalArgumentException("Malformed cron expression (expected 5 fields): " + cronExpression);
        }
        return parts;
    }

    private int toCronDay(DayOfWeek day) {
        return day.getValue() % 7;
    }

    private DayOfWeek fromCronDay(int cronDay) {
        if (cronDay == 0) {
            return DayOfWeek.SUNDAY;
        }
        return DayOfWeek.of(cronDay);
    }
}
