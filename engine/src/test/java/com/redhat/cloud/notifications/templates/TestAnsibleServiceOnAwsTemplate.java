package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestAnsibleServiceOnAwsTemplate extends EmailTemplatesInDbHelper {

    static final String NOTIFY_CUSTOMER_PROVISION_SUCCESS = "notify-customer-provision-success";

    @Override
    protected String getBundle() {
        return "ansible-automation-platform";
    }

    @Override
    protected String getApp() {
        return "ansible-service-on-aws";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(NOTIFY_CUSTOMER_PROVISION_SUCCESS);
    }

    @Test
    public void testInstantEmailTitle() {
        Action action = TestHelpers.createAnsibleAction(NOTIFY_CUSTOMER_PROVISION_SUCCESS, null);

        String result = generateEmailSubject(NOTIFY_CUSTOMER_PROVISION_SUCCESS, action);
        assertEquals("Instant notification - Notify customer environment is ready - Ansible Automation Platform Service on AWS - Ansible Automation Platform", result);
    }

    @Test
    public void testInstantEmailBody() {
        String envName  = RandomStringUtils.randomAlphanumeric(10);
        Action action = TestHelpers.createAnsibleAction(NOTIFY_CUSTOMER_PROVISION_SUCCESS, envName);

        String result = generateEmailBody(NOTIFY_CUSTOMER_PROVISION_SUCCESS, action);
        assertTrue(result.contains(envName));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
