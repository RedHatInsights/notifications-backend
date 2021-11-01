package com.redhat.cloud.notifications;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.db.EmailAggregationResources;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.CronJobRun;
import io.micrometer.core.instrument.MeterRegistry;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logging.Logger;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.control.ActivateRequestContext;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.EmailSubscriptionType.*;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.*;

@ApplicationScoped
public class DailyEmailAggregationJob {

    public static final String AGGREGATION_CHANNEL = "aggregation";

    private static final Logger LOG = Logger.getLogger(DailyEmailAggregationJob.class.getName());

    private final EmailAggregationResources emailAggregationResources;
    private final ObjectMapper objectMapper;

    MeterRegistry registry;

    private final AtomicInteger processedGauge;

    @Inject
    @Channel(AGGREGATION_CHANNEL)
    Emitter<String> emitter;

    public DailyEmailAggregationJob(EmailAggregationResources emailAggregationResources, ObjectMapper objectMapper,
            MeterRegistry registry) {
        this.emailAggregationResources = emailAggregationResources;
        this.objectMapper = objectMapper;
        this.registry = registry;

        processedGauge = registry.gauge("daily.email.aggregation.job.processed", new AtomicInteger(0));
    }

    @ActivateRequestContext
    public void processDailyEmail() {
        LocalDateTime now = LocalDateTime.now(UTC);
        List<AggregationCommand> aggregationCommands = processAggregateEmails(now);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (AggregationCommand aggregationCommand : aggregationCommands) {
            try {
                final String payload = objectMapper.writeValueAsString(aggregationCommand);
                futures.add(emitter.send(payload).toCompletableFuture());
            } catch (JsonProcessingException e) {
                LOG.warn("Could not transform AggregationCommand to JSON object.", e);
            }

            emailAggregationResources.updateLastCronJobRun(now);

            // resolve completable futures so the Quarkus main thread doesn't stop before everything has been sent
            try {
                CompletionStage<Void> combinedDataCompletionStage = CompletableFuture
                        .allOf(futures.toArray(new CompletableFuture[0]));
                combinedDataCompletionStage.toCompletableFuture().get();
            } catch (InterruptedException | ExecutionException ie) {
                LOG.error("Writing AggregationCommands failed", ie);
            }
        }
    }

    List<AggregationCommand> processAggregateEmails(LocalDateTime endTime) {
        final CronJobRun lastCronJobRun = emailAggregationResources.getLastCronJobRun();
        LocalDateTime startTime = lastCronJobRun.getLastRun();

        LOG.info(String.format("Collecting email aggregation for period (%s, %s) and type %s", startTime, endTime,
                DAILY));

        final List<AggregationCommand> pendingAggregationCommands = emailAggregationResources
                .getApplicationsWithPendingAggregation(startTime, endTime).stream()
                .map(aggregationKey -> new AggregationCommand(aggregationKey, startTime, endTime, DAILY))
                .collect(Collectors.toList());

        LOG.info(String.format(
                "Finished collecting email aggregations for period (%s, %s) and type %s after %d seconds. %d (accountIds, applications) pairs were processed",
                startTime, endTime, DAILY, SECONDS.between(endTime, LocalDateTime.now(UTC)),
                pendingAggregationCommands.size()));

        processedGauge.set(pendingAggregationCommands.size());

        return pendingAggregationCommands;
    }
}
