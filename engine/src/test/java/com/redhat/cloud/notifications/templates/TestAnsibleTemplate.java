package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.templates.models.Environment;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestAnsibleTemplate {

    @Inject
    Environment environment;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    Ansible ansible;

    @AfterEach
    void afterEach() {
        featureFlipper.setAnsibleEmailTemplatesV2Enabled(false);
    }

    @Test
    public void testInstantEmailTitle() {
        Action action = TestHelpers.createAnsibleAction(null);
        String result = ansible.getTitle(Ansible.REPORT_AVAILABLE_EVENT, EmailSubscriptionType.INSTANT)
            .data("action", action)
            .data("environment", environment)
            .render();
        assertTrue(result.contains("Ansible"));

        // test template V2
        featureFlipper.setAnsibleEmailTemplatesV2Enabled(true);
        result = ansible.getTitle(Ansible.REPORT_AVAILABLE_EVENT, EmailSubscriptionType.INSTANT)
            .data("action", action)
            .data("environment", environment)
            .render();
        assertEquals("Instant notification - Ansible", result);

        assertThrows(UnsupportedOperationException.class, () -> {
            ansible.getTitle("unknown-event-type", EmailSubscriptionType.INSTANT)
                .data("action", action)
                .data("environment", environment)
                .render();
        });
    }

    @Test
    public void testInstantEmailBody() {
        Action action = TestHelpers.createAnsibleAction("reportUrl");
        String result = ansible.getBody(Ansible.REPORT_AVAILABLE_EVENT, EmailSubscriptionType.INSTANT)
            .data("action", action)
            .data("environment", environment)
            .render();
        assertTrue(result.contains("/ansible/insights/reports/reportUrl"));

        // test template V2
        featureFlipper.setAnsibleEmailTemplatesV2Enabled(true);
        result = ansible.getBody(Ansible.REPORT_AVAILABLE_EVENT, EmailSubscriptionType.INSTANT)
            .data("action", action)
            .data("environment", environment)
            .render();
        assertTrue(result.contains("/ansible/insights/reports/reportUrl"));
        assertTrue(result.contains(TemplateService.HCC_LOGO_TARGET));

        assertThrows(UnsupportedOperationException.class, () -> {
            ansible.getBody("unknown-event-type", EmailSubscriptionType.INSTANT)
                .data("action", action)
                .data("environment", environment)
                .render();
        });
    }
}
