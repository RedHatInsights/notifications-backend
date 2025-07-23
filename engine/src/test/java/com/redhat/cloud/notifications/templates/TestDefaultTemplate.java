package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.NotificationsConsoleCloudEvent;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@QuarkusTest
public class TestDefaultTemplate extends EmailTemplatesInDbHelper {

    private static final String EVENT_TYPE_NAME = "event-type-without-template";

    @InjectMock
    EngineConfig engineConfig;

    @Override
    protected String getApp() {
        return "policies";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(EVENT_TYPE_NAME);
    }

    @BeforeEach
    void beforeEach() {
        when(engineConfig.isDefaultTemplateEnabled()).thenReturn(true);
    }

    @Test
    public void testInstantEmailTitle() {
        Action action = TestHelpers.createPoliciesAction("", "my-bundle", "my-app", "FooMachine");

        String result = generateEmailSubject(EVENT_TYPE_NAME, action);
        assertTrue(result.contains("my-bundle/my-app/policy-triggered triggered"), "Title contains the bundle/app/event-type");
    }

    @Test
    public void testInstantEmailBody() {
        Action action = TestHelpers.createPoliciesAction("", "my-bundle", "my-app", "FooMachine");
        String result = generateEmailBody(EVENT_TYPE_NAME, action);
        assertTrue(result.contains("my-bundle/my-app/policy-triggered notification was triggered"), "Body should contain bundle/app/event-type");
        assertTrue(result.contains("You are receiving this email because the email template associated with this event type is not configured properly"));
    }

    @Test
    public void testInstantEmailTitleCloudEvents() throws Exception {
        NotificationsConsoleCloudEvent event = TestHelpers.createConsoleCloudEvent();
        String result = generateEmailSubject(EVENT_TYPE_NAME, event);

        assertEquals("com.redhat.console.insights.policies.policy-triggered triggered", result.trim());
    }

    @Test
    public void testInstantEmailBodyCloudEvents() throws Exception {
        NotificationsConsoleCloudEvent event = TestHelpers.createConsoleCloudEvent();
        String result = generateEmailBody(EVENT_TYPE_NAME, event);

        assertTrue(result.contains("com.redhat.console.insights.policies.policy-triggered notification was triggered"), "Body should contain bundle/app/event-type");
        assertTrue(result.contains("You are receiving this email because the email template associated with this event type is not configured properly"));
    }
}
