package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.models.EventAggregationCriterion;

import java.util.Map;
import java.util.UUID;

public class EmailPayloadAggregatorFactory {

    @FunctionalInterface
    interface AggregatorCreator {
        AbstractEmailPayloadAggregator create(int inventoryMax, int vulnerabilityMax, int resourceOptMax);
    }

    private static final Map<String, AggregatorCreator> AGGREGATORS = Map.ofEntries(
        Map.entry("application-services/ansible",               (i, v, r) -> new AnsibleEmailAggregator()),
        Map.entry("subscription-services/errata-notifications", (i, v, r) -> new ErrataEmailPayloadAggregator()),
        Map.entry("subscription-services/application-services", (i, v, r) -> new ApplicationServicesEmailPayloadAggregator()),
        Map.entry("rhel/advisor",                               (i, v, r) -> new AdvisorEmailAggregator()),
        Map.entry("rhel/compliance",                            (i, v, r) -> new ComplianceEmailAggregator()),
        Map.entry("rhel/inventory",                             (i, v, r) -> new InventoryEmailAggregator(i)),
        Map.entry("rhel/patch",                                 (i, v, r) -> new PatchEmailPayloadAggregator()),
        Map.entry("rhel/resource-optimization",                 (i, v, r) -> new ResourceOptimizationPayloadAggregator(r)),
        Map.entry("rhel/vulnerability",                         (i, v, r) -> new VulnerabilityEmailPayloadAggregator(v))
    );

    private EmailPayloadAggregatorFactory() {

    }

    public static AbstractEmailPayloadAggregator by(EventAggregationCriterion aggregationKey, String username,
                                                       Map<UUID, Map<Severity, Boolean>> severitiesByEventType,
                                                       int inventoryMaxDisplayed, int vulnerabilityMaxDisplayed, int resourceOptMaxTracked) {
        String bundle = aggregationKey.getBundle();
        String application = aggregationKey.getApplication();

        AbstractEmailPayloadAggregator aggregator = getAggregator(bundle, application, inventoryMaxDisplayed, vulnerabilityMaxDisplayed, resourceOptMaxTracked);
        if (aggregator != null) {
            aggregator.userName = username;
            aggregator.severitiesByEventType = severitiesByEventType;
        }
        return aggregator;
    }

    private static AbstractEmailPayloadAggregator getAggregator(String bundle, String application,
                                                                    int inventoryMaxDisplayed, int vulnerabilityMaxDisplayed, int resourceOptMaxTracked) {
        String key = bundle + "/" + application;
        AggregatorCreator creator = AGGREGATORS.get(key);
        if (creator == null) {
            creator = AGGREGATORS.get(bundle + "/" + application.toLowerCase());
        }
        return creator != null ? creator.create(inventoryMaxDisplayed, vulnerabilityMaxDisplayed, resourceOptMaxTracked) : null;
    }
}
