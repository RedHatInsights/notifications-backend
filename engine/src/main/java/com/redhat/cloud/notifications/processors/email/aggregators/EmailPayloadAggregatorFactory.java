package com.redhat.cloud.notifications.processors.email.aggregators;

import java.time.LocalDateTime;

public class EmailPayloadAggregatorFactory {

    private static final String RHEL = "rhel";
    private static final String APPLICATION_SERVICES = "application-services";
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

    public static AbstractEmailPayloadAggregator by(String bundle, String application, LocalDateTime start, LocalDateTime end) {
        AbstractEmailPayloadAggregator aggregator = byApplication(bundle, application);
        if (aggregator != null) {
            aggregator.setStartTime(start);
            aggregator.setEndTimeKey(end);
        }

        return aggregator;
    }

    private static AbstractEmailPayloadAggregator byApplication(String bundle, String application) {
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
