package com.redhat.cloud.notifications.exports.filters.events;

import com.redhat.cloud.event.apps.exportservice.v1.ExportRequestClass;
import com.redhat.cloud.notifications.exports.filters.FilterExtractionException;
import io.quarkus.logging.Log;

import javax.enterprise.context.ApplicationScoped;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class EventFiltersExtractor {
    /**
     * The key for "initial date" filter.
     */
    public static final String FILTER_DATE_FROM = "from";
    /**
     * The key for the "final date" filter.
     */
    public static final String FILTER_DATE_TO = "to";

    /**
     * Extracts the event filters from the Cloud Event.
     * @param exportRequestUuid the UUID of the export request.
     * @param resourceUuid the UUID of the resource request.
     * @param exportRequest the export request itself to get the filters from.
     * @throws FilterExtractionException if the filters could not be extracted.
     */
    public EventFilters extract(final UUID exportRequestUuid, final UUID resourceUuid, final ExportRequestClass exportRequest) throws FilterExtractionException {
        final Map<String, Object> filters = exportRequest.getFilters();
        if (filters == null) {
            return new EventFilters(null, null);
        }

        // Extract the "from" and/or "to" date filters.
        final LocalDate from;
        try {
            from = this.extractDateFromObject(filters.get(FILTER_DATE_FROM));
        } catch (final DateTimeParseException e) {
            Log.debugf("[export_request_uuid: %s][resource_uuid: %s] bad \"from\" date format. Notifying the export service with a 400 error. Received date: %s", exportRequestUuid, resourceUuid, filters.get(FILTER_DATE_FROM));

            throw new FilterExtractionException("unable to parse the 'from' date filter with the 'yyyy-mm-dd' format");
        } catch (final IllegalStateException e) {
            Log.debugf("[export_request_uuid: %][resource_uuid: %s] well formatted but invalid \"from\" date received: %s. Error cause: %s", exportRequestUuid, resourceUuid, filters.get(FILTER_DATE_FROM), e.getMessage());

            throw new FilterExtractionException(String.format("invalid 'from' filter date specified: %s", e.getMessage()));
        }

        final LocalDate to;
        try {
            to = this.extractDateFromObject(filters.get(FILTER_DATE_TO));
        } catch (final DateTimeParseException e) {
            Log.debugf("[export_request_uuid: %][resource_uuid: %s] bad \"to\" date format. Notifying the export service with a 400 error. Received date: %s", exportRequestUuid, resourceUuid, filters.get(FILTER_DATE_TO));

            throw new FilterExtractionException("unable to parse the 'to' date filter with the 'yyyy-mm-dd' format");
        } catch (final IllegalStateException e) {
            Log.debugf("[export_request_uuid: %][resource_uuid: %s] well formatted but invalid \"to\" date received: %s. Error cause: %s", exportRequestUuid, resourceUuid, filters.get(FILTER_DATE_TO), e.getMessage());

            throw new FilterExtractionException(String.format("invalid 'to' filter date specified: %s", e.getMessage()));
        }

        // Make sure that the "from" date is before of the "to" date, to avoid
        // hitting the database with conditions that would report no results.
        if (to != null && from != null && to.isBefore(from)) {
            Log.debugf("[export_request_uuid: %s][resource_uuid: %s] the received \"to\" date filter [%s] is before the \"from\" date filter [%s]", exportRequestUuid, resourceUuid, from.toString(), to.toString());

            throw new FilterExtractionException("'from' date must be earlier than the 'to' date");
        }

        return new EventFilters(from, to);
    }

    /**
     * Extracts the date from the provided object. It also looks if the given
     * date is valid, in the sense of it not being older than a month or in the
     * future. {@code null} dates are considered valid, since that means that
     * there is no filter to apply to the queries.
     * @param date the date which will be attempted to parse.
     * @return the parsed date or {@code null} if there is no date in the given
     *         object.
     */
    protected LocalDate extractDateFromObject(final Object date) {
        if (date == null) {
            return null;
        }

        final LocalDate parsedDate = LocalDate.parse((String) date);

        final LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (parsedDate.isAfter(today)) {
            throw new IllegalStateException("the specified date is in the future");
        }

        final LocalDate aMonthAgo = today.minusMonths(1);
        if (aMonthAgo.isAfter(parsedDate)) {
            throw new IllegalStateException("the specified date is older than a month");
        }

        return parsedDate;
    }
}
