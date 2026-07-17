package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EventAggregationCriterion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EmailPayloadAggregatorFactoryTest {

    @ParameterizedTest
    @CsvSource({
        "application-services, ansible,               AnsibleEmailAggregator",
        "subscription-services, errata-notifications,  ErrataEmailPayloadAggregator",
        "subscription-services, application-services,  ApplicationServicesEmailPayloadAggregator",
        "rhel, advisor,                                AdvisorEmailAggregator",
        "rhel, compliance,                             ComplianceEmailAggregator",
        "rhel, inventory,                              InventoryEmailAggregator",
        "rhel, patch,                                  PatchEmailPayloadAggregator",
        "rhel, resource-optimization,                  ResourceOptimizationPayloadAggregator",
        "rhel, vulnerability,                          VulnerabilityEmailPayloadAggregator"
    })
    void shouldReturnCorrectAggregator(String bundle, String application, String expectedClassName) {
        EventAggregationCriterion key = createKey(bundle, application.trim());
        AbstractEmailPayloadAggregator aggregator = EmailPayloadAggregatorFactory.by(key, "user", null, 50, 100, 10000);
        assertNotNull(aggregator, "Expected aggregator for " + bundle + "/" + application.trim());
        assertTrue(aggregator.getClass().getSimpleName().equals(expectedClassName.trim()),
            "Expected " + expectedClassName.trim() + " but got " + aggregator.getClass().getSimpleName());
    }

    @Test
    void shouldReturnNullForUnknownBundleApplication() {
        EventAggregationCriterion key = createKey("unknown-bundle", "unknown-app");
        assertNull(EmailPayloadAggregatorFactory.by(key, "user", null, 50, 100, 10000));
    }

    private static EventAggregationCriterion createKey(String bundle, String application) {
        return new EventAggregationCriterion("org-1", UUID.randomUUID(), UUID.randomUUID(), bundle, application);
    }

    @Test
    void shouldHandleCaseInsensitiveApplicationFallback() {
        EventAggregationCriterion key = createKey("application-services", "Ansible");
        AbstractEmailPayloadAggregator aggregator = EmailPayloadAggregatorFactory.by(key, "user", null, 50, 100, 10000);
        assertNotNull(aggregator, "Should match via toLowerCase fallback");
        assertTrue(aggregator instanceof AnsibleEmailAggregator);
    }
}
