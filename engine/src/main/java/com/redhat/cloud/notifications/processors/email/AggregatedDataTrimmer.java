package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.SubscriptionRepository;
import com.redhat.cloud.notifications.ingress.Recipient;
import com.redhat.cloud.notifications.ingress.RecipientsAuthorizationCriterion;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EventAggregationCriterion;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.recipients.RecipientSettings;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.recipientsresolver.ExternalRecipientsResolver;
import com.redhat.cloud.notifications.recipients.request.ActionRecipientSettings;
import com.redhat.cloud.notifications.recipients.request.EndpointRecipientSettings;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Restores per-recipient filtering over aggregator's already-folded digest content. Aggregator folds
 * every event in the window into one superset context, tagging each included item (see
 * {@code AbstractEmailPayloadAggregator.tagMetadata()} in the {@code aggregator} module) instead of
 * filtering per recipient at fold time. This class is the engine-side counterpart that restores that
 * filtering, operating on the already-folded JSON instead of re-scanning raw events, so it stays off
 * the heavy DB-paging path {@link EmailAggregator} still uses for non-flagged orgs.
 */
@ApplicationScoped
public class AggregatedDataTrimmer {

    private static final String SEVERITY_KEY = "_severity";
    private static final String EVENT_TYPE_KEY = "_eventType";
    private static final String EVENT_TYPE_ID_KEY = "_eventTypeId";
    private static final String SUBSCRIBED_BY_DEFAULT_KEY = "_subscribedByDefault";
    private static final String RECIPIENTS_OVERRIDE_KEY = "_recipientsOverride";
    private static final String RECIPIENTS_AUTHORIZATION_CRITERION_KEY = "_recipientsAuthorizationCriterion";

    private static final Set<String> TAG_KEYS = Set.of(
        SEVERITY_KEY, EVENT_TYPE_KEY, EVENT_TYPE_ID_KEY, SUBSCRIBED_BY_DEFAULT_KEY, RECIPIENTS_OVERRIDE_KEY, RECIPIENTS_AUTHORIZATION_CRITERION_KEY);

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    ExternalRecipientsResolver externalRecipientsResolver;

    @Inject
    EngineConfig engineConfig;

    /**
     * Resolution bucket key: every tagged item sharing the same eventType + override + authorization
     * criterion resolves to the same eligible-recipients set, so the (comparatively expensive) resolver
     * call is made at most once per distinct combination actually present in the digest, not once per
     * raw event.
     */
    private record BucketKey(UUID eventTypeId, String eventTypeName, boolean subscribedByDefault, String overrideCanonical, String criterionCanonical) {
        /**
         * Returns {@code null} if the item has no {@code _eventTypeId} tag. Aggregator always stamps it
         * today, but aggregator and engine are deployed independently, so a digest folded by a
         * differently-versioned aggregator build is a real possibility -- callers must treat a null
         * result as "exclude this item" rather than let it propagate into a {@code UUID.fromString(null)}
         * NullPointerException.
         */
        static BucketKey from(JsonObject item) {
            String eventTypeId = item.getString(EVENT_TYPE_ID_KEY);
            if (eventTypeId == null) {
                return null;
            }
            JsonArray override = item.getJsonArray(RECIPIENTS_OVERRIDE_KEY);
            JsonObject criterion = item.getJsonObject(RECIPIENTS_AUTHORIZATION_CRITERION_KEY);
            return new BucketKey(
                UUID.fromString(eventTypeId),
                item.getString(EVENT_TYPE_KEY),
                item.getBoolean(SUBSCRIBED_BY_DEFAULT_KEY, false),
                override == null ? null : override.encode(),
                criterion == null ? null : criterion.encode()
            );
        }
    }

