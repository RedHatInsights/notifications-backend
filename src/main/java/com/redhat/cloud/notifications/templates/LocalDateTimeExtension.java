package com.redhat.cloud.notifications.templates;

import io.quarkus.qute.TemplateExtension;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@TemplateExtension
public class LocalDateTimeExtension {

    private static final DateTimeFormatter utcDateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm 'UTC'").withLocale(Locale.US);
    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy").withLocale(Locale.US);

    public static String toUtcFormat(LocalDateTime date) {
        return date.format(utcDateTimeFormatter);
    }

    public static String toUtcFormat(String date) {
        return toUtcFormat(fromIsoLocalDateTime(date));
    }

    public static String toStringFormat(LocalDateTime date) {
        return date.format(dateFormatter);
    }

    public static LocalDateTime fromIsoLocalDateTime(String date) {
        return LocalDateTime.parse(date, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
