package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.BundleRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.EventRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventAggregationCriterion;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.processors.SystemEndpointTypeProcessor;
import com.redhat.cloud.notifications.processors.email.connector.dto.EmailNotification;
import com.redhat.cloud.notifications.processors.email.connector.dto.RecipientSettings;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
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
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static jakarta.transaction.Transactional.TxType.REQUIRES_NEW;

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

    @Inject
    EmailActorsResolver emailActorsResolver;

    @Inject
    EmailAggregator emailAggregator;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MeterRegistry registry;

    @Inject
    com.redhat.cloud.notifications.qute.templates.TemplateService commonQuteTemplateService;

    @Inject
    EngineConfig engineConfig;

    @Inject
    ActionParser actionParser;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    EventRepository eventRepository;

    @Inject
    BundleRepository bundleRepository;

    // This executor is used to run a task asynchronously using a worker thread from a threads pool managed by Quarkus.
    @Inject
    @AggregationManagedExecutor
    ManagedExecutor managedExecutor;

    @Inject
    Instance<AsyncAggregation> asyncAggregations;

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

    public void processAggregation(Event event) {
        if (engineConfig.isAsyncAggregationEnabled()) {
            /*
             * The aggregation process is long-running task. To avoid blocking the thread used to consume
             * Kafka messages from the ingress topic, we're performing the aggregation from a worker thread.
             */
            AsyncAggregation asyncAggregation = asyncAggregations.get();
            asyncAggregation.setEvent(event);
            managedExecutor.runAsync(asyncAggregation)
                    .thenRun(() -> {
                        /*
                         * When a @Dependent bean is injected into an @ApplicationScoped bean using Instance<T>,
                         * the dependent bean has to be destroyed manually when it's no longer needed. Otherwise,
                         * instances of the dependent bean accumulate in memory, causing a memory leak.
                         */
                        asyncAggregations.destroy(asyncAggregation);
                    });
        } else {
            processAggregationSync(event);
        }
    }

    @Transactional(REQUIRES_NEW)
    public void processAggregationAsync(Event event) {
        processAggregationSync(event);
    }

    public void processAggregationSync(Event event) {

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
                processBundleAggregation(aggregationCommands, event);
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

    private void processBundleAggregation(List<AggregationCommand> aggregationCommands, Event aggregatorEvent) {
        final String bundleName = aggregationCommands.get(0).getAggregationKey().getBundle();
        // Patch event display name for event log rendering
        Bundle bundle = bundleRepository.getBundle(bundleName)
                .orElseThrow(() -> new IllegalArgumentException("Bundle not found: " + bundleName));

        String eventTypeDisplayName = String.format("%s - %s",
            aggregatorEvent.getEventTypeDisplayName(),
            bundle.getDisplayName());
        eventRepository.updateEventDisplayName(aggregatorEvent.getId(), eventTypeDisplayName);

        Endpoint endpoint = endpointRepository.getOrCreateDefaultSystemSubscription(null, aggregatorEvent.getOrgId(), EndpointType.EMAIL_SUBSCRIPTION);

        Map<User, List<ApplicationAggregatedData>> userData = aggregateByApplication(aggregationCommands);

        Map<List<ApplicationAggregatedData>, Set<User>> usersWithSameData = groupUsersByAggregatedData(userData);

        sendAggregatedEmails(usersWithSameData, bundle, aggregatorEvent, endpoint);
    }

    /*
     * Aggregates event data per application and collects results per user.
     * Each application's aggregation command is processed independently; errors in one
     * application do not prevent others from being aggregated.
     */
    private Map<User, List<ApplicationAggregatedData>> aggregateByApplication(List<AggregationCommand> aggregationCommands) {
        Map<User, List<ApplicationAggregatedData>> userData = new HashMap<>();

        for (AggregationCommand cmd : aggregationCommands) {
            Log.debugf("Processing aggregation command: %s", cmd);

            try {
                Application app = applicationRepository.getApplication(
                        cmd.getAggregationKey().getBundle(),
                        cmd.getAggregationKey().getApplication())
                    .orElseThrow(() -> new IllegalArgumentException(String.format(
                        "Application not found: %s/%s",
                        cmd.getAggregationKey().getBundle(),
                        cmd.getAggregationKey().getApplication())));

                Map<User, Map<String, Object>> aggregatedByUser = emailAggregator.getAggregated(
                    app.getId(), cmd.getAggregationKey(),
                    cmd.getSubscriptionType(), cmd.getStart(), cmd.getEnd());

                aggregatedByUser.forEach((user, data) ->
                    userData.computeIfAbsent(user, unused -> new ArrayList<>())
                        .add(new ApplicationAggregatedData(data, cmd.getAggregationKey().getApplication())));
            } catch (Exception ex) {
                Log.error("Error processing " + cmd.getAggregationKey(), ex);
            }
        }
        return userData;
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
                                      Bundle bundle, Event aggregatorEvent, Endpoint endpoint) {
        Map<String, Object> mapDataTitle = Map.of("source", Map.of("bundle", Map.of("display_name", bundle.getDisplayName())));
        TemplateDefinition templateTitleDefinition = new TemplateDefinition(IntegrationType.EMAIL_DAILY_DIGEST_BUNDLE_AGGREGATION_TITLE, null, null, null);

        usersWithSameData.forEach((appDataList, users) -> {
            Set<String> recipientsUsernames = users.stream().map(User::getUsername).collect(Collectors.toSet());
            Set<RecipientSettings> recipientSettings = extractAndTransformRecipientSettings(aggregatorEvent, List.of(endpoint));

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
                this.emailActorsResolver.getEmailSender(aggregatorEvent),
                aggregatorEvent.getOrgId(),
                recipientSettings,
                recipientsUsernames,
                Collections.emptySet(),
                false,
                null,
                templateContext,
                true
            );

            connectorSender.send(aggregatorEvent, endpoint, JsonObject.mapFrom(emailNotification));
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
