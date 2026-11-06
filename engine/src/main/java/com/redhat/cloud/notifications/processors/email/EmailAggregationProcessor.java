package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.BundleRepository;
import com.redhat.cloud.notifications.db.repositories.EventRepository;
import com.redhat.cloud.notifications.db.repositories.PayloadDetailsRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventAggregationCriterion;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.processors.SystemEndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.email.connector.dto.EmailNotification;
import com.redhat.cloud.notifications.processors.email.connector.dto.RecipientSettings;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.utils.ActionParser;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/*
 * This class needs more cleanup but this will be done later to make the reviews easier.
 * TODO Stop extending SystemEndpointTypeProcessor.
 */
@ApplicationScoped
public class EmailAggregationProcessor extends SystemEndpointTypeProcessor {

    public static final String AGGREGATION_COMMAND_REJECTED_COUNTER_NAME = "aggregation.command.rejected";
    public static final String AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME = "aggregation.command.processed";
    public static final String AGGREGATION_COMMAND_ERROR_COUNTER_NAME = "aggregation.command.error";

    public static final String AGGREGATION_CONSUMED_TIMER_NAME = "aggregation.time.consumed";
    protected static final String TAG_KEY_BUNDLE = "bundle";
    protected static final String TAG_KEY_ORG_ID = "orgid";

    private static final String APPLICATION_SERVICES_APP_NAME = "application-services";
    private static final String PRODUCTS_KEY = "products";
    private static final String PAYLOADS_KEY = "payloads";
    private static final String GLOBAL_RELEASES_NUMBER_KEY = "global_releases_number";

    @Inject
    EmailActorsResolver emailActorsResolver;

    @Inject
    EmailAggregator emailAggregator;

    @Inject
    AggregatedDataTrimmer aggregatedDataTrimmer;

    @Inject
    PayloadDetailsRepository payloadDetailsRepository;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MeterRegistry registry;

    @Inject
    ActionParser actionParser;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    EventRepository eventRepository;

    @Inject
    BundleRepository bundleRepository;

    @Inject
    Environment environment;

    private Counter rejectedAggregationCommandCount;
    private Counter processedAggregationCommandCount;
    private Counter failedAggregationCommandCount;

    @Inject
    ConnectorSender connectorSender;

    @PostConstruct
    void postConstruct() {
        rejectedAggregationCommandCount = registry.counter(AGGREGATION_COMMAND_REJECTED_COUNTER_NAME);
        processedAggregationCommandCount = registry.counter(AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME);
        failedAggregationCommandCount = registry.counter(AGGREGATION_COMMAND_ERROR_COUNTER_NAME);
    }

    @Override
    public void process(Event event, List<Endpoint> endpoints) {
        throw new UnsupportedOperationException("No longer used");
    }

    public void processAggregation(Event event, List<Endpoint> endpoints) {

        List<AggregationCommand> aggregationCommands = new ArrayList<>();
        Timer.Sample consumedTimer = Timer.start(registry);
        String bundle = "unknown";

        try {
            try {
                Action action = actionParser.fromJsonString(event.getPayload());
                Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

                for (com.redhat.cloud.notifications.ingress.Event actionEvent : action.getEvents()) {
                    try {
                        AggregationCommand command = objectMapper.convertValue(actionEvent.getPayload().getAdditionalProperties(), AggregationCommand.class);
                        try {
                            JsonObject aggregationKey = new JsonObject(actionEvent.getPayload().getAdditionalProperties()).getJsonObject("aggregationKey");
                            EventAggregationCriterion key = objectMapper.convertValue(aggregationKey, EventAggregationCriterion.class);
                            Set<ConstraintViolation<EventAggregationCriterion>> constraintViolations = validator.validate(key);
                            if (constraintViolations.isEmpty()) {
                                command.setAggregationKey(key);
                            }
                        } catch (Exception e) {
                            Log.error("Kafka aggregation payload parsing key failed to be cast as 'EventAggregationCriteria' for event: " + event.getId() + ", aggregation: " + actionEvent.toString(), e);
                        }
                        if (command.getAggregationKey() == null) {
                            Log.warnf("Skipping aggregation command with null key for event: %s", event.getId());
                            rejectedAggregationCommandCount.increment();
                            continue;
                        }
                        aggregationCommands.add(command);
                    } catch (Exception e) {
                        Log.error("Kafka aggregation payload parsing failed for event: " + event.getId() + ", aggregation: " + actionEvent.toString(), e);
                        rejectedAggregationCommandCount.increment();
                    }
                }
            } catch (Exception e) {
                Log.error("Kafka aggregation payload parsing failed for event " + event.getId(), e);
                rejectedAggregationCommandCount.increment();
                return;
            }

            if (aggregationCommands.isEmpty()) {
                Log.warnf("No valid aggregation commands parsed from event %s", event.getId());
                return;
            }

            bundle = aggregationCommands.get(0).getAggregationKey().getBundle();

            processedAggregationCommandCount.increment(aggregationCommands.size());
            try {
                processBundleAggregation(aggregationCommands, event, endpoints);
            } catch (Exception e) {
                Log.warn("Error while processing aggregation", e);
                failedAggregationCommandCount.increment();
            }
        } finally {
            consumedTimer.stop(registry.timer(
                AGGREGATION_CONSUMED_TIMER_NAME,
                TAG_KEY_BUNDLE, bundle,
                TAG_KEY_ORG_ID, event.getOrgId()
            ));
        }
    }

