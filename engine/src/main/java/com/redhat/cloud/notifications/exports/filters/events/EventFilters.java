package com.redhat.cloud.notifications.exports.filters.events;

import java.time.LocalDate;

/**
 * Represents the filters that can be applied to the queries when fetching the
 * events.
 * @param from the initial date to filter from.
 * @param to the final date to filter from.
 */
public record EventFilters(LocalDate from, LocalDate to) {
}
