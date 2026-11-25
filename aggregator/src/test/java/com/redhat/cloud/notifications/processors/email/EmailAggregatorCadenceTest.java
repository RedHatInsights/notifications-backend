package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
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
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.SubscriptionType.WEEKLY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Folds real events of the database for both cadences, to show the subscription type picks the right subscribers
 * (the engine-copied {@link EmailAggregatorTest} only uses DAILY).
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class EmailAggregatorCadenceTest {

    private static final String ORG_ID = "cadence-org";
    private static final String BUNDLE = "rhel";
    private static final String APPLICATION = "advisor";
    private static final String EVENT_TYPE = "new-recommendation";

    @InjectMock
    @RestClient
    RecipientsResolverService recipientsResolverService;

    @InjectMock
    EndpointRepository endpointRepository;

    @Inject
    EmailAggregator emailAggregator;

    @Inject
    ResourceHelpers resourceHelpers;

    @BeforeEach
    void beforeEach() {
        resourceHelpers.clearEvents();
        resourceHelpers.clearEmailSubscriptions();
        Endpoint endpoint = new Endpoint();
        endpoint.setProperties(new SystemSubscriptionProperties());
        endpoint.setType(EndpointType.EMAIL_SUBSCRIPTION);
        when(endpointRepository.getTargetEmailSubscriptionEndpoints(anyString(), any(UUID.class))).thenReturn(List.of(endpoint));
        when(recipientsResolverService.getRecipients(any(RecipientsQuery.class))).then(parameters -> {
            RecipientsQuery query = parameters.getArgument(0);
            return query.subscribers.stream().map(username -> {
                User user = new User();
                user.setEmail(username);
                user.setUsername(username);
                return user;
            }).collect(Collectors.toSet());
        });
    }

    @AfterEach
    void afterEach() {
        resourceHelpers.clearEvents();
        resourceHelpers.clearEmailSubscriptions();
    }

    @Test
    void eachCadenceFoldsOnlyForItsOwnSubscribers() {
        Application application = resourceHelpers.findOrCreateApplication(BUNDLE, APPLICATION);
        EventType eventType = resourceHelpers.findOrCreateEventType(application.getId(), EVENT_TYPE);
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "weekly-user", eventType, WEEKLY);
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "daily-user", eventType, DAILY);
        resourceHelpers.addEventEmailAggregation(ORG_ID, BUNDLE, APPLICATION, advisorPayload("rule-1"), false, null, EVENT_TYPE);
        resourceHelpers.addEventEmailAggregation(ORG_ID, BUNDLE, APPLICATION, advisorPayload("rule-2"), false, null, EVENT_TYPE);
        EventAggregationCriterion key = new EventAggregationCriterion(ORG_ID, application.getBundleId(), application.getId(), BUNDLE, APPLICATION);
        LocalDateTime start = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1);
        LocalDateTime end = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1);

        Map<User, Map<String, Object>> weekly = emailAggregator.getAggregated(application.getId(), key, WEEKLY, start, end);
        Map<User, Map<String, Object>> daily = emailAggregator.getAggregated(application.getId(), key, DAILY, start, end);

        assertEquals(1, weekly.size());
        User weeklyUser = weekly.keySet().iterator().next();
        assertEquals("weekly-user", weeklyUser.getUsername());
        assertEquals(2, newRecommendationsSize(weekly.get(weeklyUser)));

        assertEquals(1, daily.size());
        User dailyUser = daily.keySet().iterator().next();
        assertEquals("daily-user", dailyUser.getUsername());
        assertEquals(2, newRecommendationsSize(daily.get(dailyUser)));
    }

    @SuppressWarnings("unchecked")
    private static int newRecommendationsSize(Map<String, Object> userData) {
        Map<String, Object> advisorData = (Map<String, Object>) userData.get("advisor");
        Object newRecommendations = advisorData.get("new_recommendations");
        return newRecommendations == null ? 0 : ((Map<?, ?>) newRecommendations).size();
    }

    private static JsonObject advisorPayload(String ruleId) {
        Action action = new Action();
        action.setBundle(BUNDLE);
        action.setApplication(APPLICATION);
        action.setTimestamp(LocalDateTime.now());
        action.setEventType(EVENT_TYPE);
        action.setOrgId(ORG_ID);
        action.setContext(new Context.ContextBuilder()
            .withAdditionalProperty("inventory_id", "host-" + ruleId)
            .withAdditionalProperty("display_name", "Test machine")
            .build());
        action.setEvents(List.of(new Event.EventBuilder()
            .withMetadata(new Metadata.MetadataBuilder().build())
            .withPayload(new Payload.PayloadBuilder()
                .withAdditionalProperty("rule_id", ruleId)
                .withAdditionalProperty("rule_description", "test rule description")
                .withAdditionalProperty("total_risk", "2")
                .withAdditionalProperty("has_incident", "false")
                .withAdditionalProperty("rule_url", "http://test-rule/" + ruleId)
                .build())
            .build()));
        JsonObject payload = TestHelpers.wrapActionToJsonObject(action);
        payload.remove(BaseTransformer.SOURCE);
        return payload;
    }
}
