package com.redhat.cloud.notifications.templates.common;

import com.redhat.cloud.notifications.EmailTemplatesRendererHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestAnsibleServiceOnAwsTemplate extends EmailTemplatesRendererHelper {

    static final String NOTIFY_CUSTOMER_PROVISION_SUCCESS = "notify-customer-provision-success";

    @Override
    protected String getBundle() {
        return "ansible-automation-platform";
    }

    @Override
    protected String getApp() {
        return "ansible-service-on-aws";
    }

    @Test
    public void testInstantEmailTitle() {
        Action action = TestHelpers.createAnsibleAction(NOTIFY_CUSTOMER_PROVISION_SUCCESS, null, null);

        String result = generateEmailSubject(NOTIFY_CUSTOMER_PROVISION_SUCCESS, action);
        assertEquals("Environment is ready - Ansible Automation Platform Service on AWS", result);
    }

    @Test
    public void testInstantEmailBody() {
        String envName  = "cus-" + RandomStringUtils.randomAlphanumeric(5).toLowerCase();
        String bitwardenURL = "https://www.example.com";
        Action action = TestHelpers.createAnsibleAction(NOTIFY_CUSTOMER_PROVISION_SUCCESS, envName, bitwardenURL);

        String result = generateEmailBody(NOTIFY_CUSTOMER_PROVISION_SUCCESS, action);
        assertTrue(result.contains(envName));
        assertTrue(result.contains(bitwardenURL));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
