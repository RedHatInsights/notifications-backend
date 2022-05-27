package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregationKey;

public class EmailPayloadAggregatorFactory {

    private static final String RHEL = "rhel";
    private static final String APPLICATION_SERVICES = "application-services";
    private static final String POLICIES = "policies";
    private static final String COMPLIANCE = "compliance";
    private static final String DRIFT = "drift";
    private static final String RHOSAK = "rhosak";
    private static final String ANSIBLE = "ansible";
    private static final String PATCH = "patch";

    private EmailPayloadAggregatorFactory() {

    }

    public static AbstractEmailPayloadAggregator by(EmailAggregationKey aggregationKey) {
        String bundle = aggregationKey.getBundle();
        String application = aggregationKey.getApplication();

        if (bundle.equals(RHEL) && application.equals(POLICIES)) {
            return new PoliciesEmailPayloadAggregator();
        }
        if (bundle.equals(RHEL) && application.equals(COMPLIANCE)) {
            return new ComplianceEmailAggregator();
        }
        if (bundle.equals(RHEL) && application.equals(DRIFT)) {
            return new DriftEmailPayloadAggregator();
        }
        if (bundle.equals(RHEL) && application.equals(PATCH)) {
            return new PatchEmailPayloadAggregator();
        }
        if (bundle.equals(APPLICATION_SERVICES) && application.equalsIgnoreCase(RHOSAK)) {
            return new RhosakEmailAggregator();
        }

        if (bundle.equals(APPLICATION_SERVICES) && application.equalsIgnoreCase(ANSIBLE)) {
            return new AnsibleEmailAggregator();
        }

        return null;
    }
}
