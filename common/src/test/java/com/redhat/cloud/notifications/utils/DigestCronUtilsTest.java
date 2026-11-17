package com.redhat.cloud.notifications.utils;

import com.redhat.cloud.notifications.models.SubscriptionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DigestCronUtilsTest {

    final DigestCronUtils digestCronUtils = new DigestCronUtils();

    @Test
    void testBuildCronExpressionDaily() {
        String cron = digestCronUtils.buildCronExpression(SubscriptionType.DAILY, LocalTime.of(14, 30), null);
        assertEquals("30 14 * * *", cron);
    }

    @Test
    void testBuildCronExpressionDailyMidnight() {
        String cron = digestCronUtils.buildCronExpression(SubscriptionType.DAILY, LocalTime.of(0, 0), null);
        assertEquals("0 0 * * *", cron);
    }

    @Test
    void testBuildCronExpressionWeeklyMonday() {
        String cron = digestCronUtils.buildCronExpression(SubscriptionType.WEEKLY, LocalTime.of(10, 15), DayOfWeek.MONDAY);
        assertEquals("15 10 * * 1", cron);
    }

    @Test
    void testBuildCronExpressionWeeklySunday() {
        String cron = digestCronUtils.buildCronExpression(SubscriptionType.WEEKLY, LocalTime.of(8, 0), DayOfWeek.SUNDAY);
        assertEquals("0 8 * * 0", cron);
    }

    @Test
    void testBuildCronExpressionWeeklySaturday() {
        String cron = digestCronUtils.buildCronExpression(SubscriptionType.WEEKLY, LocalTime.of(23, 45), DayOfWeek.SATURDAY);
        assertEquals("45 23 * * 6", cron);
    }

    @Test
    void testBuildCronExpressionWeeklyNullDayThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            digestCronUtils.buildCronExpression(SubscriptionType.WEEKLY, LocalTime.of(10, 0), null)
        );
    }

    @ParameterizedTest
    @EnumSource(DayOfWeek.class)
    void testCronDayRoundTrip(DayOfWeek day) {
        String cron = digestCronUtils.buildCronExpression(SubscriptionType.WEEKLY, LocalTime.of(9, 0), day);
        DayOfWeek parsed = digestCronUtils.parseDayFromCron(cron);
        assertEquals(day, parsed);
    }

    @Test
    void testParseTimeFromCron() {
        assertEquals(LocalTime.of(14, 30), digestCronUtils.parseTimeFromCron("30 14 * * *"));
        assertEquals(LocalTime.of(0, 0), digestCronUtils.parseTimeFromCron("0 0 * * *"));
        assertEquals(LocalTime.of(23, 45), digestCronUtils.parseTimeFromCron("45 23 * * 6"));
    }

    @Test
    void testParseDayFromCronDaily() {
        assertNull(digestCronUtils.parseDayFromCron("30 14 * * *"));
    }

    @Test
    void testParseDayFromCronWeekly() {
        assertEquals(DayOfWeek.SUNDAY, digestCronUtils.parseDayFromCron("0 8 * * 0"));
        assertEquals(DayOfWeek.MONDAY, digestCronUtils.parseDayFromCron("0 8 * * 1"));
        assertEquals(DayOfWeek.FRIDAY, digestCronUtils.parseDayFromCron("0 8 * * 5"));
        assertEquals(DayOfWeek.SATURDAY, digestCronUtils.parseDayFromCron("0 8 * * 6"));
    }

    @Test
    void testCalculateCurrentSlotExactlyOnSchedule() {
        // Nov 18 2024 is a Monday. Running exactly at Monday 00:30 should return that same slot.
        LocalDateTime at = LocalDateTime.of(2024, 11, 18, 0, 30);
        LocalDateTime slot = digestCronUtils.calculateCurrentSlot("30 0 * * 1", at);
        assertEquals(at, slot);
    }

    @Test
    void testCalculateCurrentSlotRunningLate() {
        // Monday 00:30 cron, job runs on Wednesday at 10:00 (late/retry).
        // Should return the last Monday slot, not the next one.
        LocalDateTime at = LocalDateTime.of(2024, 11, 20, 10, 0); // Wednesday Nov 20 2024
        LocalDateTime slot = digestCronUtils.calculateCurrentSlot("30 0 * * 1", at);
        assertEquals(LocalDateTime.of(2024, 11, 18, 0, 30), slot); // Monday Nov 18
    }

    @Test
    void testCalculateCurrentSlotAfterScheduleChange() {
        // Admin changed from Monday 00:30 to Wednesday 09:00. First run is Wed Nov 20 09:00.
        // Should return Nov 20 09:00 (the current Wednesday slot), not Nov 13 09:00.
        LocalDateTime at = LocalDateTime.of(2024, 11, 20, 9, 0); // Wednesday Nov 20 2024
        LocalDateTime slot = digestCronUtils.calculateCurrentSlot("0 9 * * 3", at);
        assertEquals(at, slot);
    }
}
