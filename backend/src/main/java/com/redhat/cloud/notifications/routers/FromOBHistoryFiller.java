package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Constants;
import com.redhat.cloud.notifications.db.repositories.NotificationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Process the error information from OB, which we need to put
 * into the notification history.
 */
@ApplicationScoped
@Path(Constants.API_NOTIFICATIONS_V_1_0 + "/ob")
public class FromOBHistoryFiller {

    public static final String MESSAGES_ERROR_COUNTER_NAME = "ob.messages.error";
    public static final String MESSAGES_PROCESSED_COUNTER_NAME = "ob.messages.processed";


    @Inject
    NotificationRepository notificationHistoryRepository;

    private Counter messagesProcessedCounter;
    private Counter messagesErrorCounter;

    @Inject
    MeterRegistry meterRegistry;

    @PostConstruct
    void init() {
        messagesProcessedCounter = meterRegistry.counter(MESSAGES_PROCESSED_COUNTER_NAME);
        messagesErrorCounter = meterRegistry.counter(MESSAGES_ERROR_COUNTER_NAME);
    }


    @POST
    @Path("/errors")
    public Response handleCallback(@NotEmpty String payload, @HeaderParam("x-rhose-original-event-id") UUID eventUUId) {

        if (eventUUId == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("No id passed").build();
        }

        String eventId = eventUUId.toString();
        JsonObject ce;
        try {
            Log.infof("Processing return from OB with id %s and payload: %s", eventId, payload);
            Object o = Json.decodeValue(payload);
            ce = (JsonObject) o;

        } catch (Exception e) {
            messagesErrorCounter.increment();
            Log.error("| Failure to parse incoming CloudEvent");
            return Response.status(Response.Status.BAD_REQUEST).build();
        }

        try {
            Map<String, Object> historyMap = new HashMap<>();
            historyMap.put("historyId", eventId);
            historyMap.put("successful", false);
            String reason = ce.getString("deadletterreason");
            historyMap.put("details", reason);
            historyMap.put("duration", 0); // OB does not supply this

            notificationHistoryRepository.updateHistoryItem(historyMap);
        } catch (Exception e) {
            messagesErrorCounter.increment();
            Response.ResponseBuilder builder;
            if (e instanceof NoResultException) {
                builder = Response.status(Response.Status.BAD_REQUEST);
                Log.warnf("|  Failure, eventId not found %s", eventId);
            } else {
                builder = Response.status(Response.Status.INTERNAL_SERVER_ERROR);
                Log.error("|  Failure to update history", e);
            }
            return builder.build();
        } finally {
            messagesProcessedCounter.increment();
        }
        return Response.accepted().build();
    }
}
