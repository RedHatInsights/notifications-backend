package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.templates.models.Environment;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestAnsibleTemplate {

    @Inject
    Environment environment;

    @Test
    public void testInstantEmailTitle() {
        Action action = TestHelpers.createAnsibleAction("", "", "", "reportUrl");
        String result = Ansible.Templates.instantEmailTitle()
            .data("action", action)
            .data("environment", environment)
            .render();
        assertTrue(result.contains("Ansible"));
    }

    @Test
    public void testInstantEmailBody() {
        Action action = TestHelpers.createAnsibleAction("", "", "", "reportUrl");
        String result = Ansible.Templates.instantEmailBody()
                .data("action", action)
                .data("environment", environment)
                .render();
        assertTrue(result.contains("/ansible/insights/reports/reportUrl"));
    }

    @Test
    public void testInstantEmailTitleV2() {
        Action action = TestHelpers.createAnsibleAction("", "", "", "reportUrl");
        String result = Ansible.Templates.instantEmailTitleV2()
            .data("action", action)
            .data("environment", environment)
            .render();
        assertEquals("Instant notification - Ansible", result);
    }

    @Test
    public void testInstantEmailBodyV2() {
        Action action = TestHelpers.createAnsibleAction("", "", "", "reportUrl");
        String result = Ansible.Templates.instantEmailBodyV2()
            .data("action", action)
            .data("environment", environment)
            .render();
        assertTrue(result.contains("/ansible/insights/reports/reportUrl"));
    }
}
