package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.config.AggregatorConfig;
import com.redhat.cloud.notifications.db.AggregationOrgConfigRepository;
import com.redhat.cloud.notifications.db.EmailAggregationRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.AggregationCommand;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.PushGateway;
import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;
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
import java.util.stream.Collectors;

import static java.time.ZoneOffset.UTC;

@ApplicationScoped
public class DailyEmailAggregationJob {

    public static final String EGRESS_CHANNEL = "egress";
    public static final String BUNDLE_NAME = "console";
    public static final String APP_NAME = "notifications";
    public static final String EVENT_TYPE_NAME = "aggregation";

    @Inject
    AggregatorConfig aggregatorConfig;

    @Inject
    EmailAggregationRepository emailAggregationResources;

    @Inject
    AggregationOrgConfigRepository aggregationOrgConfigRepository;

    @ConfigProperty(name = "prometheus.pushgateway.url", defaultValue = "http://localhost:8080")
    String prometheusPushGatewayUrl;

    @ConfigProperty(name = "notifications.default.daily.digest.time", defaultValue = "00:00")
    LocalTime defaultDailyDigestTime;

    @Inject
    @Channel(EGRESS_CHANNEL)
    Emitter<String> emitterIngress;

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
            // get targeted schedule execution time
            LocalDateTime now = computeScheduleExecutionTime();

            if (aggregatorConfig.isAggregationBasedOnEventEnabled()) {
                aggregationOrgConfigRepository.createMissingDefaultConfigurationBasedOnEvent(defaultDailyDigestTime);
            } else {
                aggregationOrgConfigRepository.createMissingDefaultConfiguration(defaultDailyDigestTime);
            }
            List<AggregationCommand> aggregationCommands = processAggregateEmailsWithOrgPref(now, registry);
            Log.infof("found %s commands", aggregationCommands.size());
            Log.debugf("Aggregation commands: %s", aggregationCommands);

            aggregationCommands.stream().collect(Collectors.groupingBy(AggregationCommand::getOrgId))
                .values().forEach(this::sendIt);

            List<String> orgIdsToUpdate = aggregationCommands.stream().map(aggregationCommand -> aggregationCommand.getOrgId()).collect(Collectors.toList());
            Log.debugf("Found following org IDs to update: %s", orgIdsToUpdate);

            aggregationOrgConfigRepository.updateLastCronJobRunAccordingOrgPref(orgIdsToUpdate, now);

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
            if (!LaunchMode.current().isDevOrTest()) {
                PushGateway pg = new PushGateway(prometheusPushGatewayUrl);
                try {
                    pg.pushAdd(registry, "aggregator_job");
                } catch (IOException e) {
                    Log.warn("Could not push metrics to Prometheus Pushgateway.", e);
                }
            }
        }
    }


    /**
     * Compute targeted schedule execution time local date time.
     * User can schedule execution time every 15 min.
     * Event if this job is executed at 05:02, we are looking for schedule executions of 05:00
     *
     * @return the local date time
     */
    public LocalDateTime computeScheduleExecutionTime() {
        final LocalDateTime currentTime = LocalDateTime.now(UTC).withSecond(0).withNano(0);

        // correct Time
        if (currentTime.getMinute() < 15) {
            return currentTime.withMinute(0);
        } else if (currentTime.getMinute() < 30) {
            return currentTime.withMinute(15);
        } else if (currentTime.getMinute() < 45) {
            return currentTime.withMinute(30);
        } else {
            return currentTime.withMinute(45);
        }
    }

    List<AggregationCommand> processAggregateEmailsWithOrgPref(LocalDateTime endTime, CollectorRegistry registry) {

        List<AggregationCommand> pendingAggregationCommands = new ArrayList<>();

        final List<AggregationCommand> pendingAggregationCommandsFromEmailAggregation = emailAggregationResources.getApplicationsWithPendingAggregationAccordinfOrgPref(endTime);
        if (aggregatorConfig.isAggregationBasedOnEventEnabled()) {
            final List<AggregationCommand> pendingAggregationCommandsFromEvents = emailAggregationResources.getApplicationsWithPendingAggregationAccordingOrgPref(endTime);

            List<AggregationCommand> differences = pendingAggregationCommandsFromEmailAggregation.stream()
                .filter(element -> !pendingAggregationCommandsFromEvents.contains(element))
                .collect(Collectors.toList());

            if (!differences.isEmpty()) {
                Log.warnf("Fetching aggregation from legacy way have more record than the events way:");
                for (AggregationCommand differencesCommand : differences) {
                    Log.info(differencesCommand.toString());
                }
            }

            differences = pendingAggregationCommandsFromEvents.stream()
                .filter(element -> !pendingAggregationCommandsFromEmailAggregation.contains(element))
                .collect(Collectors.toList());

            if (!differences.isEmpty()) {
                Log.warnf("Fetching aggregation from events have more record than the legacy way:");
                for (AggregationCommand differencesCommand : differences) {
                    Log.info(differencesCommand.toString());
                }
            }

            pendingAggregationCommands = pendingAggregationCommandsFromEvents.stream().filter(e -> aggregatorConfig.isAggregationBasedOnEventEnabledByOrgId(e.getOrgId())).collect(Collectors.toList());
            pendingAggregationCommands.addAll(pendingAggregationCommandsFromEmailAggregation.stream().filter(e -> !aggregatorConfig.isAggregationBasedOnEventEnabledByOrgId(e.getOrgId())).collect(Collectors.toList()));
        } else {
            pendingAggregationCommands = pendingAggregationCommandsFromEmailAggregation;
        }

        pairsProcessed = Gauge
                .build()
                .name("aggregator_job_orgid_application_pairs_processed")
                .help("Number of orgId and application pairs processed.")
                .register(registry);
        pairsProcessed.set(pendingAggregationCommands.size());

        return pendingAggregationCommands;
    }

    private void sendIt(List<AggregationCommand> aggregationCommands) {

        List<Event> eventList = new ArrayList<>();
        aggregationCommands.stream().forEach(aggregationCommand -> {
            Payload.PayloadBuilder payloadBuilder = new Payload.PayloadBuilder();
            Map<String, Object> payload = JsonObject.mapFrom(aggregationCommand).getMap();
            payload.forEach(payloadBuilder::withAdditionalProperty);

            eventList.add(new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(payloadBuilder.build())
                .build());
        });

        Action action = new Action.ActionBuilder()
            .withBundle(BUNDLE_NAME)
            .withApplication(APP_NAME)
            .withEventType(EVENT_TYPE_NAME)
            .withOrgId(aggregationCommands.get(0).getOrgId())
            .withTimestamp(LocalDateTime.now(UTC))
            .withEvents(eventList)
            .withContext(new Context.ContextBuilder()
                .withAdditionalProperty("single_daily_digest_enabled", true)
                .build())
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
