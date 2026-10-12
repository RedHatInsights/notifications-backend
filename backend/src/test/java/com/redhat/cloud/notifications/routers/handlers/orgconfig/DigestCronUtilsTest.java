package com.redhat.cloud.notifications.routers.handlers.orgconfig;

import com.redhat.cloud.notifications.models.SubscriptionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DigestCronUtilsTest {

    @Test
    void testBuildCronExpressionDaily() {
        String cron = DigestCronUtils.buildCronExpression(SubscriptionType.DAILY, LocalTime.of(14, 30), null);
        assertEquals("30 14 * * *", cron);
    }

    @Test
    void testBuildCronExpressionDailyMidnight() {
        String cron = DigestCronUtils.buildCronExpression(SubscriptionType.DAILY, LocalTime.of(0, 0), null);
        assertEquals("0 0 * * *", cron);
    }

    @Test
    void testBuildCronExpressionWeeklyMonday() {
        String cron = DigestCronUtils.buildCronExpression(SubscriptionType.WEEKLY, LocalTime.of(10, 15), DayOfWeek.MONDAY);
        assertEquals("15 10 * * 1", cron);
    }

    @Test
    void testBuildCronExpressionWeeklySunday() {
        String cron = DigestCronUtils.buildCronExpression(SubscriptionType.WEEKLY, LocalTime.of(8, 0), DayOfWeek.SUNDAY);
        assertEquals("0 8 * * 0", cron);
    }

    @Test
    void testBuildCronExpressionWeeklySaturday() {
        String cron = DigestCronUtils.buildCronExpression(SubscriptionType.WEEKLY, LocalTime.of(23, 45), DayOfWeek.SATURDAY);
        assertEquals("45 23 * * 6", cron);
    }

    @Test
    void testBuildCronExpressionWeeklyNullDayThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            DigestCronUtils.buildCronExpression(SubscriptionType.WEEKLY, LocalTime.of(10, 0), null)
        );
    }

    @ParameterizedTest
    @EnumSource(DayOfWeek.class)
    void testCronDayRoundTrip(DayOfWeek day) {
        String cron = DigestCronUtils.buildCronExpression(SubscriptionType.WEEKLY, LocalTime.of(9, 0), day);
        DayOfWeek parsed = DigestCronUtils.parseDayFromCron(cron);
        assertEquals(day, parsed);
    }

    @Test
    void testParseTimeFromCron() {
        assertEquals(LocalTime.of(14, 30), DigestCronUtils.parseTimeFromCron("30 14 * * *"));
        assertEquals(LocalTime.of(0, 0), DigestCronUtils.parseTimeFromCron("0 0 * * *"));
        assertEquals(LocalTime.of(23, 45), DigestCronUtils.parseTimeFromCron("45 23 * * 6"));
    }

    @Test
    void testParseDayFromCronDaily() {
        assertNull(DigestCronUtils.parseDayFromCron("30 14 * * *"));
    }

    @Test
    void testParseDayFromCronWeekly() {
        assertEquals(DayOfWeek.SUNDAY, DigestCronUtils.parseDayFromCron("0 8 * * 0"));
        assertEquals(DayOfWeek.MONDAY, DigestCronUtils.parseDayFromCron("0 8 * * 1"));
        assertEquals(DayOfWeek.FRIDAY, DigestCronUtils.parseDayFromCron("0 8 * * 5"));
        assertEquals(DayOfWeek.SATURDAY, DigestCronUtils.parseDayFromCron("0 8 * * 6"));
    }
}
