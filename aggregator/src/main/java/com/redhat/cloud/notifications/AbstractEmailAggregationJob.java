package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.config.AggregatorConfig;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.AggregationCommand;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.exporter.PushGateway;
import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.time.ZoneOffset.UTC;

/**
 * Abstract base class for email aggregation jobs (daily, weekly, etc.).
 * Subclasses must implement template methods to customize behavior.
 */
public abstract class AbstractEmailAggregationJob {

    public static final String EGRESS_CHANNEL = "egress";
    public static final String BUNDLE_NAME = "console";
    public static final String APP_NAME = "notifications";

    @Inject
    protected AggregatorConfig aggregatorConfig;

    @ConfigProperty(name = "prometheus.pushgateway.url", defaultValue = "http://localhost:8080")
    String prometheusPushGatewayUrl;

    @Inject
    @Channel(EGRESS_CHANNEL)
    Emitter<String> emitterIngress;

    private Gauge pairsProcessed;

    /**
     * Main entry point for the aggregation job.
     */
    @ActivateRequestContext
    public void processEmail() {
        // Cluster coordination check - fail-safe if Unleash unreachable
        if (!shouldRunOnThisCluster()) {
            Log.infof("%s aggregator run skipped on %s cluster due to Unleash flag",
                getJobType(), aggregatorConfig.getClusterId().orElse("unknown"));
            return; // Exit gracefully with success
        }

        // Job start - SEC-MON-REQ-1 compliance (EOI-3 admin_action, EOI-5 process_status)
        Log.infof("[action: RUN_JOB][resource_type: %s_email_aggregation][principal: system][outcome: started] %s email aggregation job started",
            getJobType().toLowerCase(), getJobType());

        CollectorRegistry registry = new CollectorRegistry();
        Gauge duration = Gauge
                .build()
                .name("aggregator_job_duration_seconds")
                .help("Duration of the aggregator job in seconds.")
                .register(registry);
        Gauge.Timer durationTimer = duration.startTimer();

        try {
            LocalDateTime executionTime = computeExecutionTime();

            createMissingDefaultConfigs();

            List<AggregationCommand> aggregationCommands = processAggregateEmailsWithOrgPref(executionTime, registry);
            Log.infof("found %s %s commands", aggregationCommands.size(), getJobType().toLowerCase());
            Log.debugf("%s aggregation commands: %s", getJobType(), aggregationCommands);

            aggregationCommands.stream()
                .filter(cmd -> cmd.getOrgId() != null && cmd.getBundleId() != null)
                .collect(Collectors.groupingBy(AggregationCommand::getOrgId))
                .values()
                .forEach(val -> val.stream()
                    .collect(Collectors.groupingBy(AggregationCommand::getBundleId))
                    .values()
                    .forEach(this::sendIt));

            List<String> orgIdsToUpdate = aggregationCommands.stream()
                .map(AggregationCommand::getOrgId)
                .filter(orgId -> orgId != null)
                .distinct()
                .collect(Collectors.toList());
            Log.debugf("Found following org IDs to update for %s digest: %s", getJobType().toLowerCase(), orgIdsToUpdate);

            updateOrgConfigs(orgIdsToUpdate, executionTime);

            Gauge lastSuccess = Gauge
                    .build()
                    .name(getLastSuccessMetricName())
                    .help(String.format("Last time the %s aggregator job succeeded.", getJobType().toLowerCase()))
                    .register(registry);
            lastSuccess.setToCurrentTime();

            // Job completion - SEC-MON-REQ-1 compliance (EOI-3 admin_action, EOI-5 process_status)
            Log.infof("[action: RUN_JOB][resource_type: %s_email_aggregation][principal: system][outcome: success] %s email aggregation job completed successfully",
                getJobType().toLowerCase(), getJobType());
        } catch (Exception ex) {
            // Job failure - SEC-MON-REQ-1 compliance (EOI-3 admin_action, EOI-11 warnings_or_errors)
            Log.errorf("[action: RUN_JOB][resource_type: %s_email_aggregation][principal: system][outcome: failure][reason: job_failed] %s aggregation job failed",
                getJobType().toLowerCase(), getJobType());
            throw ex;
        } finally {
            durationTimer.setDuration();
            if (!LaunchMode.current().isDevOrTest()) {
                if (aggregatorConfig.isScrapeExportMode()) {
                    exposeMetricsForScraping(registry);
                } else {
                    pushMetricsToGateway(registry);
                }
            }
        }
    }