    /**
     * Trims the already-folded digest content for one app down to a per-recipient view, mirroring the
     * shape {@link EmailAggregator#getAggregated} returns for the legacy per-recipient fold path, so
     * {@code EmailAggregationProcessor.aggregateByApplication()} doesn't need to treat the two paths
     * differently beyond which method it calls.
     *
     * <p>Three passes: (1) {@link #collectBuckets} walks the folded tree once to find the distinct
     * resolution buckets present, (2) {@link #resolveEligibleUsernamesByBucket} resolves each bucket's
     * eligible recipients once (local lookup, or a bounded resolver call), (3)
     * {@link #buildTrimmedResultByUser} walks the tree again per candidate recipient to assemble their
     * personalized, tag-stripped copy.
     */
    public Map<User, Map<String, Object>> trim(String orgId, EventAggregationCriterion aggregationKey, SubscriptionType subscriptionType, Map<String, Object> foldedData) {
        UUID appId = aggregationKey.getApplicationId();
        JsonObject root = new JsonObject(foldedData);

        Map<String, Set<String>> subscribersByEventType = subscriptionRepository.getSubscribersByEventType(orgId, appId, subscriptionType);
        Map<String, Set<String>> unsubscribersByEventType = subscriptionRepository.getUnsubscribersByEventType(orgId, appId, subscriptionType);
        Optional<Map<String, Set<SubscribedEventTypeSeverities>>> subscribersWithSeverities = Optional.empty();
        if (engineConfig.isIncludeSeverityToFilterRecipientsEnabled(orgId, appId)) {
            subscribersWithSeverities = Optional.of(subscriptionRepository.getSubscriptionsByEventTypeWithSeverities(orgId, appId, subscriptionType));
        }

        Map<BucketKey, JsonObject> representativeItemByBucket = collectBuckets(root);
        Map<BucketKey, Set<String>> eligibleUsernamesByBucket =
            resolveEligibleUsernamesByBucket(orgId, representativeItemByBucket, subscribersByEventType, unsubscribersByEventType);
        return buildTrimmedResultByUser(root, eligibleUsernamesByBucket, subscribersWithSeverities);
    }

    /**
     * Pass 1: walks the folded tree once and groups every tagged item by the {@link BucketKey} it
     * belongs to, keeping one representative item per bucket -- resolving eligibility only needs one
     * item's tags per bucket, not every item in it.
     */
    private Map<BucketKey, JsonObject> collectBuckets(JsonObject root) {
        Map<BucketKey, JsonObject> buckets = new HashMap<>();
        collectBuckets(root, buckets);
        return buckets;
    }

    private void collectBuckets(Object node, Map<BucketKey, JsonObject> buckets) {
        if (node instanceof JsonArray array) {
            for (Object element : array) {
                collectBuckets(element, buckets);
            }
        } else if (node instanceof JsonObject obj) {
            if (obj.containsKey(EVENT_TYPE_KEY)) {
                BucketKey key = BucketKey.from(obj);
                if (key == null) {
                    Log.warnf("Folded digest item is missing %s, excluding it from the trimmed digest", EVENT_TYPE_ID_KEY);
                } else {
                    buckets.putIfAbsent(key, obj);
                }
            } else {
                for (String field : obj.fieldNames()) {
                    collectBuckets(obj.getValue(field), buckets);
                }
            }
        }
    }

    /**
     * Pass 2: resolves every bucket found in pass 1 to its eligible-usernames set, once each.
     */
    private Map<BucketKey, Set<String>> resolveEligibleUsernamesByBucket(String orgId, Map<BucketKey, JsonObject> representativeItemByBucket,
                                                                          Map<String, Set<String>> subscribersByEventType, Map<String, Set<String>> unsubscribersByEventType) {
        Map<BucketKey, Set<String>> eligibleUsernamesByBucket = new HashMap<>();
        for (Map.Entry<BucketKey, JsonObject> entry : representativeItemByBucket.entrySet()) {
            eligibleUsernamesByBucket.put(entry.getKey(), resolveEligibleUsernames(orgId, entry.getKey(), entry.getValue(), subscribersByEventType, unsubscribersByEventType));
        }
        return eligibleUsernamesByBucket;
    }

