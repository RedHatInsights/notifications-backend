package com.redhat.cloud.notifications.templates.common;

import com.redhat.cloud.notifications.EmailTemplatesRendererHelper;
import com.redhat.cloud.notifications.ErrataTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.ErrataTestHelpers.buildErrataAggregatedPayload;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TestErrataTemplate extends EmailTemplatesRendererHelper {

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
        assertTrue(result.contains("There are 3 bug fixes affecting your subscriptions."));
        assertTrue(result.contains("href=\"https://access.redhat.com/errata/RHSA-2024:"));
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
        assertTrue(result.contains("There are 3 security updates affecting your subscriptions."));
        assertTrue(result.contains("href=\"https://access.redhat.com/errata/RHSA-2024:"));
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
        assertTrue(result.contains("There are 3 enhancements affecting your subscriptions."));
        assertTrue(result.contains("href=\"https://access.redhat.com/errata/RHSA-2024:"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("secalert@redhat.com"));
    }

    @Test
    public void testDailyDigestEmailBody() {
        String result = generateAggregatedEmailBody(buildErrataAggregatedPayload());
        assertTrue(result.contains("There are 9 bug fixes affecting your subscriptions."));
        assertTrue(result.contains("There are 18 enhancements affecting your subscriptions."));
        assertTrue(result.contains("There are 24 security updates affecting your subscriptions"));
        assertEquals(51, StringUtils.countMatches(result, "https://access.redhat.com/errata/RHSA-2024:"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
