package com.redhat.cloud.notifications.routers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.events.KafkaMessageWithIdBuilder;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.routers.dailydigest.TriggerDailyDigestRequest;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;

@Path(API_INTERNAL + "/daily-digest")
public class DailyDigestResource {

    public static final String AGGREGATION_OUT_CHANNEL = "toaggregation";

    @Channel(AGGREGATION_OUT_CHANNEL)
    @Inject
    Emitter<String> aggregationEmitter;

    @Inject
    Environment environment;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Triggers a daily digest by sending a command to the Kafka "aggregation"
     * queue.
     * @param triggerDailyDigestRequest the settings of the aggregation
     *                                     command.
     */
    @Consumes(APPLICATION_JSON)
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
            EmailSubscriptionType.DAILY
        );

        try {
            final Message<String> message = KafkaMessageWithIdBuilder.build(
                this.objectMapper.writeValueAsString(aggregationCommand)
            );

            this.aggregationEmitter.send(message);

            return Response.noContent().build();
        } catch (final JsonProcessingException e) {
            Log.errorf(
                "unable to trigger a test a daily digest because the aggregation command could not be serialized: %s. The aggregation command in question is: %s",
                e.getMessage(),
                aggregationCommand
            );

            return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("unable to trigger a daily digest due to an internal error")
                .type(TEXT_PLAIN)
                .build();
        }
    }
}