    /**
     * Mirrors {@code EmailAggregator.resolveRecipients()}'s eligibility decision, but once per distinct
     * bucket instead of once per raw event. The vast majority of traffic (explicit subscribe/unsubscribe,
     * no override, no Kessel criterion, no admin/group-restricted endpoint) resolves with zero external
     * calls -- everything needed is already in our own subscription tables.
     */
    private Set<String> resolveEligibleUsernames(String orgId, BucketKey key, JsonObject representativeItem,
                                                  Map<String, Set<String>> subscribersByEventType, Map<String, Set<String>> unsubscribersByEventType) {
        Set<String> subscribers = subscribersByEventType.getOrDefault(key.eventTypeName(), Set.of());
        Set<String> unsubscribers = unsubscribersByEventType.getOrDefault(key.eventTypeName(), Set.of());

        List<Endpoint> endpoints = endpointRepository.getTargetEmailSubscriptionEndpoints(orgId, key.eventTypeId());
        boolean hasRestrictedEndpoint = endpoints.stream().anyMatch(endpoint -> {
            SystemSubscriptionProperties properties = endpoint.getProperties(SystemSubscriptionProperties.class);
            return properties.isOnlyAdmins() || properties.isIgnorePreferences() || properties.getGroupId() != null;
        });

        RecipientsAuthorizationCriterion criterion = representativeItem.getJsonObject(RECIPIENTS_AUTHORIZATION_CRITERION_KEY) == null ? null
            : representativeItem.getJsonObject(RECIPIENTS_AUTHORIZATION_CRITERION_KEY).mapTo(RecipientsAuthorizationCriterion.class);
        List<ActionRecipientSettings> overrideSettings = parseRecipientsOverride(representativeItem.getJsonArray(RECIPIENTS_OVERRIDE_KEY));

        boolean needsResolverCall = key.subscribedByDefault() || !overrideSettings.isEmpty() || criterion != null || hasRestrictedEndpoint;
        if (!needsResolverCall) {
            Set<String> eligible = new HashSet<>(subscribers);
            eligible.removeAll(unsubscribers);
            return eligible;
        }

        Set<RecipientSettings> recipientSettings = Stream.concat(
            endpoints.stream().map(endpoint -> (RecipientSettings) new EndpointRecipientSettings(endpoint)),
            overrideSettings.stream().map(settings -> (RecipientSettings) settings)
        ).collect(Collectors.toSet());

        return externalRecipientsResolver.recipientUsers(orgId, recipientSettings, subscribers, unsubscribers, key.subscribedByDefault(), criterion).stream()
            .filter(user -> user.getEmail() != null && !user.getEmail().isBlank())
            .map(User::getUsername)
            .collect(Collectors.toSet());
    }

    private List<ActionRecipientSettings> parseRecipientsOverride(JsonArray override) {
        if (override == null || override.isEmpty()) {
            return List.of();
        }
        return override.stream()
            .map(r -> ((JsonObject) r).mapTo(Recipient.class))
            .map(r -> new ActionRecipientSettings(r.getOnlyAdmins(), r.getIgnoreUserPreferences(), r.getUsers(), r.getEmails()))
            .collect(Collectors.toList());
    }

    /**
     * Pass 3: rebuilds one personalized, tag-stripped copy of the folded tree per candidate recipient --
     * the union of every bucket's eligible set -- dropping a candidate from the result entirely if
     * nothing survives for them (e.g. bucket-eligible but excluded by severity on every item).
     */
    private Map<User, Map<String, Object>> buildTrimmedResultByUser(JsonObject root, Map<BucketKey, Set<String>> eligibleUsernamesByBucket,
                                                                      Optional<Map<String, Set<SubscribedEventTypeSeverities>>> subscribersWithSeverities) {
        Set<String> candidateUsernames = eligibleUsernamesByBucket.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        Map<User, Map<String, Object>> result = new HashMap<>();
        for (String username : candidateUsernames) {
            int[] survivorCount = new int[1];
            JsonObject trimmed = (JsonObject) trimForUser(root, username, eligibleUsernamesByBucket, subscribersWithSeverities, survivorCount);
            if (survivorCount[0] > 0) {
                User user = new User();
                user.setUsername(username);
                // mapTo(Map.class) performs a deep Jackson conversion (same convention as aggregator's
                // own AbstractEmailPayloadAggregator.getContext()) so nested values are plain Map/List,
                // not JsonObject/JsonArray -- unlike getMap(), which would leave this trimmed copy's
                // nested values (built via JsonObject.put(JsonObject/JsonArray)) as live Vert.x wrapper
                // objects instead of the plain types the rest of the pipeline expects.
                result.put(user, trimmed.mapTo(Map.class));
            }
        }
        return result;
    }

