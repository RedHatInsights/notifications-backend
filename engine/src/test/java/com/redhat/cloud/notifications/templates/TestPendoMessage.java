package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.DriftTestHelpers;
import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.processors.email.EmailPendo;
import io.quarkus.test.junit.QuarkusTest;
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

    @Test
    public void testInstantEmailBody() {
        Action action = DriftTestHelpers.createDriftAction("rhel", "drift", "host-01", "Machine 1");
        String result = generateEmailBody(EVENT_TYPE_NAME, action);
        assertTrue(result.contains("baseline_01"));
        assertTrue(result.contains("Machine 1"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertFalse(result.contains(GENERAL_PENDO_MESSAGE));

        EmailPendo emailPendo = new EmailPendo(GENERAL_PENDO_TITLE, GENERAL_PENDO_MESSAGE);

        result = generateEmailBody(EVENT_TYPE_NAME, action, emailPendo);
        assertTrue(result.contains("baseline_01"));
        assertTrue(result.contains("Machine 1"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains(GENERAL_PENDO_MESSAGE));
    }
}