    /**
     * Template method: Return the job type (e.g., "Daily", "Weekly").
     */
    protected abstract String getJobType();

    /**
     * Template method: Return the event type name (e.g., "aggregation", "weekly-aggregation").
     */
    protected abstract String getEventTypeName();

    /**
     * Template method: Compute the execution time for this run.
     */
    protected abstract LocalDateTime computeExecutionTime();

    /**
     * Template method: Create missing default configurations for organizations.
     */
    protected abstract void createMissingDefaultConfigs();

    /**
     * Template method: Get pending aggregation commands from the repository.
     */
    protected abstract List<AggregationCommand> getPendingAggregations(LocalDateTime executionTime);

    /**
     * Template method: Update organization configurations after processing.
     */
    protected abstract void updateOrgConfigs(List<String> orgIds, LocalDateTime executionTime);

    /**
     * Template method: Return the metric name for pairs processed.
     */
    protected abstract String getPairsProcessedMetricName();

    /**
     * Template method: Return the metric name for last success.
     */
    protected abstract String getLastSuccessMetricName();

    /**
     * Template method: Return the push gateway job name.
     */
    protected abstract String getPushGatewayJobName();

    List<AggregationCommand> processAggregateEmailsWithOrgPref(LocalDateTime endTime, CollectorRegistry registry) {
        List<AggregationCommand> pendingAggregationCommands = getPendingAggregations(endTime);

        pairsProcessed = Gauge
                .build()
                .name(getPairsProcessedMetricName())
                .help(String.format("Number of orgId and application pairs processed for %s digest.", getJobType().toLowerCase()))
                .register(registry);
        pairsProcessed.set(pendingAggregationCommands.size());

        return pendingAggregationCommands;
    }

    private void sendIt(List<AggregationCommand> aggregationCommands) {
        if (aggregationCommands.isEmpty()) {
            Log.warn("sendIt called with empty aggregationCommands list");
            return;
        }

        List<Event> eventList = new ArrayList<>();
        aggregationCommands.forEach(aggregationCommand -> {
            Payload.PayloadBuilder payloadBuilder = new Payload.PayloadBuilder();
            Map<String, Object> payload = JsonObject.mapFrom(aggregationCommand).getMap();
            payload.forEach(payloadBuilder::withAdditionalProperty);

            eventList.add(new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(payloadBuilder.build())
                .build());
        });

        Action action = new Action.ActionBuilder()
            .withId(UUID.randomUUID())
            .withBundle(BUNDLE_NAME)
            .withApplication(APP_NAME)
            .withEventType(getEventTypeName())
            .withOrgId(aggregationCommands.get(0).getOrgId())
            .withTimestamp(LocalDateTime.now(UTC))
            .withEvents(eventList)
            .withContext(buildContext())
            .build();

        String encodedAction = Parser.encode(action);
        Log.infof("Encoded %s Payload: %s", getJobType(), encodedAction);
        Message<String> message = Message.of(encodedAction);
        emitterIngress.send(message);
    }

    /**
     * Template method: Build the context for the Kafka action.
     * Default is empty context. Override if needed.
     */
    protected Context buildContext() {
        return new Context.ContextBuilder().build();
    }

