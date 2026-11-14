package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationServicesEmailPayloadAggregatorTest {

    private static final String ORG_ID = "test-org-id";

    private ApplicationServicesEmailPayloadAggregator aggregator;

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
        aggregator.aggregate(createReleaseAggregation("keycloak-releases", "Red Hat build of Keycloak", 2, Severity.LOW));
        assertEquals(ORG_ID, aggregator.getOrgId());
    }

    @Test
    void shouldAggregateMultipleProducts() {
        aggregator.aggregate(createReleaseAggregation("keycloak-releases", "Red Hat build of Keycloak", 2, Severity.LOW));
        aggregator.aggregate(createReleaseAggregation("eap-releases", "Red Hat JBoss Enterprise Application Platform", 3, Severity.LOW));

        JsonObject products = JsonObject.mapFrom(aggregator.getContext())
            .getJsonObject("application-services")
            .getJsonObject("products");

        assertTrue(products.containsKey("keycloak-releases"));
        assertTrue(products.containsKey("eap-releases"));
        assertEquals(2, products.getJsonObject("keycloak-releases").getJsonArray("payloads").size());
        assertEquals(3, products.getJsonObject("eap-releases").getJsonArray("payloads").size());
    }

    @Test
    void shouldCalculateGlobalReleasesNumber() {
        aggregator.aggregate(createReleaseAggregation("keycloak-releases", "Red Hat build of Keycloak", 2, Severity.LOW));
        aggregator.aggregate(createReleaseAggregation("eap-releases", "Red Hat JBoss Enterprise Application Platform", 3, Severity.LOW));

        JsonObject appServices = JsonObject.mapFrom(aggregator.getContext()).getJsonObject("application-services");
        assertEquals(5, appServices.getInteger("global_releases_number"));
    }

    @Test
    void shouldSkipNullEventType() {
        JsonObject action = new JsonObject();
        aggregator.aggregate(new EmailAggregation(ORG_ID, "rhel", "application-services", action, Severity.LOW, UUID.randomUUID()));

        JsonObject appServices = JsonObject.mapFrom(aggregator.getContext()).getJsonObject("application-services");
        assertTrue(appServices.getJsonObject("products").isEmpty());
        assertEquals(0, appServices.getInteger("global_releases_number"));
    }

    @Test
    void shouldTagEachItemWithItsOwnSeverity() {
        aggregator.aggregate(createReleaseAggregation("keycloak-releases", "Red Hat build of Keycloak", 1, Severity.IMPORTANT));

        JsonArray payloads = JsonObject.mapFrom(aggregator.getContext())
            .getJsonObject("application-services")
            .getJsonObject("products")
            .getJsonObject("keycloak-releases")
            .getJsonArray("payloads");

        assertEquals("IMPORTANT", payloads.getJsonObject(0).getString("_severity"));
    }

    @Test
    void shouldTagEachItemWithFullMetadata() {
        EmailAggregation aggregation = createReleaseAggregation("keycloak-releases", "Red Hat build of Keycloak", 1, Severity.IMPORTANT);
        aggregation.setSubscribedByDefault(true);
        aggregation.setRecipientsOverride(new JsonArray().add(new JsonObject().put("only_admins", true)));

        aggregator.aggregate(aggregation);

        JsonObject item = JsonObject.mapFrom(aggregator.getContext())
            .getJsonObject("application-services")
            .getJsonObject("products")
            .getJsonObject("keycloak-releases")
            .getJsonArray("payloads")
            .getJsonObject(0);

        assertEquals("keycloak-releases", item.getString("_eventType"));
        assertTrue(item.getBoolean("_subscribedByDefault"));
        assertEquals(1, item.getJsonArray("_recipientsOverride").size());
    }

    private static EmailAggregation createReleaseAggregation(String eventType, String displayName, int releaseCount, Severity severity) {
        JsonArray events = new JsonArray();
        for (int i = 0; i < releaseCount; i++) {
            events.add(new JsonObject().put("payload", new JsonObject().put("version", "1." + i)));
        }
        JsonObject action = new JsonObject()
            .put("event_type", eventType)
            .put("context", new JsonObject())
            .put("source", new JsonObject().put("event_type", new JsonObject().put("display_name", displayName)))
            .put("events", events);

        EmailAggregation aggregation = new EmailAggregation(ORG_ID, "rhel", "application-services", action, severity, UUID.randomUUID());
        aggregation.setEventTypeName(eventType);
        return aggregation;
    }
}