    /**
     * Recursive worker for pass 3: rebuilds {@code node} for one {@code username}, pruning any tagged
     * item the user isn't eligible for (bucket membership, then severity parity) and stripping tag
     * fields from survivors via {@link #stripTags}.
     */
    private Object trimForUser(Object node, String username, Map<BucketKey, Set<String>> eligibleUsernamesByBucket,
                                Optional<Map<String, Set<SubscribedEventTypeSeverities>>> subscribersWithSeverities, int[] survivorCount) {
        if (node instanceof JsonArray array) {
            JsonArray result = new JsonArray();
            for (Object element : array) {
                Object trimmed = trimForUser(element, username, eligibleUsernamesByBucket, subscribersWithSeverities, survivorCount);
                if (trimmed != null) {
                    result.add(trimmed);
                }
            }
            return result;
        } else if (node instanceof JsonObject obj) {
            if (obj.containsKey(EVENT_TYPE_KEY)) {
                BucketKey key = BucketKey.from(obj);
                if (key == null || !eligibleUsernamesByBucket.getOrDefault(key, Set.of()).contains(username)) {
                    return null;
                }
                if (subscribersWithSeverities.isPresent() && !severityAdmits(username, key.eventTypeId(), obj, subscribersWithSeverities.get())) {
                    return null;
                }
                survivorCount[0]++;
                return stripTags(obj);
            } else {
                JsonObject result = new JsonObject();
                for (String field : obj.fieldNames()) {
                    // Omit the field entirely when pruned, mirroring the array branch above -- matters if
                    // a future aggregator ever nests a tagged item directly under an object key instead of
                    // inside an array, which none of today's aggregators do.
                    Object trimmed = trimForUser(obj.getValue(field), username, eligibleUsernamesByBucket, subscribersWithSeverities, survivorCount);
                    if (trimmed != null) {
                        result.put(field, trimmed);
                    }
                }
                return result;
            }
        } else {
            return node;
        }
    }

    /**
     * Exact parity with the legacy per-recipient {@code AbstractEmailPayloadAggregator.aggregate()}
     * (engine's original, pre-fold-only class): no entry for this eventType, or an entry without this
     * specific severity key, both mean exclusion -- not "admit by default".
     */
    private boolean severityAdmits(String username, UUID eventTypeId, JsonObject item, Map<String, Set<SubscribedEventTypeSeverities>> subscribersWithSeverities) {
        Severity severity = item.containsKey(SEVERITY_KEY) ? Severity.valueOf(item.getString(SEVERITY_KEY)) : null;
        Set<SubscribedEventTypeSeverities> userSeverities = subscribersWithSeverities.getOrDefault(username, Set.of());
        return userSeverities.stream()
            .filter(s -> eventTypeId.equals(s.eventTypeId()))
            .findFirst()
            .filter(s -> s.severities().containsKey(severity))
            .map(s -> s.severities().get(severity))
            .orElse(false);
    }

    /**
     * Strips exactly the known tag keys, not every field starting with {@code _} -- a business payload
     * field that happens to start with an underscore must survive untouched.
     */
    private JsonObject stripTags(JsonObject item) {
        JsonObject copy = new JsonObject();
        for (String field : item.fieldNames()) {
            if (!TAG_KEYS.contains(field)) {
                copy.put(field, item.getValue(field));
            }
        }
        return copy;
    }
}
