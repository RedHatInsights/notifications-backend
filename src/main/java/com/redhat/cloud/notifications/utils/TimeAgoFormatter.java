package com.redhat.cloud.notifications.utils;

import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalField;
import java.util.List;
import java.util.stream.Collectors;

public class TimeAgoFormatter {

    private static class Configuration {
        private final TemporalField temporalField;
        private final String singular;
        private final String pluralFormat;

        Configuration(TemporalField temporalField, String singular, String pluralFormat) {
            this.temporalField = temporalField;
            this.singular = singular;
            this.pluralFormat = pluralFormat;
        }

    }

    private static final List<Configuration> config = List.of(
            new Configuration(ChronoField.YEAR, "1 year ago", "%d years ago"),
            new Configuration(ChronoField.MONTH_OF_YEAR, "1 month ago", "%d months ago"),
            new Configuration(ChronoField.DAY_OF_MONTH, "1 day ago", "%d days ago"),
            new Configuration(null, "Today", null)
    );

    static {
        List<Configuration> fallbackConfiguration = TimeAgoFormatter.config
                .stream()
                .filter(configuration -> configuration.temporalField == null && configuration.singular != null)
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
        Period period = Period.between(dateTime.toLocalDate(), base.toLocalDate());

        for (Configuration configuration : TimeAgoFormatter.config) {
            if (configuration.temporalField == null) {
                return configuration.singular;
            }

            long units = period.get(configuration.temporalField.getBaseUnit());
            if (units >= 1) {
                // Calculate natural units so that 2018-Jun-16 is 3 years ago from 2021-Apr-15 and not 2
                long naturalUnit = base.toLocalDate().get(configuration.temporalField) - dateTime.get(configuration.temporalField);
                if (naturalUnit == 1) {
                    return configuration.singular;
                } else if (naturalUnit > 1) {
                    return String.format(configuration.pluralFormat, naturalUnit);
                }
            }
        }

        throw new RuntimeException("Configuration does not have a fallback label");
    }
}
