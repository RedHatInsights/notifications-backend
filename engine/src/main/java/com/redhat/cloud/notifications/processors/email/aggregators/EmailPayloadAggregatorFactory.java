package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregationKey;

import java.time.LocalDateTime;

public class EmailPayloadAggregatorFactory {

    private static final String RHEL = "rhel";
    private static final String SUBSCRIPTION_SERVICES = "subscription-services";
    private static final String APPLICATION_SERVICES = "application-services";
    private static final String ADVISOR = "advisor";
    private static final String POLICIES = "policies";
    private static final String COMPLIANCE = "compliance";
    private static final String ANSIBLE = "ansible";
    private static final String PATCH = "patch";
    private static final String VULNERABILITY = "vulnerability";
    private static final String INVENTORY = "inventory";
    private static final String RESOURCE_OPTIMIZATION = "resource-optimization";
    private static final String IMAGE_BUILDER = "image-builder";
    private static final String ERRATA = "errata-notifications";


    private EmailPayloadAggregatorFactory() {

    }

    public static AbstractEmailPayloadAggregator by(EmailAggregationKey aggregationKey, LocalDateTime start, LocalDateTime end) {
        String bundle = aggregationKey.getBundle();
        String application = aggregationKey.getApplication();

        AbstractEmailPayloadAggregator aggregator = getAggregator(bundle, application);
        if (aggregator != null) {
            aggregator.setStartTime(start);
            aggregator.setEndTimeKey(end);
        }
        return aggregator;
    }

    private static AbstractEmailPayloadAggregator getAggregator(String bundle, String application) {
        switch (bundle) {
            case APPLICATION_SERVICES:
                switch (application.toLowerCase()) { // TODO Remove toLowerCase if possible
                    case ANSIBLE:
                        return new AnsibleEmailAggregator();
                    default:
                        // Do nothing.
                        break;
                }
                break;
            case SUBSCRIPTION_SERVICES:
                switch (application) {
                    case ERRATA:
                        return new ErrataEmailPayloadAggregator();
                    default:
                        // Do nothing.
                        break;
                }
                break;
            case RHEL:
                switch (application) {
                    case ADVISOR:
                        return new AdvisorEmailAggregator();
                    case COMPLIANCE:
                        return new ComplianceEmailAggregator();
                    case IMAGE_BUILDER:
                        return new ImageBuilderAggregator();
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
