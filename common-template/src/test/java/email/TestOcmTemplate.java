package email;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.Severity;
import helpers.OcmTestHelpers;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestOcmTemplate extends EmailTemplatesRendererHelper {

    private static final String CLUSTER_UPDATE = "cluster-update";
    private static final String CLUSTER_LIFECYCLE = "cluster-lifecycle";
    private static final String CLUSTER_CUSTOMER_SUPPORT = "customer-support";
    private static final String CAPACITY_MANAGEMENT = "capacity-management";
    private static final String CLUSTER_ACCESS = "cluster-access";
    private static final String CLUSTER_ADD_ON = "cluster-add-on";
    private static final String CLUSTER_CONFIGURATION = "cluster-configuration";
    private static final String CLUSTER_NETWORKING = "cluster-networking";
    private static final String CLUSTER_OWNERSHIP = "cluster-ownership";
    private static final String CLUSTER_SCALING = "cluster-scaling";
    private static final String CLUSTER_SECURITY = "cluster-security";
    private static final String CLUSTER_SUBSCRIPTION = "cluster-subscription";
    private static final String GENERAL_NOTIFICATION = "general-notification";

    @Override
    protected String getBundle() {
        return "openshift";
    }

    @Override
    protected String getApp() {
        return "cluster-manager";
    }

    static final List<String> getIdenticalTemplateContentEventTypeNames() {
        return new ArrayList<>(Arrays.asList(CAPACITY_MANAGEMENT, CLUSTER_ACCESS, CLUSTER_ADD_ON, CLUSTER_CONFIGURATION, CLUSTER_NETWORKING, CLUSTER_OWNERSHIP, CLUSTER_SCALING, CLUSTER_SECURITY, CLUSTER_SUBSCRIPTION, GENERAL_NOTIFICATION));
    }

    @Test
    public void testUpgradeEmailTitle() {
        Action action = OcmTestHelpers.createOcmAction("Dummy cluster name", "OSDTrial", "<b>Dummy server name</b> need a revision", "Title provided by Cluster Manager", null, Optional.empty());
        eventTypeDisplayName = "Cluster Update";
        String result = generateEmailSubject(CLUSTER_UPDATE, action);
        assertEquals("Cluster Update - Title provided by Cluster Manager", result);

        // Test with Critical severity level (OCM severity Critical)
        action.setSeverity(Severity.CRITICAL.name());
        String criticalResult = generateEmailSubject(CLUSTER_UPDATE, action);
        assertEquals("[CRITICAL] Cluster Update - Title provided by Cluster Manager", criticalResult);

        // Test with Important severity level (OCM severity Major)
        action.setSeverity(Severity.IMPORTANT.name());
        String majorResult = generateEmailSubject(CLUSTER_UPDATE, action);
        assertEquals("[IMPORTANT] Cluster Update - Title provided by Cluster Manager", majorResult);

        // Test with Moderate severity level (OCM severity Warning)
        action.setSeverity(Severity.MODERATE.name());
        String warningResult = generateEmailSubject(CLUSTER_UPDATE, action);
        assertEquals("[MODERATE] Cluster Update - Title provided by Cluster Manager", warningResult);

        // Test with Low severity level (OCM severity Info)
        action.setSeverity(Severity.LOW.name());
        String infoResult = generateEmailSubject(CLUSTER_UPDATE, action);
        assertEquals("[LOW] Cluster Update - Title provided by Cluster Manager", infoResult);

        // Test with None severity level (OCM severity Debug)
        action.setSeverity(Severity.NONE.name());
        String debugResult = generateEmailSubject(CLUSTER_UPDATE, action);
        assertEquals("Cluster Update - Title provided by Cluster Manager", debugResult);

        // Test with Undefined severity level (should never be sent)
        action.setSeverity(Severity.UNDEFINED.name());
        String undefinedResult = generateEmailSubject(CLUSTER_UPDATE, action);
        assertEquals("Cluster Update - Title provided by Cluster Manager", undefinedResult);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testUpgradeScheduledInstantEmailBody(boolean useBetaTemplate) {
        Action action = OcmTestHelpers.createOcmAction("Dummy cluster name", "OSDTrial", "<b>Dummy server name</b> need a revision", "Title provided by Cluster Manager", "Upgrade scheduled", Optional.of(Map.of("template_sub_type", "upgrade-scheduled-template")));
        String result = generateEmailBody(CLUSTER_UPDATE, action, useBetaTemplate);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("Upgrade scheduled"));
        assertTrue(result.contains(((Map<String, String>) action.getEvents().get(0).getPayload().getAdditionalProperties().get("global_vars")).get("log_description")));
        assertTrue(result.contains("What can you expect"));
        assertTrue(result.contains("Thank you for choosing Red Hat OpenShift Dedicated Trial."));
        assertFalse(result.contains("Check these resources for more information"));
        assertTrue(result.contains("What should you do to minimize impact"));

        action = OcmTestHelpers.createOcmAction("Dummy cluster name", "OSDTrial", "<b>Dummy server name</b> need a revision", "Title provided by Cluster Manager", "Upgrade scheduled", Optional.of(Map.of("template_sub_type", "upgrade-scheduled-template", "doc_references", List.of("https://docs.redhat.com/en/documentation/red_hat_openshift_service_on_aws_classic_architecture/4/html/red_hat_openshift_cluster_manager/ocm-overview", "https://console.redhat.com/openshift"))));
        result = generateEmailBody(CLUSTER_UPDATE, action, useBetaTemplate);
        assertTrue(result.contains("Check these resources for more information"));
        assertTrue(result.contains("https://docs.redhat.com/en/documentation/red_hat_openshift_service_on_aws_classic_architecture/4/html/red_hat_openshift_cluster_manager/ocm-overview"));
        assertTrue(result.contains("https://console.redhat.com/openshift"));

        action = OcmTestHelpers.createOcmAction("Dummy cluster name", "OSDTrial", "<b>Dummy server name</b> need a revision", "Title provided by Cluster Manager", "Upgrade scheduled", Optional.of(Map.of("template_sub_type", "upgrade-scheduled-template-rosa-hcp")));
        result = generateEmailBody(CLUSTER_UPDATE, action, useBetaTemplate);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("Upgrade scheduled"));
        assertTrue(result.contains(((Map<String, String>) action.getEvents().get(0).getPayload().getAdditionalProperties().get("global_vars")).get("log_description")));
        assertTrue(result.contains("Thank you for choosing Red Hat OpenShift Dedicated Trial."));
        assertFalse(result.contains("What should you do to minimize impact"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testUpgradeEndedInstantEmailBody(boolean useBetaTemplate) {
        Action action = OcmTestHelpers.createOcmAction("Dummy cluster name", "MOA", "<b>Dummy server name</b> is ready to go", "Title provided by Cluster Manager", null, Optional.of(Map.of("template_sub_type", "upgrade-ended-template")));
        String result = generateEmailBody(CLUSTER_UPDATE, action, useBetaTemplate);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("Title provided by Cluster Manager"));
        assertTrue(result.contains(((Map<String, String>) action.getEvents().get(0).getPayload().getAdditionalProperties().get("global_vars")).get("log_description")));
        assertFalse(result.contains("What can you expect"));
        assertTrue(result.contains("Thank you for choosing Red Hat OpenShift Service on AWS."));
        assertFalse(result.contains("Check these resources for more information"));

        Map<String, Object> additionalMapParameters = new HashMap<>();
        additionalMapParameters.put("template_sub_type", "upgrade-ended-template");
        additionalMapParameters.put("doc_references", null);
        action = OcmTestHelpers.createOcmAction("Dummy cluster name", "MOA", "<b>Dummy server name</b> is ready to go", "Title provided by Cluster Manager", null, Optional.of(additionalMapParameters));
        result = generateEmailBody(CLUSTER_UPDATE, action, useBetaTemplate);
        assertFalse(result.contains("Check these resources for more information"));

        action = OcmTestHelpers.createOcmAction("Dummy cluster name", "MOA", "<b>Dummy server name</b> is ready to go", "Title provided by Cluster Manager", null, Optional.of(Map.of("template_sub_type", "osd-trial-deletion-template", "doc_references", List.of("https://docs.redhat.com/en/documentation/red_hat_openshift_service_on_aws_classic_architecture/4/html/red_hat_openshift_cluster_manager/ocm-overview", "https://console.redhat.com/openshift"))));
        result = generateEmailBody(CLUSTER_UPDATE, action, useBetaTemplate);
        assertTrue(result.contains("Check these resources for more information"));
        assertTrue(result.contains("https://docs.redhat.com/en/documentation/red_hat_openshift_service_on_aws_classic_architecture/4/html/red_hat_openshift_cluster_manager/ocm-overview"));
        assertTrue(result.contains("https://console.redhat.com/openshift"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testApprovedAccessEmailBody(boolean useBetaTemplate) {
        // test generic template case
        Action action = OcmTestHelpers.createOcmAction("Dummy cluster name", "MOA", "<b>Dummy server name</b> need a revision", "Title provided by Cluster Manager", null, Optional.of(Map.of("template_sub_type", "ocm-approved-access-template")));
        String result = generateEmailBody(CLUSTER_CUSTOMER_SUPPORT, action, useBetaTemplate);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("Title provided by Cluster Manager"));
        assertTrue(result.contains("This notification is for your"));
        assertFalse(result.contains("Welcome to your OpenShift Dedicated"));
        assertFalse(result.contains("We are notifying you about your"));
        assertFalse(result.contains("Thank you for trialing OpenShift Dedicated"));

        assertTrue(result.contains("Your organization has enabled \"Approved Access\" for ROSA clusters"));
        assertFalse(result.contains("Your subscription provides"));
        assertFalse(result.contains("To learn more about the OpenShift Dedicated trial"));
        assertFalse(result.contains("You will be notified once your cluster is deleted"));
        assertFalse(result.contains("about OpenShift Dedicated, and create a new cluster at any time"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testClusterLifecycleInstantEmailBody(boolean useBetaTemplate) {
        // test generic template case
        Action action = OcmTestHelpers.createOcmAction("Dummy cluster name", "OSD", "<b>Dummy server name</b> need a revision", "Title provided by Cluster Manager");
        String result = generateEmailBody(CLUSTER_LIFECYCLE, action, useBetaTemplate);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("Title provided by Cluster Manager"));
        assertTrue(result.contains("This notification is for your"));
        assertFalse(result.contains("Welcome to your OpenShift Dedicated"));
        assertFalse(result.contains("We are notifying you about your"));
        assertFalse(result.contains("Thank you for trialing OpenShift Dedicated"));

        assertTrue(result.contains("Your subscription provides"));
        assertFalse(result.contains("To learn more about the OpenShift Dedicated trial"));
        assertFalse(result.contains("You will be notified once your cluster is deleted"));
        assertFalse(result.contains("about OpenShift Dedicated, and create a new cluster at any time"));

        // test generic template case with osd trial
        action = OcmTestHelpers.createOcmAction("Dummy cluster name", "OSDTrial", "<b>Dummy server name</b> need a revision", "Title provided by Cluster Manager");
        result = generateEmailBody(CLUSTER_LIFECYCLE, action, useBetaTemplate);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("Title provided by Cluster Manager"));
        assertTrue(result.contains("This notification is for your"));
        assertFalse(result.contains("Welcome to your OpenShift Dedicated"));
        assertFalse(result.contains("We are notifying you about your"));
        assertFalse(result.contains("Thank you for trialing OpenShift Dedicated"));

        assertFalse(result.contains("Your subscription provides"));
        assertFalse(result.contains("To learn more about the OpenShift Dedicated trial"));
        assertFalse(result.contains("You will be notified once your cluster is deleted"));
        assertFalse(result.contains("about OpenShift Dedicated, and create a new cluster at any time"));

        // test generic template case with trial_creation subtype
        action = OcmTestHelpers.createOcmAction("Dummy cluster name", "OSDTrial", "<b>Dummy server name</b> need a revision", "Title provided by Cluster Manager", "Trial creation", Optional.of(Map.of("template_sub_type", "osd-trial-creation-template")));
        result = generateEmailBody(CLUSTER_LIFECYCLE, action, useBetaTemplate);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("Trial creation"));
        assertFalse(result.contains("Title provided by Cluster Manager"));
        assertFalse(result.contains("This notification is for your"));
        assertTrue(result.contains("Welcome to your OpenShift Dedicated"));
        assertTrue(result.contains("We are notifying you about your"));
        assertFalse(result.contains("Thank you for trialing OpenShift Dedicated"));

        assertFalse(result.contains("Your subscription provides"));
        assertTrue(result.contains("To learn more about the OpenShift Dedicated trial"));
        assertFalse(result.contains("You will be notified once your cluster is deleted"));
        assertFalse(result.contains("about OpenShift Dedicated, and create a new cluster at any time"));

        // test generic template case with trial_reminder subtype
        action = OcmTestHelpers.createOcmAction("Dummy cluster name", "OSDTrial", "<b>Dummy server name</b> need a revision", "Title provided by Cluster Manager", "Trial reminder", Optional.of(Map.of("template_sub_type", "osd-trial-reminder-template")));
        result = generateEmailBody(CLUSTER_LIFECYCLE, action, useBetaTemplate);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("Trial reminder"));
        assertFalse(result.contains("Title provided by Cluster Manager"));
        assertFalse(result.contains("This notification is for your"));
        assertFalse(result.contains("Welcome to your OpenShift Dedicated"));
        assertTrue(result.contains("We are notifying you about your"));
        assertFalse(result.contains("Thank you for trialing OpenShift Dedicated"));

        assertFalse(result.contains("Your subscription provides"));
        assertFalse(result.contains("To learn more about the OpenShift Dedicated trial"));
        assertTrue(result.contains("You will be notified once your cluster is deleted"));
        assertFalse(result.contains("about OpenShift Dedicated, and create a new cluster at any time"));

        // test generic template case with trial_delete subtype
        action = OcmTestHelpers.createOcmAction("Dummy cluster name", "OSDTrial", "<b>Dummy server name</b> need a revision", "Title provided by Cluster Manager", "Trial delete", Optional.of(Map.of("template_sub_type", "osd-trial-deletion-template")));
        result = generateEmailBody(CLUSTER_LIFECYCLE, action, useBetaTemplate);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("Trial delete"));
        assertFalse(result.contains("Title provided by Cluster Manager"));
        assertFalse(result.contains("This notification is for your"));
        assertFalse(result.contains("Welcome to your OpenShift Dedicated"));
        assertFalse(result.contains("We are notifying you about your"));
        assertTrue(result.contains("Thank you for trialing OpenShift Dedicated"));

        assertFalse(result.contains("Your subscription provides"));
        assertFalse(result.contains("To learn more about the OpenShift Dedicated trial"));
        assertFalse(result.contains("You will be notified once your cluster is deleted"));
        assertTrue(result.contains("about OpenShift Dedicated, and create a new cluster at any time"));
        assertFalse(result.contains("Check these resources for more information"));

        Map<String, Object> additionalMapParameters = new HashMap<>();
        additionalMapParameters.put("template_sub_type", "osd-trial-deletion-template");
        additionalMapParameters.put("doc_references", null);
        action = OcmTestHelpers.createOcmAction("Dummy cluster name", "OSDTrial", "<b>Dummy server name</b> need a revision", "Title provided by Cluster Manager", "Trial delete", Optional.of(additionalMapParameters));
        result = generateEmailBody(CLUSTER_LIFECYCLE, action, useBetaTemplate);
        assertFalse(result.contains("Check these resources for more information"));

        action = OcmTestHelpers.createOcmAction("Dummy cluster name", "OSDTrial", "<b>Dummy server name</b> need a revision", "Title provided by Cluster Manager", "Trial delete", Optional.of(Map.of("template_sub_type", "osd-trial-deletion-template", "doc_references", List.of("https://docs.redhat.com/en/documentation/red_hat_openshift_service_on_aws_classic_architecture/4/html/red_hat_openshift_cluster_manager/ocm-overview", "https://console.redhat.com/openshift"))));
        result = generateEmailBody(CLUSTER_LIFECYCLE, action, useBetaTemplate);
        assertTrue(result.contains("Check these resources for more information"));
        assertTrue(result.contains("https://docs.redhat.com/en/documentation/red_hat_openshift_service_on_aws_classic_architecture/4/html/red_hat_openshift_cluster_manager/ocm-overview"));
        assertTrue(result.contains("https://console.redhat.com/openshift"));
    }

    @ParameterizedTest
    @MethodSource("getIdenticalTemplateContentEventTypeNames")
    public void testIdenticalInstantEmailBody(String eventType) {
        testIdenticalInstantEmailBody(eventType, true);
        testIdenticalInstantEmailBody(eventType, false);
    }

    public void testIdenticalInstantEmailBody(String eventType, boolean useBetaTemplate) {
        // test generic template case
        Action action = OcmTestHelpers.createOcmAction("Dummy cluster name", "OSD", "<b>Dummy server name</b> need a revision", "Title provided by Cluster Manager");
        String result = generateEmailBody(eventType, action, useBetaTemplate);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("Title provided by Cluster Manager"));
        assertTrue(result.contains("This notification is for your"));
        assertFalse(result.contains("Welcome to your OpenShift Dedicated"));
        assertFalse(result.contains("We are notifying you about your"));
        assertFalse(result.contains("Thank you for trialing OpenShift Dedicated"));

        assertTrue(result.contains("Your subscription provides"));
        assertFalse(result.contains("To learn more about the OpenShift Dedicated trial"));
        assertFalse(result.contains("You will be notified once your cluster is deleted"));
        assertFalse(result.contains("about OpenShift Dedicated, and create a new cluster at any time"));

        // test generic template case with osd trial
        action = OcmTestHelpers.createOcmAction("Dummy cluster name", "OSDTrial", "<b>Dummy server name</b> need a revision", "Title provided by Cluster Manager");
        result = generateEmailBody(eventType, action, useBetaTemplate);
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("Title provided by Cluster Manager"));
        assertTrue(result.contains("This notification is for your"));
        assertFalse(result.contains("Welcome to your OpenShift Dedicated"));
        assertFalse(result.contains("We are notifying you about your"));
        assertFalse(result.contains("Thank you for trialing OpenShift Dedicated"));

        assertFalse(result.contains("Your subscription provides"));
        assertFalse(result.contains("To learn more about the OpenShift Dedicated trial"));
        assertFalse(result.contains("You will be notified once your cluster is deleted"));
        assertFalse(result.contains("about OpenShift Dedicated, and create a new cluster at any time"));
    }
}
