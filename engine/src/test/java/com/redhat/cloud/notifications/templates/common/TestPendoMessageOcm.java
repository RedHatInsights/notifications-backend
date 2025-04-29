package com.redhat.cloud.notifications.templates.common;

import com.redhat.cloud.notifications.EmailTemplatesRendererHelper;
import com.redhat.cloud.notifications.OcmTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.processors.email.EmailPendo;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestPendoMessageOcm extends EmailTemplatesRendererHelper {

    private static final String CLUSTER_LIFECYCLE = "cluster-lifecycle";

    @Override
    protected String getBundle() {
        return "openshift";
    }

    @Override
    protected String getApp() {
        return "cluster-manager";
    }

    protected List<String> getUsedEventTypeNames() {
        return List.of(CLUSTER_LIFECYCLE);
    }

    @Test
    public void testInstantEmailBody() {
        EmailPendo emailPendo = new EmailPendo("OCM_PENDO_TITLE", "OCM_PENDO_MESSAGE");

        Action action = OcmTestHelpers.createOcmAction("Batcave", "OSD", "<b>Batmobile</b> need a revision", "Awesome subject");
        String result = generateEmailBody(CLUSTER_LIFECYCLE, action);
        commonValidations(result);
        assertFalse(result.contains(emailPendo.getPendoTitle()));
        assertFalse(result.contains(emailPendo.getPendoMessage()));

        result = generateEmailBody(CLUSTER_LIFECYCLE, action, emailPendo, false);
        commonValidations(result);
        assertTrue(result.contains(emailPendo.getPendoTitle()));
        assertTrue(result.contains(emailPendo.getPendoMessage()));
    }

    private static void commonValidations(String result) {
        assertTrue(result.contains("Batmobile"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
