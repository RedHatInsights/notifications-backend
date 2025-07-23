package com.redhat.cloud.notifications.templates.common;

import com.redhat.cloud.notifications.EmailTemplatesRendererHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.NotificationsConsoleCloudEvent;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@QuarkusTest
public class TestDefaultTemplate extends EmailTemplatesRendererHelper {

    private static final String EVENT_TYPE_NAME = "event-type-without-template";

    @InjectSpy
    protected TemplateService templateService;

    @Override
    protected String getApp() {
        return "policies";
    }

    @Override
    protected String getAppDisplayName() {
        return "Policies";
    }

    @BeforeEach
    void beforeEach() {
        when(templateService.isDefaultEmailTemplateEnabled()).thenReturn(true);
        templateService.init();
    }

    @Test
    public void testInstantEmailTitle() {
        Action action = TestHelpers.createPoliciesAction("", "my-bundle", "my-app", "FooMachine");
        eventTypeDisplayName = "Policy Triggered";

        String result = generateEmailSubject(EVENT_TYPE_NAME, action);
        assertEquals("Instant notification - Policy Triggered - Policies - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testInstantEmailBody() {
        Action action = TestHelpers.createPoliciesAction("", "my-bundle", "my-app", "FooMachine");
        eventTypeDisplayName = "Policy Triggered";
        String result = generateEmailBody(EVENT_TYPE_NAME, action);

        assertTrue(result.contains("Red Hat Enterprise Linux/Policies/Policy Triggered notification was triggered"), "Body should contain bundle/app/event-type");
        assertTrue(result.contains("You are receiving this email because the email template associated with this event type is not configured properly"));
    }


    @Test
    public void testInstantEmailTitleCloudEvents() throws Exception {
        NotificationsConsoleCloudEvent event = TestHelpers.createConsoleCloudEvent();
        eventTypeDisplayName = "Event without template";
        TemplateDefinition templateDefinition = new TemplateDefinition(IntegrationType.EMAIL_TITLE, getBundle(), getApp(), EVENT_TYPE_NAME);
        String result = generateEmail(templateDefinition, event, null, false);

        assertEquals("Instant notification - Event without template - Policies - Red Hat Enterprise Linux", result.trim());
    }

    @Test
    public void testInstantEmailBodyCloudEvents() throws Exception {
        NotificationsConsoleCloudEvent event = TestHelpers.createConsoleCloudEvent();
        eventTypeDisplayName = "Event without template";
        TemplateDefinition templateDefinition = new TemplateDefinition(IntegrationType.EMAIL_BODY, getBundle(), getApp(), EVENT_TYPE_NAME);
        String result = generateEmail(templateDefinition, event, null, false);

        assertTrue(result.contains("Red Hat Enterprise Linux/Policies/Event without template notification was triggered."), "Body should contain bundle/app/event-type");
        assertTrue(result.contains("You are receiving this email because the email template associated with this event type is not configured properly"));
    }
}
