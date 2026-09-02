package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.EmailAggregationRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventAggregationCriterion;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.recipientsresolver.RecipientsResolverService;
import com.redhat.cloud.notifications.recipients.recipientsresolver.pojo.RecipientsQuery;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheManager;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class EmailAggregatorTest {

    @InjectSpy
    EmailAggregationRepository emailAggregationRepository;

    @InjectMock
    @RestClient
    RecipientsResolverService recipientsResolverService;

    @InjectSpy
    EmailAggregator emailAggregator;

    @Inject
    ResourceHelpers resourceHelpers;

    @InjectMock
    EndpointRepository endpointRepository;

    @Inject
    CacheManager cacheManager;

    @InjectMock
    EngineConfig engineConfig;

    Application application;
    EventType eventType1;
    EventType eventType2;
    final EventAggregationCriterion AGGREGATION_KEY = new EventAggregationCriterion("org-1", UUID.randomUUID(), UUID.randomUUID(), "rhel", "advisor");

    @BeforeEach
    void beforeEach() {
        emailAggregator.maxPageSize = 5;
        clearCachedData("recipients-resolver-results");
        clearInvocations(recipientsResolverService);
        resourceHelpers.clearEvents();
    }

    @AfterEach
    void afterEach() {
        resourceHelpers.clearEmailSubscriptions();
        resourceHelpers.clearEvents();
    }

    private void initDataForSubscriptionTests() {
        // init test environment
        application = resourceHelpers.findOrCreateApplication("rhel", "advisor");
        eventType1 = resourceHelpers.findOrCreateEventType(application.getId(), "new-recommendation");
        eventType2 = resourceHelpers.findOrCreateEventType(application.getId(), "not-used");
        resourceHelpers.findOrCreateEventType(application.getId(), "event-type-2");
        resourceHelpers.createEventTypeEmailSubscription("org-1", "user-2", eventType2, DAILY);

        Endpoint endpoint = new Endpoint();
        endpoint.setProperties(new SystemSubscriptionProperties());
        endpoint.setType(EndpointType.EMAIL_SUBSCRIPTION);

        when(endpointRepository.getTargetEmailSubscriptionEndpoints(anyString(), any(UUID.class))).thenReturn(List.of(endpoint));
        when(recipientsResolverService.getRecipients(any(RecipientsQuery.class))).then(parameters -> {
            RecipientsQuery query = parameters.getArgument(0);
            Set<String> users = query.subscribers;
            return users.stream().map(usrStr -> {
                User usr = new User();
                usr.setEmail(usrStr);
                usr.setUsername(usrStr);
                return usr;
            }).collect(Collectors.toSet());
        });
    }

    @Test
    void shouldTestNoRecipientSubscribedToTheRightEventType() {
        initDataForSubscriptionTests();

        // Test user subscription based on event type
        Map<User, Map<String, Object>> result = aggregate();

        verify(emailAggregationRepository, times(1)).getEmailAggregationBasedOnEvent(any(EventAggregationCriterion.class), any(LocalDateTime.class), any(LocalDateTime.class), anyInt(), anyInt());
        verify(emailAggregationRepository, times(1)).getEmailAggregationBasedOnEvent(any(EventAggregationCriterion.class), any(LocalDateTime.class), any(LocalDateTime.class), eq(0), eq(emailAggregator.maxPageSize));

        // nobody subscribed to the right event type yet
        assertEquals(0, result.size());
    }

    @Test
    void shouldTestOneRecipientSubscribed() {
        initDataForSubscriptionTests();

        resourceHelpers.createEventTypeEmailSubscription("org-1", "user-2", eventType1, DAILY);
        Map<User, Map<String, Object>> result = aggregate();

        verify(emailAggregationRepository, times(1)).getEmailAggregationBasedOnEvent(any(EventAggregationCriterion.class), any(LocalDateTime.class), any(LocalDateTime.class), anyInt(), anyInt());
        verify(emailAggregationRepository, times(1)).getEmailAggregationBasedOnEvent(any(EventAggregationCriterion.class), any(LocalDateTime.class), any(LocalDateTime.class), eq(0), eq(emailAggregator.maxPageSize));

        assertEquals(1, result.size());
        assertTrue(result.keySet().stream().findFirst().isPresent());
        User user = result.keySet().stream().findFirst().get();
        assertEquals("user-2", user.getEmail());
        assertEquals(4, getNewRecommendationsSize(result.get(user)));
        verify(recipientsResolverService, times(4)).getRecipients(any(RecipientsQuery.class));
    }

    @Test
    void shouldTestOneRecipientSubscribedSeverityEnabled() {
        // enable filter on severity without any user severity subscription config
        when(engineConfig.isIncludeSeverityToFilterRecipientsEnabled(anyString(), any())).thenReturn(true);
        // nothing should change
        shouldTestOneRecipientSubscribed();
    }

    @Test
    void shouldTestOneRecipientSubscribedToModerateSeverity() {
        when(engineConfig.isIncludeSeverityToFilterRecipientsEnabled(anyString(), any())).thenReturn(true);
        initDataForSubscriptionTests();

        // enable filter on "MODERATE" severity
        resourceHelpers.createEventTypeEmailSubscription("org-1", "user-2", eventType1, DAILY, Map.of(Severity.MODERATE, true));

        Map<User, Map<String, Object>> result = aggregate();

        verify(emailAggregationRepository, times(1)).getEmailAggregationBasedOnEvent(any(EventAggregationCriterion.class), any(LocalDateTime.class), any(LocalDateTime.class), anyInt(), anyInt());
        verify(emailAggregationRepository, times(1)).getEmailAggregationBasedOnEvent(any(EventAggregationCriterion.class), any(LocalDateTime.class), any(LocalDateTime.class), eq(0), eq(emailAggregator.maxPageSize));

        assertEquals(1, result.size());
        assertTrue(result.keySet().stream().findFirst().isPresent());
        User user = result.keySet().stream().findFirst().get();
        assertEquals("user-2", user.getEmail());
        // we should have only one result here because only one event have the "MODERATE" severity
        assertEquals(1, getNewRecommendationsSize(result.get(user)));
        verify(recipientsResolverService, times(4)).getRecipients(any(RecipientsQuery.class));
    }

    @Test
    void shouldTestOneRecipientUnsubscribedFromAllSeverities() {
        when(engineConfig.isIncludeSeverityToFilterRecipientsEnabled(anyString(), any())).thenReturn(true);
        initDataForSubscriptionTests();

        // User unsubscribe from all severities (don't make real sens, but just to check)
        Map<Severity, Boolean> severities = new HashMap<>();
        for (Severity severity : Severity.values()) {
            severities.put(severity, false);
        }

        resourceHelpers.createEventTypeEmailSubscription("org-1", "user-2", eventType1, DAILY, severities);
        // because after the previous aggregate() call the email_aggregation DB table was not purged, we already have 4 records on database
        Map<User, Map<String, Object>> result = aggregate();

        verify(emailAggregationRepository, times(1)).getEmailAggregationBasedOnEvent(any(EventAggregationCriterion.class), any(LocalDateTime.class), any(LocalDateTime.class), anyInt(), anyInt());
        verify(emailAggregationRepository, times(1)).getEmailAggregationBasedOnEvent(any(EventAggregationCriterion.class), any(LocalDateTime.class), any(LocalDateTime.class), eq(0), eq(emailAggregator.maxPageSize));

        assertEquals(1, result.size());
        assertTrue(result.keySet().stream().findFirst().isPresent());
        User user = result.keySet().stream().findFirst().get();
        assertEquals("user-2", user.getEmail());
        // we should have 0 result since user unsubscribed from all severities
        assertEquals(0, getNewRecommendationsSize(result.get(user)));
        verify(recipientsResolverService, times(4)).getRecipients(any(RecipientsQuery.class));

        // disable the severity filtering
        when(engineConfig.isIncludeSeverityToFilterRecipientsEnabled(anyString(), any())).thenReturn(false);

        result = aggregate();

        assertEquals(1, result.size());
        assertTrue(result.keySet().stream().findFirst().isPresent());
        user = result.keySet().stream().findFirst().get();
        assertEquals("user-2", user.getEmail());
        assertEquals(5, getNewRecommendationsSize(result.get(user)));
    }

    private Map<User, Map<String, Object>> aggregate() {
        Map<User, Map<String, Object>> result = new HashMap<>();

        for (int i = 0; i < 4; i++) {
            Severity severity = Severity.values()[i];
            JsonObject payload = createAdvisorPayload("org-1", "rule-" + RandomStringUtils.secure().next(10));
            resourceHelpers.addEventEmailAggregation("org-1", "rhel", "advisor", payload, false, severity, "new-recommendation");
        }
        JsonObject payload = createAdvisorPayload("org-2", "rule-" + RandomStringUtils.secure().next(10));
        resourceHelpers.addEventEmailAggregation("org-2", "rhel", "advisor", payload, false, null, "new-recommendation");

        Application advisorApp = resourceHelpers.findApp("rhel", "advisor");
        EventAggregationCriterion aggregationKey = new EventAggregationCriterion(AGGREGATION_KEY.getOrgId(), advisorApp.getBundleId(), advisorApp.getId(), AGGREGATION_KEY.getBundle(), AGGREGATION_KEY.getApplication());

        result.putAll(emailAggregator.getAggregated(application.getId(), aggregationKey, DAILY, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1), LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1)));
        return result;
    }

    private static JsonObject createAdvisorPayload(String orgId, String ruleId) {
        Action action = new Action();
        action.setBundle("rhel");
        action.setApplication("advisor");
        action.setTimestamp(LocalDateTime.now());
        action.setEventType("new-recommendation");
        action.setOrgId(orgId);

        action.setContext(
                new Context.ContextBuilder()
                        .withAdditionalProperty("inventory_id", RandomStringUtils.secure().next(10))
                        .withAdditionalProperty("hostname", "test-host")
                        .withAdditionalProperty("display_name", "Test machine")
                        .withAdditionalProperty("rhel_version", "8.3")
                        .withAdditionalProperty("host_url", "http://test-host-url")
                        .build()
        );
        action.setEvents(List.of(
                new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(
                                new Payload.PayloadBuilder()
                                        .withAdditionalProperty("rule_id", ruleId)
                                        .withAdditionalProperty("rule_description", "test rule description")
                                        .withAdditionalProperty("total_risk", "2")
                                        .withAdditionalProperty("has_incident", "false")
                                        .withAdditionalProperty("rule_url", "http://test-rule/" + ruleId)
                                        .build()
                        )
                        .build()
        ));

        JsonObject payload = TestHelpers.wrapActionToJsonObject(action);
        payload.remove(BaseTransformer.SOURCE);
        return payload;
    }

    @SuppressWarnings("unchecked")
    private static int getNewRecommendationsSize(Map<String, Object> userData) {
        Map<String, Object> advisorData = (Map<String, Object>) userData.get("advisor");
        Object newRecs = advisorData.get("new_recommendations");
        return newRecs == null ? 0 : ((Map<?, ?>) newRecs).size();
    }

    public void clearCachedData(String cacheName) {
        Optional<Cache> cache = cacheManager.getCache(cacheName);
        if (cache.isPresent()) {
            cache.get().invalidateAll().await().indefinitely();
        }
    }
}
