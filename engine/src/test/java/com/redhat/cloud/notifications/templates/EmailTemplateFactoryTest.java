package com.redhat.cloud.notifications.templates;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailTemplateFactoryTest {

    EmailTemplateFactory testee = new EmailTemplateFactory();

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
}
