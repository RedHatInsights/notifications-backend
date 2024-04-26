package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.redhat.cloud.notifications.processors.email.EmailActorsResolver.GENERAL_PENDO_MESSAGE;
import static com.redhat.cloud.notifications.processors.email.EmailActorsResolver.GENERAL_PENDO_TITLE;
import static com.redhat.cloud.notifications.processors.email.EmailActorsResolver.OCM_PENDO_MESSAGE;
import static com.redhat.cloud.notifications.processors.email.EmailActorsResolver.OCM_PENDO_TITLE;
import static com.redhat.cloud.notifications.processors.email.EmailActorsResolver.OPENSHIFT_SENDER_PROD;
import static com.redhat.cloud.notifications.processors.email.EmailActorsResolver.OPENSHIFT_SENDER_STAGE;
import static com.redhat.cloud.notifications.processors.email.EmailActorsResolver.RH_INSIGHTS_SENDER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
public class EmailActorsResolverTest {

    @Inject
    EmailActorsResolver emailActorsResolver;

    /**
     * Tests that the "Red Hat Insights" sender is returned by default.
     */
    @Test
    void testDefaultEmailSenderInsights() {
        try {
            emailActorsResolver.setEmailChangesActivationDate(LocalDate.parse("2050-01-01"));
            Event event = buildEvent(null, "rhel", "policies");
            assertEquals(RH_INSIGHTS_SENDER, emailActorsResolver.getEmailSender(event), "unexpected email sender returned from the function under test");
        } finally {
            emailActorsResolver.setEmailChangesActivationDate(LocalDate.parse("2000-01-01"));
        }
    }

    /**
     * Tests that the "Red Hat Hybrid Cloud Console" sender is returned by default.
     */
    @Test
    void testDefaultEmailSenderHCC() {
        try {
            emailActorsResolver.setEmailChangesActivationDate(LocalDate.parse("2024-01-01"));
            Event event = buildEvent(null, "rhel", "policies");
            assertEquals(this.emailActorsResolver.getRhHccSender(), emailActorsResolver.getEmailSender(event), "unexpected email sender returned from the function under test");
        } finally {
            emailActorsResolver.setEmailChangesActivationDate(LocalDate.parse("2000-01-01"));
        }
    }

    /**
     * Tests that the OpenShift sender is returned for OCM events originating from the stage source environment.
     */
    @Test
    void testOpenshiftClusterManagerStageEmailSender() {
        Event event = buildEvent("stage", "openshift", "cluster-manager");
        assertEquals(this.emailActorsResolver.getOpenshiftSenderStage(), emailActorsResolver.getEmailSender(event), "unexpected email sender returned from the function under test");
        try {
            emailActorsResolver.setEmailChangesActivationDate(LocalDate.parse("2050-01-01"));

            event = buildEvent("stage", "openshift", "cluster-manager");
            assertEquals(OPENSHIFT_SENDER_STAGE, emailActorsResolver.getEmailSender(event), "unexpected email sender returned from the function under test");
        } finally {
            emailActorsResolver.setEmailChangesActivationDate(LocalDate.parse("2000-01-01"));
        }
    }

    /**
     * Tests that the OpenShift sender is returned for OCM events originating from source environments other than stage.
     */
    @Test
    void testOpenshiftClusterManagerDefaultEmailSender() {
        Event event = buildEvent("prod", "openshift", "cluster-manager");
        assertEquals(this.emailActorsResolver.getOpenshiftSenderProd(), emailActorsResolver.getEmailSender(event), "unexpected email sender returned from the function under test");
        try {
            emailActorsResolver.setEmailChangesActivationDate(LocalDate.parse("2050-01-01"));

            event = buildEvent("prod", "openshift", "cluster-manager");
            assertEquals(OPENSHIFT_SENDER_PROD, emailActorsResolver.getEmailSender(event), "unexpected email sender returned from the function under test");
        } finally {
            emailActorsResolver.setEmailChangesActivationDate(LocalDate.parse("2000-01-01"));
        }
    }

    /**
     * Tests default pendo message.
     */
    @Test
    void testDefaultEmailPendoMessage() {
        Event event = buildEvent(null, "rhel", "policies");
        assertNull(emailActorsResolver.getPendoEmailMessage(event), "unexpected email pendo message returned from the function under test");

        try {
            emailActorsResolver.setEmailChangesActivationDate(LocalDate.parse("2050-01-01"));
            assertEquals(String.format(GENERAL_PENDO_MESSAGE, "January 01, 2050"), emailActorsResolver.getPendoEmailMessage(event).getPendoMessage(), "unexpected email pendo message returned from the function under test");
            assertEquals(GENERAL_PENDO_TITLE, emailActorsResolver.getPendoEmailMessage(event).getPendoTitle(), "unexpected email pendo title returned from the function under test");
        } finally {
            emailActorsResolver.setEmailChangesActivationDate(LocalDate.parse("2000-01-01"));
        }
    }

    /**
     * Tests OCM pendo message.
     */
    @Test
    void testOpenshiftClusterManagerPendoMessage() {
        Event event = buildEvent("prod", "openshift", "cluster-manager");
        assertNull(emailActorsResolver.getPendoEmailMessage(event), "unexpected email pendo message returned from the function under test");

        try {
            emailActorsResolver.setEmailChangesActivationDate(LocalDate.parse("2050-01-01"));
            assertEquals(String.format(OCM_PENDO_MESSAGE, "January 01, 2050"), emailActorsResolver.getPendoEmailMessage(event).getPendoMessage(), "unexpected email pendo message returned from the function under test");
            assertEquals(OCM_PENDO_TITLE, emailActorsResolver.getPendoEmailMessage(event).getPendoTitle(), "unexpected email pendo title returned from the function under test");

            event = buildEvent("stage", "openshift", "cluster-manager");
            assertEquals(String.format(OCM_PENDO_MESSAGE, "January 01, 2050"), emailActorsResolver.getPendoEmailMessage(event).getPendoMessage(), "unexpected email pendo message returned from the function under test");
            assertEquals(OCM_PENDO_TITLE, emailActorsResolver.getPendoEmailMessage(event).getPendoTitle(), "unexpected email pendo title returned from the function under test");

        } finally {
            emailActorsResolver.setEmailChangesActivationDate(LocalDate.parse("2000-01-01"));
        }
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
