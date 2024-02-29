package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.OcmTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.processors.email.EmailPendo;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.List;

import static com.redhat.cloud.notifications.processors.email.EmailActorsResolver.OCM_PENDO_MESSAGE;
import static com.redhat.cloud.notifications.processors.email.EmailActorsResolver.OCM_PENDO_TITLE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestPendoMessageOcm extends EmailTemplatesInDbHelper  {

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
        Action action = OcmTestHelpers.createOcmAction("Batcave", "OSD", "<b>Batmobile</b> need a revision", "Awesome subject");
        String result = generateEmailBody(CLUSTER_LIFECYCLE, action);
        assertTrue(result.contains("Batmobile"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertFalse(result.contains(OCM_PENDO_MESSAGE));

        EmailPendo emailPendo = new EmailPendo(OCM_PENDO_TITLE, OCM_PENDO_MESSAGE);

        result = generateEmailBody(CLUSTER_LIFECYCLE, action, emailPendo);
        assertTrue(result.contains("Batmobile"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains(OCM_PENDO_MESSAGE));
    }
}
