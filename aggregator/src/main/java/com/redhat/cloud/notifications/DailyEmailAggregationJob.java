package com.redhat.cloud.notifications;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.AggregationOrgConfigRepository;
import com.redhat.cloud.notifications.db.EmailAggregationRepository;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.AggregationOrgConfig;
import com.redhat.cloud.notifications.models.CronJobRun;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.PushGateway;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.control.ActivateRequestContext;
import javax.inject.Inject;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.SECONDS;

@ApplicationScoped
public class DailyEmailAggregationJob {

    public static final String AGGREGATION_CHANNEL = "aggregation";

    @Inject
    EmailAggregationRepository emailAggregationResources;

    @Inject
    AggregationOrgConfigRepository aggregationOrgConfigRepository;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "prometheus.pushgateway.url")
    String prometheusPushGatewayUrl;

    @ConfigProperty(name = "notification.default.daily.digest.hour", defaultValue = "0")
    int defaultDailyDigestHour;

    @Inject
    @Channel(AGGREGATION_CHANNEL)
    Emitter<String> emitter;

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
            List<AggregationCommand> aggregationCommands = null;
            if (featureFlipper.isAggregatorOrgPrefEnabled()) {
                aggregationOrgConfigRepository.createMissingDefaultConfiguration(defaultDailyDigestHour);
                aggregationCommands = processAggregateEmailsWithOrgPref(now, registry);
            } else {
                aggregationCommands = processAggregateEmails(now, registry);
            }
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (AggregationCommand aggregationCommand : aggregationCommands) {
                try {
                    final String payload = objectMapper.writeValueAsString(aggregationCommand);
                    futures.add(emitter.send(payload).toCompletableFuture());
                } catch (JsonProcessingException e) {
                    Log.warn("Could not transform AggregationCommand to JSON object.", e);
                }

                // resolve completable futures so the Quarkus main thread doesn't stop before everything has been sent
                try {
                    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
                } catch (InterruptedException | ExecutionException ie) {
                    Log.error("Writing AggregationCommands failed", ie);
                }
            }

            if (featureFlipper.isAggregatorOrgPrefEnabled()) {
                emailAggregationResources.updateLastCronJobRunAccordingOrgPref(now);
            } else {
                emailAggregationResources.updateLastCronJobRun(now);
            }

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
            PushGateway pg = new PushGateway(prometheusPushGatewayUrl);
            try {
                pg.pushAdd(registry, "aggregator_job");
            } catch (IOException e) {
                Log.warn("Could not push metrics to Prometheus Pushgateway.", e);
            }
        }
    }

    List<AggregationCommand> processAggregateEmailsWithOrgPref(LocalDateTime endTime, CollectorRegistry registry) {

        final List<AggregationCommand> pendingAggregationCommands =
                    emailAggregationResources.getApplicationsWithPendingAggregationAccordinfOrfPref(endTime);
        pairsProcessed = Gauge
                .build()
                .name("aggregator_job_orgid_application_pairs_processed")
                .help("Number of orgId and application pairs processed.")
                .register(registry);
        pairsProcessed.set(pendingAggregationCommands.size());

        return pendingAggregationCommands;
    }

    @Deprecated(forRemoval = true)
    List<AggregationCommand> processAggregateEmails(LocalDateTime endTime, CollectorRegistry registry) {

        final CronJobRun lastCronJobRun = emailAggregationResources.getLastCronJobRun();
        LocalDateTime startTime = lastCronJobRun.getLastRun();

        Log.infof("Collecting email aggregation for period (%s, %s) and type %s", startTime, endTime, DAILY);

        final List<AggregationCommand> pendingAggregationCommands =
                emailAggregationResources.getApplicationsWithPendingAggregation(startTime, endTime)
                        .stream()
                        .map(aggregationKey -> new AggregationCommand(aggregationKey, startTime, endTime, DAILY))
                        .collect(Collectors.toList());

        Log.infof(
                "Finished collecting email aggregations for period (%s, %s) and type %s after %d seconds. %d (orgIds, applications) pairs were processed",
                startTime,
                endTime,
                DAILY,
                SECONDS.between(endTime, LocalDateTime.now(UTC)),
                pendingAggregationCommands.size()
        );

        pairsProcessed = Gauge
                .build()
                .name("aggregator_job_orgid_application_pairs_processed")
                .help("Number of orgId and application pairs processed.")
                .register(registry);
        pairsProcessed.set(pendingAggregationCommands.size());

        return pendingAggregationCommands;
    }

    Gauge getPairsProcessed() {
        return pairsProcessed;
    }

    // For automatic tests purpose
    protected void setDefaultDailyDigestHour(int defaultDailyDigestHour) {
        this.defaultDailyDigestHour = defaultDailyDigestHour;
    }
}
