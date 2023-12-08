package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.processors.email.EmailActorsResolver.OPENSHIFT_SENDER_PROD;
import static com.redhat.cloud.notifications.processors.email.EmailActorsResolver.OPENSHIFT_SENDER_STAGE;
import static com.redhat.cloud.notifications.processors.email.EmailActorsResolver.RH_INSIGHTS_SENDER;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class EmailActorsResolverTest {

    @Inject
    EmailActorsResolver emailActorsResolver;

    /**
     * Tests that the "Red Hat Insights" sender is returned by default.
     */
    @Test
    void testDefaultEmailSender() {
        Event event = new Event();
        assertEquals(RH_INSIGHTS_SENDER, this.emailActorsResolver.getEmailSender(event), "unexpected email sender returned from the function under test");
    }

    /**
     * Tests that the OpenShift sender is returned for OCM events originating from the stage source environment.
     */
    @Test
    void testOpenshiftClusterManagerStageEmailSender() {
        Event event = buildOCMEvent("stage");
        assertEquals(OPENSHIFT_SENDER_STAGE, this.emailActorsResolver.getEmailSender(event), "unexpected email sender returned from the function under test");
    }

    /**
     * Tests that the OpenShift sender is returned for OCM events originating from source environments other than stage.
     */
    @Test
    void testOpenshiftClusterManagerDefaultEmailSender() {
        Event event = buildOCMEvent("prod");
        assertEquals(OPENSHIFT_SENDER_PROD, this.emailActorsResolver.getEmailSender(event), "unexpected email sender returned from the function under test");
    }

    private static Event buildOCMEvent(String sourceEnvironment) {

        Bundle bundle = new Bundle();
        bundle.setName("openshift");

        Application app = new Application();
        app.setBundle(bundle);
        app.setName("cluster-manager");

        EventType eventType = new EventType();
        eventType.setApplication(app);

        Event event = new Event();
        event.setEventType(eventType);
        event.setSourceEnvironment(sourceEnvironment);

        return event;
    }
}
