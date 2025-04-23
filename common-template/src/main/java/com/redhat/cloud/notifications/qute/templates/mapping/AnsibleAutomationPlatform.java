package com.redhat.cloud.notifications.qute.templates.mapping;


import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import java.util.Map;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.*;
import static java.util.Map.entry;

public class AnsibleAutomationPlatform {
    static final String BUNDLE_NAME = "ansible-automation-platform";

    static final String ANSIBLE_SERVICE_ON_AWS_APP_NAME = "ansible-service-on-aws";
    static final String ANSIBLE_SERVICE_ON_AWS_FOLDER_NAME = "AnsibleServiceOnAws/";
    public static final String ANSIBLE_SERVICE_NOTIFY_CUSTOMER_PROVISION_SUCCESS = "notify-customer-provision-success";

    public static final Map<TemplateDefinition, String> templatesMap = Map.ofEntries(
        // Ansible service on AWS
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, ANSIBLE_SERVICE_ON_AWS_APP_NAME, ANSIBLE_SERVICE_NOTIFY_CUSTOMER_PROVISION_SUCCESS), ANSIBLE_SERVICE_ON_AWS_FOLDER_NAME + "notifyCustomerProvisionSuccessEmailTitle.txt"),
        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, ANSIBLE_SERVICE_ON_AWS_APP_NAME, ANSIBLE_SERVICE_NOTIFY_CUSTOMER_PROVISION_SUCCESS), ANSIBLE_SERVICE_ON_AWS_FOLDER_NAME + "notifyCustomerProvisionSuccessEmailBody.html")
    );
}
