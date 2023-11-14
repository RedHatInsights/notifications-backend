package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.OcmTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestOcmTemplate extends EmailTemplatesInDbHelper  {

    private static final String EVENT_TYPE_NAME = "cluster-update";

    @Override
    protected String getBundle() {
        return "openshift";
    }

    @Override
    protected String getApp() {
        return "cluster-manager";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(EVENT_TYPE_NAME);
    }

    @Test
    public void testUpgradeEmailTitle() {
        Action action = OcmTestHelpers.createOcmAction("Batcave", "OSDTrial", "<b>Batmobile</b> need a revision", "scheduled", "Awesome subject");

        String result = generateEmailSubject(EVENT_TYPE_NAME, action);
        assertEquals("Awesome subject", result);
    }

    @Test
    public void testUpgradeScheduledInstantEmailBody() {
        Action action = OcmTestHelpers.createOcmAction("Batcave", "OSDTrial", "<b>Batmobile</b> need a revision", "scheduled", "Awesome subject");
        String result = generateEmailBody(EVENT_TYPE_NAME, action);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("Upgrade scheduled"));
        assertTrue(result.contains(((Map<String, String>) action.getEvents().get(0).getPayload().getAdditionalProperties().get("global_vars")).get("log_description")));
        assertTrue(result.contains("What can you expect"));
        assertTrue(result.contains("Thank you for choosing Red Hat OpenShift Dedicated Trial."));
    }

    @Test
    public void testUpgradeEndedInstantEmailBody() {
        Action action = OcmTestHelpers.createOcmAction("Batcave", "MOA", "<b>Batmobile</b> is ready to go", "ended", "Awesome subject");
        String result = generateEmailBody(EVENT_TYPE_NAME, action);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("Upgrade ended"));
        assertTrue(result.contains(((Map<String, String>) action.getEvents().get(0).getPayload().getAdditionalProperties().get("global_vars")).get("log_description")));
        assertFalse(result.contains("What can you expect"));
        assertTrue(result.contains("Thank you for choosing Red Hat OpenShift Service on AWS."));
    }
}
