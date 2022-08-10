package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class EmailTemplateFactoryTest {

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    EmailTemplateFactory testee;

    @BeforeEach
    void beforeEach() {
        featureFlipper.setUseDefaultTemplate(false);
    }

    @Test
    void shouldReturnNotSupportedEmailTemplate() {
        final EmailTemplate emailTemplate = testee.get("none", "none");

        assertTrue(emailTemplate instanceof EmailTemplateNotSupported);
    }

    @Test
    void shouldReturnAdvisorOpenShift() {
        final EmailTemplate emailTemplate = testee.get("openshift", "advisor");
        assertTrue(emailTemplate instanceof AdvisorOpenshift);
    }

    @Test
    void ShouldReturnNotSupportedWhenApplicationIsNotAdvisor() {
        final EmailTemplate emailTemplate = testee.get("openshift", "none");
        assertTrue(emailTemplate instanceof EmailTemplateNotSupported);
    }

    @Test
    void shouldReturnPolicies() {
        final EmailTemplate emailTemplate = testee.get("rhel", "policies");
        assertTrue(emailTemplate instanceof Policies);
    }

    @Test
    void shouldReturnAdvisor() {
        final EmailTemplate emailTemplate = testee.get("rhel", "advisor");
        assertTrue(emailTemplate instanceof Advisor);
    }

    @Test
    void shouldReturnDrift() {
        final EmailTemplate emailTemplate = testee.get("rhel", "drift");
        assertTrue(emailTemplate instanceof Drift);
    }

    @Test
    void shouldReturnNotSupportedWhenBundleIsRhelAndApplicationIsUnknown() {
        final EmailTemplate emailTemplate = testee.get("rhel", "driftZzZ");
        assertTrue(emailTemplate instanceof EmailTemplateNotSupported);
    }

    @Test
    void shouldReturnRbac() {
        final EmailTemplate emailTemplate = testee.get("console", "rbac");
        assertTrue(emailTemplate instanceof Rbac);
    }

    @Test
    void shouldUseDefaultWhenEnabledAndNotFound() {
        featureFlipper.setUseDefaultTemplate(true);
        EmailTemplate emailTemplate = testee.get("foo", "bar");
        assertTrue(emailTemplate instanceof Default);
        Default defaultEmailTemplate = (Default) emailTemplate;
        assertTrue(defaultEmailTemplate.wrappedEmailTemplate instanceof EmailTemplateNotSupported);

        assertTrue(emailTemplate.isSupported("any", EmailSubscriptionType.INSTANT));
        assertTrue(emailTemplate.isEmailSubscriptionSupported(EmailSubscriptionType.INSTANT));

        assertFalse(emailTemplate.isSupported("any", EmailSubscriptionType.DAILY));
        assertFalse(emailTemplate.isEmailSubscriptionSupported(EmailSubscriptionType.DAILY));

        featureFlipper.setUseDefaultTemplate(false);
        emailTemplate = testee.get("foo", "bar");
        assertTrue(emailTemplate instanceof EmailTemplateNotSupported);
    }
}
