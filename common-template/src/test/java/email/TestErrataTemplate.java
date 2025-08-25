package email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.redhat.cloud.notifications.ingress.Action;
import helpers.ErrataTestHelpers;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestErrataTemplate extends EmailTemplatesRendererHelper {

    static final String NEW_SUBSCRIPTION_BUGFIX_ERRATA = "new-subscription-bugfix-errata";
    static final String NEW_SUBSCRIPTION_SECURITY_UPDATE_ERRATA = "new-subscription-security-errata";
    static final String NEW_SUBSCRIPTION_ENHANCEMENT_ERRATA = "new-subscription-enhancement-errata";

    private static final Action ACTION = ErrataTestHelpers.createErrataAction();

    @Override
    protected String getBundle() {
        return "subscription-services";
    }

    @Override
    protected String getApp() {
        return "errata-notifications";
    }

    @Override
    protected String getBundleDisplayName() {
        return "Subscription Services";
    }

    @Override
    protected String getAppDisplayName() {
        return "Errata";
    }

    @Test
    public void testNewSubscriptionBugfixErrataEmailTitle() {
        eventTypeDisplayName = "Subscription Bug Fixes";
        String result = generateEmailSubject(NEW_SUBSCRIPTION_BUGFIX_ERRATA, ACTION);
        assertEquals("Instant notification - Subscription Bug Fixes - Errata - Subscription Services", result);
    }

    @Test
    public void testNewSubscriptionBugfixErrataEmailBody() {
        String result = generateEmailBody(NEW_SUBSCRIPTION_BUGFIX_ERRATA, ACTION);
        assertTrue(result.contains("There are 4 bug fixes affecting your subscriptions."));
        assertTrue(result.contains("href=\"https://access.redhat.com/errata/RHSA-2024:3843\""));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("secalert@redhat.com"));
    }

    @Test
    public void testNewSubscriptionSecurityUpdateErrataEmailTitle() {
        eventTypeDisplayName = "Subscription Security Updates";
        String result = generateEmailSubject(NEW_SUBSCRIPTION_SECURITY_UPDATE_ERRATA, ACTION);
        assertEquals("Instant notification - Subscription Security Updates - Errata - Subscription Services", result);
    }

    @Test
    public void testNewSubscriptionSecurityUpdateErrataEmailBody() {
        String result = generateEmailBody(NEW_SUBSCRIPTION_SECURITY_UPDATE_ERRATA, ACTION);
        assertTrue(result.contains("There are 4 security updates affecting your subscriptions."));
        assertTrue(result.contains("href=\"https://access.redhat.com/errata/RHSA-2024:3843\""));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("secalert@redhat.com"));
        assertTrue(result.contains("Moderate"), "Security advisory severity has a dedicated column");
    }

    @Test
    public void testNewSubscriptionEnhancementErrataEmailTitle() {
        eventTypeDisplayName = "Subscription Enhancements";
        String result = generateEmailSubject(NEW_SUBSCRIPTION_ENHANCEMENT_ERRATA, ACTION);
        assertEquals("Instant notification - Subscription Enhancements - Errata - Subscription Services", result);
    }

    @Test
    public void testNewSubscriptionEnhancementErrataEmailBody() {
        String result = generateEmailBody(NEW_SUBSCRIPTION_ENHANCEMENT_ERRATA, ACTION);
        assertTrue(result.contains("There are 4 enhancements affecting your subscriptions."));
        assertTrue(result.contains("href=\"https://access.redhat.com/errata/RHSA-2024:3843\""));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("secalert@redhat.com"));
    }

    @Test
    public void testDailyDigestEmailBody() throws JsonProcessingException {
        final String result = generateAggregatedEmailBody(JSON_ERRATA_DEFAULT_AGGREGATION_CONTEXT);
        assertTrue(result.contains("There are 9 bug fixes affecting your subscriptions."));
        assertTrue(result.contains("There are 18 enhancements affecting your subscriptions."));
        assertTrue(result.contains("There are 24 security updates affecting your subscriptions"));
        assertEquals(51, StringUtils.countMatches(result, "https://access.redhat.com/errata/RHSA-2024:"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    public static final String JSON_ERRATA_DEFAULT_AGGREGATION_CONTEXT = "{" +
        "   \"errata\":{" +
        "      \"new-subscription-bugfix-errata\":[" +
        "         {" +
        "            \"id\":\"RHSA-2024:1074\"," +
        "            \"severity\":\"Moderate\"," +
        "            \"synopsis\":\"Red Hat build of Quarkus 3.8.4 release\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:279\"," +
        "            \"severity\":\"Important\"," +
        "            \"synopsis\":\"c-ares security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:5588\"," +
        "            \"severity\":\"Low\"," +
        "            \"synopsis\":\"cockpit security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:40\"," +
        "            \"severity\":\"Moderate\"," +
        "            \"synopsis\":\"Red Hat build of Quarkus 3.8.4 release\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:558\"," +
        "            \"severity\":\"Important\"," +
        "            \"synopsis\":\"c-ares security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:068\"," +
        "            \"severity\":\"Low\"," +
        "            \"synopsis\":\"cockpit security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:18\"," +
        "            \"severity\":\"Moderate\"," +
        "            \"synopsis\":\"Red Hat build of Quarkus 3.8.4 release\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:5579\"," +
        "            \"severity\":\"Important\"," +
        "            \"synopsis\":\"c-ares security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:1365\"," +
        "            \"severity\":\"Low\"," +
        "            \"synopsis\":\"cockpit security update\"" +
        "         }" +
        "      ]," +
        "      \"new-subscription-enhancement-errata\":[" +
        "         {" +
        "            \"id\":\"RHSA-2024:07\"," +
        "            \"severity\":\"Moderate\"," +
        "            \"synopsis\":\"Red Hat build of Quarkus 3.8.4 release\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:75\"," +
        "            \"severity\":\"Important\"," +
        "            \"synopsis\":\"c-ares security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:782\"," +
        "            \"severity\":\"Low\"," +
        "            \"synopsis\":\"cockpit security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:0\"," +
        "            \"severity\":\"Moderate\"," +
        "            \"synopsis\":\"Red Hat build of Quarkus 3.8.4 release\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:2921\"," +
        "            \"severity\":\"Important\"," +
        "            \"synopsis\":\"c-ares security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:737\"," +
        "            \"severity\":\"Low\"," +
        "            \"synopsis\":\"cockpit security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:82\"," +
        "            \"severity\":\"Moderate\"," +
        "            \"synopsis\":\"Red Hat build of Quarkus 3.8.4 release\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:8388\"," +
        "            \"severity\":\"Important\"," +
        "            \"synopsis\":\"c-ares security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:6\"," +
        "            \"severity\":\"Low\"," +
        "            \"synopsis\":\"cockpit security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:2\"," +
        "            \"severity\":\"Moderate\"," +
        "            \"synopsis\":\"Red Hat build of Quarkus 3.8.4 release\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:857\"," +
        "            \"severity\":\"Important\"," +
        "            \"synopsis\":\"c-ares security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:2\"," +
        "            \"severity\":\"Low\"," +
        "            \"synopsis\":\"cockpit security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:11\"," +
        "            \"severity\":\"Moderate\"," +
        "            \"synopsis\":\"Red Hat build of Quarkus 3.8.4 release\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:5339\"," +
        "            \"severity\":\"Important\"," +
        "            \"synopsis\":\"c-ares security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:970\"," +
        "            \"severity\":\"Low\"," +
        "            \"synopsis\":\"cockpit security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:666\"," +
        "            \"severity\":\"Moderate\"," +
        "            \"synopsis\":\"Red Hat build of Quarkus 3.8.4 release\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:2996\"," +
        "            \"severity\":\"Important\"," +
        "            \"synopsis\":\"c-ares security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:999\"," +
        "            \"severity\":\"Low\"," +
        "            \"synopsis\":\"cockpit security update\"" +
        "         }" +
        "      ]," +
        "      \"new-subscription-security-errata\":[" +
        "         {" +
        "            \"id\":\"RHSA-2024:0\"," +
        "            \"severity\":\"Moderate\"," +
        "            \"synopsis\":\"Red Hat build of Quarkus 3.8.4 release\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:835\"," +
        "            \"severity\":\"Important\"," +
        "            \"synopsis\":\"c-ares security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:521\"," +
        "            \"severity\":\"Low\"," +
        "            \"synopsis\":\"cockpit security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:3906\"," +
        "            \"severity\":\"Moderate\"," +
        "            \"synopsis\":\"Red Hat build of Quarkus 3.8.4 release\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:5568\"," +
        "            \"severity\":\"Important\"," +
        "            \"synopsis\":\"c-ares security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:699\"," +
        "            \"severity\":\"Low\"," +
        "            \"synopsis\":\"cockpit security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:86\"," +
        "            \"severity\":\"Moderate\"," +
        "            \"synopsis\":\"Red Hat build of Quarkus 3.8.4 release\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:49\"," +
        "            \"severity\":\"Important\"," +
        "            \"synopsis\":\"c-ares security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:81\"," +
        "            \"severity\":\"Low\"," +
        "            \"synopsis\":\"cockpit security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:95\"," +
        "            \"severity\":\"Moderate\"," +
        "            \"synopsis\":\"Red Hat build of Quarkus 3.8.4 release\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:7758\"," +
        "            \"severity\":\"Important\"," +
        "            \"synopsis\":\"c-ares security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:9409\"," +
        "            \"severity\":\"Low\"," +
        "            \"synopsis\":\"cockpit security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:34\"," +
        "            \"severity\":\"Moderate\"," +
        "            \"synopsis\":\"Red Hat build of Quarkus 3.8.4 release\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:573\"," +
        "            \"severity\":\"Important\"," +
        "            \"synopsis\":\"c-ares security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:47\"," +
        "            \"severity\":\"Low\"," +
        "            \"synopsis\":\"cockpit security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:1\"," +
        "            \"severity\":\"Moderate\"," +
        "            \"synopsis\":\"Red Hat build of Quarkus 3.8.4 release\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:8\"," +
        "            \"severity\":\"Important\"," +
        "            \"synopsis\":\"c-ares security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:728\"," +
        "            \"severity\":\"Low\"," +
        "            \"synopsis\":\"cockpit security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:132\"," +
        "            \"severity\":\"Moderate\"," +
        "            \"synopsis\":\"Red Hat build of Quarkus 3.8.4 release\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:1\"," +
        "            \"severity\":\"Important\"," +
        "            \"synopsis\":\"c-ares security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:0\"," +
        "            \"severity\":\"Low\"," +
        "            \"synopsis\":\"cockpit security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:461\"," +
        "            \"severity\":\"Moderate\"," +
        "            \"synopsis\":\"Red Hat build of Quarkus 3.8.4 release\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:6\"," +
        "            \"severity\":\"Important\"," +
        "            \"synopsis\":\"c-ares security update\"" +
        "         }," +
        "         {" +
        "            \"id\":\"RHSA-2024:816\"," +
        "            \"severity\":\"Low\"," +
        "            \"synopsis\":\"cockpit security update\"" +
        "         }" +
        "      ]," +
        "      \"base_url\":\"https://access.redhat.com/errata/\"" +
        "   }," +
        "   \"start_time\":null," +
        "   \"end_time\":null" +
        "}";
}
