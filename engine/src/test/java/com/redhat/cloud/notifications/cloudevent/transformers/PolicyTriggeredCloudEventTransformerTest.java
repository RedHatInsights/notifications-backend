package com.redhat.cloud.notifications.cloudevent.transformers;

import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.events.EventWrapperCloudEvent;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.NotificationsConsoleCloudEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class PolicyTriggeredCloudEventTransformerTest {

    @Inject
    PolicyTriggeredCloudEventTransformer transformer;

    @Test
    void testPolicies() throws IOException {
        InputStream policyCloudEvent = TestLifecycleManager.class.getClassLoader().getResourceAsStream("cloudevents/policies.json");
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

        assertEquals(3, action.getEvents().size());
    }

    @Test
    void testPoliciesWithNullTagValue() throws IOException {
        InputStream policyCloudEvent = TestLifecycleManager.class.getClassLoader().getResourceAsStream("cloudevents/policies-with-null-tag-values.json");
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

        assertEquals(4, action.getEvents().size());
    }
}
