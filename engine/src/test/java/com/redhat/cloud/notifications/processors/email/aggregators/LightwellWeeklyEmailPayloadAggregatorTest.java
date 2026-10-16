package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.models.EmailAggregation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.redhat.cloud.notifications.LightwellTestHelpers.createEmailAggregation;
import static com.redhat.cloud.notifications.LightwellTestHelpers.createEmailAggregationMultiplePackages;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

public class LightwellWeeklyEmailPayloadAggregatorTest {

    private LightwellWeeklyEmailPayloadAggregator aggregator;
    private final String bundle = "lightwell";
    private final String application = "lightwell";

    @BeforeEach
    void setUp() {
        aggregator = new LightwellWeeklyEmailPayloadAggregator();
    }

    @Test
    void emptyAggregatorHasNoOrgId() {
        Assertions.assertNull(aggregator.getOrgId(), "Empty aggregator has no orgId");
    }

    @Test
    void shouldSetOrgId() {
        EmailAggregation aggregation = createEmailAggregation(
            DEFAULT_ORG_ID, bundle, application, "java-remediated",
            "org.glassfish.jaxb:codemodel", "https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:codemodel",
            "Java Remediated"
        );
        aggregator.aggregate(aggregation);
        Assertions.assertEquals(DEFAULT_ORG_ID, aggregator.getOrgId());
    }

    @Test
    void validateSinglePackageSingleEventType() {
        EmailAggregation aggregation = createEmailAggregation(
            DEFAULT_ORG_ID, bundle, application, "java-remediated",
            "org.glassfish.jaxb:codemodel", "https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:codemodel",
            "Java Remediated"
        );
        aggregator.aggregate(aggregation);

        Map<String, Object> context = aggregator.getContext();

        Assertions.assertEquals(1, context.get("total_packages"));
        Assertions.assertEquals(1, context.get("total_repositories"));

        Map<String, Object> packagesByEventType = (Map<String, Object>) context.get("packages_by_event_type");
        Assertions.assertNotNull(packagesByEventType);
        Assertions.assertTrue(packagesByEventType.containsKey("java-remediated"));

        Map<String, Object> javaRemediated = (Map<String, Object>) packagesByEventType.get("java-remediated");
        Assertions.assertEquals("Java Remediated", javaRemediated.get("event_type_display_name"));

        ArrayList<LinkedHashMap<String, Object>> packages = (ArrayList<LinkedHashMap<String, Object>>) javaRemediated.get("packages");
        Assertions.assertEquals(1, packages.size());
        Assertions.assertEquals("org.glassfish.jaxb:codemodel", packages.get(0).get("package_name"));
        Assertions.assertEquals("https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:codemodel", packages.get(0).get("package_link"));
    }

    @Test
    void validateMultiplePackagesSingleEventType() {
        aggregator.aggregate(createEmailAggregation(
            DEFAULT_ORG_ID, bundle, application, "java-remediated",
            "org.glassfish.jaxb:codemodel", "https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:codemodel",
            "Java Remediated"
        ));
        aggregator.aggregate(createEmailAggregation(
            DEFAULT_ORG_ID, bundle, application, "java-remediated",
            "org.glassfish.jaxb:jaxb-core", "https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:jaxb-core",
            "Java Remediated"
        ));

        Map<String, Object> context = aggregator.getContext();

        Assertions.assertEquals(2, context.get("total_packages"));
        Assertions.assertEquals(1, context.get("total_repositories"));

        Map<String, Object> packagesByEventType = (Map<String, Object>) context.get("packages_by_event_type");
        Map<String, Object> javaRemediated = (Map<String, Object>) packagesByEventType.get("java-remediated");

        ArrayList<LinkedHashMap<String, Object>> packages = (ArrayList<LinkedHashMap<String, Object>>) javaRemediated.get("packages");
        Assertions.assertEquals(2, packages.size());
    }

    @Test
    void validateMultipleEventTypes() {
        aggregator.aggregate(createEmailAggregation(
            DEFAULT_ORG_ID, bundle, application, "java-remediated",
            "org.glassfish.jaxb:codemodel", "https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:codemodel",
            "Java Remediated"
        ));
        aggregator.aggregate(createEmailAggregation(
            DEFAULT_ORG_ID, bundle, application, "java-remediated",
            "org.glassfish.jaxb:jaxb-core", "https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:jaxb-core",
            "Java Remediated"
        ));
        aggregator.aggregate(createEmailAggregation(
            DEFAULT_ORG_ID, bundle, application, "python-remediated",
            "requests", "https://console.redhat.com/lightwell/packages/requests",
            "Python Remediated"
        ));

        Map<String, Object> context = aggregator.getContext();

        Assertions.assertEquals(3, context.get("total_packages"));
        Assertions.assertEquals(2, context.get("total_repositories"));

        Map<String, Object> packagesByEventType = (Map<String, Object>) context.get("packages_by_event_type");
        Assertions.assertTrue(packagesByEventType.containsKey("java-remediated"));
        Assertions.assertTrue(packagesByEventType.containsKey("python-remediated"));

        Map<String, Object> javaRemediated = (Map<String, Object>) packagesByEventType.get("java-remediated");
        ArrayList<LinkedHashMap<String, Object>> javaPackages = (ArrayList<LinkedHashMap<String, Object>>) javaRemediated.get("packages");
        Assertions.assertEquals(2, javaPackages.size());

        Map<String, Object> pythonRemediated = (Map<String, Object>) packagesByEventType.get("python-remediated");
        ArrayList<LinkedHashMap<String, Object>> pythonPackages = (ArrayList<LinkedHashMap<String, Object>>) pythonRemediated.get("packages");
        Assertions.assertEquals(1, pythonPackages.size());
        Assertions.assertEquals("requests", pythonPackages.get(0).get("package_name"));
    }

