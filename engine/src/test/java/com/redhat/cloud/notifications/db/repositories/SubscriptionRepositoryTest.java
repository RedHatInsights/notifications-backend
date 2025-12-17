package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SubscriptionType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class SubscriptionRepositoryTest {

    private static final String ORG_ID = "someOrgId";
    private static final String BUNDLE_NAME = "best-bundle";
    private static final String APP_NAME = "awesome-app";

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @InjectMock
    EngineConfig engineConfig;

    @AfterEach
    void afterEach() {
        resourceHelpers.deleteBundle(BUNDLE_NAME);
    }

    @Test
    void testEmailSubscribersUserIdGroupedByEventType() {
        Bundle bundle = resourceHelpers.createBundle(BUNDLE_NAME);
        Application application = resourceHelpers.createApp(bundle.getId(), APP_NAME);

        EventType eventTypeA = resourceHelpers.createEventType(application.getId(), "event-type-a");
        EventType eventTypeB = resourceHelpers.createEventType(application.getId(), "event-type-b");
        EventType eventTypeC = resourceHelpers.createEventType(application.getId(), "event-type-c");
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "User-01", eventTypeA, SubscriptionType.DAILY);
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "User-01", eventTypeB, SubscriptionType.DAILY);
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "User-02", eventTypeC, SubscriptionType.DAILY);
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "User-02", eventTypeA, SubscriptionType.DAILY);
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "User-03", eventTypeC, SubscriptionType.DAILY);

        Map<String, Set<String>> mapUsersByEventType = subscriptionRepository.getSubscribersByEventType(ORG_ID, application.getId(), SubscriptionType.DAILY);
        assertEquals(3, mapUsersByEventType.size());
        assertEquals(2, mapUsersByEventType.get("event-type-a").size());
        assertTrue(mapUsersByEventType.get("event-type-a").contains("User-01"));
        assertTrue(mapUsersByEventType.get("event-type-a").contains("User-02"));
        assertFalse(mapUsersByEventType.get("event-type-a").contains("User-03"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testEmailSubscribersUserIdWithoutSeverity(boolean useSeverity) {
        when(engineConfig.isIncludeSeverityToFilterRecipientsEnabled(anyString())).thenReturn(useSeverity);

        Bundle bundle = resourceHelpers.createBundle(BUNDLE_NAME);
        Application application = resourceHelpers.createApp(bundle.getId(), APP_NAME);

        EventType eventTypeA = resourceHelpers.createEventType(application.getId(), "event-type-a");
        EventType eventTypeB = resourceHelpers.createEventType(application.getId(), "event-type-b");
        EventType eventTypeC = resourceHelpers.createEventType(application.getId(), "event-type-c");
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "User-01", eventTypeA, SubscriptionType.INSTANT);
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "User-01", eventTypeB, SubscriptionType.INSTANT);
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "User-02", eventTypeC, SubscriptionType.INSTANT);
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "User-02", eventTypeA, SubscriptionType.INSTANT);
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "User-03", eventTypeC, SubscriptionType.INSTANT);

        Map<String, Set<String>> mapUsersByEventType = subscriptionRepository.getSubscribersByEventType(ORG_ID, application.getId(), SubscriptionType.INSTANT);
        assertEquals(3, mapUsersByEventType.size());
        assertEquals(2, mapUsersByEventType.get("event-type-a").size());
        assertTrue(mapUsersByEventType.get("event-type-a").contains("User-01"));
        assertTrue(mapUsersByEventType.get("event-type-a").contains("User-02"));
        assertFalse(mapUsersByEventType.get("event-type-a").contains("User-03"));
    }

    @Test
    void testEmailSubscribersUserId() {
        Bundle bundle = resourceHelpers.createBundle(BUNDLE_NAME);
        Application application = resourceHelpers.createApp(bundle.getId(), APP_NAME);

        EventType eventTypeA = resourceHelpers.createEventType(application.getId(), "event-type-a");
        EventType eventTypeB = resourceHelpers.createEventType(application.getId(), "event-type-b");
        EventType eventTypeC = resourceHelpers.createEventType(application.getId(), "event-type-c");
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "User-01", eventTypeA, SubscriptionType.INSTANT, Map.of(Severity.MODERATE, true, Severity.LOW, false));
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "User-01", eventTypeA, SubscriptionType.DAILY);
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "User-01", eventTypeB, SubscriptionType.INSTANT);
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "User-02", eventTypeC, SubscriptionType.INSTANT);

        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "User-02", eventTypeA, SubscriptionType.INSTANT, Map.of(Severity.CRITICAL, true));
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "User-03", eventTypeC, SubscriptionType.INSTANT);
        resourceHelpers.createEventTypeEmailSubscription(ORG_ID, "User-04", eventTypeA, SubscriptionType.INSTANT);

        // All users who subscribed to eventTypeA must be returned
        List<String> listRecipients = subscriptionRepository.getSubscribers(ORG_ID, eventTypeA.getId(), SubscriptionType.INSTANT, Optional.empty());
        assertEquals(new HashSet<>(listRecipients), Set.of("User-01", "User-02", "User-04"));

        // Only users how subscribed to eventTypeA + severity MODERATE and use how subscribed without any severity details (db column severities = null) must be returned
        listRecipients = subscriptionRepository.getSubscribers(ORG_ID, eventTypeA.getId(), SubscriptionType.INSTANT, Optional.of(Severity.MODERATE));
        assertEquals(new HashSet<>(listRecipients), Set.of("User-01", "User-04"));

        // Only users how subscribed to eventTypeA + severity LOW and use how subscribed without any severity details (db column severities = null) must be returned
        listRecipients = subscriptionRepository.getSubscribers(ORG_ID, eventTypeA.getId(), SubscriptionType.INSTANT, Optional.of(Severity.LOW));
        assertEquals(new HashSet<>(listRecipients), Set.of("User-04"));

        // Only users how subscribed to eventTypeA + severity UNDEFINED and use how subscribed without any severity details (db column severities = null) must be returned
        listRecipients = subscriptionRepository.getSubscribers(ORG_ID, eventTypeA.getId(), SubscriptionType.INSTANT, Optional.of(Severity.UNDEFINED));
        assertEquals(new HashSet<>(listRecipients), Set.of("User-04"));
    }
}
