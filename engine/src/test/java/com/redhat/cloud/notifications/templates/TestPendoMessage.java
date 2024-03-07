package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.DriftTestHelpers;
import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.processors.email.EmailActorsResolver;
import com.redhat.cloud.notifications.processors.email.EmailPendo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.util.List;

import static com.redhat.cloud.notifications.processors.email.EmailActorsResolver.GENERAL_PENDO_MESSAGE;
import static com.redhat.cloud.notifications.processors.email.EmailActorsResolver.GENERAL_PENDO_TITLE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestPendoMessage extends EmailTemplatesInDbHelper  {

    private static final String EVENT_TYPE_NAME = "drift-baseline-detected";

    @Override
    protected String getApp() {
        return "drift";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(EVENT_TYPE_NAME);
    }

    @Inject
    EmailActorsResolver emailActorsResolver;

    @Test
    public void testInstantEmailBody() {
        EmailPendo emailPendo = new EmailPendo(GENERAL_PENDO_TITLE, emailActorsResolver.addDateOnPendoMessage(GENERAL_PENDO_MESSAGE));

        Action action = DriftTestHelpers.createDriftAction("rhel", "drift", "host-01", "Machine 1");
        String result = generateEmailBody(EVENT_TYPE_NAME, action);
        commonValidations(result);
        assertFalse(result.contains(emailPendo.getPendoTitle()));
        assertFalse(result.contains(emailPendo.getPendoMessage()));

        result = generateEmailBody(EVENT_TYPE_NAME, action, emailPendo);
        commonValidations(result);
        assertTrue(result.contains(emailPendo.getPendoTitle()));
        assertTrue(result.contains(emailPendo.getPendoMessage()));
    }

    private static void commonValidations(String result) {
        assertTrue(result.contains("baseline_01"));
        assertTrue(result.contains("Machine 1"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