    @Test
    void validateMultipleEventsInSingleAggregation() {
        EmailAggregation aggregation = createEmailAggregationMultiplePackages(bundle, application);
        aggregator.aggregate(aggregation);

        Map<String, Object> context = aggregator.getContext();

        Assertions.assertEquals(2, context.get("total_packages"));
        Assertions.assertEquals(1, context.get("total_repositories"));

        Map<String, Object> packagesByEventType = (Map<String, Object>) context.get("packages_by_event_type");
        Map<String, Object> javaRemediated = (Map<String, Object>) packagesByEventType.get("java-remediated");

        ArrayList<LinkedHashMap<String, Object>> packages = (ArrayList<LinkedHashMap<String, Object>>) javaRemediated.get("packages");
        Assertions.assertEquals(2, packages.size());
    }

    @Test
    void validateSamePackageMultipleAggregations() {
        aggregator.aggregate(createEmailAggregation(
            DEFAULT_ORG_ID, bundle, application, "java-remediated",
            "org.glassfish.jaxb:codemodel", "https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:codemodel",
            "Java Remediated"
        ));
        aggregator.aggregate(createEmailAggregation(
            DEFAULT_ORG_ID, bundle, application, "java-remediated",
            "org.glassfish.jaxb:codemodel", "https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:codemodel",
            "Java Remediated"
        ));

        Map<String, Object> context = aggregator.getContext();

        // Same package aggregated multiple times should only appear once
        Assertions.assertEquals(1, context.get("total_packages"));
        Assertions.assertEquals(1, context.get("total_repositories"));

        Map<String, Object> packagesByEventType = (Map<String, Object>) context.get("packages_by_event_type");
        Map<String, Object> javaRemediated = (Map<String, Object>) packagesByEventType.get("java-remediated");

        ArrayList<LinkedHashMap<String, Object>> packages = (ArrayList<LinkedHashMap<String, Object>>) javaRemediated.get("packages");
        Assertions.assertEquals(1, packages.size());
    }

    @Test
    void shouldBeEmptyWhenNoAggregations() {
        Assertions.assertTrue(aggregator.isEmpty());
    }

    @Test
    void shouldNotBeEmptyAfterAggregation() {
        aggregator.aggregate(createEmailAggregation(
            DEFAULT_ORG_ID, bundle, application, "java-remediated",
            "org.glassfish.jaxb:codemodel", "https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:codemodel",
            "Java Remediated"
        ));
        Assertions.assertFalse(aggregator.isEmpty());
    }

    @Test
    void validateContextStructureMatchesTemplateExpectations() {
        // Aggregate multiple packages across different event types
        aggregator.aggregate(createEmailAggregation(
            DEFAULT_ORG_ID, bundle, application, "java-remediated",
            "org.glassfish.jaxb:codemodel", "https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:codemodel",
            "Java Remediated"
        ));
        aggregator.aggregate(createEmailAggregation(
            DEFAULT_ORG_ID, bundle, application, "java-remediated",
            "org.glassfish.jaxb:jaxb-core", "https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:jaxb-core",
            "Java Remediated"
        ));
        aggregator.aggregate(createEmailAggregation(
            DEFAULT_ORG_ID, bundle, application, "java-remediated",
            "org.glassfish.jaxb:txw2", "https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:txw2",
            "Java Remediated"
        ));
        aggregator.aggregate(createEmailAggregation(
            DEFAULT_ORG_ID, bundle, application, "python-remediated",
            "requests", "https://console.redhat.com/lightwell/packages/requests",
            "Python Remediated"
        ));
        aggregator.aggregate(createEmailAggregation(
            DEFAULT_ORG_ID, bundle, application, "python-remediated",
            "flask", "https://console.redhat.com/lightwell/packages/flask",
            "Python Remediated"
        ));

        Map<String, Object> context = aggregator.getContext();

        // Verify the structure matches what weeklyEmailBody.html template expects
        Assertions.assertEquals(2, context.get("total_repositories")); // 2 event types
        Assertions.assertEquals(5, context.get("total_packages")); // 5 unique packages

        Map<String, Object> packagesByEventType = (Map<String, Object>) context.get("packages_by_event_type");
        Assertions.assertNotNull(packagesByEventType);

        // Verify each event type has the expected structure
        for (Map.Entry<String, Object> entry : packagesByEventType.entrySet()) {
            Map<String, Object> eventTypeData = (Map<String, Object>) entry.getValue();

            // Template uses: eventTypeEntry.value.event_type_display_name
            Assertions.assertTrue(eventTypeData.containsKey("event_type_display_name"));

            // Template uses: eventTypeEntry.value.packages
            Assertions.assertTrue(eventTypeData.containsKey("packages"));
            ArrayList<LinkedHashMap<String, Object>> packages = (ArrayList<LinkedHashMap<String, Object>>) eventTypeData.get("packages");

            // Each package must have package_name and package_link (used by template)
            for (Map<String, Object> pkg : packages) {
                Assertions.assertTrue(pkg.containsKey("package_name"));
                Assertions.assertTrue(pkg.containsKey("package_link"));
                // Ensure no unexpected fields that template doesn't use
                Assertions.assertEquals(2, pkg.size(), "Package should only have package_name and package_link");
            }
        }
    }
}