    private void processBundleAggregation(List<AggregationCommand> aggregationCommands, Event aggregatorEvent, List<Endpoint> endpoints) {
        final String bundleName = aggregationCommands.get(0).getAggregationKey().getBundle();
        final SubscriptionType subscriptionType = aggregationCommands.get(0).getSubscriptionType();
        // Patch event display name for event log rendering
        Bundle bundle = bundleRepository.getBundle(bundleName)
                .orElseThrow(() -> new IllegalArgumentException("Bundle not found: " + bundleName));

        String eventTypeDisplayName = String.format("%s - %s",
            aggregatorEvent.getEventTypeDisplayName(),
            bundle.getDisplayName());
        eventRepository.updateEventDisplayName(aggregatorEvent.getId(), eventTypeDisplayName);

        Map<User, List<ApplicationAggregatedData>> userData = aggregateByApplication(aggregationCommands);

        Map<List<ApplicationAggregatedData>, Set<User>> usersWithSameData = groupUsersByAggregatedData(userData);

        sendAggregatedEmails(usersWithSameData, bundle, aggregatorEvent, endpoints, subscriptionType);
    }

    /*
     * Aggregates event data per application and collects results per user.
     * Each application's aggregation command is processed independently; errors in one
     * application do not prevent others from being aggregated.
     */
    private Map<User, List<ApplicationAggregatedData>> aggregateByApplication(List<AggregationCommand> aggregationCommands) {
        Map<User, List<ApplicationAggregatedData>> userData = new HashMap<>();
        // Scoped to this one bundle's processing, never a class field: an offloaded payload_details row
        // holds the whole bundle's combined data, so multiple commands below commonly share the same
        // aggregatedDataPayloadDetailsId and would otherwise each re-fetch and re-parse the same row.
        Map<UUID, JsonObject> offloadedPayloadCache = new HashMap<>();

        for (AggregationCommand aggregationCommand : aggregationCommands) {
            Log.debugf("Processing aggregation command: %s", aggregationCommand);

            try {
                Application app = applicationRepository.getApplication(
                        aggregationCommand.getAggregationKey().getBundle(),
                        aggregationCommand.getAggregationKey().getApplication())
                    .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Application not found: %s/%s",
                        aggregationCommand.getAggregationKey().getBundle(),
                        aggregationCommand.getAggregationKey().getApplication())));

                Map<User, Map<String, Object>> aggregatedByUser = getAggregatedByUser(aggregationCommand, app.getId(), offloadedPayloadCache);

