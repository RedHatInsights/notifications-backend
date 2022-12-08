package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregationKey;

public class EmailPayloadAggregatorFactory {

    private static final String RHEL = "rhel";
    private static final String APPLICATION_SERVICES = "application-services";
    private static final String ADVISOR = "advisor";
    private static final String POLICIES = "policies";
    private static final String COMPLIANCE = "compliance";
    private static final String DRIFT = "drift";
    private static final String RHOSAK = "rhosak";
    private static final String ANSIBLE = "ansible";
    private static final String PATCH = "patch";
    private static final String VULNERABILITY = "vulnerability";
    private static final String INVENTORY = "inventory";
    private static final String RESOURCE_OPTIMIZATION = "resource-optimization";

    private EmailPayloadAggregatorFactory() {

    }

    public static AbstractEmailPayloadAggregator by(EmailAggregationKey aggregationKey) {
        String bundle = aggregationKey.getBundle();
        String application = aggregationKey.getApplication();

        switch (bundle) {
            case APPLICATION_SERVICES:
                switch (application.toLowerCase()) { // TODO Remove toLowerCase if possible
                    case ANSIBLE:
                        return new AnsibleEmailAggregator();
                    case RHOSAK:
                        return new RhosakEmailAggregator();
                    default:
                        // Do nothing.
                        break;
                }
                break;
            case RHEL:
                switch (application) {
                    case ADVISOR:
                        return new AdvisorEmailPayloadAggregator();
                    case COMPLIANCE:
                        return new ComplianceEmailAggregator();
                    case DRIFT:
                        return new DriftEmailPayloadAggregator();
                    case INVENTORY:
                        return new InventoryEmailAggregator();
                    case PATCH:
                        return new PatchEmailPayloadAggregator();
                    case POLICIES:
                        return new PoliciesEmailPayloadAggregator();
                    case RESOURCE_OPTIMIZATION:
                        return new ResourceOptimizationPayloadAggregator();
                    case VULNERABILITY:
                        return new VulnerabilityEmailPayloadAggregator();
                    default:
                        // Do nothing
                        break;
                }
                break;
            default:
                // Do nothing.
                break;
        }

        return null;
    }
}
