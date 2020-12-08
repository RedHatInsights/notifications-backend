package com.redhat.cloud.notifications.templates;

import io.quarkus.qute.TemplateExtension;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@TemplateExtension
public class LocalDateTimeExtension {

    private static final DateTimeFormatter utcDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm 'UTC'");

    public static String toUtcFormat(LocalDateTime date) {
        return date.format(utcDateFormatter);
    }

    public static String toUtcFormat(String date) {
        return toUtcFormat(fromIsoLocalDateTime(date));
    }

    public static LocalDateTime fromIsoLocalDateTime(String date) {
        return LocalDateTime.parse(date, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
