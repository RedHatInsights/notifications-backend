package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.ApplicationServicesTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationServicesEmailPayloadAggregatorTest {

    ApplicationServicesEmailPayloadAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new ApplicationServicesEmailPayloadAggregator();
    }

    @Test
    void emptyAggregatorHasNoOrgId() {
        assertNull(aggregator.getOrgId(), "Empty aggregator has no orgId");
    }

    @Test
    void shouldSetOrgId() {
        aggregator.aggregate(TestHelpers.createEmailAggregationFromAction(ApplicationServicesTestHelpers.createKeycloakReleasesAction(), "Red Hat build of Keycloak"));
        assertEquals(DEFAULT_ORG_ID, aggregator.getOrgId());
    }

    @Test
    void shouldAggregateKeycloakReleases() {
        aggregator.aggregate(TestHelpers.createEmailAggregationFromAction(ApplicationServicesTestHelpers.createKeycloakReleasesAction(), "Red Hat build of Keycloak"));

        Map<String, Object> context = aggregator.getContext();
        JsonObject appServices = JsonObject.mapFrom(context).getJsonObject("application-services");
        JsonObject products = appServices.getJsonObject("products");

        assertTrue(products.containsKey("keycloak-releases"));
        JsonObject keycloak = products.getJsonObject("keycloak-releases");
        assertEquals("Red Hat build of Keycloak", keycloak.getString("description"));

        JsonArray payloads = keycloak.getJsonArray("payloads");
        assertEquals(2, payloads.size());
        assertEquals("jbossnetwork/restricted/softwareDetail.html?softwareId=108766", payloads.getJsonObject(0).getString("download_path"));
        assertEquals("Red Hat build of Keycloak 26.2.13 Maven Repository", payloads.getJsonObject(0).getString("description"));
        assertEquals("26.2.13", payloads.getJsonObject(0).getString("version"));
        assertEquals("jbossnetwork/restricted/softwareDetail.html?softwareId=108767", payloads.getJsonObject(1).getString("download_path"));
    }

    @Test
    void shouldAggregateMultipleProducts() {
        aggregator.aggregate(TestHelpers.createEmailAggregationFromAction(ApplicationServicesTestHelpers.createKeycloakReleasesAction(), "Red Hat build of Keycloak"));
        aggregator.aggregate(TestHelpers.createEmailAggregationFromAction(ApplicationServicesTestHelpers.createEapReleasesAction(), "Red Hat JBoss Enterprise Application Platform"));

        Map<String, Object> context = aggregator.getContext();
        JsonObject appServices = JsonObject.mapFrom(context).getJsonObject("application-services");
        JsonObject products = appServices.getJsonObject("products");

        assertTrue(products.containsKey("keycloak-releases"));
        assertTrue(products.containsKey("eap-releases"));

        assertEquals(2, products.getJsonObject("keycloak-releases").getJsonArray("payloads").size());
        assertEquals(3, products.getJsonObject("eap-releases").getJsonArray("payloads").size());
    }

    @Test
    void shouldCalculateGlobalReleasesNumber() {
        aggregator.aggregate(TestHelpers.createEmailAggregationFromAction(ApplicationServicesTestHelpers.createKeycloakReleasesAction(), "Red Hat build of Keycloak"));
        aggregator.aggregate(TestHelpers.createEmailAggregationFromAction(ApplicationServicesTestHelpers.createEapReleasesAction(), "Red Hat JBoss Enterprise Application Platform"));

        Map<String, Object> context = aggregator.getContext();
        JsonObject appServices = JsonObject.mapFrom(context).getJsonObject("application-services");

        assertEquals(5, appServices.getInteger("global_releases_number"));
    }

    @Test
    void shouldSkipNullEventType() {
        aggregator.aggregate(TestHelpers.createEmailAggregationFromAction(ApplicationServicesTestHelpers.createActionWithNoEventType()));

        Map<String, Object> context = aggregator.getContext();
        JsonObject appServices = JsonObject.mapFrom(context).getJsonObject("application-services");
        JsonObject products = appServices.getJsonObject("products");

        assertTrue(products.isEmpty());
        assertEquals(0, appServices.getInteger("global_releases_number"));
    }

    @Test
    void shouldSkipNullContext() {
        aggregator.aggregate(TestHelpers.createEmailAggregationFromAction(ApplicationServicesTestHelpers.createActionWithNoContext()));

        Map<String, Object> context = aggregator.getContext();
        JsonObject appServices = JsonObject.mapFrom(context).getJsonObject("application-services");
        JsonObject products = appServices.getJsonObject("products");

        assertTrue(products.isEmpty());
        assertEquals(0, appServices.getInteger("global_releases_number"));
    }

    @Test
    void shouldSkipNullFamily() {
        aggregator.aggregate(TestHelpers.createEmailAggregationFromAction(ApplicationServicesTestHelpers.createActionWithNoFamily()));

        Map<String, Object> context = aggregator.getContext();
        JsonObject appServices = JsonObject.mapFrom(context).getJsonObject("application-services");
        JsonObject products = appServices.getJsonObject("products");

        assertTrue(products.isEmpty());
        assertEquals(0, appServices.getInteger("global_releases_number"));
    }

    @Test
    void shouldHandleEmptyEvents() {
        aggregator.aggregate(TestHelpers.createEmailAggregationFromAction(ApplicationServicesTestHelpers.createActionWithEmptyEvents(), "Red Hat build of Keycloak"));

        Map<String, Object> context = aggregator.getContext();
        JsonObject appServices = JsonObject.mapFrom(context).getJsonObject("application-services");
        JsonObject products = appServices.getJsonObject("products");

        assertTrue(products.containsKey("keycloak-releases"));
        assertEquals(0, products.getJsonObject("keycloak-releases").getJsonArray("payloads").size());
        assertEquals(0, appServices.getInteger("global_releases_number"));
    }

    @Test
    void shouldReturnConsistentContextOnMultipleCalls() {
        aggregator.aggregate(TestHelpers.createEmailAggregationFromAction(ApplicationServicesTestHelpers.createKeycloakReleasesAction(), "Red Hat build of Keycloak"));
        aggregator.aggregate(TestHelpers.createEmailAggregationFromAction(ApplicationServicesTestHelpers.createEapReleasesAction(), "Red Hat JBoss Enterprise Application Platform"));

        Map<String, Object> context1 = aggregator.getContext();
        Map<String, Object> context2 = aggregator.getContext();

        JsonObject appServices1 = JsonObject.mapFrom(context1).getJsonObject("application-services");
        JsonObject appServices2 = JsonObject.mapFrom(context2).getJsonObject("application-services");

        assertEquals(appServices1.getInteger("global_releases_number"), appServices2.getInteger("global_releases_number"));
        assertEquals(appServices1.getJsonObject("products").fieldNames(), appServices2.getJsonObject("products").fieldNames());
    }

    @Test
    void shouldSkipNullPayloadEvents() {
        aggregator.aggregate(ApplicationServicesTestHelpers.createAggregationWithNullPayloadEvent());

        Map<String, Object> context = aggregator.getContext();
        JsonObject appServices = JsonObject.mapFrom(context).getJsonObject("application-services");
        JsonObject products = appServices.getJsonObject("products");

        assertTrue(products.containsKey("keycloak-releases"));
        JsonArray payloads = products.getJsonObject("keycloak-releases").getJsonArray("payloads");
        assertEquals(1, payloads.size());
        assertEquals("jbossnetwork/restricted/softwareDetail.html?softwareId=108766", payloads.getJsonObject(0).getString("download_path"));
        assertEquals(1, appServices.getInteger("global_releases_number"));
    }

    @Test
    void shouldAccumulatePayloadsForSameEventType() {
        // Aggregate keycloak twice - payloads should accumulate
        aggregator.aggregate(TestHelpers.createEmailAggregationFromAction(ApplicationServicesTestHelpers.createKeycloakReleasesAction(), "Red Hat build of Keycloak"));
        aggregator.aggregate(TestHelpers.createEmailAggregationFromAction(ApplicationServicesTestHelpers.createKeycloakReleasesAction(), "Red Hat build of Keycloak"));

        Map<String, Object> context = aggregator.getContext();
        JsonObject appServices = JsonObject.mapFrom(context).getJsonObject("application-services");
        JsonObject products = appServices.getJsonObject("products");

        JsonArray payloads = products.getJsonObject("keycloak-releases").getJsonArray("payloads");
        assertEquals(4, payloads.size());
        assertEquals(4, appServices.getInteger("global_releases_number"));
    }
}
