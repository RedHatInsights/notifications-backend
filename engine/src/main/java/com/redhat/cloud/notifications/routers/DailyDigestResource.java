package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.routers.dailydigest.TriggerDailyDigestRequest;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;
import static java.time.ZoneOffset.UTC;

@Path(API_INTERNAL + "/daily-digest")
public class DailyDigestResource {

    public static final String AGGREGATION_OUT_CHANNEL = "egress";
    public static final String BUNDLE_NAME = "console";
    public static final String APP_NAME = "notifications";
    public static final String EVENT_TYPE_NAME = "aggregation";

    @Channel(AGGREGATION_OUT_CHANNEL)
    @Inject
    Emitter<String> aggregationEmitter;

    @Inject
    Environment environment;

    /**
     * Triggers a daily digest by sending a command to the Kafka "aggregation"
     * queue.
     * @param triggerDailyDigestRequest the settings of the aggregation
     *                                     command.
     */
    @Consumes(APPLICATION_JSON)
    @Path("/trigger")
    @Produces(TEXT_PLAIN)
    @POST
    public Response triggerDailyDigest(final TriggerDailyDigestRequest triggerDailyDigestRequest) {
        if (!this.environment.isLocal() && !this.environment.isStage()) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity("the daily digests can only be triggered in the local or stage environment")
                .type(TEXT_PLAIN)
                .build();
        }

        final EmailAggregationKey emailAggregationKey = new EmailAggregationKey(
            triggerDailyDigestRequest.getOrgId(),
            triggerDailyDigestRequest.getBundleName(),
            triggerDailyDigestRequest.getApplicationName()
        );

        final AggregationCommand aggregationCommand = new AggregationCommand(
            emailAggregationKey,
            triggerDailyDigestRequest.getStart(),
            triggerDailyDigestRequest.getEnd(),
            SubscriptionType.DAILY
        );

        sendIt(aggregationCommand);
        return Response.noContent().build();
    }

    private void sendIt(AggregationCommand aggregationCommand) {

        Payload.PayloadBuilder payloadBuilder = new Payload.PayloadBuilder();
        Map<String, Object> payload = JsonObject.mapFrom(aggregationCommand).getMap();
        payload.forEach(payloadBuilder::withAdditionalProperty);

        Action action = new Action.ActionBuilder()
            .withBundle(BUNDLE_NAME)
            .withApplication(APP_NAME)
            .withEventType(EVENT_TYPE_NAME)
            .withOrgId(aggregationCommand.getAggregationKey().getOrgId())
            .withTimestamp(LocalDateTime.now(UTC))
            .withEvents(List.of(
                new Event.EventBuilder()
                    .withMetadata(new Metadata.MetadataBuilder().build())
                    .withPayload(payloadBuilder.build())
                    .build()))
            .build();

        String encodedAction = Parser.encode(action);
        Log.infof("Encoded Payload: %s", encodedAction);
        Message<String> message = Message.of(encodedAction);
        aggregationEmitter.send(message);
    }

}
