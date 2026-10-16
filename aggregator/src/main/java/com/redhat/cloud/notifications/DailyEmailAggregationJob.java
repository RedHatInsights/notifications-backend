package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.config.AggregatorConfig;
import com.redhat.cloud.notifications.db.AggregationOrgConfigRepository;
import com.redhat.cloud.notifications.db.EmailAggregationRepository;
import com.redhat.cloud.notifications.db.EndpointRepository;
import com.redhat.cloud.notifications.db.EventRepository;
import com.redhat.cloud.notifications.db.EventTypeRepository;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.processors.email.EmailAggregationProcessor;
import io.micrometer.core.instrument.MeterRegistry;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.time.ZoneOffset.UTC;

@ApplicationScoped
public class DailyEmailAggregationJob {

    public static final String EGRESS_CHANNEL = "egress";
    public static final String BUNDLE_NAME = "console";
    public static final String APP_NAME = "notifications";
    public static final String EVENT_TYPE_NAME = "aggregation";

    /**
     * Counts, once per org per run, which pipeline an org's aggregation was routed
     * through -- gives direct visibility into canary-rollout progress (how many orgs
     * are on which path right now) without cross-referencing Unleash's admin UI
     * against job logs.
     */
    public static final String AGGREGATION_PIPELINE_PATH_COUNTER_NAME = "aggregator.pipeline.path";
    public static final String TAG_KEY_PATH = "path";
    public static final String PATH_LEGACY = "legacy";
    public static final String PATH_DIRECT_DISPATCH = "direct-dispatch";

    /**
     * Counts bundle-group dispatch attempts (either path) that failed with an
     * uncaught exception. Kept from propagating out of the per-org/per-bundle loop
     * so that one org's failure doesn't abort the whole cron run -- which would skip
     * {@code updateLastCronJobRunAccordingOrgPref} for every org, including ones
     * already processed successfully earlier in the same run.
     */
    public static final String AGGREGATION_BUNDLE_GROUP_ERROR_COUNTER_NAME = "aggregator.pipeline.bundle.group.error";

    @Inject
    AggregatorConfig aggregatorConfig;

    @Inject
    EmailAggregationRepository emailAggregationResources;

    @Inject
    AggregationOrgConfigRepository aggregationOrgConfigRepository;

    @Inject
    EventTypeRepository eventTypeRepository;

    @Inject
    EventRepository eventRepository;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    EmailAggregationProcessor emailAggregationProcessor;

    @Inject
    MeterRegistry meterRegistry;

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
                .forEach((orgId, orgCommands) -> {
                    boolean directDispatch = aggregatorConfig.isAggregatorDirectDispatchEnabled(orgId);
                    String path = directDispatch ? PATH_DIRECT_DISPATCH : PATH_LEGACY;

                    // Rollout-progress compliance log + metric - SEC-MON-REQ-1 (EOI-5 process_status)
                    Log.infof("[action: SELECT_AGGREGATION_PATH][resource_type: daily_email_aggregation][principal: system][outcome: success] Org %s routed to %s path", orgId, path);
                    meterRegistry.counter(AGGREGATION_PIPELINE_PATH_COUNTER_NAME, TAG_KEY_PATH, path).increment();

                    orgCommands.stream()
                        .collect(Collectors.groupingBy(AggregationCommand::getBundleId))
                        .values()
                        .forEach(bundleCommands -> {
                            try {
                                if (directDispatch) {
                                    processDirectDispatch(bundleCommands);
                                } else {
                                    sendIt(bundleCommands);
                                }
                            } catch (Exception ex) {
                                Log.errorf(ex, "[org_id: %s] Error processing aggregation on the %s path, skipping this bundle group", orgId, path);
                                meterRegistry.counter(AGGREGATION_BUNDLE_GROUP_ERROR_COUNTER_NAME, TAG_KEY_PATH, path).increment();
                            }
                        });
                });

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

    private Action buildAction(List<AggregationCommand> aggregationCommands) {
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

        return new Action.ActionBuilder()
            .withId(UUID.randomUUID())
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
    }

    private void sendIt(List<AggregationCommand> aggregationCommands) {
        Action action = buildAction(aggregationCommands);

        String encodedAction = Parser.encode(action);
        Log.infof("Encoded Payload: %s", encodedAction);
        Message<String> message = Message.of(encodedAction);
        emitterIngress.send(message);
    }

    /**
     * Per-org direct-dispatch counterpart to {@link #sendIt}: instead of publishing the
     * {@code AggregationCommand} trigger to {@code ingress} for the engine to pick up,
     * builds and persists the same trigger {@link Event} directly, then runs the ported
     * data-aggregation/recipient-resolution/dispatch pipeline in this process.
     */
    private void processDirectDispatch(List<AggregationCommand> aggregationCommands) {
        String orgId = aggregationCommands.get(0).getOrgId();
        Action action = buildAction(aggregationCommands);

        EventType eventType = eventTypeRepository.getEventType(BUNDLE_NAME, APP_NAME, EVENT_TYPE_NAME);
        com.redhat.cloud.notifications.models.Event aggregatorEvent = new com.redhat.cloud.notifications.models.Event(
            eventType, Parser.encode(action), new EventWrapperAction(action), Optional.empty(), null);
        eventRepository.create(aggregatorEvent);

        // Direct-dispatch trigger Event persisted - SEC-MON-REQ-1 compliance (EOI-3 admin_action, EOI-5 process_status)
        Log.infof("[action: CREATE_EVENT][resource_type: aggregation_event][principal: system][outcome: success] Persisted direct-dispatch aggregation trigger event [org_id: %s, event_id: %s]",
            orgId, aggregatorEvent.getId());

        Set<UUID> applicationIds = aggregationCommands.stream()
            .map(cmd -> cmd.getAggregationKey().getApplicationId())
            .collect(Collectors.toSet());

        List<Endpoint> endpoints = endpointRepository.getTargetEmailEndpointsForAggregation(orgId, applicationIds);
        endpoints.removeIf(endpoint -> {
            // Default endpoints (orgId is null) used as system integrations for all orgs can't be blacklisted.
            if (null != endpoint.getOrgId() && aggregatorConfig.isBlacklistedEndpoint(endpoint.getId())) {
                Log.infof("Org: %s, skipping endpoint: %s because it was blacklisted", endpoint.getOrgId(), endpoint.getId());
                return true;
            }
            return false;
        });
        if (endpoints.isEmpty()) {
            Log.warnf("[org_id: %s] No target email endpoints found for aggregation, skipping", orgId);
            return;
        }

        emailAggregationProcessor.processBundleAggregation(aggregationCommands, aggregatorEvent, endpoints);
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
