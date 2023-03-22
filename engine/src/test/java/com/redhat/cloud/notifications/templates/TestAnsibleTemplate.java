package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import java.util.List;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestAnsibleTemplate extends EmailTemplatesInDbHelper {

    @Inject
    FeatureFlipper featureFlipper;

    @AfterEach
    void afterEach() {
        featureFlipper.setAnsibleEmailTemplatesV2Enabled(false);
        migrate();
    }

    @Override
    protected String getBundle() {
        return "ansible";
    }

    @Override
    protected String getApp() {
        return "reports";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(Ansible.REPORT_AVAILABLE_EVENT);
    }

    @Test
    public void testInstantEmailTitle() {
        Action action = TestHelpers.createAnsibleAction(null);
        statelessSessionFactory.withSession(statelessSession -> {

            String result = generateEmailSubject(Ansible.REPORT_AVAILABLE_EVENT, action);
            assertTrue(result.contains("Ansible"));

            featureFlipper.setAnsibleEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailSubject(Ansible.REPORT_AVAILABLE_EVENT, action);
            assertEquals("Instant notification - Ansible", result);
        });
    }

    @Test
    public void testInstantEmailBody() {
        Action action = TestHelpers.createAnsibleAction("reportUrl");
        statelessSessionFactory.withSession(statelessSession -> {

            String result = generateEmailBody(Ansible.REPORT_AVAILABLE_EVENT, action);
            assertTrue(result.contains("/ansible/insights/reports/reportUrl"));

            featureFlipper.setAnsibleEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailBody(Ansible.REPORT_AVAILABLE_EVENT, action);
            assertTrue(result.contains("/ansible/insights/reports/reportUrl"));
            assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        });
    }
}
