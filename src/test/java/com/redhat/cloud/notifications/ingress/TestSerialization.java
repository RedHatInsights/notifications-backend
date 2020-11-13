package com.redhat.cloud.notifications.ingress;

import com.redhat.cloud.notifications.events.EventConsumer;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
        targetAction.setEventId(UUID.randomUUID().toString()); // UUID probably isn't what we want..
        targetAction.setEventType("Any");
        targetAction.setTags(new ArrayList<>());
        // targetAction.setParams(PoliciesParams.newBuilder().setTriggers(new HashMap<>()).build());

        PoliciesParams params = new PoliciesParams();
        Map<String, String> triggers = new HashMap<>();
        params.setTriggers(triggers);
        targetAction.setParams(params);

        Context context = new Context();
        context.setAccountId("testTenant");
        Map<String, String> values = new HashMap<>();
        values.put("k", "v");
        values.put("k2", "v2");
        values.put("k3", "v");
        context.setMessage(values);
        targetAction.setEvent(context);

        String payload = serializeAction(targetAction);

        Action action = eventConsumer.extractPayload(payload);
        assertNotNull(action);
        assertEquals(targetAction.getEventId(), action.getEventId());
        assertEquals(targetAction.getEvent().getAccountId(), action.getEvent().getAccountId());
        assertEquals(targetAction.getEvent().getMessage().get("k3"), action.getEvent().getMessage().get("k3"));
    }
}
