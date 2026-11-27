package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.models.SubscriptionType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explicit assertions on the "plain" trigger, the Action the aggregation jobs emit for an org that does not use the new
 * aggregation pipeline (or when the pipeline has nothing to add). They document the shape the engine reads: if one of
 * these fails, the trigger changed.
 */
final class PlainTriggerAssertions {

    private static final Set<String> EVENT_PAYLOAD_KEYS = Set.of("aggregationKey", "bundleId", "orgId", "start", "end", "subscriptionType");
    private static final Set<String> AGGREGATION_KEY_KEYS = Set.of("orgId", "bundleId", "applicationId", "bundle", "application");

    private PlainTriggerAssertions() {
    }

    /**
     * Asserts the shape of one plain trigger: fixed bundle and application, the given event type, an EMPTY context, no
     * recipients, and events that all belong to the Action's org and one bundle and have exactly the expected keys.
     */
    static void assertPlainTrigger(Action action, String expectedEventType, SubscriptionType expectedSubscriptionType) {
        assertEquals("console", action.getBundle());
        assertEquals("notifications", action.getApplication());
        assertEquals(expectedEventType, action.getEventType());
        assertNotNull(action.getOrgId());
        assertTrue(action.getContext().getAdditionalProperties().isEmpty(), "A plain trigger has an empty context, got " + action.getContext().getAdditionalProperties());
        assertTrue(action.getRecipients().isEmpty());
        assertFalse(action.getEvents().isEmpty());

        Set<String> bundles = new TreeSet<>();
        for (Event event : action.getEvents()) {
            Map<String, Object> payload = event.getPayload().getAdditionalProperties();
            assertEquals(EVENT_PAYLOAD_KEYS, payload.keySet());
            assertEquals(action.getOrgId(), payload.get("orgId"));
            assertEquals(expectedSubscriptionType.name(), payload.get("subscriptionType"));
            assertNotNull(payload.get("start"));
            assertNotNull(payload.get("end"));

            Map<?, ?> key = (Map<?, ?>) payload.get("aggregationKey");
            assertEquals(AGGREGATION_KEY_KEYS, key.keySet());
            assertEquals(action.getOrgId(), key.get("orgId"));
            bundles.add((String) key.get("bundle"));
        }
        assertEquals(1, bundles.size(), "One trigger covers exactly one bundle, got " + bundles);
    }

    /**
     * @return the applications of every trigger, by org and bundle, sorted: {org -> {bundle -> [application, ...]}}
     */
    static Map<String, Map<String, List<String>>> applicationsByOrgAndBundle(List<Action> actions) {
        Map<String, Map<String, List<String>>> result = new TreeMap<>();
        for (Action action : actions) {
            for (Event event : action.getEvents()) {
                Map<?, ?> key = (Map<?, ?>) event.getPayload().getAdditionalProperties().get("aggregationKey");
                result.computeIfAbsent(action.getOrgId(), org -> new TreeMap<>())
                    .computeIfAbsent((String) key.get("bundle"), bundle -> new java.util.ArrayList<>())
                    .add((String) key.get("application"));
            }
        }
        result.values().forEach(bundles -> bundles.values().forEach(java.util.Collections::sort));
        return result;
    }
}
