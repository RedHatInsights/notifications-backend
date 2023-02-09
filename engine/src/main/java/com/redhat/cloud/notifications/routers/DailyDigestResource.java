package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.events.KafkaMessageWithIdBuilder;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.routers.dailydigest.TriggerDailyDigestRequestDto;
import com.redhat.cloud.notifications.templates.models.Environment;
import io.vertx.core.json.Json;
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

    /**
     * Triggers a daily digest by sending a command to the Kafka "aggregation"
     * queue.
     * @param triggerDailyDigestRequestDto the settings of the aggregation
     *                                     command.
     */
    @Consumes(APPLICATION_JSON)
    @Produces(TEXT_PLAIN)
    @POST
    public Response triggerDailyDigest(final TriggerDailyDigestRequestDto triggerDailyDigestRequestDto) {
        if (!this.environment.isEnvironmentStage()) {
            return Response
                .status(Response.Status.BAD_REQUEST)
                .entity("the daily digests can only be triggered in the stage environment")
                .type(TEXT_PLAIN)
                .build();
        }

        final EmailAggregationKey emailAggregationKey = new EmailAggregationKey(
            triggerDailyDigestRequestDto.getOrgId(),
            triggerDailyDigestRequestDto.getBundleName(),
            triggerDailyDigestRequestDto.getApplicationName()
        );

        final AggregationCommand aggregationCommand = new AggregationCommand(
            emailAggregationKey,
            triggerDailyDigestRequestDto.getStart(),
            triggerDailyDigestRequestDto.getEnd(),
            EmailSubscriptionType.DAILY
        );

        final Message<String> message = KafkaMessageWithIdBuilder.build(Json.encode(aggregationCommand));

        this.aggregationEmitter.send(message);

        return Response.noContent().build();
    }
}
