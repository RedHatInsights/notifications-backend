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
import com.redhat.cloud.notifications.processors.email.AggregatedDataOffloader;
import com.redhat.cloud.notifications.processors.email.EmailDataAggregator;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.HTTPServer;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

    @Inject
    EmailDataAggregator emailDataAggregator;

    @Inject
    AggregatedDataOffloader aggregatedDataOffloader;

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
        // Cluster coordination check - fail-safe if Unleash unreachable
        if (!shouldRunOnThisCluster()) {
            Log.infof("Aggregator run skipped on %s cluster due to Unleash flag",
                aggregatorConfig.getClusterId().orElse("unknown"));
            return; // Exit gracefully with success
        }

        // Job start - SEC-MON-REQ-1 compliance (EOI-3 admin_action, EOI-5 process_status)
        Log.infof("[action: RUN_JOB][resource_type: daily_email_aggregation][principal: system][outcome: started] Daily email aggregation job started");

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

            aggregationOrgConfigRepository.createMissingDefaultConfigurationBasedOnEvent(defaultDailyDigestTime);

            List<AggregationCommand> aggregationCommands = processAggregateEmailsWithOrgPref(now, registry);
            Log.infof("found %s commands", aggregationCommands.size());
            Log.debugf("Aggregation commands: %s", aggregationCommands);

            aggregationCommands.stream()
                .collect(Collectors.groupingBy(AggregationCommand::getOrgId))
                .values()
                .forEach(val -> val.stream()
                    .collect(Collectors.groupingBy(AggregationCommand::getBundleId))
                    .values()
                    .forEach(this::process));

            List<String> orgIdsToUpdate = aggregationCommands.stream().map(aggregationCommand -> aggregationCommand.getOrgId()).collect(Collectors.toList());
            Log.debugf("Found following org IDs to update: %s", orgIdsToUpdate);

            aggregationOrgConfigRepository.updateLastCronJobRunAccordingOrgPref(orgIdsToUpdate, now);

            Gauge lastSuccess = Gauge
                    .build()
                    .name("aggregator_job_last_success")
                    .help("Last time the aggregator job succeeded.")
                    .register(registry);
            lastSuccess.setToCurrentTime();

            // Job completion - SEC-MON-REQ-1 compliance (EOI-3 admin_action, EOI-5 process_status)
            Log.infof("[action: RUN_JOB][resource_type: daily_email_aggregation][principal: system][outcome: success] Daily email aggregation job completed successfully");
        } catch (Exception ex) {
            // Job failure - SEC-MON-REQ-1 compliance (EOI-3 admin_action, EOI-11 warnings_or_errors)
            Log.errorf("[action: RUN_JOB][resource_type: daily_email_aggregation][principal: system][outcome: failure][reason: job_failed] Daily aggregation job failed");
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

    private void pushMetricsToGateway(CollectorRegistry registry) {
        PushGateway pg = new PushGateway(prometheusPushGatewayUrl);
        try {
            pg.pushAdd(registry, "aggregator_job");
        } catch (IOException e) {
            Log.warn("Could not push metrics to Prometheus Pushgateway.", e);
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
                Log.infof("Exposing metrics for scraping on port %d for %d seconds", metricsPort.get(), keepAliveSeconds);
                Thread.sleep(keepAliveSeconds * 1000L);
            } finally {
                server.close();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.warn("Interrupted while exposing metrics for scraping.", e);
        } catch (Exception e) {
            Log.warn("Could not expose metrics for scraping.", e);
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

        List<AggregationCommand> pendingAggregationCommands = emailAggregationResources.getApplicationsWithPendingAggregationAccordingOrgPref(endTime);

        pairsProcessed = Gauge
                .build()
                .name("aggregator_job_orgid_application_pairs_processed")
                .help("Number of orgId and application pairs processed.")
                .register(registry);
        pairsProcessed.set(pendingAggregationCommands.size());

        return pendingAggregationCommands;
    }

    private void process(List<AggregationCommand> aggregationCommands) {
        String orgId = aggregationCommands.get(0).getOrgId();
        Map<String, Map<String, Object>> aggregatedDataByApplication = preAggregate(orgId, aggregationCommands);
        sendIt(orgId, aggregationCommands, aggregatedDataByApplication);
    }

    private Map<String, Map<String, Object>> preAggregate(String orgId, List<AggregationCommand> aggregationCommands) {
        Map<String, Map<String, Object>> aggregatedDataByApplication = new LinkedHashMap<>();
        if (!aggregatorConfig.isAggregatorEventPreprocessingEnabled(orgId)) {
            return aggregatedDataByApplication;
        }

        for (AggregationCommand aggregationCommand : aggregationCommands) {
            // A folding failure for one app must not block the whole digest: engine already
            // falls back to folding that app's raw events itself when aggregatedData is absent.
            try {
                Map<String, Object> aggregatedData = emailDataAggregator.getAggregatedData(
                    aggregationCommand.getAggregationKey(), aggregationCommand.getStart(), aggregationCommand.getEnd());
                if (aggregatedData != null) {
                    aggregatedDataByApplication.put(aggregationCommand.getAggregationKey().getApplication(), aggregatedData);
                }
            } catch (Exception ex) {
                Log.errorf(ex, "[org_id: %s] Failed to pre-fold data for %s, engine will fold the raw events itself",
                    orgId, aggregationCommand.getAggregationKey());
            }
        }

        return aggregatedDataByApplication;
    }

    private void sendIt(String orgId, List<AggregationCommand> aggregationCommands, Map<String, Map<String, Object>> aggregatedDataByApplication) {
        UUID actionId = UUID.randomUUID();
        LocalDateTime timestamp = LocalDateTime.now(UTC);

        String encodedAction = Parser.encode(buildAction(aggregationCommands, aggregatedDataByApplication, null, actionId, timestamp));

        if (!aggregatedDataByApplication.isEmpty() && aggregatedDataOffloader.exceedsMaxRequestSize(encodedAction)) {
            encodedAction = offloadOversizedPayload(orgId, aggregationCommands, aggregatedDataByApplication, actionId, timestamp);
        }

        Log.infof("Encoded payload ready, length=%d chars", encodedAction.length());
        Message<String> message = Message.of(encodedAction);
        emitterIngress.send(message);
    }

    /**
     * Offloads an oversized pre-folded digest payload to {@code payload_details} and re-encodes the
     * Action with a reference in place of the inline data. An offload failure must not abort the whole
     * cron run: it falls back to sending no pre-folded data at all for this org/bundle, same as if
     * folding itself had failed in {@link #preAggregate}.
     */
    private String offloadOversizedPayload(String orgId, List<AggregationCommand> aggregationCommands,
                                            Map<String, Map<String, Object>> aggregatedDataByApplication, UUID actionId, LocalDateTime timestamp) {
        try {
            AggregationCommand anyFoldedCommand = aggregationCommands.stream()
                .filter(cmd -> aggregatedDataByApplication.containsKey(cmd.getAggregationKey().getApplication()))
                .findFirst()
                .orElseThrow();
            UUID payloadDetailsId = aggregatedDataOffloader.offload(aggregatedDataByApplication,
                anyFoldedCommand.getAggregationKey(), anyFoldedCommand.getStart(), anyFoldedCommand.getEnd());
            Log.infof("[org_id: %s] Pre-folded digest payload for bundle was too large to send inline, offloaded to payload_details as %s",
                orgId, payloadDetailsId);
            return Parser.encode(buildAction(aggregationCommands, aggregatedDataByApplication, payloadDetailsId, actionId, timestamp));
        } catch (Exception ex) {
            Log.errorf(ex, "[org_id: %s] Failed to offload oversized pre-folded digest payload, engine will fold the raw events itself",
                orgId);
            aggregatedDataByApplication.clear();
            return Parser.encode(buildAction(aggregationCommands, aggregatedDataByApplication, null, actionId, timestamp));
        }
    }

    /**
     * Builds the Kafka {@link Action} for one org+bundle group. Unconditionally sets both
     * {@link AggregationCommand#setAggregatedData}/{@link AggregationCommand#setAggregatedDataPayloadDetailsId}
     * on every command (clearing whichever doesn't apply) rather than only touching apps present in
     * {@code aggregatedDataByApplication} -- this keeps the method a pure function of its arguments
     * regardless of what a previous call against the same (mutable) {@code aggregationCommands} set,
     * which matters for the offload/clear-all-and-retry fallback path in {@link #offloadOversizedPayload}.
     */
    private Action buildAction(List<AggregationCommand> aggregationCommands, Map<String, Map<String, Object>> aggregatedDataByApplication,
                                UUID offloadedPayloadDetailsId, UUID actionId, LocalDateTime timestamp) {
        List<Event> eventList = new ArrayList<>();
        for (AggregationCommand aggregationCommand : aggregationCommands) {
            String applicationName = aggregationCommand.getAggregationKey().getApplication();
            Map<String, Object> aggregatedData = aggregatedDataByApplication.get(applicationName);
            if (aggregatedData == null) {
                aggregationCommand.setAggregatedData(null);
                aggregationCommand.setAggregatedDataPayloadDetailsId(null);
            } else if (offloadedPayloadDetailsId != null) {
                aggregationCommand.setAggregatedData(null);
                aggregationCommand.setAggregatedDataPayloadDetailsId(offloadedPayloadDetailsId);
            } else {
                aggregationCommand.setAggregatedData(aggregatedData);
                aggregationCommand.setAggregatedDataPayloadDetailsId(null);
            }

            Payload.PayloadBuilder payloadBuilder = new Payload.PayloadBuilder();
            Map<String, Object> payload = JsonObject.mapFrom(aggregationCommand).getMap();
            payload.forEach(payloadBuilder::withAdditionalProperty);

            eventList.add(new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(payloadBuilder.build())
                .build());
        }

        return new Action.ActionBuilder()
            .withId(actionId)
            .withBundle(BUNDLE_NAME)
            .withApplication(APP_NAME)
            .withEventType(EVENT_TYPE_NAME)
            .withOrgId(aggregationCommands.get(0).getOrgId())
            .withTimestamp(timestamp)
            .withEvents(eventList)
            .withContext(new Context.ContextBuilder()
                .withAdditionalProperty("single_daily_digest_enabled", true)
                .build())
            .build();
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
            Log.debug("NOTIFICATIONS_AGGREGATOR_CLUSTER_ID not configured - cluster coordination disabled, proceeding with aggregation");
            return true;
        }

        Optional<String> activeCluster = aggregatorConfig.getActiveCluster();

        // If Unleash disabled or unreachable, don't run (fail-safe when cluster ID is configured)
        // This includes: Unleash disabled, unreachable, variant disabled, invalid payload
        if (activeCluster.isEmpty()) {
            Log.info("Unable to determine active cluster from Unleash - failing safe by not running");
            return false;
        }

        // Check if this cluster matches the active cluster
        boolean shouldRun = thisClusterId.get().equals(activeCluster.get());

        if (shouldRun) {
            Log.infof("Cluster coordination check passed: this cluster (%s) matches active cluster (%s)",
                thisClusterId.get(), activeCluster.get());
        } else {
            Log.infof("Cluster coordination check failed: this cluster (%s) does not match active cluster (%s)",
                thisClusterId.get(), activeCluster.get());
        }

        return shouldRun;
    }

    // For automatic tests purpose
    protected void setDefaultDailyDigestTime(LocalTime defaultDailyDigestTime) {
        this.defaultDailyDigestTime = defaultDailyDigestTime;
    }
}
