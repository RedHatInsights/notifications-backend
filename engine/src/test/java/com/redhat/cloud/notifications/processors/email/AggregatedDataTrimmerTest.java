package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.EventPayloadTestHelper;
import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.ingress.RecipientsAuthorizationCriterion;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventAggregationCriterion;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.recipientsresolver.RecipientsResolverService;
import com.redhat.cloud.notifications.recipients.recipientsresolver.pojo.RecipientsQuery;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class AggregatedDataTrimmerTest {

    private static final String ORG_ID = "trimmer-test-org";

    @Inject
    AggregatedDataTrimmer trimmer;

    @Inject
    ResourceHelpers resourceHelpers;

    @InjectMock
    EndpointRepository endpointRepository;

    @InjectMock
    EngineConfig engineConfig;

    @InjectMock
    @RestClient
    RecipientsResolverService recipientsResolverService;

    Application application;
    EventType eventType;
    EventAggregationCriterion aggregationKey;

    @BeforeEach
    void beforeEach() {
        application = resourceHelpers.findOrCreateApplication("rhel", "compliance");
        eventType = resourceHelpers.findOrCreateEventType(application.getId(), "report-upload-failed");
        aggregationKey = new EventAggregationCriterion(ORG_ID, application.getBundleId(), application.getId(), "rhel", "compliance");

        Endpoint endpoint = new Endpoint();
        endpoint.setType(EndpointType.EMAIL_SUBSCRIPTION);
        endpoint.setProperties(new SystemSubscriptionProperties());
        when(endpointRepository.getTargetEmailSubscriptionEndpoints(anyString(), any(UUID.class))).thenReturn(List.of(endpoint));
    }

    @AfterEach
    void afterEach() {
        resourceHelpers.clearEmailSubscriptions();
    }

    @Test
    void shouldIncludeExplicitSubscriberAndExcludeExplicitUnsubscriber() {
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "subscriber", eventType, DAILY);

        Map<String, Object> foldedData = Map.of("compliance", new JsonObject()
            .put("report-upload-failed", new JsonArray().add(taggedItem(false, null, null)))
            .getMap());

        Map<User, Map<String, Object>> result = trimmer.trim(ORG_ID, aggregationKey, DAILY, foldedData);

        assertEquals(1, result.size());
        assertEquals("subscriber", result.keySet().iterator().next().getUsername());
        verifyZeroResolverCalls();
    }

    @Test
    void shouldExcludeUserWithNoSubscriptionRow() {
        Map<String, Object> foldedData = Map.of("compliance", new JsonObject()
            .put("report-upload-failed", new JsonArray().add(taggedItem(false, null, null)))
            .getMap());

        Map<User, Map<String, Object>> result = trimmer.trim(ORG_ID, aggregationKey, DAILY, foldedData);

        assertTrue(result.isEmpty());
        verifyZeroResolverCalls();
    }

    @Test
    void shouldStripTagsFromSurvivingItems() {
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "subscriber", eventType, DAILY);

        JsonObject item = taggedItem(false, null, null).put("_severity", "CRITICAL").put("policy_id", "policy1");
        Map<String, Object> foldedData = Map.of("compliance", new JsonObject()
            .put("report-upload-failed", new JsonArray().add(item))
            .getMap());

        Map<User, Map<String, Object>> result = trimmer.trim(ORG_ID, aggregationKey, DAILY, foldedData);

        JsonObject trimmedItem = extractFirstItem(result.values().iterator().next());
        assertEquals("policy1", trimmedItem.getString("policy_id"));
        assertFalse(trimmedItem.containsKey("_eventType"));
        assertFalse(trimmedItem.containsKey("_eventTypeId"));
        assertFalse(trimmedItem.containsKey("_subscribedByDefault"));
        assertFalse(trimmedItem.containsKey("_severity"));
    }

    @Test
    void shouldNotStripBusinessFieldsThatHappenToStartWithUnderscore() {
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "subscriber", eventType, DAILY);

        JsonObject item = taggedItem(false, null, null).put("_internal_id", "not-a-tag");
        Map<String, Object> foldedData = Map.of("compliance", new JsonObject()
            .put("report-upload-failed", new JsonArray().add(item))
            .getMap());

        Map<User, Map<String, Object>> result = trimmer.trim(ORG_ID, aggregationKey, DAILY, foldedData);

        JsonObject trimmedItem = extractFirstItem(result.values().iterator().next());
        assertEquals("not-a-tag", trimmedItem.getString("_internal_id"), "only the known tag keys should be stripped, not every underscore-prefixed field");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldTraverseDeeplyNestedMixedObjectAndArrayStructures() {
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "subscriber", eventType, DAILY);

        JsonObject item = taggedItem(false, null, null).put("policy_id", "deep-policy");
        // Mimics the deeper nesting real aggregators use (e.g. ApplicationServices' root -> products ->
        // eventType -> payloads, or Inventory's array-of-arrays), not just the flat 2-level Compliance
        // shape every other test in this class uses.
        Map<String, Object> foldedData = Map.of("compliance", new JsonObject()
            .put("wrapper", new JsonObject()
                .put("nested", new JsonArray().add(new JsonArray().add(item))))
            .getMap());

        Map<User, Map<String, Object>> result = trimmer.trim(ORG_ID, aggregationKey, DAILY, foldedData);

        assertEquals(1, result.size());
        Map<String, Object> compliance = (Map<String, Object>) result.values().iterator().next().get("compliance");
        Map<String, Object> wrapper = (Map<String, Object>) compliance.get("wrapper");
        List<Object> nested = (List<Object>) wrapper.get("nested");
        List<Object> innerArray = (List<Object>) nested.get(0);
        JsonObject trimmedItem = new JsonObject((Map<String, Object>) innerArray.get(0));
        assertEquals("deep-policy", trimmedItem.getString("policy_id"));
    }

    @Test
    void shouldCallResolverOnceForSubscribedByDefaultBucket() {
        mockResolver(Set.of("default-subscriber"));

        Map<String, Object> foldedData = Map.of("compliance", new JsonObject()
            .put("report-upload-failed", new JsonArray()
                .add(taggedItem(true, null, null))
                .add(taggedItem(true, null, null)))
            .getMap());

        Map<User, Map<String, Object>> result = trimmer.trim(ORG_ID, aggregationKey, DAILY, foldedData);

        assertEquals(1, result.size());
        assertEquals("default-subscriber", result.keySet().iterator().next().getUsername());
        Map<?, ?> compliance = (Map<?, ?>) result.values().iterator().next().get("compliance");
        assertEquals(2, ((List<?>) compliance.get("report-upload-failed")).size());
        verify(recipientsResolverService, org.mockito.Mockito.times(1)).getRecipients(any(RecipientsQuery.class));
    }

    @Test
    void shouldCallResolverForRecipientsOverrideBucket() {
        mockResolver(Set.of("admin-user"));
        JsonArray override = new JsonArray().add(new JsonObject().put("only_admins", true));

        Map<String, Object> foldedData = Map.of("compliance", new JsonObject()
            .put("report-upload-failed", new JsonArray().add(taggedItem(false, override, null)))
            .getMap());

        Map<User, Map<String, Object>> result = trimmer.trim(ORG_ID, aggregationKey, DAILY, foldedData);

        assertEquals(1, result.size());
        assertEquals("admin-user", result.keySet().iterator().next().getUsername());
        verify(recipientsResolverService, org.mockito.Mockito.times(1)).getRecipients(any(RecipientsQuery.class));
        assertFalse(extractFirstItem(result.values().iterator().next()).containsKey("_recipientsOverride"));
    }

    @Test
    void shouldCallResolverWhenEndpointIsRestricted() {
        mockResolver(Set.of("admin-only-user"));
        SystemSubscriptionProperties restrictedProperties = new SystemSubscriptionProperties();
        restrictedProperties.setOnlyAdmins(true);
        Endpoint restrictedEndpoint = new Endpoint();
        restrictedEndpoint.setType(EndpointType.EMAIL_SUBSCRIPTION);
        restrictedEndpoint.setProperties(restrictedProperties);
        when(endpointRepository.getTargetEmailSubscriptionEndpoints(anyString(), any(UUID.class))).thenReturn(List.of(restrictedEndpoint));

        Map<String, Object> foldedData = Map.of("compliance", new JsonObject()
            .put("report-upload-failed", new JsonArray().add(taggedItem(false, null, null)))
            .getMap());

        Map<User, Map<String, Object>> result = trimmer.trim(ORG_ID, aggregationKey, DAILY, foldedData);

        assertEquals(1, result.size());
        assertEquals("admin-only-user", result.keySet().iterator().next().getUsername());
        verify(recipientsResolverService, org.mockito.Mockito.times(1)).getRecipients(any(RecipientsQuery.class));
    }

    @Test
    void shouldForwardKesselCriterionToResolver() {
        mockResolver(Set.of("authorized-user"));
        RecipientsAuthorizationCriterion criterion = EventPayloadTestHelper.buildRecipientsAuthorizationCriterion();
        JsonObject criterionJson = JsonObject.mapFrom(criterion);

        Map<String, Object> foldedData = Map.of("compliance", new JsonObject()
            .put("report-upload-failed", new JsonArray().add(taggedItem(false, null, criterionJson)))
            .getMap());

        Map<User, Map<String, Object>> result = trimmer.trim(ORG_ID, aggregationKey, DAILY, foldedData);

        assertEquals(1, result.size());
        assertEquals("authorized-user", result.keySet().iterator().next().getUsername());

        org.mockito.ArgumentCaptor<RecipientsQuery> captor = org.mockito.ArgumentCaptor.forClass(RecipientsQuery.class);
        verify(recipientsResolverService).getRecipients(captor.capture());
        assertEquals(criterion.getId(), captor.getValue().recipientsAuthorizationCriterion.getId());
        assertFalse(extractFirstItem(result.values().iterator().next()).containsKey("_recipientsAuthorizationCriterion"));
    }

    @Test
    void shouldApplySeverityTrimParity() {
        when(engineConfig.isIncludeSeverityToFilterRecipientsEnabled(anyString(), any())).thenReturn(true);
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "subscriber", eventType, DAILY, Map.of(Severity.CRITICAL, true, Severity.LOW, false));

        JsonObject criticalItem = taggedItem(false, null, null).put("_severity", "CRITICAL");
        JsonObject lowItem = taggedItem(false, null, null).put("_severity", "LOW");
        Map<String, Object> foldedData = Map.of("compliance", new JsonObject()
            .put("report-upload-failed", new JsonArray().add(criticalItem).add(lowItem))
            .getMap());

        Map<User, Map<String, Object>> result = trimmer.trim(ORG_ID, aggregationKey, DAILY, foldedData);

        assertEquals(1, result.size());
        List<?> survivors = (List<?>) ((Map<?, ?>) result.values().iterator().next().get("compliance")).get("report-upload-failed");
        assertEquals(1, survivors.size(), "only the CRITICAL item should survive, LOW is explicitly excluded");
    }

    @Test
    void shouldExcludeItemWithNoSeverityTagWhenSeverityFilteringEnabled() {
        // Legacy parity: AbstractEmailPayloadAggregator.aggregate() looks up severities().get(severity),
        // and a null severity has no entry in that map (the keys are the four real Severity values) --
        // so a tagged item with no _severity key is excluded, not admitted by default, exactly like an
        // item whose severity the recipient explicitly didn't subscribe to.
        when(engineConfig.isIncludeSeverityToFilterRecipientsEnabled(anyString(), any())).thenReturn(true);
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "subscriber", eventType, DAILY, Map.of(Severity.CRITICAL, true));

        JsonObject itemWithoutSeverityTag = taggedItem(false, null, null);
        Map<String, Object> foldedData = Map.of("compliance", new JsonObject()
            .put("report-upload-failed", new JsonArray().add(itemWithoutSeverityTag))
            .getMap());

        Map<User, Map<String, Object>> result = trimmer.trim(ORG_ID, aggregationKey, DAILY, foldedData);

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldExcludeSubscribedByDefaultRecipientWithNoSeverityRowAtAll() {
        // Matches the legacy AbstractEmailPayloadAggregator.aggregate()'s "no entry found -> excluded"
        // parity case: a subscribedByDefault recipient who never touched their preferences has no
        // EventTypeEmailSubscription row at all, so getSubscriptionsByEventTypeWithSeverities() has
        // nothing for them -- severity filtering then excludes every item for them, not admits by
        // default, exactly as it would for the legacy per-recipient fold.
        when(engineConfig.isIncludeSeverityToFilterRecipientsEnabled(anyString(), any())).thenReturn(true);
        mockResolver(Set.of("default-subscriber"));

        JsonObject item = taggedItem(true, null, null).put("_severity", "CRITICAL");
        Map<String, Object> foldedData = Map.of("compliance", new JsonObject()
            .put("report-upload-failed", new JsonArray().add(item))
            .getMap());

        Map<User, Map<String, Object>> result = trimmer.trim(ORG_ID, aggregationKey, DAILY, foldedData);

        assertTrue(result.isEmpty(), "default-subscriber is bucket-eligible but has zero severity rows, so every item is excluded and they drop out entirely");
    }

    @Test
    void shouldExcludeItemMissingEventTypeIdInsteadOfThrowing() {
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "subscriber", eventType, DAILY);

        JsonObject itemMissingEventTypeId = taggedItem(false, null, null);
        itemMissingEventTypeId.remove("_eventTypeId");
        Map<String, Object> foldedData = Map.of("compliance", new JsonObject()
            .put("report-upload-failed", new JsonArray().add(itemMissingEventTypeId))
            .getMap());

        Map<User, Map<String, Object>> result = trimmer.trim(ORG_ID, aggregationKey, DAILY, foldedData);

        assertTrue(result.isEmpty(), "an item with no _eventTypeId tag cannot be bucketed, so it must be excluded, not crash the whole digest");
    }

    private void mockResolver(Set<String> usernames) {
        when(recipientsResolverService.getRecipients(any(RecipientsQuery.class))).thenReturn(
            usernames.stream().map(username -> {
                User user = new User();
                user.setUsername(username);
                user.setEmail(username + "@redhat.com");
                return user;
            }).collect(java.util.stream.Collectors.toSet())
        );
    }

    private void verifyZeroResolverCalls() {
        org.mockito.Mockito.verifyNoInteractions(recipientsResolverService);
    }

    private JsonObject taggedItem(boolean subscribedByDefault, JsonArray override, JsonObject criterion) {
        JsonObject item = new JsonObject()
            .put("_eventType", eventType.getName())
            .put("_eventTypeId", eventType.getId().toString())
            .put("_subscribedByDefault", subscribedByDefault);
        if (override != null) {
            item.put("_recipientsOverride", override);
        }
        if (criterion != null) {
            item.put("_recipientsAuthorizationCriterion", criterion);
        }
        return item;
    }

    @SuppressWarnings("unchecked")
    private JsonObject extractFirstItem(Map<String, Object> trimmedData) {
        Map<String, Object> compliance = (Map<String, Object>) trimmedData.get("compliance");
        List<Object> items = (List<Object>) compliance.get("report-upload-failed");
        return new JsonObject((Map<String, Object>) items.get(0));
    }
}
