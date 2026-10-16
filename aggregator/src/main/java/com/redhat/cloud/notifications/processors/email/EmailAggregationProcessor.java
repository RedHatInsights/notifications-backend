package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.db.ApplicationRepository;
import com.redhat.cloud.notifications.db.BundleRepository;
import com.redhat.cloud.notifications.db.EventRepository;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.processors.ConnectorSender;
import com.redhat.cloud.notifications.processors.email.connector.dto.EmailNotification;
import com.redhat.cloud.notifications.processors.email.connector.dto.RecipientSettings;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.request.ActionRecipientSettings;
import com.redhat.cloud.notifications.recipients.request.EndpointRecipientSettings;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * In-process counterpart to engine's {@code EmailAggregationProcessor}, used
 * when an org's aggregation is flag-gated to run fully inside {@code aggregator}
 * instead of publishing an {@code AggregationCommand} trigger to {@code ingress}.
 * Unlike engine's version, this class has no JSON-parsing prologue to recover
 * from -- the caller already holds typed {@link AggregationCommand}s in memory.
 */
@ApplicationScoped
public class EmailAggregationProcessor {

    public static final String AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME = "aggregation.inprocess.command.processed";
    public static final String AGGREGATION_COMMAND_ERROR_COUNTER_NAME = "aggregation.inprocess.command.error";
    public static final String AGGREGATION_CONSUMED_TIMER_NAME = "aggregation.inprocess.time.consumed";
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
    ApplicationRepository applicationRepository;

    @Inject
    EventRepository eventRepository;

    @Inject
    BundleRepository bundleRepository;

    @Inject
    Environment environment;

    @Inject
    ConnectorSender connectorSender;

    private Counter processedAggregationCommandCount;
    private Counter failedAggregationCommandCount;

    @PostConstruct
    void postConstruct() {
        processedAggregationCommandCount = registry.counter(AGGREGATION_COMMAND_PROCESSED_COUNTER_NAME);
        failedAggregationCommandCount = registry.counter(AGGREGATION_COMMAND_ERROR_COUNTER_NAME);
    }

    public void processBundleAggregation(List<AggregationCommand> aggregationCommands, Event aggregatorEvent, List<Endpoint> endpoints) {
        final String bundleName = aggregationCommands.get(0).getAggregationKey().getBundle();
        Timer.Sample consumedTimer = Timer.start(registry);

        try {
            // Patch event display name for event log rendering
            Bundle bundle = bundleRepository.getBundle(bundleName)
                    .orElseThrow(() -> new IllegalArgumentException("Bundle not found: " + bundleName));

            String eventTypeDisplayName = String.format("%s - %s",
                aggregatorEvent.getEventTypeDisplayName(),
                bundle.getDisplayName());
            eventRepository.updateEventDisplayName(aggregatorEvent.getId(), eventTypeDisplayName);

            Map<User, List<ApplicationAggregatedData>> userData = aggregateByApplication(aggregationCommands);

            Map<List<ApplicationAggregatedData>, Set<User>> usersWithSameData = groupUsersByAggregatedData(userData);

            sendAggregatedEmails(usersWithSameData, bundle, aggregatorEvent, endpoints);
        } finally {
            consumedTimer.stop(registry.timer(
                AGGREGATION_CONSUMED_TIMER_NAME,
                TAG_KEY_BUNDLE, bundleName,
                TAG_KEY_ORG_ID, aggregatorEvent.getOrgId()
            ));
        }
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
                processedAggregationCommandCount.increment();
            } catch (Exception ex) {
                Log.error("Error processing " + cmd.getAggregationKey(), ex);
                failedAggregationCommandCount.increment();
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
                                      Bundle bundle, Event aggregatorEvent, List<Endpoint> endpoints) {

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
                true
            );

            // We need to send an endpoint to connectorSender only to find the right connector
            // and create a notifications history entry.
            // Endpoint restrictions will be evaluated from payload content (recipientSettings)
            connectorSender.send(aggregatorEvent, endpoints.getFirst(), JsonObject.mapFrom(emailNotification));
            Log.debugf("Sent aggregation email notification to connector: %s", emailNotification);
        });
    }

    /*
     * Ported from engine's SystemEndpointTypeProcessor -- not inherited here since
     * the rest of that class hierarchy (process(), generic endpoint-type dispatch)
     * doesn't apply to the direct-dispatch aggregation path.
     */
    private Set<com.redhat.cloud.notifications.recipients.RecipientSettings> extractRecipientSettings(final Event event, final List<Endpoint> endpoints) {
        return Stream.concat(
            endpoints.stream().map(EndpointRecipientSettings::new),
            ActionRecipientSettings.fromEventWrapper(event.getEventWrapper()).stream()
        ).collect(Collectors.toSet());
    }

    private Set<RecipientSettings> extractAndTransformRecipientSettings(final Event event, final List<Endpoint> endpoints) {
        final Set<com.redhat.cloud.notifications.recipients.RecipientSettings> recipientSettings = this.extractRecipientSettings(event, endpoints);

        return recipientSettings
            .stream()
            .map(RecipientSettings::new)
            .collect(Collectors.toSet());
    }

    private Map<String, Object> buildFullConnectorTemplateContext(List<ApplicationAggregatedData> action, Bundle bundle) {
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("environment", environment);
        additionalContext.put("bundle_name", bundle.getName());
        additionalContext.put("bundle_display_name", bundle.getDisplayName());
        additionalContext.put("application_aggregated_data_list", new JsonArray(objectMapper.convertValue(action, List.class)));
        return additionalContext;
    }
}
