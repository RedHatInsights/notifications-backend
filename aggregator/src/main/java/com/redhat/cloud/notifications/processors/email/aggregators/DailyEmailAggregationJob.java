package com.redhat.cloud.notifications.processors.email.aggregators;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.db.EmailAggregationResources;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.CronJobRun;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logmanager.Level;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.time.ZoneOffset.UTC;

@ApplicationScoped
public class DailyEmailAggregationJob {

    private static final Logger LOG = Logger.getLogger(DailyEmailAggregationJob.class.getName());

    @Inject
    EmailAggregationResources emailAggregationResources;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @Channel("aggregation")
    Emitter<String> emitter;

    public void processDailyEmail() {
        List<AggregationCommand> aggregationCommands = processAggregateEmails();

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (AggregationCommand aggregationCommand : aggregationCommands) {
            try {
                final String payload = objectMapper.writeValueAsString(aggregationCommand);
                emitter.send(payload);
                futures.add(emitter.send(payload).toCompletableFuture());
            } catch (JsonProcessingException e) {
                LOG.warning("Could not transform AggregationCommand to JSON object.");
            }
        }

        final CronJobRun lastCronJobRun = emailAggregationResources.getLastCronJobRun();
        emailAggregationResources.updateLastCronJobRun(lastCronJobRun.getId(), Instant.now());

        try {
            CompletionStage<Void> combinedDataCompletionStage = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            combinedDataCompletionStage.toCompletableFuture().get();
        } catch (InterruptedException | ExecutionException ie) {
            LOG.log(Level.SEVERE, "Writing AggregationCommands failed", ie);
        }
    }

    List<AggregationCommand> processAggregateEmails() {
        Instant scheduledFireTime = emailAggregationResources.getLastCronJobRun().getLastRun();
//        scheduledFireTime = scheduledFireTime.plus(5, ChronoUnit.HOURS);
        Instant yesterdayScheduledFireTime = scheduledFireTime.minus(EmailSubscriptionType.DAILY.getDuration());

        LocalDateTime endTime = LocalDateTime.ofInstant(scheduledFireTime, UTC);
        LocalDateTime startTime = LocalDateTime.ofInstant(yesterdayScheduledFireTime, UTC);

        final LocalDateTime aggregateStarted = LocalDateTime.now();

        LOG.info(String.format("Collecting email aggregation for period (%s, %s) and type %s", startTime, endTime, EmailSubscriptionType.DAILY));

        final List<AggregationCommand> pendingAggregationCommands =
                emailAggregationResources.getApplicationsWithPendingAggregation(startTime, endTime)
                        .stream()
                        .map(aggregationKey -> new AggregationCommand(aggregationKey, startTime, endTime, EmailSubscriptionType.DAILY))
                        .collect(Collectors.toList());

        LOG.info(
                String.format(
                        "Finished collecting email aggregations for period (%s, %s) and type %s after %d seconds. %d (accountIds, applications) pairs were processed",
                        startTime,
                        endTime,
                        EmailSubscriptionType.DAILY,
                        ChronoUnit.SECONDS.between(aggregateStarted, LocalDateTime.now()),
                        pendingAggregationCommands.size()
                )
        );

        return pendingAggregationCommands;
    }
}
