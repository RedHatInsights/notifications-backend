package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class EmailActorsResolverTest {

    @Inject
    EmailActorsResolver emailActorsResolver;

    @Inject
    EngineConfig engineConfig;

    /**
     * Tests that the "Red Hat Hybrid Cloud Console" sender is returned by default.
     */
    @Test
    void testDefaultEmailSenderHCC() {
        Event event = buildEvent(null, "rhel", "policies");
        assertEquals(engineConfig.getRhHccSender(), emailActorsResolver.getEmailSender(event), "unexpected email sender returned from the function under test");
    }

    /**
     * Tests that the OpenShift sender is returned for OCM events originating from the stage source environment.
     */
    @Test
    void testOpenshiftClusterManagerStageEmailSender() {
        Event event = buildEvent("stage", "openshift", "cluster-manager");
        assertEquals(engineConfig.getRhOpenshiftSenderStage(), emailActorsResolver.getEmailSender(event), "unexpected email sender returned from the function under test");
    }

    /**
     * Tests that the OpenShift sender is returned for OCM events originating from source environments other than stage.
     */
    @Test
    void testOpenshiftClusterManagerDefaultEmailSender() {
        Event event = buildEvent("prod", "openshift", "cluster-manager");
        assertEquals(engineConfig.getRhOpenshiftSenderProd(), emailActorsResolver.getEmailSender(event), "unexpected email sender returned from the function under test");
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
