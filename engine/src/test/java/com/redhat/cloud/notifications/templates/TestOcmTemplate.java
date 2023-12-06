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

    private static final String CLUSTER_UPDATE = "cluster-update";
    private static final String CLUSTER_LIFECYCLE = "cluster-lifecycle";

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
        return List.of(CLUSTER_UPDATE, CLUSTER_LIFECYCLE);
    }

    @Test
    public void testUpgradeEmailTitle() {
        Action action = OcmTestHelpers.createOcmAction("Batcave", "OSDTrial", "<b>Batmobile</b> need a revision", "Awesome subject", null, null);

        String result = generateEmailSubject(CLUSTER_UPDATE, action);
        assertEquals("Awesome subject", result);
    }

    @Test
    public void testUpgradeScheduledInstantEmailBody() {
        Action action = OcmTestHelpers.createOcmAction("Batcave", "OSDTrial", "<b>Batmobile</b> need a revision", "Awesome subject", "upgrade-scheduled-template", "Upgrade scheduled");
        String result = generateEmailBody(CLUSTER_UPDATE, action);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("Upgrade scheduled"));
        assertTrue(result.contains(((Map<String, String>) action.getEvents().get(0).getPayload().getAdditionalProperties().get("global_vars")).get("log_description")));
        assertTrue(result.contains("What can you expect"));
        assertTrue(result.contains("Thank you for choosing Red Hat OpenShift Dedicated Trial."));
    }

    @Test
    public void testUpgradeEndedInstantEmailBody() {
        Action action = OcmTestHelpers.createOcmAction("Batcave", "MOA", "<b>Batmobile</b> is ready to go", "Awesome subject", "upgrade-ended-template", null);
        String result = generateEmailBody(CLUSTER_UPDATE, action);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("Awesome subject"));
        assertTrue(result.contains(((Map<String, String>) action.getEvents().get(0).getPayload().getAdditionalProperties().get("global_vars")).get("log_description")));
        assertFalse(result.contains("What can you expect"));
        assertTrue(result.contains("Thank you for choosing Red Hat OpenShift Service on AWS."));
    }

    @Test
    public void testClusterLifecycleInstantEmailBody() {
        // test generic template case
        Action action = OcmTestHelpers.createOcmAction("Batcave", "OSD", "<b>Batmobile</b> need a revision", "Awesome subject");
        String result = generateEmailBody(CLUSTER_LIFECYCLE, action);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("Awesome subject"));
        assertTrue(result.contains("This notification is for your"));
        assertFalse(result.contains("Welcome to your OpenShift Dedicated"));
        assertFalse(result.contains("We are notifying you about your"));
        assertFalse(result.contains("Thank you for trialing OpenShift Dedicated"));

        assertTrue(result.contains("Your subscription provides"));
        assertFalse(result.contains("To learn more about the OpenShift Dedicated trial"));
        assertFalse(result.contains("You will be notified once your cluster is deleted"));
        assertFalse(result.contains("about OpenShift Dedicated, and create a new cluster at any time"));

        // test generic template case with osd trial
        action = OcmTestHelpers.createOcmAction("Batcave", "OSDTrial", "<b>Batmobile</b> need a revision", "Awesome subject");
        result = generateEmailBody(CLUSTER_LIFECYCLE, action);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("Awesome subject"));
        assertTrue(result.contains("This notification is for your"));
        assertFalse(result.contains("Welcome to your OpenShift Dedicated"));
        assertFalse(result.contains("We are notifying you about your"));
        assertFalse(result.contains("Thank you for trialing OpenShift Dedicated"));

        assertFalse(result.contains("Your subscription provides"));
        assertFalse(result.contains("To learn more about the OpenShift Dedicated trial"));
        assertFalse(result.contains("You will be notified once your cluster is deleted"));
        assertFalse(result.contains("about OpenShift Dedicated, and create a new cluster at any time"));

        // test generic template case with trial_creation subtype
        action = OcmTestHelpers.createOcmAction("Batcave", "OSDTrial", "<b>Batmobile</b> need a revision", "Awesome subject", "osd-trial-creation-template", "Trial creation");
        result = generateEmailBody(CLUSTER_LIFECYCLE, action);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("Trial creation"));
        assertFalse(result.contains("Awesome subject"));
        assertFalse(result.contains("This notification is for your"));
        assertTrue(result.contains("Welcome to your OpenShift Dedicated"));
        assertTrue(result.contains("We are notifying you about your"));
        assertFalse(result.contains("Thank you for trialing OpenShift Dedicated"));

        assertFalse(result.contains("Your subscription provides"));
        assertTrue(result.contains("To learn more about the OpenShift Dedicated trial"));
        assertFalse(result.contains("You will be notified once your cluster is deleted"));
        assertFalse(result.contains("about OpenShift Dedicated, and create a new cluster at any time"));

        // test generic template case with trial_reminder subtype
        action = OcmTestHelpers.createOcmAction("Batcave", "OSDTrial", "<b>Batmobile</b> need a revision", "Awesome subject", "osd-trial-reminder-template", "Trial reminder");
        result = generateEmailBody(CLUSTER_LIFECYCLE, action);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("Trial reminder"));
        assertFalse(result.contains("Awesome subject"));
        assertFalse(result.contains("This notification is for your"));
        assertFalse(result.contains("Welcome to your OpenShift Dedicated"));
        assertTrue(result.contains("We are notifying you about your"));
        assertFalse(result.contains("Thank you for trialing OpenShift Dedicated"));

        assertFalse(result.contains("Your subscription provides"));
        assertFalse(result.contains("To learn more about the OpenShift Dedicated trial"));
        assertTrue(result.contains("You will be notified once your cluster is deleted"));
        assertFalse(result.contains("about OpenShift Dedicated, and create a new cluster at any time"));

        // test generic template case with trial_delete subtype
        action = OcmTestHelpers.createOcmAction("Batcave", "OSDTrial", "<b>Batmobile</b> need a revision", "Awesome subject", "osd-trial-deletion-template", "Trial delete");
        result = generateEmailBody(CLUSTER_LIFECYCLE, action);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("Trial delete"));
        assertFalse(result.contains("Awesome subject"));
        assertFalse(result.contains("This notification is for your"));
        assertFalse(result.contains("Welcome to your OpenShift Dedicated"));
        assertFalse(result.contains("We are notifying you about your"));
        assertTrue(result.contains("Thank you for trialing OpenShift Dedicated"));

        assertFalse(result.contains("Your subscription provides"));
        assertFalse(result.contains("To learn more about the OpenShift Dedicated trial"));
        assertFalse(result.contains("You will be notified once your cluster is deleted"));
        assertTrue(result.contains("about OpenShift Dedicated, and create a new cluster at any time"));
    }
}
