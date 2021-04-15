package com.redhat.cloud.notifications.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

public class TestTimeAgoFormatter {

    @Test
    public void testToday() {
        TimeAgoFormatter formatter = new TimeAgoFormatter();

        Assertions.assertEquals("Today", formatter.format(
                LocalDateTime.of(2020, 12, 31, 23, 59, 59),
                LocalDateTime.of(2020, 12, 31, 1, 12, 10)
        ));
        Assertions.assertEquals("Today", formatter.format(
                LocalDateTime.of(2020, 12, 31, 23, 59, 59),
                LocalDateTime.of(2020, 12, 31, 1, 1, 55)
        ));
    }

    @Test
    public void test1DayAgo() {
        TimeAgoFormatter formatter = new TimeAgoFormatter();

        Assertions.assertEquals("1 day ago", formatter.format(
                LocalDateTime.of(2020, 12, 31, 23, 59, 59),
                LocalDateTime.of(2020, 12, 30, 1, 1, 55)
        ));

        Assertions.assertEquals("1 day ago", formatter.format(
                LocalDateTime.of(2020, 12, 31, 23, 59, 59),
                LocalDateTime.of(2020, 12, 30, 23, 59, 59)
        ));
    }

    @Test
    public void testManyDaysAgo() {
        TimeAgoFormatter formatter = new TimeAgoFormatter();

        Assertions.assertEquals("11 days ago", formatter.format(
                LocalDateTime.of(2020, 12, 31, 23, 59, 59),
                LocalDateTime.of(2020, 12, 20, 1, 1, 55)
        ));

        Assertions.assertEquals("13 days ago", formatter.format(
                LocalDateTime.of(2020, 12, 31, 23, 59, 59),
                LocalDateTime.of(2020, 12, 18, 23, 59, 59)
        ));
    }

    @Test
    public void test1MonthAgo() {
        TimeAgoFormatter formatter = new TimeAgoFormatter();

        Assertions.assertEquals("1 month ago", formatter.format(
                LocalDateTime.of(2020, 12, 31, 23, 59, 59),
                LocalDateTime.of(2020, 11, 30, 1, 1, 55)
        ));

        Assertions.assertEquals("1 month ago", formatter.format(
                LocalDateTime.of(2020, 12, 31, 23, 59, 59),
                LocalDateTime.of(2020, 11, 30, 23, 59, 59)
        ));
    }

    @Test
    public void testManyMonthsAgo() {
        TimeAgoFormatter formatter = new TimeAgoFormatter();

        Assertions.assertEquals("5 months ago", formatter.format(
                LocalDateTime.of(2020, 12, 31, 23, 59, 59),
                LocalDateTime.of(2020, 7, 30, 1, 1, 55)
        ));

        Assertions.assertEquals("9 months ago", formatter.format(
                LocalDateTime.of(2020, 12, 31, 23, 59, 59),
                LocalDateTime.of(2020, 3, 30, 23, 59, 59)
        ));
    }

    @Test
    public void test1YearAgo() {
        TimeAgoFormatter formatter = new TimeAgoFormatter();

        Assertions.assertEquals("1 year ago", formatter.format(
                LocalDateTime.of(2020, 12, 31, 23, 59, 59),
                LocalDateTime.of(2019, 11, 30, 1, 1, 55)
        ));

        Assertions.assertEquals("1 year ago", formatter.format(
                LocalDateTime.of(2020, 12, 31, 23, 59, 59),
                LocalDateTime.of(2019, 11, 30, 23, 59, 59)
        ));
    }

    @Test
    public void testManyYearsAgo() {
        TimeAgoFormatter formatter = new TimeAgoFormatter();

        Assertions.assertEquals("5 years ago", formatter.format(
                LocalDateTime.of(2020, 12, 31, 23, 59, 59),
                LocalDateTime.of(2015, 11, 30, 1, 1, 55)
        ));

        Assertions.assertEquals("20 years ago", formatter.format(
                LocalDateTime.of(2020, 12, 31, 23, 59, 59),
                LocalDateTime.of(2000, 11, 30, 23, 59, 59)
        ));

        Assertions.assertEquals("3 years ago", formatter.format(
                LocalDateTime.of(2021, 4, 15, 12, 0, 0),
                LocalDateTime.of(2018, 6, 16, 12, 0, 0)
        ));
    }

}
