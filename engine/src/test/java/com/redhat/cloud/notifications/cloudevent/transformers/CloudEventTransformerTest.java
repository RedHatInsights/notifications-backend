package com.redhat.cloud.notifications.cloudevent.transformers;

import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.events.EventWrapperCloudEvent;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.NotificationsConsoleCloudEvent;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CloudEventTransformerTest {

    private class DummyCloudEventTransformer extends CloudEventTransformer {

        @Override
        public Action.ActionBuilderBase<?> buildAction(Action.ActionBuilderBase<Action> actionBuilder, EventWrapperCloudEvent cloudEvent) {
            return actionBuilder;
        }
    }

    DummyCloudEventTransformer transformer = new DummyCloudEventTransformer();

    @Test
    public void testTransformer() throws IOException {
        InputStream policyCloudEvent = TestLifecycleManager.class.getClassLoader().getResourceAsStream("cloudevents/cloudevent.json");
        NotificationsConsoleCloudEvent cloudEvent = new ConsoleCloudEventParser().fromJsonString(
                IOUtils.toString(policyCloudEvent, UTF_8),
                NotificationsConsoleCloudEvent.class
        );

        Action action = transformer.toAction(
                new EventWrapperCloudEvent(cloudEvent),
                "rhel",
                "policies",
                "policy-triggered"
        );

        assertNotNull(action);

        assertEquals("rhel", action.getBundle());
        assertEquals("policies", action.getApplication());
        assertEquals("policy-triggered", action.getEventType());

        assertEquals("11789772", action.getOrgId());
        assertEquals("6089719", action.getAccountId());

        // 2023-05-03T02:09:06.245424792Z
        assertEquals(LocalDateTime.of(2023, 5, 3, 2, 9, 6, 245424792), action.getTimestamp());
        assertEquals(UUID.fromString("2de1e968-b851-47b1-a8ac-1d355ad223bb"), action.getId());
    }

}
