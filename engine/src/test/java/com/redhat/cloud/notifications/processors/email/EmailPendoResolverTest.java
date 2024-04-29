package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.processors.email.EmailPendoResolver.GENERAL_PENDO_MESSAGE;
import static com.redhat.cloud.notifications.processors.email.EmailPendoResolver.GENERAL_PENDO_TITLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@QuarkusTest
class EmailPendoResolverTest {

    @Inject
    EmailPendoResolver emailPendoResolver;

    @InjectSpy
    EngineConfig engineConfig;

    @Inject
    Environment environment;

    /**
     * Tests default pendo message.
     */
    @Test
    void testDefaultEmailPendoMessage() {
        Event event = buildEvent(null, "rhel", "policies");
        assertEquals(String.format(GENERAL_PENDO_MESSAGE, environment.url()), emailPendoResolver.getPendoEmailMessage(event).getPendoMessage(), "unexpected email pendo message returned from the function under test");
        assertEquals(GENERAL_PENDO_TITLE, emailPendoResolver.getPendoEmailMessage(event).getPendoTitle(), "unexpected email pendo title returned from the function under test");
    }

    @Test
    void testDefaultEmailPendoMessageOnEmailOnlyEnv() {
        when(engineConfig.isEmailsOnlyModeEnabled()).thenReturn(true);

        Event event = buildEvent(null, "rhel", "policies");
        assertNull(emailPendoResolver.getPendoEmailMessage(event), "unexpected email pendo message returned from the function under test");
    }

    /**
     * Tests OCM pendo message.
     */
    @Test
    void testOpenshiftClusterManagerPendoMessage() {
        Event event = buildEvent("prod", "openshift", "cluster-manager");
        assertNull(emailPendoResolver.getPendoEmailMessage(event), "unexpected email pendo message returned from the function under test");

        event = buildEvent("stage", "openshift", "cluster-manager");
        assertNull(emailPendoResolver.getPendoEmailMessage(event), "unexpected email pendo message returned from the function under test");
    }

    private static Event buildEvent(String sourceEnvironment, String bundleName, String appName) {

        Bundle bundle = new Bundle();
        bundle.setName(bundleName);

        Application app = new Application();
        app.setBundle(bundle);
        app.setName(appName);

        EventType eventType = new EventType();
        eventType.setApplication(app);

        Event event = new Event();
        event.setOrgId("12345");
        event.setEventType(eventType);
        event.setSourceEnvironment(sourceEnvironment);

        return event;
    }
}