    private void pushMetricsToGateway(CollectorRegistry registry) {
        PushGateway pg = new PushGateway(prometheusPushGatewayUrl);
        try {
            pg.pushAdd(registry, getPushGatewayJobName());
        } catch (IOException e) {
            Log.warnf("Could not push %s aggregator metrics to Prometheus Pushgateway.", getJobType().toLowerCase(), e);
        }
    }

    /**
     * Interim fix for RHCLOUD-50496: Pushgateway isn't deployed on HCC clusters yet
     * (RHCLOUD-48965), so instead of pushing, expose the registry on an embedded HTTP
     * server for long enough to be scraped, then let the job exit normally. To be
     * removed once Pushgateway is available on HCC.
     */
    private void exposeMetricsForScraping(CollectorRegistry registry) {
        Optional<Integer> metricsPort = aggregatorConfig.getMetricsPort();
        if (metricsPort.isEmpty()) {
            return;
        }

        try {
            int keepAliveSeconds = aggregatorConfig.getMetricsScrapeKeepAliveSeconds();
            HTTPServer server = new HTTPServer.Builder()
                    .withPort(metricsPort.get())
                    .withRegistry(registry)
                    .withDaemonThreads(true)
                    .build();
            try {
                Log.infof("Exposing %s aggregator metrics for scraping on port %d for %d seconds",
                    getJobType().toLowerCase(), metricsPort.get(), keepAliveSeconds);
                Thread.sleep(keepAliveSeconds * 1000L);
            } finally {
                server.close();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.warnf("Interrupted while exposing %s aggregator metrics for scraping.", getJobType().toLowerCase(), e);
        } catch (Exception e) {
            Log.warnf("Could not expose %s aggregator metrics for scraping.", getJobType().toLowerCase(), e);
        }
    }

    Gauge getPairsProcessed() {
        return pairsProcessed;
    }

    /**
     * Determines if this cluster should run the aggregation job.
     *
     * BACKWARD COMPATIBILITY:
     * - If cluster ID is NOT configured, cluster coordination is disabled - returns true (run)
     * - This allows existing single-cluster deployments to work without changes
     *
     * FAIL-SAFE BEHAVIOR (when cluster ID IS configured):
     * Returns false (skip) in ALL error conditions:
     * - Unleash variant is not enabled or unreachable
     * - Unleash variant has invalid/missing payload
     * - Unleash returns a cluster ID that doesn't match this instance
     *
     * Returns true (run) ONLY if:
     * - Cluster ID is configured AND Unleash variant returns valid data
     * - AND the returned cluster ID exactly matches this instance's CLUSTER_ID
     */
    private boolean shouldRunOnThisCluster() {
        Optional<String> thisClusterId = aggregatorConfig.getClusterId();

        // If no cluster ID configured, cluster coordination is disabled - allow run (backward compatibility)
        if (thisClusterId.isEmpty()) {
            Log.debugf("NOTIFICATIONS_AGGREGATOR_CLUSTER_ID not configured - cluster coordination disabled, proceeding with %s aggregation",
                getJobType().toLowerCase());
            return true;
        }

        Optional<String> activeCluster = aggregatorConfig.getActiveCluster();

        // If Unleash disabled or unreachable, don't run (fail-safe when cluster ID is configured)
        // This includes: Unleash disabled, unreachable, variant disabled, invalid payload
        if (activeCluster.isEmpty()) {
            Log.infof("Unable to determine active cluster from Unleash for %s aggregation - failing safe by not running",
                getJobType().toLowerCase());
            return false;
        }

        // Check if this cluster matches the active cluster
        boolean shouldRun = thisClusterId.get().equals(activeCluster.get());

        if (shouldRun) {
            Log.infof("%s aggregation cluster coordination check passed: this cluster (%s) matches active cluster (%s)",
                getJobType(), thisClusterId.get(), activeCluster.get());
        } else {
            Log.infof("%s aggregation cluster coordination check failed: this cluster (%s) does not match active cluster (%s)",
                getJobType(), thisClusterId.get(), activeCluster.get());
        }

        return shouldRun;
    }
}
