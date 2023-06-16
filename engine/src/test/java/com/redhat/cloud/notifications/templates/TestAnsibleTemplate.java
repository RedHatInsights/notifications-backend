package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.List;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestAnsibleTemplate extends EmailTemplatesInDbHelper {

    static final String REPORT_AVAILABLE_EVENT = "report-available";

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    EntityManager entityManager;

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
        return List.of(REPORT_AVAILABLE_EVENT);
    }

    @Test
    public void testInstantEmailTitle() {
        Action action = TestHelpers.createAnsibleAction(null);

        String result = generateEmailSubject(REPORT_AVAILABLE_EVENT, action);
        assertTrue(result.contains("Ansible"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setAnsibleEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailSubject(REPORT_AVAILABLE_EVENT, action);
        assertEquals("Instant notification - Ansible", result);
    }

    @Test
    public void testInstantEmailBody() {
        Action action = TestHelpers.createAnsibleAction("reportUrl");

        String result = generateEmailBody(REPORT_AVAILABLE_EVENT, action);
        assertTrue(result.contains("/ansible/insights/reports/reportUrl"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setAnsibleEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailBody(REPORT_AVAILABLE_EVENT, action);
        assertTrue(result.contains("/ansible/insights/reports/reportUrl"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
