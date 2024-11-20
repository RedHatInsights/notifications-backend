package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.ErrataTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestErrataTemplate extends EmailTemplatesInDbHelper {

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
    protected List<String> getUsedEventTypeNames() {
        return List.of(NEW_SUBSCRIPTION_BUGFIX_ERRATA, NEW_SUBSCRIPTION_SECURITY_UPDATE_ERRATA, NEW_SUBSCRIPTION_ENHANCEMENT_ERRATA);
    }

    @Test
    public void testNewSubscriptionBugfixErrataEmailTitle() {
        String result = generateEmailSubject(NEW_SUBSCRIPTION_BUGFIX_ERRATA, ACTION);
        assertEquals("Instant notification - Bug fixes - Errata - Subscription Services", result);
    }

    @Test
    public void testNewSubscriptionBugfixErrataEmailBody() {
        String result = generateEmailBody(NEW_SUBSCRIPTION_BUGFIX_ERRATA, ACTION);
        assertTrue(result.contains("There are 3 bug fixes affecting your subscriptions."));
        assertTrue(result.contains("href=\"https://access.redhat.com/errata/RHSA-2024:3843\""));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("secalert@redhat.com"));
    }

    @Test
    public void testNewSubscriptionSecurityUpdateErrataEmailTitle() {
        String result = generateEmailSubject(NEW_SUBSCRIPTION_SECURITY_UPDATE_ERRATA, ACTION);
        assertEquals("Instant notification - Security updates - Errata - Subscription Services", result);
    }

    @Test
    public void testNewSubscriptionSecurityUpdateErrataEmailBody() {
        String result = generateEmailBody(NEW_SUBSCRIPTION_SECURITY_UPDATE_ERRATA, ACTION);
        assertTrue(result.contains("There are 3 security updates affecting your subscriptions."));
        assertTrue(result.contains("href=\"https://access.redhat.com/errata/RHSA-2024:3843\""));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("secalert@redhat.com"));
        assertTrue(result.contains("Moderate"), "Security advisory severity has a dedicated column");
    }

    @Test
    public void testNewSubscriptionEnhancementErrataEmailTitle() {
        String result = generateEmailSubject(NEW_SUBSCRIPTION_ENHANCEMENT_ERRATA, ACTION);
        assertEquals("Instant notification - Enhancements - Errata - Subscription Services", result);
    }

    @Test
    public void testNewSubscriptionEnhancementErrataEmailBody() {
        String result = generateEmailBody(NEW_SUBSCRIPTION_ENHANCEMENT_ERRATA, ACTION);
        assertTrue(result.contains("There are 3 enhancements affecting your subscriptions."));
        assertTrue(result.contains("href=\"https://access.redhat.com/errata/RHSA-2024:3843\""));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("secalert@redhat.com"));
    }
}
