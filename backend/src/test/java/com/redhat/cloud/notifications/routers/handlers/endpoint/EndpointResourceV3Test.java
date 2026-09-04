package com.redhat.cloud.notifications.routers.handlers.endpoint;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.PagerDutyProperties;
import com.redhat.cloud.notifications.models.PagerDutySeverity;
import com.redhat.cloud.notifications.models.SourcesSecretable;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointTypeDTO;
import com.redhat.cloud.notifications.models.dto.v3.endpoint.EndpointDTO;
import com.redhat.cloud.notifications.models.dto.v3.endpoint.EndpointSecretsDTO;
import com.redhat.cloud.notifications.models.dto.v3.endpoint.properties.CamelPropertiesDTO;
import com.redhat.cloud.notifications.models.dto.v3.endpoint.properties.PagerDutyPropertiesDTO;
import com.redhat.cloud.notifications.models.dto.v3.endpoint.properties.SystemSubscriptionPropertiesDTO;
import com.redhat.cloud.notifications.models.dto.v3.endpoint.properties.WebhookPropertiesDTO;
import com.redhat.cloud.notifications.routers.sources.Secret;
import com.redhat.cloud.notifications.routers.sources.SourcesPskService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Random;
import java.util.UUID;

import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.FULL_ACCESS;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the behavior that is specific to the v3 endpoints API: the RHCLOUD-34316 secrets handling (v3 never
 * returns secrets in an endpoint's payload, secrets can only be managed through the dedicated "/{id}/secrets"
 * endpoints, and a plain endpoint update must not wipe them), the v3 property mappers (webhook, PagerDuty
 * severity), the "read_only" flag, the linked event types grouping, and the list endpoint's filters/pagination.
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EndpointResourceV3Test extends DbIsolatedTest {

    @InjectMock
    BackendConfig backendConfig;

    @InjectMock
    @RestClient
    SourcesPskService sourcesServiceMock;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EntityManager entityManager;

    private Header identityHeader;
    private String accountId;
    private String orgId;

    @BeforeEach
    void beforeEachV3Test() {
        RestAssured.basePath = TestConstants.API_INTEGRATIONS_V_3_0;

        this.accountId = "v3-secrets-account";
        this.orgId = "v3-secrets-org";
        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(this.accountId, this.orgId, "v3-secrets-user");
        this.identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);
    }

    @AfterEach
    void afterEachV3Test() {
        RestAssured.reset();
    }

    private static EndpointSecretsDTO secretsDto(final String secretToken) {
        final EndpointSecretsDTO dto = new EndpointSecretsDTO();
        dto.setSecretToken(secretToken);
        return dto;
    }

    private Secret mockSecretCreation() {
        final Secret secret = new Secret();
        secret.id = new Random().nextLong(1, Long.MAX_VALUE);

        when(sourcesServiceMock.create(anyString(), anyString(), any(Secret.class))).thenReturn(secret);
        when(sourcesServiceMock.update(anyString(), anyString(), eq(secret.id), any(Secret.class))).thenReturn(secret);

        return secret;
    }

    private JsonObject createCamelSlackEndpoint() {
        final CamelPropertiesDTO properties = new CamelPropertiesDTO();
        properties.setUrl("https://redhat.com");

        final EndpointDTO endpointDTO = new EndpointDTO();
        endpointDTO.setType(EndpointTypeDTO.CAMEL);
        endpointDTO.setSubType("slack");
        endpointDTO.setName("v3 slack endpoint");
        endpointDTO.setDescription("used to test RHCLOUD-34316");
        endpointDTO.setEnabled(true);
        endpointDTO.setProperties(properties);

        final String body = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(endpointDTO))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().body().asString();

        return new JsonObject(body);
    }

    @Test
    void testCreatedEndpointNeverExposesSecrets() {
        final JsonObject created = createCamelSlackEndpoint();
        final JsonObject createdProperties = created.getJsonObject("properties");
        assertFalse(createdProperties.containsKey("secret_token"));
        assertFalse(createdProperties.containsKey("bearer_authentication"));

        final String id = created.getString("id");

        // Set a secret out of band, then verify it never leaks back through GET (single or list).
        this.mockSecretCreation();
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(secretsDto("a-very-secret-token")))
                .put("/endpoints/" + id + "/secrets")
                .then()
                .statusCode(HttpStatus.SC_NO_CONTENT);

        final JsonObject fetched = new JsonObject(
                given()
                        .header(identityHeader)
                        .when().get("/endpoints/" + id)
                        .then()
                        .statusCode(HttpStatus.SC_OK)
                        .extract().body().asString()
        );
        final JsonObject fetchedProperties = fetched.getJsonObject("properties");
        assertFalse(fetchedProperties.containsKey("secret_token"));
        assertFalse(fetchedProperties.containsKey("bearer_authentication"));

        final JsonObject list = new JsonObject(
                given()
                        .header(identityHeader)
                        .when().get("/endpoints")
                        .then()
                        .statusCode(HttpStatus.SC_OK)
                        .extract().body().asString()
        );
        final JsonObject listedProperties = list.getJsonArray("data").getJsonObject(0).getJsonObject("properties");
        assertFalse(listedProperties.containsKey("secret_token"));
        assertFalse(listedProperties.containsKey("bearer_authentication"));
    }

    @Test
    void testCreateEndpointWithSecretsSucceedsAndNeverExposesThem() {
        final Secret secret = this.mockSecretCreation();

        final CamelPropertiesDTO properties = new CamelPropertiesDTO();
        properties.setUrl("https://redhat.com");

        final JsonObject requestBody = new JsonObject()
            .put("type", "camel")
            .put("sub_type", "slack")
            .put("name", "v3 slack endpoint created with a secret")
            .put("description", "used to test RHCLOUD-34316")
            .put("enabled", true)
            .put("properties", new JsonObject().put("url", "https://redhat.com"))
            .put("secrets", new JsonObject().put("secret_token", "set-at-creation-time"));

        final JsonObject created = new JsonObject(
                given()
                        .header(identityHeader)
                        .when()
                        .contentType(JSON)
                        .body(requestBody.encode())
                        .post("/endpoints")
                        .then()
                        .statusCode(HttpStatus.SC_OK)
                        .contentType(JSON)
                        .extract().body().asString()
        );

        final JsonObject createdProperties = created.getJsonObject("properties");
        assertFalse(createdProperties.containsKey("secret_token"));
        assertFalse(createdProperties.containsKey("bearer_authentication"));
        assertFalse(created.containsKey("secrets"));

        verify(sourcesServiceMock).create(anyString(), anyString(), any(Secret.class));

        final Endpoint dbEndpoint = endpointRepository.getEndpoint(orgId, UUID.fromString(created.getString("id")));
        assertEquals(secret.id, ((SourcesSecretable) dbEndpoint.getProperties()).getSecretTokenSourcesId());
    }

    @Test
    void testCreatePagerDutyEndpointWithoutSecretSucceeds() {
        final PagerDutyPropertiesDTO properties = new PagerDutyPropertiesDTO();

        final EndpointDTO endpointDTO = new EndpointDTO();
        endpointDTO.setType(EndpointTypeDTO.PAGERDUTY);
        endpointDTO.setName("v3 pagerduty endpoint");
        endpointDTO.setDescription("used to test RHCLOUD-34316");
        endpointDTO.setEnabled(true);
        endpointDTO.setProperties(properties);

        final JsonObject created = new JsonObject(
                given()
                        .header(identityHeader)
                        .when()
                        .contentType(JSON)
                        .body(Json.encode(endpointDTO))
                        .post("/endpoints")
                        .then()
                        .statusCode(HttpStatus.SC_OK)
                        .contentType(JSON)
                        .extract().body().asString()
        );

        assertFalse(created.getJsonObject("properties").containsKey("severity"));

        final Endpoint dbEndpoint = endpointRepository.getEndpoint(orgId, UUID.fromString(created.getString("id")));
        assertNull(dbEndpoint.getProperties(PagerDutyProperties.class).getSeverity());
    }

    @Test
    void testUpdateAndDeleteEndpointSecrets() {
        final JsonObject created = createCamelSlackEndpoint();
        final String id = created.getString("id");

        final Secret secret = this.mockSecretCreation();

        // Create.
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(secretsDto("first-secret")))
                .put("/endpoints/" + id + "/secrets")
                .then()
                .statusCode(HttpStatus.SC_NO_CONTENT);

        verify(sourcesServiceMock).create(anyString(), anyString(), any(Secret.class));

        Endpoint dbEndpoint = endpointRepository.getEndpoint(orgId, UUID.fromString(id));
        assertEquals(secret.id, ((SourcesSecretable) dbEndpoint.getProperties()).getSecretTokenSourcesId());

        // Update: since an ID already exists in the database, this must PATCH rather than create again.
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(secretsDto("second-secret")))
                .put("/endpoints/" + id + "/secrets")
                .then()
                .statusCode(HttpStatus.SC_NO_CONTENT);

        verify(sourcesServiceMock).update(anyString(), anyString(), eq(secret.id), any(Secret.class));

        // Delete.
        given()
                .header(identityHeader)
                .when()
                .delete("/endpoints/" + id + "/secrets")
                .then()
                .statusCode(HttpStatus.SC_NO_CONTENT);

        verify(sourcesServiceMock).delete(anyString(), anyString(), eq(secret.id));

        // The previous getEndpoint() call above already put this entity in the persistence
        // context; without clearing it, Hibernate would silently return that stale instance
        // instead of reflecting the deletion performed by the separate HTTP request/transaction.
        entityManager.clear();
        dbEndpoint = endpointRepository.getEndpoint(orgId, UUID.fromString(id));
        assertNull(((SourcesSecretable) dbEndpoint.getProperties()).getSecretTokenSourcesId());
    }

    @Test
    void testUpdateEndpointSecretsOnUnsupportedTypeReturnsBadRequest() {
        final SystemSubscriptionPropertiesDTO properties = new SystemSubscriptionPropertiesDTO();

        final EndpointDTO endpointDTO = new EndpointDTO();
        endpointDTO.setType(EndpointTypeDTO.DRAWER);
        endpointDTO.setName("v3 drawer endpoint");
        endpointDTO.setDescription("used to test RHCLOUD-34316");
        endpointDTO.setEnabled(true);
        endpointDTO.setProperties(properties);

        final String id = new JsonObject(
                given()
                        .header(identityHeader)
                        .when()
                        .contentType(JSON)
                        .body(Json.encode(endpointDTO))
                        .post("/endpoints")
                        .then()
                        .statusCode(HttpStatus.SC_OK)
                        .extract().body().asString()
        ).getString("id");

        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(secretsDto("should-not-be-accepted")))
                .put("/endpoints/" + id + "/secrets")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void testPlainEndpointUpdateDoesNotWipeExistingSecret() {
        final JsonObject created = createCamelSlackEndpoint();
        final String id = created.getString("id");

        final Secret secret = this.mockSecretCreation();
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(secretsDto("do-not-wipe-me")))
                .put("/endpoints/" + id + "/secrets")
                .then()
                .statusCode(HttpStatus.SC_NO_CONTENT);

        // A plain update of the endpoint (whose v3 payload has no secret fields at all) must not
        // touch the secret that was just configured.
        final CamelPropertiesDTO properties = new CamelPropertiesDTO();
        properties.setUrl("https://redhat.com");

        final EndpointDTO updateDTO = new EndpointDTO();
        updateDTO.setType(EndpointTypeDTO.CAMEL);
        updateDTO.setSubType("slack");
        updateDTO.setName("v3 slack endpoint (renamed)");
        updateDTO.setDescription("used to test RHCLOUD-34316");
        updateDTO.setEnabled(true);
        updateDTO.setProperties(properties);

        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(updateDTO))
                .put("/endpoints/" + id)
                .then()
                .statusCode(HttpStatus.SC_OK);

        verify(sourcesServiceMock, never()).delete(anyString(), anyString(), anyLong());

        final Endpoint dbEndpoint = endpointRepository.getEndpoint(orgId, UUID.fromString(id));
        assertEquals(secret.id, ((SourcesSecretable) dbEndpoint.getProperties()).getSecretTokenSourcesId());
        assertNotNull(dbEndpoint.getName());
        assertEquals("v3 slack endpoint (renamed)", dbEndpoint.getName());
    }

    @Test
    void testWebhookEndpointPropertiesRoundTrip() {
        final WebhookPropertiesDTO properties = new WebhookPropertiesDTO();
        properties.setUrl("https://redhat.com/webhook");

        final EndpointDTO endpointDTO = new EndpointDTO();
        endpointDTO.setType(EndpointTypeDTO.WEBHOOK);
        endpointDTO.setName("v3 webhook endpoint");
        endpointDTO.setDescription("used to test the v3 webhook properties mapper");
        endpointDTO.setEnabled(true);
        endpointDTO.setProperties(properties);

        final JsonObject created = new JsonObject(
                given()
                        .header(identityHeader)
                        .when()
                        .contentType(JSON)
                        .body(Json.encode(endpointDTO))
                        .post("/endpoints")
                        .then()
                        .statusCode(HttpStatus.SC_OK)
                        .contentType(JSON)
                        .extract().body().asString()
        );
        assertEquals("https://redhat.com/webhook", created.getJsonObject("properties").getString("url"));

        final String id = created.getString("id");
        final JsonObject fetched = new JsonObject(
                given()
                        .header(identityHeader)
                        .when().get("/endpoints/" + id)
                        .then()
                        .statusCode(HttpStatus.SC_OK)
                        .extract().body().asString()
        );
        assertEquals("https://redhat.com/webhook", fetched.getJsonObject("properties").getString("url"));
    }

    @Test
    void testPagerDutySeverityIsNotExposedAndUpdatePreservesIt() {
        final PagerDutyPropertiesDTO properties = new PagerDutyPropertiesDTO();

        final EndpointDTO endpointDTO = new EndpointDTO();
        endpointDTO.setType(EndpointTypeDTO.PAGERDUTY);
        endpointDTO.setName("v3 pagerduty severity endpoint");
        endpointDTO.setDescription("severity must not be exposed in v3");
        endpointDTO.setEnabled(true);
        endpointDTO.setProperties(properties);

        final JsonObject created = new JsonObject(
                given()
                        .header(identityHeader)
                        .when()
                        .contentType(JSON)
                        .body(Json.encode(endpointDTO))
                        .post("/endpoints")
                        .then()
                        .statusCode(HttpStatus.SC_OK)
                        .contentType(JSON)
                        .extract().body().asString()
        );
        assertFalse(created.getJsonObject("properties").containsKey("severity"));

        final String id = created.getString("id");

        // Override the DB severity to CRITICAL to verify that an update does not reset it.
        overridePagerDutySeverity(UUID.fromString(id), PagerDutySeverity.CRITICAL);

        final PagerDutyPropertiesDTO updateProperties = new PagerDutyPropertiesDTO();
        final EndpointDTO updateDTO = new EndpointDTO();
        updateDTO.setType(EndpointTypeDTO.PAGERDUTY);
        updateDTO.setName("v3 pagerduty severity endpoint (renamed)");
        updateDTO.setDescription("severity must not be exposed in v3");
        updateDTO.setEnabled(true);
        updateDTO.setProperties(updateProperties);

        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(updateDTO))
                .put("/endpoints/" + id)
                .then()
                .statusCode(HttpStatus.SC_OK);

        entityManager.clear();
        final Endpoint dbEndpoint = endpointRepository.getEndpoint(orgId, UUID.fromString(id));
        assertEquals(PagerDutySeverity.CRITICAL, dbEndpoint.getProperties(PagerDutyProperties.class).getSeverity());
    }

    @Test
    void testReadOnlyFlagReflectsEndpointOwnership() {
        // A regular, org-owned endpoint must never be reported as read-only.
        final JsonObject createdOwned = createCamelSlackEndpoint();
        final JsonObject fetchedOwned = new JsonObject(
                given()
                        .header(identityHeader)
                        .when().get("/endpoints/" + createdOwned.getString("id"))
                        .then()
                        .statusCode(HttpStatus.SC_OK)
                        .extract().body().asString()
        );
        assertFalse(fetchedOwned.getBoolean("read_only"));

        // A system endpoint (no org id) must be reported as read-only. v3 has no route to create one: it is
        // only ever surfaced through GET, so it is created directly in the database.
        final Endpoint systemEndpoint = this.resourceHelpers.createEndpoint(null, null, EndpointType.EMAIL_SUBSCRIPTION);
        final JsonObject fetchedSystem = new JsonObject(
                given()
                        .header(identityHeader)
                        .when().get("/endpoints/" + systemEndpoint.getId())
                        .then()
                        .statusCode(HttpStatus.SC_OK)
                        .extract().body().asString()
        );
        assertTrue(fetchedSystem.getBoolean("read_only"));

        // The list endpoint must expose the same distinction.
        final JsonArray listedEndpoints = new JsonObject(
                given()
                        .header(identityHeader)
                        .when().get("/endpoints")
                        .then()
                        .statusCode(HttpStatus.SC_OK)
                        .extract().body().asString()
        ).getJsonArray("data");

        boolean sawOwnedEndpoint = false;
        boolean sawSystemEndpoint = false;
        for (int i = 0; i < listedEndpoints.size(); i++) {
            final JsonObject listedEndpoint = listedEndpoints.getJsonObject(i);
            if (listedEndpoint.getString("id").equals(createdOwned.getString("id"))) {
                assertFalse(listedEndpoint.getBoolean("read_only"));
                sawOwnedEndpoint = true;
            } else if (listedEndpoint.getString("id").equals(systemEndpoint.getId().toString())) {
                assertTrue(listedEndpoint.getBoolean("read_only"));
                sawSystemEndpoint = true;
            }
        }
        assertTrue(sawOwnedEndpoint);
        assertTrue(sawSystemEndpoint);
    }

    @Test
    void testEventTypesGroupByBundlesAndApplicationsIsPopulatedOnGet() {
        final Bundle bundle = this.resourceHelpers.createBundle(RandomStringUtils.randomAlphabetic(10).toLowerCase(), "v3 bundle");
        final Application application = this.resourceHelpers.createApplication(bundle.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "v3 application");
        final EventType eventType = this.resourceHelpers.createEventType(application.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "v3 event type", "description");

        final JsonObject requestBody = new JsonObject()
            .put("type", "camel")
            .put("sub_type", "slack")
            .put("name", "v3 endpoint with linked event type")
            .put("description", "used to test eventTypesGroupByBundlesAndApplications")
            .put("enabled", true)
            .put("properties", new JsonObject().put("url", "https://redhat.com"))
            .put("event_types", new io.vertx.core.json.JsonArray().add(eventType.getId().toString()));

        final String id = new JsonObject(
                given()
                        .header(identityHeader)
                        .when()
                        .contentType(JSON)
                        .body(requestBody.encode())
                        .post("/endpoints")
                        .then()
                        .statusCode(HttpStatus.SC_OK)
                        .extract().body().asString()
        ).getString("id");

        final JsonObject fetched = new JsonObject(
                given()
                        .header(identityHeader)
                        .when().get("/endpoints/" + id)
                        .then()
                        .statusCode(HttpStatus.SC_OK)
                        .extract().body().asString()
        );

        final JsonArray bundles = fetched.getJsonArray("event_types_group_by_bundles_and_applications");
        assertEquals(1, bundles.size());
        final JsonObject bundleJson = bundles.getJsonObject(0);
        assertEquals(bundle.getId().toString(), bundleJson.getString("id"));

        final JsonArray applications = bundleJson.getJsonArray("applications");
        assertEquals(1, applications.size());
        final JsonObject applicationJson = applications.getJsonObject(0);
        assertEquals(application.getId().toString(), applicationJson.getString("id"));

        final JsonArray eventTypes = applicationJson.getJsonArray("event_types");
        assertEquals(1, eventTypes.size());
        assertEquals(eventType.getId().toString(), eventTypes.getJsonObject(0).getString("id"));
    }

    @Test
    void testListEndpointsSupportsTypeActiveNameFiltersAndPagination() {
        final WebhookProperties firstEnabledWebhook = new WebhookProperties();
        firstEnabledWebhook.setMethod(HttpType.POST);
        firstEnabledWebhook.setUrl("https://redhat.com/1");

        final WebhookProperties secondEnabledWebhook = new WebhookProperties();
        secondEnabledWebhook.setMethod(HttpType.POST);
        secondEnabledWebhook.setUrl("https://redhat.com/2");

        final WebhookProperties disabledWebhook = new WebhookProperties();
        disabledWebhook.setMethod(HttpType.POST);
        disabledWebhook.setUrl("https://redhat.com/3");

        final CamelProperties camelProperties = new CamelProperties();
        camelProperties.setUrl("https://redhat.com/4");
        camelProperties.setExtras(new HashMap<>());

        this.resourceHelpers.createEndpoint(this.accountId, this.orgId, EndpointType.WEBHOOK, null, "alpha-fox", "d", firstEnabledWebhook, true);
        this.resourceHelpers.createEndpoint(this.accountId, this.orgId, EndpointType.WEBHOOK, null, "bravo-fox", "d", secondEnabledWebhook, true);
        this.resourceHelpers.createEndpoint(this.accountId, this.orgId, EndpointType.WEBHOOK, null, "charlie-fox", "d", disabledWebhook, false);
        this.resourceHelpers.createEndpoint(this.accountId, this.orgId, EndpointType.CAMEL, "slack", "delta-wolf", "d", camelProperties, true);

        // Filter by type: only the three webhooks must be returned.
        given()
                .header(identityHeader)
                .when()
                .queryParam("type", "webhook")
                .get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("meta.count", Matchers.is(3))
                .body("data", Matchers.hasSize(3));

        // Filter by active: the disabled webhook must be excluded.
        given()
                .header(identityHeader)
                .when()
                .queryParam("active", true)
                .get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("meta.count", Matchers.is(3))
                .body("data", Matchers.hasSize(3));

        // Filter by name: only the endpoints whose name contains "fox" must be returned.
        given()
                .header(identityHeader)
                .when()
                .queryParam("name", "fox")
                .get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("meta.count", Matchers.is(3))
                .body("data", Matchers.hasSize(3));

        // Pagination: the total count must reflect all four endpoints regardless of the page size.
        given()
                .header(identityHeader)
                .when()
                .queryParam("limit", 2)
                .queryParam("offset", 0)
                .get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("meta.count", Matchers.is(4))
                .body("data", Matchers.hasSize(2));

        given()
                .header(identityHeader)
                .when()
                .queryParam("limit", 2)
                .queryParam("offset", 2)
                .get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .body("meta.count", Matchers.is(4))
                .body("data", Matchers.hasSize(2));
    }

    void overridePagerDutySeverity(UUID endpointId, PagerDutySeverity severity) {
        QuarkusTransaction.requiringNew().run(() -> {
            entityManager.createQuery("UPDATE PagerDutyProperties SET severity = :severity WHERE endpoint.id = :id")
                    .setParameter("severity", severity)
                    .setParameter("id", endpointId)
                    .executeUpdate();
            entityManager.clear();
        });
    }
}
