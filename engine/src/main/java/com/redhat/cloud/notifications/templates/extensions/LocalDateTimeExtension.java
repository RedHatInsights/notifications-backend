package com.redhat.cloud.notifications.templates.extensions;

import com.redhat.cloud.notifications.utils.TimeAgoFormatter;
import io.quarkus.qute.TemplateExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@TemplateExtension
public class LocalDateTimeExtension {

    private static final DateTimeFormatter utcDateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm 'UTC'").withLocale(Locale.US);
    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy").withLocale(Locale.US);
    private static final TimeAgoFormatter timeAgoFormatter = new TimeAgoFormatter();

    public static String toUtcFormat(LocalDateTime date) {
        return date.format(utcDateTimeFormatter);
    }

    public static String toUtcFormat(String date) {
        return toUtcFormat(fromIsoLocalDateTime(date));
    }

    public static String toStringFormat(LocalDateTime date) {
        return date.format(dateFormatter);
    }

    public static String toStringFormat(String date) {
        return toStringFormat(fromIsoLocalDateTime(date));
    }

    public static String toTimeAgo(LocalDateTime date) {
        return timeAgoFormatter.format(LocalDateTime.now(ZoneOffset.UTC), date);
    }

    public static String toTimeAgo(String date) {
        return toTimeAgo(fromIsoLocalDateTime(date));
    }

    public static LocalDateTime fromIsoLocalDateTime(String date) {
        return LocalDateTime.parse(date, DateTimeFormatter.ISO_DATE_TIME);
    }
}
