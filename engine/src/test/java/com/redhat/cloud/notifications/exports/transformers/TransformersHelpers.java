package com.redhat.cloud.notifications.exports.transformers;

import com.redhat.cloud.notifications.models.Event;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public final class TransformersHelpers {
    /**
     * Returns a list of fixture events ready to be used in the transformer
     * tests.
     * @return the list of generated events.
     */
    public static List<Event> getFixtureEvents() {
        final List<Event> events = new ArrayList<>(5);

        events.add(
            new Event(
                UUID.fromString("29e7b3b3-7019-4995-8336-b67b89876e89"),
                "bundle a with a comma \",\"",
                "application a",
                "event type a",
                getDate(2000, 0)
            )
        );
        events.add(
            new Event(
                UUID.fromString("64f5b04d-e939-44fa-a10d-ad1c13197919"),
                "bundle b",
                "application b",
                "event type b",
                getDate(2001, 1)
            )
        );
        events.add(
            new Event(
                UUID.fromString("4192b1f4-a8c5-4a22-bb66-cb5ec5965d3e"),
                "bundle c",
                "application c",
                "event type c",
                getDate(2002, 2)
            )
        );
        events.add(
            new Event(
                UUID.fromString("1166745b-163b-481a-9723-1b580f8e6d25"),
                "bundle d",
                "application d",
                "event type d",
                getDate(2003, 3)
            )
        );
        events.add(
            new Event(
                UUID.fromString("a6da4a14-b7cd-409b-82fc-904971cfa5d0"),
                "bundle e",
                "application e",
                "event type e",
                getDate(2004, 4)
            )
        );

        return events;
    }

    /**
     * Gets a date from the given year and hour.
     * @param year the year of the date.
     * @param hour the hour of the date.
     * @return the generated date.
     */
    private static Date getDate(final int year, final int hour) {
        // The date is in reality a timestamp, which mimics what it is done
        // in the Event entity's constructor.
        final LocalDateTime localDateTime = LocalDateTime.of(
            year,
            1,
            1,
            hour,
            0,
            0,
            12345
        );

        return Timestamp.valueOf(localDateTime);
    }
}
