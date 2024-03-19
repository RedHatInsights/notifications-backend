package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.events.EndpointProcessor;
import com.redhat.cloud.notifications.models.Event;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import java.time.LocalDateTime;
import java.util.List;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static java.time.Month.MARCH;

@Path(API_INTERNAL + "/replay")
public class ReplayResource {

    private static final int MAX_RESULTS = 100;

    @Inject
    EntityManager entityManager;

    @Inject
    EndpointProcessor endpointProcessor;

    public List<Event> getEvents(int firstResult, int maxResults) {
        String query = "FROM Event e JOIN FETCH e.eventType WHERE e.created > :start AND e.created <= :end";
        return entityManager.createQuery(query, Event.class)
                .setParameter("start", LocalDateTime.of(2024, MARCH, 19, 9, 13))
                .setParameter("end", LocalDateTime.of(2024, MARCH, 19, 14, 23))
                .setFirstResult(firstResult)
                .setMaxResults(maxResults)
                .getResultList();
    }

    @POST
    public void replay() {
        Log.info("Replay endpoint was called");
        int firstResult = 0;
        List<Event> events;
        do {
            Log.infof("Processing events from index %d", firstResult);
            events = getEvents(firstResult, MAX_RESULTS);
            firstResult += MAX_RESULTS;
            for (Event event : events) {
                try {
                    endpointProcessor.process(event, true);
                } catch (Exception e) {
                    Log.error("Event replay failed", e);
                }
            }
        } while (MAX_RESULTS == events.size());
    }
}
