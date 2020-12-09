package com.redhat.cloud.notifications.ingress;

import com.redhat.cloud.notifications.events.EventConsumer;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static com.redhat.cloud.notifications.TestHelpers.serializeAction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
public class TestSerialization {

    @Inject
    EventConsumer eventConsumer;

    @Test
    void testActionSerialization() throws Exception {
        Action targetAction = new Action();
        targetAction.setApplication("Policies");
        targetAction.setTimestamp(LocalDateTime.now());
        targetAction.setEventType("Any");
        targetAction.setAccountId("testTenant");
        Map<String, String> payload = new HashMap<>();
        payload.put("k", "v");
        payload.put("k2", "v2");
        payload.put("k3", "v");
        targetAction.setPayload(payload);

        String serializedAction = serializeAction(targetAction);

        Action action = eventConsumer.extractPayload(serializedAction);
        assertNotNull(action);
        assertEquals(targetAction.getAccountId(), action.getAccountId());
        assertEquals(targetAction.getPayload().get("k3"), action.getPayload().get("k3"));
    }
}
