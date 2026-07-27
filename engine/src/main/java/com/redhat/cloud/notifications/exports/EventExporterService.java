package com.redhat.cloud.notifications.exports;

import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequestClass;
import com.redhat.cloud.notifications.db.repositories.EventRepository;
import com.redhat.cloud.notifications.exports.filters.FilterExtractionException;
import com.redhat.cloud.notifications.exports.filters.events.EventFilters;
import com.redhat.cloud.notifications.exports.filters.events.EventFiltersExtractor;
import com.redhat.cloud.notifications.exports.transformers.ResultsTransformer;
import com.redhat.cloud.notifications.exports.transformers.TransformationException;
import com.redhat.cloud.notifications.exports.transformers.UnsupportedFormatException;
import com.redhat.cloud.notifications.exports.transformers.event.CSVEventTransformer;
import com.redhat.cloud.notifications.exports.transformers.event.JSONEventTransformer;
import com.redhat.cloud.notifications.models.Event;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class EventExporterService {

    @Inject
    EventFiltersExtractor eventFiltersExtractor;

    @Inject
    EventRepository eventRepository;

    /**
     * Exports the events to the format specified in the request.
     * @param resourceRequest the request to extract the filters and the
     *                        required data from.
     * @param orgId the associated organization ID of the request.
     * @return a string containing the serialized contents.
     * @throws FilterExtractionException if the filters could not be extracted
     *                                   due to them being malformed, being
     *                                   older than a month, being in the
     *                                   future, or being a "from" filter that
     *                                   is older than the "to" filter.
     * @throws TransformationException if the transformation could not be
     *                                 performed.
     * @throws UnsupportedFormatException if the specified format is not
     *                                    supported by Notifications.
     */
    public String exportEvents(final ResourceRequestClass resourceRequest, final String orgId) throws FilterExtractionException, TransformationException, UnsupportedFormatException {
        // Extract the filters from the request.
        final EventFilters eventFilters = this.eventFiltersExtractor.extract(resourceRequest);

        final ResultsTransformer<Event> transformer = switch (resourceRequest.getFormat()) {
            case CSV -> new CSVEventTransformer();
            case JSON -> new JSONEventTransformer();
            default -> throw new UnsupportedFormatException();
        };

        // Stream the events page by page directly into the transformer,
        // instead of fetching every event into a single list and only then
        // transforming it, so that the full event list and the fully
        // rendered CSV/JSON output are never both held in memory at once.
        this.eventRepository.findEventsToExport(orgId, eventFilters.from(), eventFilters.to(), transformer::addRecords);

        return transformer.finish();
    }
}