                aggregatedByUser.forEach((user, data) ->
                    userData.computeIfAbsent(user, unused -> new ArrayList<>())
                        .add(new ApplicationAggregatedData(data, aggregationCommand.getAggregationKey().getApplication())));
            } catch (Exception ex) {
                Log.error("Error processing " + aggregationCommand.getAggregationKey(), ex);
            }
        }
        return userData;
    }

    /*
     * Prefers aggregator's pre-folded digest content (fold-only handoff) when present on the command,
     * trimming it per recipient instead of re-scanning raw events. Falls back to the legacy per-recipient
     * DB-paging fold (EmailAggregator.getAggregated()) when the command carries no pre-folded data --
     * e.g. the org isn't flagged into the fold-only path yet, or no fold-only aggregator exists yet for
     * this app.
     */
    private Map<User, Map<String, Object>> getAggregatedByUser(AggregationCommand aggregationCommand, UUID appId, Map<UUID, JsonObject> offloadedPayloadCache) {
        Map<String, Object> aggregatedData = aggregationCommand.getAggregatedData();
        if (aggregatedData == null && aggregationCommand.getAggregatedDataPayloadDetailsId() != null) {
            aggregatedData = fetchOffloadedAggregatedData(aggregationCommand, offloadedPayloadCache);
        }

        if (aggregatedData != null) {
            Map<User, Map<String, Object>> trimmed = aggregatedDataTrimmer.trim(
                aggregationCommand.getOrgId(), aggregationCommand.getAggregationKey(),
                aggregationCommand.getSubscriptionType(), aggregatedData);
            if (APPLICATION_SERVICES_APP_NAME.equals(aggregationCommand.getAggregationKey().getApplication())) {
                trimmed.values().forEach(this::recomputeApplicationServicesGlobalReleasesNumber);
            }
            return trimmed;
        }

        return emailAggregator.getAggregated(
            appId, aggregationCommand.getAggregationKey(),
            aggregationCommand.getSubscriptionType(), aggregationCommand.getStart(), aggregationCommand.getEnd());
    }

    /*
     * Aggregator's fold-only pipeline doesn't track global_releases_number at all (see
     * ApplicationServicesEmailPayloadAggregator in the aggregator module): it folds every recipient's
     * events into one superset, so any running total it kept would already be wrong for every recipient
     * whose trimmed view differs from the full digest. The only place the correct per-recipient count can
     * be known is right here, after trimming, so it's recomputed from the already-trimmed products map --
     * exactly what the legacy per-recipient aggregator's own running counter amounted to, just computed
     * once at the end instead of incrementally during the fold.
     */
    @SuppressWarnings("unchecked")
    private void recomputeApplicationServicesGlobalReleasesNumber(Map<String, Object> dataByApp) {
        if (!(dataByApp.get(APPLICATION_SERVICES_APP_NAME) instanceof Map<?, ?> rawAppData)) {
            return;
        }
        Map<String, Object> applicationServices = (Map<String, Object>) rawAppData;
        if (!(applicationServices.get(PRODUCTS_KEY) instanceof Map<?, ?> products)) {
            return;
        }
        int releasesCount = 0;
        for (Object product : products.values()) {
            if (product instanceof Map<?, ?> productData && productData.get(PAYLOADS_KEY) instanceof List<?> payloads) {
                releasesCount += payloads.size();
            }
        }
        applicationServices.put(GLOBAL_RELEASES_NUMBER_KEY, releasesCount);
    }

    /*
     * The offloaded row holds the whole bundle's combined pre-folded content (every app the aggregator
     * run folded for this org+bundle), keyed by application name -- see aggregator's
     * AggregatedDataOffloader.offload()/DailyEmailAggregationJob.buildAction(). offloadedPayloadCache
     * (scoped to one aggregateByApplication() call) means the row is fetched and parsed once even when
     * several apps in the bundle share the same aggregatedDataPayloadDetailsId; computeIfAbsent() leaves
     * the cache untouched on failure, so a missing row still only fails the app(s) that need it, each
     * caught individually by aggregateByApplication()'s per-command try/catch, same as before this cache
     * existed.
     */
    private Map<String, Object> fetchOffloadedAggregatedData(AggregationCommand aggregationCommand, Map<UUID, JsonObject> offloadedPayloadCache) {
        UUID payloadDetailsId = aggregationCommand.getAggregatedDataPayloadDetailsId();
        JsonObject allApplications = offloadedPayloadCache.computeIfAbsent(payloadDetailsId, id ->
            new JsonObject(payloadDetailsRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("No payload_details row found for offloaded aggregated data reference: " + id))
                .getContents()));

        String applicationName = aggregationCommand.getAggregationKey().getApplication();
        JsonObject applicationData = allApplications.getJsonObject(applicationName);
        if (applicationData == null) {
            throw new IllegalStateException("Offloaded aggregated data reference " + payloadDetailsId
                + " has no entry for application " + applicationName);
        }
        return applicationData.getMap();
    }

    /*
     * Groups users who have identical aggregated data so they can share a single rendered email.
     */
    private Map<List<ApplicationAggregatedData>, Set<User>> groupUsersByAggregatedData(
            Map<User, List<ApplicationAggregatedData>> userData) {
        Map<List<ApplicationAggregatedData>, Set<User>> grouped = userData.keySet().stream()
            .collect(Collectors.groupingBy(userData::get, Collectors.toSet()));
        Log.debugf("Grouped %d users into %d aggregated-data groups", userData.size(), grouped.size());
        return grouped;
    }

    /*
     * Sends one aggregated email per group of users with identical data.
     * Recipients are determined at an earlier stage (see EmailAggregator) using the
     * recipients-resolver app and the subscription records from the database.
     */
    private void sendAggregatedEmails(Map<List<ApplicationAggregatedData>, Set<User>> usersWithSameData,
                                      Bundle bundle, Event aggregatorEvent, List<Endpoint> endpoints, SubscriptionType subscriptionType) {

        usersWithSameData.forEach((appDataList, users) -> {
            Set<String> recipientsUsernames = users.stream().map(User::getUsername).collect(Collectors.toSet());
            Set<RecipientSettings> recipientSettings = extractAndTransformRecipientSettings(aggregatorEvent, endpoints);

            Map<String, Object> templateContext = buildFullConnectorTemplateContext(appDataList, bundle);

            /*
             * The recipients are determined at an earlier stage (see EmailAggregator) using the
             * recipients-resolver app and the subscription records from the database.
             * The subscribedByDefault value below simply means that recipients-resolver will consider
             * the subscribers passed in the request as the recipients candidates of the aggregation email.
             * Because the recipient list has already been computed using authz constraints, we don't
             * need to pass them again for the aggregated email.
             */
            final EmailNotification emailNotification = new EmailNotification(
                emailActorsResolver.getEmailSender(aggregatorEvent),
                aggregatorEvent.getOrgId(),
                recipientSettings,
                recipientsUsernames,
                Collections.emptySet(),
                false,
                null,
                templateContext,
                true,
                subscriptionType
            );

            // We need to send an endpoint to connectorSender only to find the right connector
            // and create a notifications history entry.
            // Endpoint restrictions will be evaluated from payload content (recipientSettings)
            connectorSender.send(aggregatorEvent, endpoints.getFirst(), JsonObject.mapFrom(emailNotification));
            Log.debugf("Sent aggregation email notification to connector: %s", emailNotification);
        });
    }

    private Map<String, Object> buildFullConnectorTemplateContext(List<ApplicationAggregatedData> action, Bundle bundle) {
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("environment", environment);
        additionalContext.put("bundle_name", bundle.getName());
        additionalContext.put("bundle_display_name", bundle.getDisplayName());
        additionalContext.put("application_aggregated_data_list", new JsonArray(objectMapper.convertValue(action, List.class)));
        return additionalContext;
    }

    public class ApplicationAggregatedData {

        @JsonProperty("aggregated_data")
        Map<String, Object> aggregatedData;

        @JsonProperty("app_name")
        String appName;

        public ApplicationAggregatedData(Map<String, Object> aggregatedData, String appName) {
            this.aggregatedData = aggregatedData;
            this.appName = appName;
        }

        public Map<String, Object> getAggregatedData() {
            return aggregatedData;
        }

        public String getAppName() {
            return appName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ApplicationAggregatedData that = (ApplicationAggregatedData) o;
            return Objects.equals(aggregatedData, that.aggregatedData) && Objects.equals(appName, that.appName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(aggregatedData, appName);
        }
    }
}
