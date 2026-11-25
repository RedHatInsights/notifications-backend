package com.redhat.cloud.notifications.processors.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.config.AggregatorConfig;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.BundleRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.processors.email.connector.dto.EmailNotification;
import com.redhat.cloud.notifications.processors.email.connector.dto.RecipientSettings;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.request.EndpointRecipientSettings;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonArray;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds the aggregated emails of ONE org and bundle: the same {@link EmailNotification}s the engine's
 * {@code EmailAggregationProcessor} builds today (one per group of users that receive identical data), but without
 * sending them. It does not touch the database for writing, so a failure leaves nothing behind.
 * <p>
 * Differences with the engine on purpose: a failure while aggregating ANY application aborts the whole build (the engine
 * logs it and carries on with the other applications), so that the caller can fall back to the engine's own aggregation
 * for the complete bundle instead of sending a digest without that application's data.
 */
@ApplicationScoped
public class AggregatedDigestBuilder {

    /**
     * @param endpoint      the email endpoint the notifications are sent through (the first one, as the engine does)
     * @param notifications one notification per group of users that receive identical data
     */
    public record Digests(Endpoint endpoint, List<EmailNotification> notifications) {
        static final Digests NONE = new Digests(null, List.of());
    }

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    BundleRepository bundleRepository;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    EmailAggregator emailAggregator;

    @Inject
    AggregatorConfig aggregatorConfig;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Environment environment;

    /**
     * @param aggregationCommands the commands of ONE org and bundle
     * @return the notifications to send, or none when there is no email endpoint or nobody to send to
     * @throws RuntimeException if aggregating any of the applications fails
     */
    public Digests build(List<AggregationCommand> aggregationCommands) {
        final String orgId = aggregationCommands.getFirst().getOrgId();
        final String bundleName = aggregationCommands.getFirst().getAggregationKey().getBundle();
        final SubscriptionType subscriptionType = aggregationCommands.getFirst().getSubscriptionType();

        List<Endpoint> endpoints = findEndpoints(orgId, aggregationCommands);
        if (endpoints.isEmpty()) {
            Log.warnf("[org_id: %s] No email endpoints linked to the aggregated applications of bundle %s", orgId, bundleName);
            return Digests.NONE;
        }

        Bundle bundle = bundleRepository.getBundle(bundleName)
            .orElseThrow(() -> new IllegalArgumentException("Bundle not found: " + bundleName));

        Map<User, List<ApplicationAggregatedData>> userData = aggregateByApplication(aggregationCommands);
        Map<List<ApplicationAggregatedData>, Set<User>> usersWithSameData = groupUsersByAggregatedData(userData);

        Set<RecipientSettings> recipientSettings = extractRecipientSettings(endpoints);
        List<EmailNotification> notifications = new ArrayList<>();
        usersWithSameData.forEach((appDataList, users) -> {
            Set<String> recipientsUsernames = users.stream().map(User::getUsername).collect(Collectors.toSet());
            /*
             * The recipients were determined while aggregating (see EmailAggregator) using the recipients-resolver and
             * the subscription records. The subscribedByDefault value below only means that the recipients-resolver
             * considers the subscribers passed in the request as the recipient candidates of the aggregated email.
             * The recipient list was already computed using authz constraints, so they are not passed again.
             */
            notifications.add(new EmailNotification(
                aggregatorConfig.getRhHccSender(),
                orgId,
                recipientSettings,
                recipientsUsernames,
                Collections.emptySet(),
                false,
                null,
                buildFullConnectorTemplateContext(appDataList, bundle),
                true,
                subscriptionType
            ));
        });
        return new Digests(endpoints.getFirst(), notifications);
    }

    /*
     * Same lookup and filtering as the engine: the email endpoints linked to the aggregated applications, minus the
     * blacklisted ones. System endpoints have their orgId rewritten by the repository first, so they ARE checked.
     */
    private List<Endpoint> findEndpoints(String orgId, List<AggregationCommand> aggregationCommands) {
        Set<UUID> applicationIds = aggregationCommands.stream()
            .map(command -> command.getAggregationKey().getApplicationId())
            .collect(Collectors.toSet());
        List<Endpoint> endpoints = new ArrayList<>(endpointRepository.getTargetEmailEndpointsForAggregation(orgId, applicationIds));
        endpoints.removeIf(endpoint -> {
            // Default endpoints (orgId is null) used as system integrations for the all orgs can't be blacklisted
            if (null != endpoint.getOrgId() && aggregatorConfig.isBlacklistedEndpoint(endpoint.getId())) {
                Log.infof("Org: %s, skipping endpoint: %s because it was blacklisted", endpoint.getOrgId(), endpoint.getId());
                return true;
            }
            return false;
        });
        return endpoints;
    }

    /*
     * Aggregates event data per application and collects results per user. Unlike the engine, a failing application
     * aborts everything: the error is logged with the context needed to fix it and rethrown.
     */
    private Map<User, List<ApplicationAggregatedData>> aggregateByApplication(List<AggregationCommand> aggregationCommands) {
        Map<User, List<ApplicationAggregatedData>> userData = new HashMap<>();

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

                Map<User, Map<String, Object>> aggregatedByUser = emailAggregator.getAggregated(
                    app.getId(), aggregationCommand.getAggregationKey(),
                    aggregationCommand.getSubscriptionType(), aggregationCommand.getStart(), aggregationCommand.getEnd());

                aggregatedByUser.forEach((user, data) ->
                    userData.computeIfAbsent(user, _ -> new ArrayList<>())
                        .add(new ApplicationAggregatedData(data, aggregationCommand.getAggregationKey().getApplication())));
            } catch (Exception ex) {
                Log.errorf(ex, "Error aggregating org %s, bundle %s, application %s",
                    aggregationCommand.getOrgId(),
                    aggregationCommand.getAggregationKey().getBundle(),
                    aggregationCommand.getAggregationKey().getApplication());
                throw new IllegalStateException("Aggregation failed for " + aggregationCommand.getAggregationKey(), ex);
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
     * The engine combines the settings of the endpoints with the recipients of the trigger event. The aggregation trigger
     * carries no recipients, so only the endpoint settings remain.
     */
    private static Set<RecipientSettings> extractRecipientSettings(List<Endpoint> endpoints) {
        return endpoints.stream()
            .map(EndpointRecipientSettings::new)
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
