package com.redhat.cloud.notifications.processors.email.aggregators;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.db.EmailAggregationResources;
import com.redhat.cloud.notifications.models.AggregationCommand;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.jboss.logmanager.Level;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.models.EmailSubscriptionType.*;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.*;

@ApplicationScoped
public class DailyEmailAggregationJob {

    public static final String AGGREGATION_CHANNEL = "aggregation";

    private static final Logger LOG = Logger.getLogger(DailyEmailAggregationJob.class.getName());

    private final EmailAggregationResources emailAggregationResources;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @Channel(AGGREGATION_CHANNEL)
    Emitter<String> emitter;

    public DailyEmailAggregationJob(EmailAggregationResources emailAggregationResources) {
        this.emailAggregationResources = emailAggregationResources;
    }

    public void processDailyEmail() {
        if (isCronJobEnabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(UTC);
        List<AggregationCommand> aggregationCommands = processAggregateEmails(now);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (AggregationCommand aggregationCommand : aggregationCommands) {
            try {
                final String payload = objectMapper.writeValueAsString(aggregationCommand);
                futures.add(emitter.send(payload).toCompletableFuture());
            } catch (JsonProcessingException e) {
                LOG.warning("Could not transform AggregationCommand to JSON object.");
            }

            emailAggregationResources.updateLastCronJobRun(emailAggregationResources.getLastCronJobRun().getId(), now);

            try {
                CompletionStage<Void> combinedDataCompletionStage = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                combinedDataCompletionStage.toCompletableFuture().get();
            } catch (InterruptedException | ExecutionException ie) {
                LOG.log(Level.SEVERE, "Writing AggregationCommands failed", ie);
            }
        }
    }

    List<AggregationCommand> processAggregateEmails(LocalDateTime endTime) {
        LocalDateTime startTime = emailAggregationResources.getLastCronJobRun().getLastRun();

        LOG.info(String.format("Collecting email aggregation for period (%s, %s) and type %s", startTime, endTime, DAILY));

        final List<AggregationCommand> pendingAggregationCommands =
                emailAggregationResources.getApplicationsWithPendingAggregation(startTime, endTime)
                        .stream()
                        .map(aggregationKey -> new AggregationCommand(aggregationKey, startTime, endTime, DAILY))
                        .collect(Collectors.toList());

        LOG.info(
                String.format(
                        "Finished collecting email aggregations for period (%s, %s) and type %s after %d seconds. %d (accountIds, applications) pairs were processed",
                        startTime,
                        endTime,
                        DAILY,
                        SECONDS.between(endTime, LocalDateTime.now()),
                        pendingAggregationCommands.size()
                )
        );
        return pendingAggregationCommands;
    }

    private boolean isCronJobEnabled() {
        // The scheduled job is disabled by default.
        return ConfigProvider.getConfig().getOptionalValue("notifications.aggregator.email.subscription.periodic.cron.enabled", Boolean.class).orElse(false);
    }
}
