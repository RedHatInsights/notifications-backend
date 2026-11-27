package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EventAggregationCriterion;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.processors.email.SubscribedEventTypeSeverities;

import java.util.Set;

public class EmailPayloadAggregatorFactory {

    private static final String RHEL = "rhel";
    private static final String SUBSCRIPTION_SERVICES = "subscription-services";
    private static final String APPLICATION_SERVICES = "application-services";
    private static final String ADVISOR = "advisor";
    private static final String LIGHTWELL = "lightwell";

    private static final String COMPLIANCE = "compliance";
    private static final String PATCH = "patch";
    private static final String VULNERABILITY = "vulnerability";
    private static final String INVENTORY = "inventory";
    private static final String RESOURCE_OPTIMIZATION = "resource-optimization";
    private static final String ERRATA = "errata-notifications";

    private EmailPayloadAggregatorFactory() {

    }

    public static AbstractEmailPayloadAggregator by(EventAggregationCriterion aggregationKey, String username, Set<SubscribedEventTypeSeverities> userSeverities, SubscriptionType subscriptionType) {
        String bundle = aggregationKey.getBundle();
        String application = aggregationKey.getApplication();

        AbstractEmailPayloadAggregator aggregator = getAggregator(bundle, application, subscriptionType);
        if (aggregator != null) {
            aggregator.setUserName(username);
            aggregator.setUserSeverities(userSeverities);
        }
        return aggregator;
    }

    private static AbstractEmailPayloadAggregator getAggregator(String bundle, String application, SubscriptionType subscriptionType) {
        switch (bundle) {
            case SUBSCRIPTION_SERVICES:
                switch (application) {
                    case ERRATA:
                        return new ErrataEmailPayloadAggregator();
                    case APPLICATION_SERVICES:
                        return new ApplicationServicesEmailPayloadAggregator();
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
                    case INVENTORY:
                        return new InventoryEmailAggregator();
                    case PATCH:
                        return new PatchEmailPayloadAggregator();
                    case RESOURCE_OPTIMIZATION:
                        return new ResourceOptimizationPayloadAggregator();
                    case VULNERABILITY:
                        return new VulnerabilityEmailPayloadAggregator();
                    default:
                        // Do nothing
                        break;
                }
                break;
            case LIGHTWELL:
                switch (application) {
                    case LIGHTWELL:
                        if (SubscriptionType.WEEKLY == subscriptionType) {
                            return new LightwellWeeklyEmailPayloadAggregator();
                        } else {
                            return null;
                        }
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
