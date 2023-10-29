package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SubscriptionType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EmailSubscriptionRepositoryTest {

    private static final String ORG_ID = "someOrgId";
    private static final String BUNDLE_NAME = "best-bundle";
    private static final String APP_NAME = "awesome-app";

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EmailSubscriptionRepository emailSubscriptionRepository;

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

        Map<String, Set<String>> mapUsersByEventType = emailSubscriptionRepository.getEmailSubscribersUserIdGroupedByEventType(ORG_ID, BUNDLE_NAME, APP_NAME, SubscriptionType.DAILY);
        assertEquals(3, mapUsersByEventType.size());
        assertEquals(2, mapUsersByEventType.get("event-type-a").size());
        assertTrue(mapUsersByEventType.get("event-type-a").contains("User-01"));
        assertTrue(mapUsersByEventType.get("event-type-a").contains("User-02"));
        assertFalse(mapUsersByEventType.get("event-type-a").contains("User-03"));
    }
}
