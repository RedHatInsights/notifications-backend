package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EventAggregationCriterion;

/**
 * Routes a bundle+application pair to its fold-only aggregator. Only covers the apps whose folding
 * logic needed no rework to drop the per-recipient severity gate (Compliance, Inventory, Errata,
 * Application Services) -- returns {@code null} for every other app until the harder aggregators
 * (Advisor, Patch, Resource Optimization, Vulnerability) are ported too.
 */
public class EmailPayloadAggregatorFactory {

    private static final String RHEL = "rhel";
    private static final String SUBSCRIPTION_SERVICES = "subscription-services";

    private static final String COMPLIANCE = "compliance";
    private static final String INVENTORY = "inventory";
    private static final String ERRATA = "errata-notifications";
    private static final String APPLICATION_SERVICES = "application-services";

    private EmailPayloadAggregatorFactory() {

    }

    public static AbstractEmailPayloadAggregator by(EventAggregationCriterion aggregationKey) {
        return getAggregator(aggregationKey.getBundle(), aggregationKey.getApplication());
    }

    private static AbstractEmailPayloadAggregator getAggregator(String bundle, String application) {
        switch (bundle) {
            case RHEL:
                switch (application) {
                    case COMPLIANCE:
                        return new ComplianceEmailAggregator();
                    case INVENTORY:
                        return new InventoryEmailAggregator();
                    default:
                        // Not ported yet.
                        break;
                }
                break;
            case SUBSCRIPTION_SERVICES:
                switch (application) {
                    case ERRATA:
                        return new ErrataEmailPayloadAggregator();
                    case APPLICATION_SERVICES:
                        return new ApplicationServicesEmailPayloadAggregator();
                    default:
                        // Not ported yet.
                        break;
                }
                break;
            default:
                // Not ported yet.
                break;
        }

        return null;
    }
}
