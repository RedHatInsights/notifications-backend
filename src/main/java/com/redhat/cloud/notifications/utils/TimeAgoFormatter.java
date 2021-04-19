package com.redhat.cloud.notifications.utils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

// Based on: https://github.com/RedHatInsights/frontend-components/blob/86b02e3c206663d640d30b32087061c04db1595d/packages/components/src/DateFormat/helper.js#L4-L19
public class TimeAgoFormatter {

    private static final long SECOND = 1;
    private static final long MINUTE = SECOND * 60;
    private static final long HOUR = MINUTE * 60;
    private static final long DAY = HOUR * 24;
    private static final long MONTH = DAY * 30;
    private static final long YEAR = DAY * 365;

    private static class Configuration {
        private final Long boundary;
        private final Function<Long, String> description;

        Configuration(Long boundary, Function<Long, String> description) {
            this.boundary = boundary;
            this.description = description;
        }
    }

    private static String formatTime(long count, String unit) {
        return String.format("%d %s%s ago", count, unit, count > 1 ? "s" : "");
    }

    private static long roundDiv(double numerator, double denominator) {
        return Math.round(numerator / denominator);
    }

    private static final List<Configuration> config = List.of(
            new Configuration(MINUTE, count -> "Just now"),
            new Configuration(HOUR, count -> formatTime(roundDiv(count, MINUTE), "minute")),
            new Configuration(DAY, count -> formatTime(roundDiv(count, HOUR), "hour")),
            new Configuration(MONTH, count -> formatTime(roundDiv(count, DAY), "day")),
            new Configuration(YEAR, count -> formatTime(roundDiv(count, MONTH), "month")),
            new Configuration(Long.MAX_VALUE, count -> formatTime(roundDiv(count, YEAR), "year"))
    );

    static {
        List<Configuration> fallbackConfiguration = TimeAgoFormatter.config
                .stream()
                .filter(configuration -> configuration.boundary == Long.MAX_VALUE)
                .collect(Collectors.toList());

        if (fallbackConfiguration.size() == 0) {
            throw new RuntimeException("Configuration does not have a fallback label");
        }

        if (fallbackConfiguration.size() > 1) {
            throw new RuntimeException("Configuration has more than one fallback label");
        }

        if (TimeAgoFormatter.config.indexOf(fallbackConfiguration.get(0)) != TimeAgoFormatter.config.size() - 1) {
            throw new RuntimeException("Configuration fallback must be the last element in the list");
        }
    }

    public String format(LocalDateTime base, LocalDateTime dateTime) {
        long baseEpoch = base.toEpochSecond(ZoneOffset.UTC);
        long epoch = dateTime.toEpochSecond(ZoneOffset.UTC);
        long diff = baseEpoch - epoch;

        if (diff < 0) {
            throw new IllegalArgumentException("Base must be greater than the date to compare");
        }

        for (Configuration configuration : TimeAgoFormatter.config) {
            if (diff < configuration.boundary) {
                return configuration.description.apply(diff);
            }
        }

        throw new RuntimeException("Configuration does not have a fallback label");
    }
}
