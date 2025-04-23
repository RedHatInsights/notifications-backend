package com.redhat.cloud.notifications.templates.common;

import com.redhat.cloud.notifications.EmailTemplatesRendererHelper;
import com.redhat.cloud.notifications.PatchTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.processors.email.EmailPendo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.util.List;

import static com.redhat.cloud.notifications.processors.email.EmailPendoResolver.GENERAL_PENDO_MESSAGE;
import static com.redhat.cloud.notifications.processors.email.EmailPendoResolver.GENERAL_PENDO_TITLE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestPendoMessage extends EmailTemplatesRendererHelper {

    private static final String EVENT_TYPE_NAME = "new-advisory";

    @Inject
    Environment environment;

    @Override
    protected String getApp() {
        return "patch";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(EVENT_TYPE_NAME);
    }

    @Test
    public void testInstantEmailBody() {
        EmailPendo emailPendo = new EmailPendo(GENERAL_PENDO_TITLE, String.format(GENERAL_PENDO_MESSAGE, environment.url()));

        Action action = PatchTestHelpers.createPatchAction();
        String result = generateEmailBody(EVENT_TYPE_NAME, action);
        commonValidations(result);
        assertFalse(result.contains(emailPendo.getPendoTitle()));
        assertFalse(result.contains(emailPendo.getPendoMessage()));

        result = generateEmailBody(EVENT_TYPE_NAME, action, emailPendo, false);
        commonValidations(result);
        assertTrue(result.contains(emailPendo.getPendoTitle()));
        assertTrue(result.contains(emailPendo.getPendoMessage()));
    }

    private static void commonValidations(String result) {
        assertTrue(result.contains("name 1"));
        assertTrue(result.contains("synopsis 2"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
