package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.cloud.event.parser.exceptions.ConsoleCloudEventParsingException;
import com.redhat.cloud.notifications.cloudevent.transformers.CloudEventTransformer;
import com.redhat.cloud.notifications.cloudevent.transformers.CloudEventTransformerFactory;
import com.redhat.cloud.notifications.events.EndpointProcessor;
import com.redhat.cloud.notifications.events.EventWrapper;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.events.EventWrapperCloudEvent;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationsConsoleCloudEvent;
import com.redhat.cloud.notifications.routers.replay.EventsReplayRequest;
import com.redhat.cloud.notifications.utils.ActionParser;
import com.redhat.cloud.notifications.utils.ActionParsingException;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static com.redhat.cloud.notifications.models.NotificationStatus.FAILED_EXTERNAL;
import static com.redhat.cloud.notifications.models.NotificationStatus.SUCCESS;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@Path(API_INTERNAL + "/replay")
public class ReplayResource {

    private static final int MAX_RESULTS = 100;

    @Inject
    EntityManager entityManager;

    @Inject
    EndpointProcessor endpointProcessor;

    @Inject
    ActionParser actionParser;

    @Inject
    CloudEventTransformerFactory cloudEventTransformerFactory;

    ConsoleCloudEventParser cloudEventParser = new ConsoleCloudEventParser();

    public List<Event> getEvents(String orgId,  LocalDateTime startDate, LocalDateTime endDate, int firstResult, int maxResults) {

        String hql = "FROM Event e JOIN FETCH e.eventType " +
                "WHERE e.created > :start AND e.created <= :end " +
                "AND EXISTS (SELECT 1 FROM NotificationHistory " +
                "WHERE e = event AND compositeEndpointType.type = :endpointType AND status = :failed) " +
                "AND NOT EXISTS (SELECT 1 FROM NotificationHistory " +
                "WHERE e = event AND compositeEndpointType.type = :endpointType AND status = :success)";

        if (orgId != null) {
            hql += " AND orgId = :orgId";
        }

        TypedQuery<Event> typedQuery = entityManager.createQuery(hql, Event.class)
                .setParameter("start", startDate)
                .setParameter("end", endDate)
                .setParameter("endpointType", EMAIL_SUBSCRIPTION)
                .setParameter("failed", FAILED_EXTERNAL)
                .setParameter("success", SUCCESS)
                .setFirstResult(firstResult)
                .setMaxResults(maxResults);

        if (orgId != null) {
            typedQuery.setParameter("orgId", orgId);
        }

        return typedQuery.getResultList();
    }

    @POST
    @Consumes(APPLICATION_JSON)
    public void replay(@NotNull @Valid EventsReplayRequest eventsReplayRequest) {
        Log.infof("Replay endpoint was called for events from %s to %s", eventsReplayRequest.startDate, eventsReplayRequest.endDate);
        int firstResult = 0;
        List<Event> events;
        long processedEvents = 0;
        do {
            Log.infof("Processing events from index %d", firstResult);
            events = getEvents(eventsReplayRequest.orgId, eventsReplayRequest.startDate, eventsReplayRequest.endDate, firstResult, MAX_RESULTS);
            firstResult += MAX_RESULTS;
            for (Event event : events) {
                try {
                    EventWrapper<?, ?> eventWrapper = parsePayload(event.getPayload());

                    if (eventWrapper instanceof EventWrapperCloudEvent) {
                        // We loaded a cloud event and identified the event-type it belongs to
                        // At this point, lets check if we have a transformation available for this event
                        // If we do, transform the event - Later this will be done on a by-integration basis
                        Optional<CloudEventTransformer> transformer = cloudEventTransformerFactory.getTransformerIfSupported((EventWrapperCloudEvent) eventWrapper);
                        if (transformer.isPresent()) {
                            eventWrapper = new EventWrapperAction(
                                    transformer.get().toAction(
                                            (EventWrapperCloudEvent) eventWrapper,
                                            event.getEventType().getApplication().getBundle().getName(),
                                            event.getEventType().getApplication().getName(),
                                            event.getEventType().getName()
                                    ));
                        }
                    }

                    event.setEventWrapper(eventWrapper);

                    endpointProcessor.process(event, true);
                    processedEvents++;
                } catch (Exception e) {
                    Log.error("Event replay failed", e);
                }
            }
        } while (MAX_RESULTS == events.size());
        Log.infof("%d events where replayed", processedEvents);
    }

    private EventWrapper<?, ?> parsePayload(String payload) {
        try {
            Action action = actionParser.fromJsonString(payload);
            return new EventWrapperAction(action);
        } catch (ActionParsingException actionParseException) {
            // Try to load it as a CloudEvent
            try {
                return new EventWrapperCloudEvent(cloudEventParser.fromJsonString(payload, NotificationsConsoleCloudEvent.class));
            } catch (ConsoleCloudEventParsingException cloudEventParseException) {
                actionParseException.addSuppressed(cloudEventParseException);
                throw actionParseException;
            }
        }
    }
}
