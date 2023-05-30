package com.redhat.cloud.notifications.exports;

import com.redhat.cloud.event.apps.exportservice.v1.ResourceRequestClass;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.db.repositories.EventRepository;
import com.redhat.cloud.notifications.exports.filters.FilterExtractionException;
import com.redhat.cloud.notifications.exports.filters.events.EventFilters;
import com.redhat.cloud.notifications.exports.filters.events.EventFiltersExtractor;
import com.redhat.cloud.notifications.exports.transformers.TransformationException;
import com.redhat.cloud.notifications.exports.transformers.UnsupportedFormatException;
import com.redhat.cloud.notifications.exports.transformers.event.CSVEventTransformer;
import com.redhat.cloud.notifications.exports.transformers.event.JSONEventTransformer;
import com.redhat.cloud.notifications.models.Event;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class EventExporterService {

    @Inject
    EventFiltersExtractor eventFiltersExtractor;

    @Inject
    EventRepository eventRepository;

    @Inject
    StatelessSessionFactory statelessSessionFactory;

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

        // Fetch the events from the database.
        final AtomicReference<List<Event>> events = new AtomicReference<>();
        this.statelessSessionFactory.withSession(session -> {
            events.set(this.eventRepository.findEventsToExport(orgId, eventFilters.from(), eventFilters.to()));
        });

        switch (resourceRequest.getFormat()) {
            case CSV -> {
                return new CSVEventTransformer().transform(events.get());
            }
            case JSON -> {
                return new JSONEventTransformer().transform(events.get());
            }
            default -> throw new UnsupportedFormatException();
        }
    }
}
