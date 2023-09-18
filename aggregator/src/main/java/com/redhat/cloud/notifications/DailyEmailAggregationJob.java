package com.redhat.cloud.notifications;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.AggregationOrgConfigRepository;
import com.redhat.cloud.notifications.db.EmailAggregationRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.AggregationCommand;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.PushGateway;
import io.quarkus.logging.Log;
import io.quarkus.runtime.configuration.ProfileManager;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static io.quarkus.runtime.LaunchMode.NORMAL;
import static java.time.ZoneOffset.UTC;

@ApplicationScoped
public class DailyEmailAggregationJob {

    public static final String AGGREGATION_CHANNEL = "aggregation";
    public static final String EGRESS_CHANNEL = "egress";
    public static final String BUNDLE_NAME = "console";
    public static final String APP_NAME = "notifications";
    public static final String EVENT_TYPE_NAME = "aggregation";

    @Inject
    EmailAggregationRepository emailAggregationResources;

    @Inject
    AggregationOrgConfigRepository aggregationOrgConfigRepository;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "prometheus.pushgateway.url")
    String prometheusPushGatewayUrl;

    @ConfigProperty(name = "notifications.default.daily.digest.time", defaultValue = "00:00")
    LocalTime defaultDailyDigestTime;

    @Inject
    @Channel(AGGREGATION_CHANNEL)
    Emitter<String> emitter;

    @Inject
    @Channel(EGRESS_CHANNEL)
    Emitter<String> emitterIngress;

    @Inject
    FeatureFlipper featureFlipper;

    private Gauge pairsProcessed;

    @ActivateRequestContext
    public void processDailyEmail() {
        CollectorRegistry registry = new CollectorRegistry();
        Gauge duration = Gauge
                .build()
                .name("aggregator_job_duration_seconds")
                .help("Duration of the aggregator job in seconds.")
                .register(registry);
        Gauge.Timer durationTimer = duration.startTimer();

        try {
            LocalDateTime now = LocalDateTime.now(UTC);

            aggregationOrgConfigRepository.createMissingDefaultConfiguration(defaultDailyDigestTime);
            List<AggregationCommand> aggregationCommands = processAggregateEmailsWithOrgPref(now, registry);

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (AggregationCommand aggregationCommand : aggregationCommands) {
                try {
                    if (featureFlipper.isAggregatorSendOnIngress()) {
                        sendIt(aggregationCommand);
                    } else {
                        final String payload = objectMapper.writeValueAsString(aggregationCommand);
                        futures.add(emitter.send(payload).toCompletableFuture());
                    }
                } catch (JsonProcessingException e) {
                    Log.warn("Could not transform AggregationCommand to JSON object.", e);
                }
            }
            if (!futures.isEmpty()) {
                // resolve completable futures so the Quarkus main thread doesn't stop before everything has been sent
                try {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
                } catch (InterruptedException | ExecutionException ie) {
                    Log.error("Writing AggregationCommands failed", ie);
                }
            }

            List<String> orgIdsToUpdate = aggregationCommands.stream().map(agc -> agc.getAggregationKey().getOrgId()).collect(Collectors.toList());
            emailAggregationResources.updateLastCronJobRunAccordingOrgPref(orgIdsToUpdate, now);

            Gauge lastSuccess = Gauge
                    .build()
                    .name("aggregator_job_last_success")
                    .help("Last time the aggregator job succeeded.")
                    .register(registry);
            lastSuccess.setToCurrentTime();
        } catch (Exception ex) {
            Log.error("Daily aggregation job failed", ex);
            throw ex;
        } finally {
            durationTimer.setDuration();
            if (ProfileManager.getLaunchMode() == NORMAL) {
                PushGateway pg = new PushGateway(prometheusPushGatewayUrl);
                try {
                    pg.pushAdd(registry, "aggregator_job");
                } catch (IOException e) {
                    Log.warn("Could not push metrics to Prometheus Pushgateway.", e);
                }
            }
        }
    }

    List<AggregationCommand> processAggregateEmailsWithOrgPref(LocalDateTime endTime, CollectorRegistry registry) {

        final List<AggregationCommand> pendingAggregationCommands =
                emailAggregationResources.getApplicationsWithPendingAggregationAccordinfOrgPref(endTime);
        pairsProcessed = Gauge
                .build()
                .name("aggregator_job_orgid_application_pairs_processed")
                .help("Number of orgId and application pairs processed.")
                .register(registry);
        pairsProcessed.set(pendingAggregationCommands.size());

        return pendingAggregationCommands;
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
        emitterIngress.send(message);
    }

    Gauge getPairsProcessed() {
        return pairsProcessed;
    }

    // For automatic tests purpose
    protected void setDefaultDailyDigestTime(LocalTime defaultDailyDigestTime) {
        this.defaultDailyDigestTime = defaultDailyDigestTime;
    }
}
