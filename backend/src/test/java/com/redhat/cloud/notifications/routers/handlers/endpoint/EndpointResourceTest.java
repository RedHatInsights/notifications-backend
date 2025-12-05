package com.redhat.cloud.notifications.routers.handlers.endpoint;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.auth.kessel.KesselCheckClient;
import com.redhat.cloud.notifications.auth.kessel.KesselTestHelper;
import com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission;
import com.redhat.cloud.notifications.auth.principal.ConsoleIdentity;
import com.redhat.cloud.notifications.auth.rbac.workspace.WorkspaceUtils;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.model.Stats;
import com.redhat.cloud.notifications.db.repositories.BehaviorGroupRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointStatus;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.PagerDutyProperties;
import com.redhat.cloud.notifications.models.PagerDutySeverity;
import com.redhat.cloud.notifications.models.SourcesSecretable;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.models.dto.v1.EventTypeDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointMapper;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointTypeDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.properties.SystemSubscriptionPropertiesDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.properties.WebhookPropertiesDTO;
import com.redhat.cloud.notifications.models.validation.ValidNonPrivateUrlValidator;
import com.redhat.cloud.notifications.models.validation.ValidNonPrivateUrlValidatorTest;
import com.redhat.cloud.notifications.routers.endpoints.EndpointTestRequest;
import com.redhat.cloud.notifications.routers.endpoints.InternalEndpointTestRequest;
import com.redhat.cloud.notifications.routers.engine.EndpointTestService;
import com.redhat.cloud.notifications.routers.models.EndpointPage;
import com.redhat.cloud.notifications.routers.models.RequestSystemSubscriptionProperties;
import com.redhat.cloud.notifications.routers.sources.Secret;
import com.redhat.cloud.notifications.routers.sources.SourcesPskService;
import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.UriBuilder;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.hamcrest.Matchers;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.project_kessel.api.inventory.v1beta2.Allowed;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.FULL_ACCESS;
import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_USER;
import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.CREATE_DRAWER_INTEGRATION;
import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.CREATE_EMAIL_SUBSCRIPTION_INTEGRATION;
import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.INTEGRATIONS_CREATE;
import static com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission.INTEGRATIONS_VIEW;
import static com.redhat.cloud.notifications.models.EndpointStatus.READY;
import static com.redhat.cloud.notifications.models.EndpointType.ANSIBLE;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static com.redhat.cloud.notifications.models.HttpType.POST;
import static com.redhat.cloud.notifications.routers.handlers.endpoint.EndpointResource.AUTO_CREATED_BEHAVIOR_GROUP_NAME_TEMPLATE;
import static com.redhat.cloud.notifications.routers.handlers.endpoint.EndpointResource.DEPRECATED_SLACK_CHANNEL_ERROR;
import static com.redhat.cloud.notifications.routers.handlers.endpoint.EndpointResource.HTTPS_ENDPOINT_SCHEME_REQUIRED;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.TEXT;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.project_kessel.api.inventory.v1beta2.Allowed.ALLOWED_FALSE;
import static org.project_kessel.api.inventory.v1beta2.Allowed.ALLOWED_TRUE;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EndpointResourceTest extends DbIsolatedTest {

    /*
     * In the tests below, most JSON responses are verified using JsonObject/JsonArray instead of deserializing these
     * responses into model instances and checking their attributes values. That's because the model classes contain
     * attributes annotated with @JsonProperty(access = READ_ONLY) which can't be deserialized and therefore verified
     * here. The deserialization is still performed only to verify that the JSON responses data structure is correct.
     */

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_INTEGRATIONS_V_1_0;
        when(backendConfig.isInstantEmailsEnabled()).thenReturn(true);
        when(workspaceUtils.getDefaultWorkspaceId(DEFAULT_ORG_ID)).thenReturn(KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID);
    }

    @Inject
    ResourceHelpers helpers;

    @Inject
    ResourceHelpers resourceHelpers;

    @InjectMock
    BackendConfig backendConfig;

    @InjectMock
    KesselCheckClient kesselCheckClient;

    @Inject
    EndpointMapper endpointMapper;

    @InjectSpy
    EndpointRepository endpointRepository;

    @Inject
    EntityManager entityManager;

    /**
     * Used to verify that the "test this endpoint" payloads are sent with the
     * expected data.
     */
    @InjectMock
    @RestClient
    EndpointTestService endpointTestService;

    @Inject
    KesselTestHelper kesselTestHelper;

    /**
     * We mock the sources service's REST client because there are a few tests
     * that enable the integration, but we don't want to attempt to hit the
     * real service.
     */
    @InjectMock
    @RestClient
    SourcesPskService sourcesServiceMock;

    /**
     * Mocked RBAC's workspace utilities so that the {@link KesselTestHelper}
     * can be used.
     */
    @InjectMock
    WorkspaceUtils workspaceUtils;

    /**
     * Required to set up the mock calls to the sources service mock.
     */
    @ConfigProperty(name = "sources.psk")
    String sourcesPsk;

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Inject
    BehaviorGroupRepository behaviorGroupRepository;

    // Mock the Sources service calls.
    private Secret mockSources(SourcesSecretable properties) {
        return mockSources(properties.getSecretToken());
    }

    private Secret mockSources(final String secretToken) {

        final Secret secret = new Secret();
        secret.id = new Random().nextLong(1, Long.MAX_VALUE);
        secret.password = secretToken;

        when(sourcesServiceMock.create(anyString(), anyString(), any(Secret.class)))
            .thenReturn(secret);

        // Make sure that when the secrets are loaded when we fetch the
        // endpoint again for checking the assertions, we simulate fetching
        // the secrets from Sources too.
        when(sourcesServiceMock.getById(anyString(), anyString(), eq(secret.id)))
            .thenReturn(secret);

        return secret;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testEndpointAdding(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselPermission(INTEGRATIONS_VIEW, ALLOWED_TRUE);
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Test empty tenant
        given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .body(is("{\"data\":[],\"links\":{},\"meta\":{\"count\":0}}"));

        // Add new endpoints
        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(POST);
        properties.setDisableSslVerification(false);
        properties.setSecretToken("my-super-secret-token");
        properties.setUrl(getMockServerUrl());

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("endpoint to find");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(properties);
        ep.setServerErrors(3);

        mockSources(properties);

        Response response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(this.endpointMapper.toDTO(ep)))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        JsonObject responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(EndpointDTO.class);
        assertNotNull(responsePoint.getString("id"));
        assertEquals(3, responsePoint.getInteger("server_errors"));
        assertEquals(READY.toString(), responsePoint.getString("status"));

        // Fetch the list
        response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        EndpointPage endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        List<EndpointDTO> endpointDTOS = endpointPage.getData();

        List<Endpoint> endpoints = new ArrayList<>(endpointDTOS.size());
        for (final EndpointDTO endpointDTO : endpointDTOS) {
            endpoints.add(
                this.endpointMapper.toEntity(endpointDTO)
            );
        }

        assertEquals(1, endpoints.size());

        // Fetch single endpoint also and verify
        JsonObject responsePointSingle = fetchSingle(responsePoint.getString("id"), identityHeader);
        assertNotNull(responsePoint.getJsonObject("properties"));
        assertTrue(responsePointSingle.getBoolean("enabled"));
        assertEquals(READY.toString(), responsePoint.getString("status"));

        // Disable and fetch

        String body =
                given()
                        .header(identityHeader)
                        .when().delete("/endpoints/" + responsePoint.getString("id") + "/enable")
                        .then()
                        .statusCode(HttpStatus.SC_NO_CONTENT)
                        .extract().body().asString();
        assertEquals(0, body.length());

        responsePointSingle = fetchSingle(responsePoint.getString("id"), identityHeader);
        assertNotNull(responsePoint.getJsonObject("properties"));
        assertFalse(responsePointSingle.getBoolean("enabled"));

        // Enable and fetch

        given()
                .header(identityHeader)
                .when().put("/endpoints/" + responsePoint.getString("id") + "/enable")
                .then()
                .statusCode(HttpStatus.SC_OK);

        responsePointSingle = fetchSingle(responsePoint.getString("id"), identityHeader);
        assertNotNull(responsePoint.getJsonObject("properties"));
        assertTrue(responsePointSingle.getBoolean("enabled"));
        assertEquals(0, responsePointSingle.getInteger("server_errors"));

        // Delete
        body =
                given()
                        .header(identityHeader)
                        .when().delete("/endpoints/" + responsePoint.getString("id"))
                        .then()
                        .statusCode(HttpStatus.SC_NO_CONTENT)
                        .extract().body().asString();
        assertEquals(0, body.length());

        // Fetch single
        given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints/" + responsePoint.getString("id"))
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND)
                .contentType(TEXT);

        // Fetch all, nothing should be left
        given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .body(is("{\"data\":[],\"links\":{},\"meta\":{\"count\":0}}"));
    }

    @Test
    void testCreateEndpointWithEventTypes() {
        String accountId = "empty";
        String orgId = "empty";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        final Bundle bundle1 = resourceHelpers.createBundle(RandomStringUtils.randomAlphabetic(10).toLowerCase(), "bundle 1");

        // Create event type and endpoint
        final Application application1 = resourceHelpers.createApplication(bundle1.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "application 1");
        final EventType eventType1 = resourceHelpers.createEventType(application1.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "event type 1", "description");
        final EventType eventType2 = resourceHelpers.createEventType(application1.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "event type 2", "description");
        final EventType retictedRecipientsIntegrationEventType = resourceHelpers.createEventType(application1.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "restricted event type 2", "description", true);

        // Add new endpoints
        WebhookPropertiesDTO properties = new WebhookPropertiesDTO();
        properties.setMethod(POST.name());
        properties.setDisableSslVerification(false);
        properties.setSecretToken("my-super-secret-token");
        properties.setUrl(getMockServerUrl());

        EndpointDTO ep = new EndpointDTO();
        ep.setType(EndpointTypeDTO.WEBHOOK);
        ep.setName("endpoint to find");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(properties);
        ep.setServerErrors(3);
        ep.eventTypes = Set.of(eventType1.getId(), eventType2.getId(), retictedRecipientsIntegrationEventType.getId());

        mockSources(new WebhookProperties());

        // should fail because we try to create a webhook with an event type allowed for email and drawer integrations only
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(ep))
            .post("/endpoints")
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST)
            .contentType(JSON)
            .extract().response();

        ep.eventTypes = Set.of(eventType1.getId(), eventType2.getId());
        Response response = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(ep))
            .post("/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().response();

        JsonObject responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(EndpointDTO.class);
        assertNotNull(responsePoint.getString("id"));
        assertEquals(3, responsePoint.getInteger("server_errors"));
        assertEquals(READY.toString(), responsePoint.getString("status"));

        EndpointDTO resultDto = fetchSingleEndpointV2(UUID.fromString(responsePoint.getString("id")), identityHeader);
        assertNotNull(resultDto.getEventTypesGroupByBundlesAndApplications());
        assertEquals(1, resultDto.getEventTypesGroupByBundlesAndApplications().size());
        assertEquals(1, resultDto.getEventTypesGroupByBundlesAndApplications().stream().findFirst().get().getApplications().size());
        assertEquals(2, resultDto.getEventTypesGroupByBundlesAndApplications().stream().findFirst().get().getApplications().stream().findFirst().get().getEventTypes().size());

        Set<UUID> linkedIds = resultDto.getEventTypesGroupByBundlesAndApplications().stream().findFirst().get().getApplications().stream().findFirst().get().getEventTypes().stream().map(e -> e.getId()).collect(Collectors.toSet());
        assertEquals(ep.eventTypes, linkedIds);

        List<BehaviorGroup> bgList = given()
            .header(identityHeader)
            .basePath(TestConstants.API_NOTIFICATIONS_V_1)
            .pathParam("endpointId", responsePoint.getString("id"))
            .when()
            .get("/notifications/behaviorGroups/affectedByRemovalOfEndpoint/{endpointId}")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().body().as(new TypeRef<>() {
            });

        assertEquals(1, bgList.size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testRepeatedEndpointName(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Endpoint1
        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(POST);
        properties.setDisableSslVerification(false);
        properties.setSecretToken("my-super-secret-token");
        properties.setUrl(getMockServerUrl());

        Endpoint endpoint1 = new Endpoint();
        endpoint1.setType(EndpointType.WEBHOOK);
        endpoint1.setName("Endpoint1");
        endpoint1.setDescription("needle in the haystack");
        endpoint1.setEnabled(true);
        endpoint1.setProperties(properties);
        endpoint1.setServerErrors(3);

        mockSources(properties);

        Response response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(endpoint1))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract().response();

        String endpoint1Id = new JsonObject(response.getBody().asString()).getString("id");
        assertNotNull(endpoint1Id);

        // Trying to add the same endpoint name again results in a bad request
        // error
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(endpoint1))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST);

        // Endpoint2
        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("Endpoint2");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(properties);
        ep.setServerErrors(3);

        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Different endpoint type with same name
        CamelProperties camelProperties = new CamelProperties();
        camelProperties.setExtras(Map.of());
        camelProperties.setSecretToken("secret");
        camelProperties.setUrl("http://nowhere");

        ep = new Endpoint();
        ep.setType(EndpointType.CAMEL);
        ep.setSubType("stuff");
        ep.setName("Endpoint1");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(camelProperties);
        ep.setServerErrors(3);

        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST);

        // Updating endpoint1 name is possible

        endpoint1.setName("Endpoint1-updated");
        given()
                .header(identityHeader)
                .contentType(JSON)
                .body(Json.encode(endpoint1))
                .when()
                .put("/endpoints/" + endpoint1Id)
                .then()
                .statusCode(HttpStatus.SC_OK);

        // Updating to the name of an already existing endpoint is not possible
        endpoint1.setName("Endpoint2");
        given()
                .header(identityHeader)
                .contentType(JSON)
                .body(Json.encode(endpoint1))
                .when()
                .put("/endpoints/" + endpoint1Id)
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    private JsonObject fetchSingle(String id, Header identityHeader) {
        // Decode the header and grab the username to mock the Kessel
        // permission.
        final ConsoleIdentity identity = TestHelpers.decodeRhIdentityHeader(identityHeader.getValue());

        Response response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints/" + id)
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .body("id", equalTo(id))
                .extract().response();

        JsonObject endpoint = new JsonObject(response.getBody().asString());
        return endpoint;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testEndpointValidation(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Add new endpoint without properties
        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("endpoint with missing properties");
        ep.setDescription("Destined to fail");
        ep.setEnabled(true);

        expectReturn400(DEFAULT_USER, identityHeader, ep);

        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(POST);
        properties.setDisableSslVerification(false);
        properties.setSecretToken("my-super-secret-token");
        properties.setUrl(getMockServerUrl());

        // Test with properties, but without endpoint type
        ep.setProperties(properties);
        ep.setType(null);

        expectReturn400(DEFAULT_USER, identityHeader, ep);

        // Test with incorrect webhook properties
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("endpoint with incorrect webhook properties");
        properties.setMethod(null);
        expectReturn400(DEFAULT_USER, identityHeader, ep);

        // Type and attributes don't match
        ep.setName("endpoint with subtype too long");
        ep.setType(EndpointType.CAMEL);
        ep.setSubType("something-longer-than-20-chars");
        expectReturn400(DEFAULT_USER, identityHeader, ep);

        // Test with SSL verification disabled
        properties.setDisableSslVerification(true);
        ep.setType(ANSIBLE);
        ep.setName("endpoint with disabled SSL verification");
        expectReturn400(DEFAULT_USER, identityHeader, ep);
    }

    /**
     * Attempts creating the endpoint and asserts that a "bad request" response
     * is returned.
     * @param DEFAULT_USER the username associated with the identity header.
     * @param identityHeader the encoded identity header.
     * @param ep the endpoint to create.
     */
    private void expectReturn400(final String DEFAULT_USER, final Header identityHeader, final Endpoint ep) {
        given()
                 .header(identityHeader)
                 .when()
                 .contentType(JSON)
                 .body(Json.encode(ep))
                 .post("/endpoints")
                 .then()
                 .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void addCamelEndpoint(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselPermission(INTEGRATIONS_VIEW, ALLOWED_TRUE);
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        CamelProperties cAttr = new CamelProperties();
        cAttr.setDisableSslVerification(false);
        cAttr.setUrl(getMockServerUrl());
        cAttr.setSecretToken("secret-token");
        Map<String, String> extras = new HashMap<>();
        extras.put("template", "11");
        cAttr.setExtras(extras);

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.CAMEL);
        ep.setSubType("ansible");
        ep.setName("Push the camel through the needle's ear");
        ep.setDescription("How many humps has a camel?");
        ep.setEnabled(true);
        ep.setProperties(cAttr);

        // Mock the Sources service calls.
        final Secret secretTokenSecret = mockSources(cAttr);

        // Make sure that when the secrets are loaded when we fetch the
        // endpoint again for checking the assertions, we simulate fetching
        // the secrets from Sources too.
        when(this.sourcesServiceMock.getById(anyString(), anyString(), eq(secretTokenSecret.id))).thenReturn(secretTokenSecret);

        String responseBody = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(this.endpointMapper.toDTO(ep)))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().asString();

        JsonObject responsePoint = new JsonObject(responseBody);
        responsePoint.mapTo(EndpointDTO.class);
        String id = responsePoint.getString("id");
        assertNotNull(id);

        try {
            JsonObject endpoint = fetchSingle(id, identityHeader);
            JsonObject properties = responsePoint.getJsonObject("properties");
            assertNotNull(properties);
            assertTrue(endpoint.getBoolean("enabled"));
            assertEquals("ansible", endpoint.getString("sub_type"));
            JsonObject extrasObject = properties.getJsonObject("extras");
            assertNotNull(extrasObject);
            String template  = extrasObject.getString("template");
            assertEquals("11", template);

            assertEquals("secret-token", properties.getString("secret_token"));
        } finally {

            given()
                    .header(identityHeader)
                    .when().delete("/endpoints/" + id)
                    .then()
                    .statusCode(HttpStatus.SC_NO_CONTENT)
                    .extract().body().asString();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void addBogusCamelEndpoint(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        CamelProperties cAttr = new CamelProperties();
        cAttr.setDisableSslVerification(false);
        cAttr.setUrl(getMockServerUrl());
        Map<String, String> extras = new HashMap<>();
        extras.put("template", "11");
        cAttr.setExtras(extras);

        // This is bogus because it has no sub_type
        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.CAMEL);
        ep.setName("Push the camel through the needle's ear");
        ep.setDescription("How many humps has a camel?");
        ep.setEnabled(true);
        ep.setProperties(cAttr);

        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST);

        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testForbidSlackChannelUsage(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        Map<String, String> extras = new HashMap<>(Map.of("channel", "")); // Having a channel value is invalid.
        CamelProperties camelProperties = new CamelProperties();
        camelProperties.setUrl("https://foo.com");
        camelProperties.setExtras(extras);

        Endpoint endpoint = new Endpoint();
        endpoint.setType(EndpointType.CAMEL);
        endpoint.setSubType("slack");
        endpoint.setName("name");
        endpoint.setDescription("description");
        endpoint.setProperties(camelProperties);

        String responseBody = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(this.endpointMapper.toDTO(endpoint)))
            .post("/endpoints")
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST)
            .extract().asString();

        assertEquals(DEPRECATED_SLACK_CHANNEL_ERROR, responseBody);

        extras.remove("channel");

        String createdEndpoint = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(this.endpointMapper.toDTO(endpoint)))
            .post("/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .extract().asString();

        final JsonObject jsonResponse = new JsonObject(createdEndpoint);
        final String endpointUuidRaw = jsonResponse.getString("id");

        // Set a new endpoint's name to avoid receiving a "duplicate endpoint's
        // name" response.
        endpoint.setName(UUID.randomUUID().toString());

        // try to update endpoint without channel

        given()
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("id", endpointUuidRaw)
            .body(Json.encode(this.endpointMapper.toDTO(endpoint)))
            .when()
            .put("/endpoints/{id}")
            .then()
            .statusCode(HttpStatus.SC_OK);

        // try to update endpoint with channel
        extras.put("channel", "refused");
        given()
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("id", endpointUuidRaw)
            .body(Json.encode(endpoint))
            .when()
            .put("/endpoints/{id}")
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);

        // test create slack integration without extras object
        camelProperties.setExtras(null);
        // Set a new endpoint's name to avoid receiving a "duplicate endpoint's
        // name" response.
        endpoint.setName(UUID.randomUUID().toString());
        endpoint.setProperties(camelProperties);
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(this.endpointMapper.toDTO(endpoint)))
            .post("/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .extract().asString();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testRequireHttpsSchemeServiceNow(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        URI sNowUri = URI.create("http://redhat.com");
        testRequireHttpsScheme("servicenow", sNowUri);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testRequireHttpsSchemeSplunk(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        URI splunkUri = URI.create("http://redhat.com");
        testRequireHttpsScheme("splunk", splunkUri);
    }

    private void testRequireHttpsScheme(String subtype, URI uriWithHttpScheme) {
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        CamelProperties camelProperties = new CamelProperties();
        camelProperties.setUrl(uriWithHttpScheme.toString());
        Endpoint endpoint = new Endpoint();
        endpoint.setType(EndpointType.CAMEL);
        endpoint.setSubType(subtype);
        endpoint.setName("name");
        endpoint.setDescription("description");
        endpoint.setProperties(camelProperties);

        String invalidResp = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(this.endpointMapper.toDTO(endpoint)))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .extract().asString();

        assertEquals(HTTPS_ENDPOINT_SCHEME_REQUIRED, invalidResp);

        URI uriHttps = UriBuilder.fromUri(uriWithHttpScheme).scheme("https").build();
        camelProperties.setUrl(uriHttps.toString());
        endpoint.setProperties(camelProperties);

        String createdEndpoint = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(this.endpointMapper.toDTO(endpoint)))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract().asString();

        final String endpointUuidRaw = new JsonObject(createdEndpoint).getString("id");

        // try to update endpoint with HTTPS URI
        URI exampleUri = URI.create("https://webhook.site/a6de9c30-f4c9-49af-8d03-c9ce7e78fdb3");
        camelProperties.setUrl(exampleUri.toString());
        endpoint.setProperties(camelProperties);
        given()
                .header(identityHeader)
                .contentType(JSON)
                .pathParam("id", endpointUuidRaw)
                .body(Json.encode(this.endpointMapper.toDTO(endpoint)))
                .when()
                .put("/endpoints/{id}")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .extract().asString();

        // try to update endpoint with HTTP URI
        URI insecureExampleUri = UriBuilder.fromUri(exampleUri).scheme("http").build();
        camelProperties.setUrl(insecureExampleUri.toString());
        endpoint.setProperties(camelProperties);
        String sNowInvalidUpdate = given()
                .header(identityHeader)
                .contentType(JSON)
                .pathParam("id", endpointUuidRaw)
                .body(Json.encode(this.endpointMapper.toDTO(endpoint)))
                .when()
                .put("/endpoints/{id}")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .extract().asString();

        assertEquals(HTTPS_ENDPOINT_SCHEME_REQUIRED, sNowInvalidUpdate);

        // try to create Camel endpoint for a service which doesn't require HTTPS
        CamelProperties slackCamelProperties = new CamelProperties();
        slackCamelProperties.setUrl(insecureExampleUri.toString());
        Endpoint slackEndpoint = new Endpoint();
        slackEndpoint.setType(EndpointType.CAMEL);
        slackEndpoint.setSubType("slack");
        slackEndpoint.setName("slack-name");
        slackEndpoint.setDescription("description");
        slackEndpoint.setProperties(slackCamelProperties);

        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(this.endpointMapper.toDTO(slackEndpoint)))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void addSlackEndpoint(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselPermission(INTEGRATIONS_VIEW, ALLOWED_TRUE);
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final ConsoleIdentity ide = TestHelpers.decodeRhIdentityHeader(identityHeaderValue);

        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        CamelProperties cAttr = new CamelProperties();
        cAttr.setDisableSslVerification(false);
        cAttr.setUrl(getMockServerUrl());

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.CAMEL);
        ep.setSubType("slack");
        ep.setName("Push the camel through the needle's ear");
        ep.setDescription("I guess the camel is slacking");
        ep.setEnabled(true);
        ep.setProperties(cAttr);
        ep.setStatus(EndpointStatus.DELETING); // Trying to set other status

        String responseBody = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(this.endpointMapper.toDTO(ep)))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().asString();

        JsonObject responsePoint = new JsonObject(responseBody);
        responsePoint.mapTo(EndpointDTO.class);
        String id = responsePoint.getString("id");
        assertNotNull(id);
        assertEquals(READY.toString(), responsePoint.getString("status"));

        try {
            JsonObject endpoint = fetchSingle(id, identityHeader);
            JsonObject properties = responsePoint.getJsonObject("properties");
            assertNotNull(properties);
            assertTrue(endpoint.getBoolean("enabled"));
            assertEquals("slack", endpoint.getString("sub_type"));
            JsonObject extrasObject = properties.getJsonObject("extras");
            assertNull(extrasObject);

            ep.getProperties(CamelProperties.class).setUrl("https://redhat.com");

            // Now update
            responseBody = given()
                    .header(identityHeader)
                    .contentType(JSON)
                    .body(Json.encode(this.endpointMapper.toDTO(ep)))
                    .when()
                    .put("/endpoints/" + id)
                    .then()
                    .statusCode(HttpStatus.SC_OK)
                    .extract().asString();

            assertNotNull(responseBody);

            CamelProperties updatedProperties = entityManager.createQuery("FROM CamelProperties WHERE id = :id", CamelProperties.class)
                    .setParameter("id", UUID.fromString(id))
                    .getSingleResult();
            assertEquals(ep.getProperties(CamelProperties.class).getUrl(), updatedProperties.getUrl());

        } finally {

            given()
                    .header(identityHeader)
                    .when().delete("/endpoints/" + id)
                    .then()
                    .statusCode(HttpStatus.SC_NO_CONTENT);

            given()
                    .header(identityHeader)
                    .when().get("/endpoints/" + id)
                    .then().statusCode(HttpStatus.SC_NOT_FOUND);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testEndpointUpdates(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselPermission(INTEGRATIONS_VIEW, ALLOWED_TRUE);
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Test empty tenant
        given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .body(is("{\"data\":[],\"links\":{},\"meta\":{\"count\":0}}"));

        // Add new endpoints
        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(POST);
        properties.setDisableSslVerification(false);
        properties.setSecretToken("my-super-secret-token");
        properties.setUrl(getMockServerUrl());

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("endpoint to find");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(properties);
        ep.setServerErrors(7);

        Secret secretTokenSecret = mockSources(properties);

        Response response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(this.endpointMapper.toDTO(ep)))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        JsonObject responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(EndpointDTO.class);
        assertNotNull(responsePoint.getString("id"));
        assertEquals(7, responsePoint.getInteger("server_errors"));

        // Fetch the list
        response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .contentType(JSON)
                .when().get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        EndpointPage endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        List<EndpointDTO> endpointDTOS = endpointPage.getData();

        List<Endpoint> endpoints = new ArrayList<>(endpointDTOS.size());
        for (final EndpointDTO endpointDTO : endpointDTOS) {
            endpoints.add(
                    this.endpointMapper.toEntity(endpointDTO)
            );
        }

        assertEquals(1, endpoints.size());

        // Fetch single endpoint also and verify
        JsonObject responsePointSingle = fetchSingle(responsePoint.getString("id"), identityHeader);
        assertNotNull(responsePoint.getJsonObject("properties"));
        assertTrue(responsePointSingle.getBoolean("enabled"));

        // Update the endpoint
        responsePointSingle.put("name", "endpoint found");
        JsonObject attrSingle = responsePointSingle.getJsonObject("properties");
        attrSingle.mapTo(WebhookProperties.class);
        attrSingle.put("secret_token", "not-so-secret-anymore");

        // Give the Kessel permissions to see the endpoint.

        // Update without payload
        given()
                .header(identityHeader)
                .contentType(JSON)
                .when()
                .put(String.format("/endpoints/%s", responsePointSingle.getString("id")))
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST);

        // With payload

        given()
                .header(identityHeader)
                .contentType(JSON)
                .when()
                .body(responsePointSingle.encode())
                .put(String.format("/endpoints/%s", responsePointSingle.getString("id")))
                .then()
                .statusCode(HttpStatus.SC_OK);

        // We need to mock the Sources call a second time, because when we
        // updated the endpoint with the secret token above, in theory
        // Notifications would have called Sources to update the secret too.
        // So now that we are going to fetch the endpoint again to perform some
        // assertions, we need to simulate that we are calling Sources again
        // and that the secrets were effectively updated.
        final Secret updatedSecretTokenSecret = new Secret();
        updatedSecretTokenSecret.id = secretTokenSecret.id;
        updatedSecretTokenSecret.password = attrSingle.getString("secret_token");

        // Make sure that when the secrets are loaded when we fetch the
        // endpoint again for checking the assertions, we simulate fetching
        // the secrets from Sources too.
        when(this.sourcesServiceMock.getById(anyString(), anyString(), eq(secretTokenSecret.id))).thenReturn(updatedSecretTokenSecret);

        // Fetch single one again to see that the updates were done
        JsonObject updatedEndpoint = fetchSingle(responsePointSingle.getString("id"), identityHeader);
        JsonObject attrSingleUpdated = updatedEndpoint.getJsonObject("properties");
        attrSingleUpdated.mapTo(WebhookProperties.class);
        assertEquals("endpoint found", updatedEndpoint.getString("name"));
        assertEquals("not-so-secret-anymore", attrSingleUpdated.getString("secret_token"));
        assertEquals(0, updatedEndpoint.getInteger("server_errors"));
    }

    private static Stream<Arguments> testEndpointTypeQuery() {
        return Stream.of(
            Arguments.of(true, Set.of(EndpointType.WEBHOOK)),
            Arguments.of(true, Set.of(EndpointType.CAMEL)),
            Arguments.of(true, Set.of(EndpointType.WEBHOOK, EndpointType.CAMEL)),
            Arguments.of(false, Set.of(EndpointType.WEBHOOK)),
            Arguments.of(false, Set.of(EndpointType.CAMEL)),
            Arguments.of(false, Set.of(EndpointType.WEBHOOK, EndpointType.CAMEL))
        );
    }

    @ParameterizedTest
    @MethodSource("testEndpointTypeQuery")
    void testEndpointTypeQueryKesselRelations(boolean kesselEnabled, final Set<EndpointType> types) {
        testEndpointTypeQuery(kesselEnabled, types);

    }

    @ParameterizedTest
    @MethodSource("testEndpointTypeQuery")
    void testEndpointTypeQueryKesselInventory(boolean kesselEnabled, final Set<EndpointType> types) {
        testEndpointTypeQuery(kesselEnabled, types);
    }

    void testEndpointTypeQuery(boolean kesselEnabled, final Set<EndpointType> types) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselPermission(INTEGRATIONS_VIEW, ALLOWED_TRUE);
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Add webhook
        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(POST);
        properties.setDisableSslVerification(false);
        properties.setSecretToken("my-super-secret-token");
        properties.setUrl(getMockServerUrl());

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("endpoint to find");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(properties);

        mockSources(properties);

        final Set<UUID> createdEndpointIds = new HashSet<>();
        Response response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(this.endpointMapper.toDTO(ep)))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        JsonObject responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(EndpointDTO.class);
        assertNotNull(responsePoint.getString("id"));
        createdEndpointIds.add(UUID.fromString(responsePoint.getString("id")));

        // Add Camel
        CamelProperties camelProperties = new CamelProperties();
        camelProperties.setDisableSslVerification(false);
        camelProperties.setSecretToken("my-super-secret-token");
        camelProperties.setUrl(getMockServerUrl());
        camelProperties.setExtras(new HashMap<>());

        Endpoint camelEp = new Endpoint();
        camelEp.setType(EndpointType.CAMEL);
        camelEp.setSubType("demo");
        camelEp.setName("endpoint 2 to find");
        camelEp.setDescription("needle in the haystack");
        camelEp.setEnabled(true);
        camelEp.setProperties(camelProperties);

        response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(this.endpointMapper.toDTO(camelEp)))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(EndpointDTO.class);
        assertNotNull(responsePoint.getString("id"));
        createdEndpointIds.add(UUID.fromString(responsePoint.getString("id")));

        // Fetch the list to ensure everything was inserted correctly.
        response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        EndpointPage endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        List<EndpointDTO> endpointDTOS = endpointPage.getData();

        List<Endpoint> endpoints = new ArrayList<>(endpointDTOS.size());
        for (final EndpointDTO endpointDTO : endpointDTOS) {
            endpoints.add(
                    this.endpointMapper.toEntity(endpointDTO)
            );
        }
        assertEquals(2, endpoints.size());

        // Fetch the list with types
        response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .queryParam("type", types)
                .when().get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        endpointDTOS = endpointPage.getData();

        endpoints = new ArrayList<>(endpointDTOS.size());
        for (final EndpointDTO endpointDTO : endpointDTOS) {
            endpoints.add(
                    this.endpointMapper.toEntity(endpointDTO)
            );
        }

        // Ensure there is only the requested types
        assertEquals(
                types,
                endpoints.stream().map(Endpoint::getType).collect(Collectors.toSet())
        );
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testEndpointLimiter(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselPermission(INTEGRATIONS_VIEW, ALLOWED_TRUE);
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        final Set<UUID> createdEndpointsIdentifiers = addEndpoints(29, identityHeader);

        // Fetch the list, page 1
        Response response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .queryParam("limit", "10")
                .queryParam("offset", "0")
                .when().get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        EndpointPage endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        List<EndpointDTO> endpointDTOS = endpointPage.getData();

        List<Endpoint> endpoints = new ArrayList<>(endpointDTOS.size());
        for (final EndpointDTO endpointDTO : endpointDTOS) {
            endpoints.add(
                    this.endpointMapper.toEntity(endpointDTO)
            );
        }
        assertEquals(10, endpoints.size());
        assertEquals(29, endpointPage.getMeta().getCount());

        // Fetch the list, page 3
        response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .queryParam("limit", "10")
                .queryParam("pageNumber", "2")
                .when().get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        endpointDTOS = endpointPage.getData();

        endpoints = new ArrayList<>(endpointDTOS.size());
        for (final EndpointDTO endpointDTO : endpointDTOS) {
            endpoints.add(
                    this.endpointMapper.toEntity(endpointDTO)
            );
        }

        assertEquals(9, endpoints.size());
        assertEquals(29, endpointPage.getMeta().getCount());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testSortingOrder(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselPermission(INTEGRATIONS_VIEW, ALLOWED_TRUE);
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Create 50 test-ones with sanely sortable name & enabled & disabled & type
        final Stats stats = helpers.createTestEndpoints(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, 50);

        Response response = given()
                .header(identityHeader)
                .when()
                .get("/endpoints?limit=100")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        EndpointPage endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);

        List<Endpoint> endpoints = new ArrayList<>(endpointPage.getData().size());
        for (final EndpointDTO endpointDTO : endpointPage.getData()) {
            endpoints.add(this.endpointMapper.toEntity(endpointDTO));
        }

        assertEquals(stats.getCreatedEndpointsCount(), endpoints.size());

        response = given()
                .header(identityHeader)
                .queryParam("sort_by", "enabled")
                .when()
                .get("/endpoints?limit=100")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);

        endpoints = new ArrayList<>(endpointPage.getData().size());
        for (final EndpointDTO endpointDTO : endpointPage.getData()) {
            endpoints.add(this.endpointMapper.toEntity(endpointDTO));
        }

        assertFalse(endpoints.get(0).isEnabled());
        assertFalse(endpoints.get(stats.getDisabledCount() - 1).isEnabled());
        assertTrue(endpoints.get(stats.getDisabledCount()).isEnabled());
        assertTrue(endpoints.get(stats.getCreatedEndpointsCount() - 1).isEnabled());

        response = given()
                .header(identityHeader)
                .queryParam("sort_by", "name:desc")
                .queryParam("limit", "50")
                .queryParam("offset", stats.getCreatedEndpointsCount() - 20)
                .when()
                .get("/endpoints?limit=100")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);

        endpoints = new ArrayList<>(endpointPage.getData().size());
        for (final EndpointDTO endpointDTO : endpointPage.getData()) {
            endpoints.add(this.endpointMapper.toEntity(endpointDTO));
        }

        assertEquals(20, endpoints.size());
        assertEquals("Endpoint 1", endpoints.get(endpoints.size() - 1).getName());
        assertEquals("Endpoint 10", endpoints.get(endpoints.size() - 2).getName());
        assertEquals("Endpoint 27", endpoints.get(0).getName());

        given()
                .header(identityHeader)
                .queryParam("sort_by", "hulla:desc")
                .when()
                .get("/endpoints?limit=100")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST);

    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testWebhookAttributes(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselPermission(INTEGRATIONS_VIEW, ALLOWED_TRUE);
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Add new endpoints
        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(POST);
        properties.setDisableSslVerification(false);
        properties.setSecretToken("my-super-secret-token");
        properties.setBearerAuthentication("my-test-bearer-token");
        properties.setUrl(getMockServerUrl());

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("endpoint to find");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(properties);

        // Mock the Sources service calls.
        final Secret secretTokenSecret = new Secret();
        secretTokenSecret.id = new Random().nextLong(1, Long.MAX_VALUE);
        secretTokenSecret.password = properties.getSecretToken();

        final Secret bearerTokenSecret = new Secret();
        bearerTokenSecret.id = new Random().nextLong(1, Long.MAX_VALUE);
        bearerTokenSecret.password = properties.getBearerAuthentication();

        // The SecretUtils class follows the "secret
        // token" and "bearer token" order, so that is why we make the returns
        // in that order for the mock.
        when(this.sourcesServiceMock.create(anyString(), anyString(), any()))
            .thenReturn(secretTokenSecret, bearerTokenSecret);

        // Make sure that when the secrets are loaded when we fetch the
        // endpoint again for checking the assertions, we simulate fetching
        // the secrets from Sources too.
        when(this.sourcesServiceMock.getById(anyString(), anyString(), eq(secretTokenSecret.id))).thenReturn(secretTokenSecret);
        when(this.sourcesServiceMock.getById(anyString(), anyString(), eq(bearerTokenSecret.id))).thenReturn(bearerTokenSecret);

        Response response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(this.endpointMapper.toDTO(ep)))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        JsonObject responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(EndpointDTO.class);
        assertNotNull(responsePoint.getString("id"));

        // Fetch single endpoint and make the verifications. Make the user have
        // "edit" permission in the integration so that the secrets are not
        // redacted.

        JsonObject responsePointSingle = fetchSingle(responsePoint.getString("id"), identityHeader);

        assertNotNull(responsePoint.getJsonObject("properties"));
        assertTrue(responsePointSingle.getBoolean("enabled"));
        assertNotNull(responsePointSingle.getJsonObject("properties"));
        assertNotNull(responsePointSingle.getJsonObject("properties").getString("secret_token"));

        JsonObject attr = responsePointSingle.getJsonObject("properties");
        attr.mapTo(WebhookProperties.class);
        assertEquals(properties.getBearerAuthentication(), attr.getString("bearer_authentication"));
    }

    @ParameterizedTest
    @MethodSource("kesselFlagsEmailOrDrawerEndpoints")
    void testAddEndpointEmailOrDrawerSubscriptionAsRegularEndpoint(boolean kesselEnabled, EndpointType endpointType) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
            mockDefaultKesselUpdatePermission(CREATE_EMAIL_SUBSCRIPTION_INTEGRATION, ALLOWED_TRUE);
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        SystemSubscriptionProperties properties = new SystemSubscriptionProperties();

        Endpoint ep = new Endpoint();
        ep.setType(endpointType);
        ep.setName("Endpoint: " + endpointType.name());
        ep.setDescription("Subscribe!");
        ep.setEnabled(true);
        ep.setProperties(properties);

        // Email or Drawer subscriptions can be fetched from the properties

        Response response = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(this.endpointMapper.toDTO(ep)))
            .post("/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().response();

        JsonObject responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(EndpointDTO.class);
        assertNotNull(responsePoint.getString("id"));

        // It is always enabled
        assertEquals(true, responsePoint.getBoolean("enabled"));

        // Calling again yields the same endpoint id
        String defaultEndpointId = responsePoint.getString("id");

        Response badRequestDupName = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(this.endpointMapper.toDTO(ep)))
            .post("/endpoints")
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST)
            .contentType(JSON)
            .extract().response();

        assertTrue(badRequestDupName.getBody().asString().contains(String.format(
            "An endpoint with name [%s] already exists",
            ep.getName()
        )));

        // It is possible to disable or enable it

        given()
            .header(identityHeader)
            .when().delete("/endpoints/" + defaultEndpointId + "/enable")
            .then()
            .statusCode(HttpStatus.SC_NO_CONTENT);

        given()
            .header(identityHeader)
            .when().put("/endpoints/" + defaultEndpointId + "/enable")
            .then()
            .statusCode(HttpStatus.SC_OK);

        // It is possible to update it

        given()
                .header(identityHeader)
                .contentType(JSON)
                .body(Json.encode(ep))
                .when().put("/endpoints/" + defaultEndpointId)
                .then()
                .statusCode(HttpStatus.SC_OK);

        // It is possible to update it to other type
        ep.setType(EndpointType.WEBHOOK);

        WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setMethod(POST);
        webhookProperties.setDisableSslVerification(false);
        webhookProperties.setSecretToken("my-super-secret-token");
        webhookProperties.setUrl(getMockServerUrl());
        ep.setProperties(webhookProperties);

        given()
            .header(identityHeader)
            .contentType(JSON)
            .body(Json.encode(ep))
            .when().put("/endpoints/" + defaultEndpointId)
            .then()
            .statusCode(HttpStatus.SC_OK);

        // It is not possible to delete it

        given()
            .header(identityHeader)
            .when().delete("/endpoints/" + defaultEndpointId)
            .then()
            .statusCode(HttpStatus.SC_NO_CONTENT)
            .extract().response();
    }

    @ParameterizedTest
    @MethodSource("kesselFlagsEmailOrDrawerEndpoints")
    void testAddEndpointEmailOrDrawerSubscription(boolean kesselEnabled, EndpointType endpointType) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
            mockDefaultKesselUpdatePermission(CREATE_DRAWER_INTEGRATION, ALLOWED_TRUE);
            mockDefaultKesselUpdatePermission(CREATE_EMAIL_SUBSCRIPTION_INTEGRATION, ALLOWED_TRUE);
            mockDefaultKesselUpdatePermission(CREATE_DRAWER_INTEGRATION, ALLOWED_TRUE);
        }

        final String endpointTypeUrl;
        if (EndpointType.EMAIL_SUBSCRIPTION == endpointType) {
            endpointTypeUrl = "email_subscription";
        } else {
            endpointTypeUrl = "drawer_subscription";
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        SystemSubscriptionProperties properties = new SystemSubscriptionProperties();

        Endpoint ep = new Endpoint();
        ep.setType(endpointType);
        ep.setName("Endpoint: " + endpointType.name());
        ep.setDescription("Subscribe!");
        ep.setEnabled(true);
        ep.setProperties(properties);

        RequestSystemSubscriptionProperties requestProps = new RequestSystemSubscriptionProperties();

        // EmailSubscription or Drawer endpoints can be fetched from the properties

        Response response = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(requestProps))
            .pathParam("endpoint_type", endpointTypeUrl)
            .post("/endpoints/system/{endpoint_type}")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().response();

        JsonObject responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(EndpointDTO.class);
        assertNotNull(responsePoint.getString("id"));

        // It is always enabled
        assertEquals(true, responsePoint.getBoolean("enabled"));

        // Calling again yields the same endpoint id
        String defaultEndpointId = responsePoint.getString("id");

        response = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(requestProps))
            .pathParam("endpoint_type", endpointTypeUrl)
            .post("/endpoints/system/{endpoint_type}")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().response();

        responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(EndpointDTO.class);
        assertEquals(defaultEndpointId, responsePoint.getString("id"));

        // Different properties are different endpoints
        Set<String> endpointIds = new HashSet<>();
        endpointIds.add(defaultEndpointId);

        requestProps.setOnlyAdmins(true);

        response = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(requestProps))
            .pathParam("endpoint_type", endpointTypeUrl)
            .post("/endpoints/system/{endpoint_type}")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().response();

        responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(EndpointDTO.class);
        assertFalse(endpointIds.contains(responsePoint.getString("id")));
        endpointIds.add(responsePoint.getString("id"));

        response = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(requestProps))
            .pathParam("endpoint_type", endpointTypeUrl)
            .post("/endpoints/system/{endpoint_type}")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().response();

        responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(EndpointDTO.class);
        assertTrue(endpointIds.contains(responsePoint.getString("id")));

    }

    @ParameterizedTest
    @MethodSource("kesselFlagsEmailOrDrawerEndpoints")
    void testAddEndpointEmailOrDrawerSubscriptionRbacAsRegularEndpoint(boolean kesselEnabled, EndpointType endpointType) {

        if (kesselEnabled) {
            mockDefaultKesselPermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        String validGroupId = "f85517d0-063b-4eed-a501-e79ffc1f5ad3";
        String unknownGroupId = "f44f50d5-acab-482c-a3cf-087faf2c709c";

        MockServerConfig.addGroupResponse(identityHeaderValue, validGroupId, HttpStatus.SC_OK);
        MockServerConfig.addGroupResponse(identityHeaderValue, unknownGroupId, HttpStatus.SC_NOT_FOUND);

        SystemSubscriptionProperties requestProps = new SystemSubscriptionProperties();

        Endpoint ep = new Endpoint();
        ep.setType(endpointType);
        ep.setName("Endpoint: " + endpointType.name());
        ep.setDescription("Subscribe!");
        ep.setEnabled(true);
        ep.setProperties(requestProps);

        requestProps.setGroupId(UUID.fromString(validGroupId));

        Response response = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(endpointMapper.toDTO(ep))
            .post("/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().response();

        JsonObject responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(EndpointDTO.class);
        String endpointId = responsePoint.getString("id");
        assertNotNull(endpointId);

        // Same group again yields the same endpoint id
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(endpointMapper.toDTO(ep))
            .put("/endpoints/" + endpointId)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .extract().response();

        // Invalid group is a bad request (i.e. group does not exist)
        requestProps.setGroupId(UUID.fromString(unknownGroupId));
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(endpointMapper.toDTO(ep))
            .put("/endpoints/" + endpointId)
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST)
            .extract().response();

        // Can't specify admin and group - bad request
        requestProps.setGroupId(UUID.fromString(validGroupId));
        requestProps.setOnlyAdmins(true);
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(endpointMapper.toDTO(ep))
            .put("/endpoints/" + endpointId)
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST)
            .extract().response();
    }


    @ParameterizedTest
    @MethodSource("kesselFlagsEmailOrDrawerEndpoints")
    void testAddEndpointEmailOrDrawerSubscriptionRbac(boolean kesselEnabled, EndpointType endpointType) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(CREATE_EMAIL_SUBSCRIPTION_INTEGRATION, ALLOWED_TRUE);
            mockDefaultKesselUpdatePermission(CREATE_DRAWER_INTEGRATION, ALLOWED_TRUE);
        }

        final String endpointTypeUrl;
        if (EndpointType.EMAIL_SUBSCRIPTION == endpointType) {
            endpointTypeUrl = "email_subscription";
        } else {
            endpointTypeUrl = "drawer_subscription";
        }

        String validGroupId = "f85517d0-063b-4eed-a501-e79ffc1f5ad3";
        String unknownGroupId = "f44f50d5-acab-482c-a3cf-087faf2c709c";

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);
        MockServerConfig.addGroupResponse(identityHeaderValue, validGroupId, HttpStatus.SC_OK);
        MockServerConfig.addGroupResponse(identityHeaderValue, unknownGroupId, HttpStatus.SC_NOT_FOUND);

        RequestSystemSubscriptionProperties requestProps = new RequestSystemSubscriptionProperties();
        requestProps.setGroupId(UUID.fromString(validGroupId));

        Response response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(requestProps))
                .pathParam("endpoint_type", endpointTypeUrl)
                .post("/endpoints/system/{endpoint_type}")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        JsonObject responsePoint = new JsonObject(response.getBody().asString());

        // check rbac group id from response
        EndpointDTO endpointDto = responsePoint.mapTo(EndpointDTO.class);
        assertInstanceOf(SystemSubscriptionPropertiesDTO.class, endpointDto.getProperties());
        SystemSubscriptionPropertiesDTO emailEndpointProperties = (SystemSubscriptionPropertiesDTO) endpointDto.getProperties();
        assertEquals(requestProps.getGroupId(), emailEndpointProperties.getGroupId());
        assertEquals(1, emailEndpointProperties.getGroupIds().size());
        assertTrue(emailEndpointProperties.getGroupIds().contains(requestProps.getGroupId()));

        String endpointId = responsePoint.getString("id");
        assertNotNull(endpointId);

        // Same group again yields the same endpoint id
        response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(requestProps))
                .pathParam("endpoint_type", endpointTypeUrl)
                .post("/endpoints/system/{endpoint_type}")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(EndpointDTO.class);
        assertEquals(endpointId, responsePoint.getString("id"));

        // Invalid group is a bad request (i.e. group does not exist)
        requestProps.setGroupId(UUID.fromString(unknownGroupId));
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(requestProps))
                .pathParam("endpoint_type", endpointTypeUrl)
                .post("/endpoints/system/{endpoint_type}")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .contentType(JSON)
                .extract().response();

        // Can't specify admin and group - bad request
        requestProps.setGroupId(UUID.fromString(validGroupId));
        requestProps.setOnlyAdmins(true);
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(requestProps))
                .pathParam("endpoint_type", endpointTypeUrl)
                .post("/endpoints/system/{endpoint_type}")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .contentType(JSON)
                .extract().response();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testUnknownEndpointTypes(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselPermission(INTEGRATIONS_VIEW, ALLOWED_TRUE);
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        given()
                .header(identityHeader)
                .queryParam("type", "foo")
                .when().get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(is("Unknown endpoint type: [foo]"));

        // Same thing here.
        given()
                .header(identityHeader)
                .queryParam("type", EndpointType.WEBHOOK.toString())
                .queryParam("type", "bar")
                .when().get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(is("Unknown endpoint type: [bar]"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testConnectionCount(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselPermission(INTEGRATIONS_VIEW, ALLOWED_TRUE);
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Test empty tenant

        given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .body(is("{\"data\":[],\"links\":{},\"meta\":{\"count\":0}}"));

        final Set<UUID> createdEndpointIds = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            // Add new endpoints
            WebhookProperties properties = new WebhookProperties();
            properties.setMethod(POST);
            properties.setDisableSslVerification(false);
            properties.setSecretToken("my-super-secret-token");
            properties.setUrl(getMockServerUrl());

            Endpoint ep = new Endpoint();
            ep.setType(EndpointType.WEBHOOK);
            ep.setName("endpoint to find" + i);
            ep.setDescription("needle in the haystack" + i);
            ep.setEnabled(true);
            ep.setProperties(properties);

            mockSources(properties);

            Response response = given()
                    .header(identityHeader)
                    .when()
                    .contentType(JSON)
                    .body(Json.encode(ep))
                    .post("/endpoints")
                    .then()
                    .statusCode(HttpStatus.SC_OK)
                    .contentType(JSON)
                    .extract().response();

            JsonObject responsePoint = new JsonObject(response.getBody().asString());
            responsePoint.mapTo(EndpointDTO.class);
            assertNotNull(responsePoint.getString("id"));

            createdEndpointIds.add(UUID.fromString(responsePoint.getString("id")));

            // Fetch the list
            given()
                    // Set header to x-rh-identity
                    .header(identityHeader)
                    .when().get("/endpoints")
                    .then()
                    .statusCode(HttpStatus.SC_OK)
                    .contentType(JSON);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testActive(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselPermission(INTEGRATIONS_VIEW, ALLOWED_TRUE);
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(TestConstants.DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        final Stats stats = resourceHelpers.createTestEndpoints(TestConstants.DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, 11);

        // Get all endpoints

        Response response = given()
                .header(identityHeader)
                .when()
                .get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        EndpointPage endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        assertEquals(stats.getCreatedEndpointsCount(), endpointPage.getMeta().getCount());
        assertEquals(stats.getCreatedEndpointsCount(), endpointPage.getData().size());

        // Only active
        response = given()
                .header(identityHeader)
                .when()
                .queryParam("active", "true")
                .get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        assertEquals(stats.getCreatedEndpointsCount() - stats.getDisabledCount(), endpointPage.getMeta().getCount());
        assertEquals(stats.getCreatedEndpointsCount() - stats.getDisabledCount(), endpointPage.getData().size());

        // Only inactive
        response = given()
                .header(identityHeader)
                .when()
                .queryParam("active", "false")
                .get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        assertEquals(stats.getDisabledCount(), endpointPage.getMeta().getCount());
        assertEquals(stats.getDisabledCount(), endpointPage.getData().size());

    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testSearch(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselPermission(INTEGRATIONS_VIEW, ALLOWED_TRUE);
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        addEndpoints(10, identityHeader);

        Response response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .queryParam("limit", "20")
                .queryParam("offset", "0")
                .queryParam("name", "2")
                .when().get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        EndpointPage endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        assertEquals(1, endpointPage.getMeta().getCount());
        assertEquals(1, endpointPage.getData().size());
        assertEquals("Endpoint 2", endpointPage.getData().get(0).getName());

        response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .queryParam("limit", "20")
                .queryParam("offset", "0")
                .queryParam("name", "foo")
                .when().get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        assertEquals(0, endpointPage.getMeta().getCount());
        assertEquals(0, endpointPage.getData().size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testSearchWithType(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselPermission(INTEGRATIONS_VIEW, ALLOWED_TRUE);
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        final Set<UUID> createdEndpoints = addEndpoints(10, identityHeader);

        Response response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .queryParam("limit", "20")
                .queryParam("offset", "0")
                .queryParam("name", "2")
                .queryParam("type", "WEBHOOK")
                .when().get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        EndpointPage endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        assertEquals(1, endpointPage.getMeta().getCount());
        assertEquals(1, endpointPage.getData().size());
        assertEquals("Endpoint 2", endpointPage.getData().get(0).getName());

        // because Kessel ListIntegration mocks uses Multi, we have to re-init the mock before each call
        response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .queryParam("limit", "20")
                .queryParam("offset", "0")
                .queryParam("name", "foo")
                .queryParam("type", "WEBHOOK")
                .when().get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        assertEquals(0, endpointPage.getMeta().getCount());
        assertEquals(0, endpointPage.getData().size());
    }

    /**
     * Tests that when an invalid URL is provided via the endpoint's properties, regardless if those properties are
     * {@link CamelProperties} or {@link WebhookProperties}, the proper constraint violation message is returned from
     * the handler.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testEndpointInvalidUrls(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        // Set up the RBAC access for the test.
        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Create the properties for the endpoint. Leave the URL so that we can set it afterwards.
        final var camelProperties = new CamelProperties();
        camelProperties.setDisableSslVerification(false);
        camelProperties.setSecretToken("endpoint-invalid-urls-secret-token");

        final var webhookProperties = new WebhookProperties();
        webhookProperties.setDisableSslVerification(false);
        webhookProperties.setMethod(POST);
        webhookProperties.setSecretToken("endpoint-invalid-urls-secret-token");

        // Create an endpoint without the type and the properties set.
        final var endpoint = new Endpoint();
        endpoint.setDescription("endpoint-invalid-urls-description");
        endpoint.setEnabled(true);
        endpoint.setName("endpoint-invalid-urls-name");
        endpoint.setServerErrors(0);

        // Create a simple class to make testing easier.
        final class TestCase {
            public final String expectedErrorMessage;
            public final String[] testUrls;

            TestCase(final String expectedErrorMessage, final String[] testUrls) {
                this.expectedErrorMessage = expectedErrorMessage;
                this.testUrls = testUrls;
            }
        }

        // Create all the test cases we will be testing in this test.
        final List<TestCase> testCases = new ArrayList<>();
        testCases.add(
            new TestCase(ValidNonPrivateUrlValidator.INVALID_URL, ValidNonPrivateUrlValidatorTest.malformedUrls)
        );

        testCases.add(
            new TestCase(ValidNonPrivateUrlValidator.INVALID_URL, ValidNonPrivateUrlValidatorTest.malformedUris)
        );

        testCases.add(
            new TestCase(ValidNonPrivateUrlValidator.INVALID_SCHEME, ValidNonPrivateUrlValidatorTest.invalidSchemes)
        );

        testCases.add(
            new TestCase(ValidNonPrivateUrlValidator.PRIVATE_IP, ValidNonPrivateUrlValidatorTest.internalHosts)
        );

        testCases.add(
            new TestCase(ValidNonPrivateUrlValidator.LOOPBACK_ADDRESS, ValidNonPrivateUrlValidatorTest.loopbackAddress)
        );

        testCases.add(
            new TestCase(
                ValidNonPrivateUrlValidator.UNKNOWN_HOST,
                ValidNonPrivateUrlValidatorTest.unknownHosts
            )
        );
        try {
            LaunchMode.set(LaunchMode.NORMAL);

            // Test the URLs with both camel and webhook endpoints.
            for (final var testCase : testCases) {
                for (final var url : testCase.testUrls) {
                    // Test with a camel endpoint.
                    camelProperties.setUrl(url);
                    endpoint.setSubType("slack");
                    endpoint.setType(EndpointType.CAMEL);
                    endpoint.setProperties(camelProperties);

                    final String camelResponse =
                        given()
                            .header(identityHeader)
                            .when()
                            .contentType(JSON)
                            .body(Json.encode(this.endpointMapper.toDTO(endpoint)))
                            .post("/endpoints")
                            .then()
                            .statusCode(HttpStatus.SC_BAD_REQUEST)
                            .extract()
                            .asString();

                    final String camelConstraintViolation = TestHelpers.extractConstraintViolationFromResponse(camelResponse);

                    Assertions.assertEquals(testCase.expectedErrorMessage, camelConstraintViolation, String.format("unexpected constraint violation for url \"%s\"", url));

                    // Test with a webhook endpoint.
                    webhookProperties.setUrl(url);
                    // Reset the subtype since it doesn't make sense a "slack" subtype for webhook endpoints.
                    endpoint.setSubType(null);
                    endpoint.setType(EndpointType.WEBHOOK);
                    endpoint.setProperties(webhookProperties);

                    final String webhookResponse =
                        given()
                            .header(identityHeader)
                            .when()
                            .contentType(JSON)
                            .body(Json.encode(endpoint))
                            .post("/endpoints")
                            .then()
                            .statusCode(HttpStatus.SC_BAD_REQUEST)
                            .extract()
                            .asString();

                    final String webhookConstraintViolation = TestHelpers.extractConstraintViolationFromResponse(webhookResponse);

                    Assertions.assertEquals(testCase.expectedErrorMessage, webhookConstraintViolation, String.format("unexpected constraint violation for url \"%s\"", url));
                }
            }
        } finally {
            LaunchMode.set(LaunchMode.TEST);
        }
    }

    /**
     * Tests that when a valid URL is provided via the endpoint's properties, regardless if those properties are
     * {@link CamelProperties} or {@link WebhookProperties}, no constraint violations are raised.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testEndpointValidUrls(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        // Set up the RBAC access for the test.
        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Set up the fixture data.
        final boolean disableSslVerification = false;
        final String secretToken = "endpoint-invalid-urls-secret-token";

        // Create the properties for the endpoint. Leave the URL so that we can set it afterwards.
        final CamelProperties camelProperties = new CamelProperties();
        camelProperties.setDisableSslVerification(disableSslVerification);
        camelProperties.setSecretToken(secretToken);

        final WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setDisableSslVerification(disableSslVerification);
        webhookProperties.setMethod(POST);
        webhookProperties.setSecretToken(secretToken);

        // Mock the Sources service calls. In this test we don't assert for the
        // secrets' values, so we can simply return the same secret over and
        // over.
        final Secret secret = new Secret();
        secret.id = new Random().nextLong(1, Long.MAX_VALUE);
        secret.password = "test-endpoint-valid-urls-password";
        secret.username = "test-endpoint-valid-urls-username";

        when(this.sourcesServiceMock.create(anyString(), anyString(), any()))
            .thenReturn(secret);

        for (final String url : ValidNonPrivateUrlValidatorTest.validUrls) {
            // Test with a camel endpoint.
            camelProperties.setUrl(url);

            final Endpoint endpoint = new Endpoint();
            endpoint.setDescription("test-endpoints-valid-urls");
            endpoint.setName(UUID.randomUUID().toString());
            endpoint.setType(EndpointType.CAMEL);
            endpoint.setProperties(camelProperties);
            endpoint.setSubType("slack");

            EndpointDTO dto = this.endpointMapper.toDTO(endpoint);

            given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(dto))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK);

            // Test with a webhook endpoint.
            webhookProperties.setUrl(url);
            // Set a new endpoint's name to avoid receiving a "duplicate
            // endpoint's name" response.
            endpoint.setName(UUID.randomUUID().toString());
            endpoint.setType(EndpointType.WEBHOOK);
            endpoint.setSubType(null);
            endpoint.setProperties(webhookProperties);

            dto = this.endpointMapper.toDTO(endpoint);

            given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(dto))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK);
        }
    }

    /**
     * Test that endpoint.sub_type is only allowed when it's required.
     * If it's not required, then it should be rejected.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testEndpointSubtypeIsOnlyAllowedWhenRequired(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(POST);
        properties.setDisableSslVerification(false);
        properties.setSecretToken("my-super-secret-token");
        properties.setUrl(getMockServerUrl());

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.WEBHOOK);
        ep.setSubType("slack");
        ep.setName("endpoint to find");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(properties);
        ep.setServerErrors(3);

        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(this.endpointMapper.toDTO(ep)))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST);

        CamelProperties cAttr = new CamelProperties();
        cAttr.setDisableSslVerification(false);
        cAttr.setUrl(getMockServerUrl());
        Map<String, String> extras = new HashMap<>();
        extras.put("template", "11");
        cAttr.setExtras(extras);

        Endpoint camelEp = new Endpoint();
        camelEp.setType(EndpointType.CAMEL);
        camelEp.setSubType(null);
        camelEp.setName("Push the camel through the needle's ear");
        camelEp.setDescription("How many humps has a camel?");
        camelEp.setEnabled(true);
        camelEp.setProperties(cAttr);

        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(camelEp))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    /**
     * Tests that when sending a payload to the "/test" REST endpoint, a Kafka message is sent with a test event for
     * that endpoint.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testEndpointTest(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        final Endpoint createdEndpoint = this.resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.CAMEL);

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Call the endpoint under test.

        final String path = String.format("/endpoints/%s/test", createdEndpoint.getId());
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .post(path)
            .then()
            .statusCode(HttpStatus.SC_NO_CONTENT);

        // Capture the sent payload to verify it.
        final ArgumentCaptor<InternalEndpointTestRequest> capturedPayload = ArgumentCaptor.forClass(InternalEndpointTestRequest.class);
        Mockito.verify(this.endpointTestService).testEndpoint(capturedPayload.capture());

        final InternalEndpointTestRequest sentPayload = capturedPayload.getValue();

        Assertions.assertEquals(createdEndpoint.getId(), sentPayload.endpointUuid, "the sent endpoint UUID in the payload doesn't match the one from the fixture");
        Assertions.assertEquals(DEFAULT_ORG_ID, sentPayload.orgId, "the sent org id in the payload doesn't match the one from the fixture");
        assertNull(sentPayload.message, "the sent message should be null since no custom message was specified");
    }

    /**
     * Tests that when the "test endpoint" handler is called with an endpoint
     * UUID that doesn't exist, a not found response is returned.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testEndpointTestNotFound(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Call the endpoint under test.
        final String responseBody = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("id", UUID.randomUUID())
            .post("/endpoints/{id}/test")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND)
            .extract()
            .body()
            .asString();

        Assertions.assertEquals("integration not found", responseBody, "unexpected not found error message returned");
    }

    /**
     * Tests that when a user specifies a custom message, then it gets properly
     * sent to the engine.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testEndpointTestCustomMessage(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        final Endpoint createdEndpoint = this.resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.CAMEL);

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        final String customTestMessage = "Hello, World!";
        final EndpointTestRequest endpointTestRequest = new EndpointTestRequest();
        endpointTestRequest.message = customTestMessage;

        // Call the endpoint under test.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("id", createdEndpoint.getId())
            .body(Json.encode(endpointTestRequest))
            .post("/endpoints/{id}/test")
            .then()
            .statusCode(HttpStatus.SC_NO_CONTENT);

        // Capture the sent payload to verify it.
        final ArgumentCaptor<InternalEndpointTestRequest> capturedPayload = ArgumentCaptor.forClass(InternalEndpointTestRequest.class);
        Mockito.verify(this.endpointTestService).testEndpoint(capturedPayload.capture());

        final InternalEndpointTestRequest sentPayload = capturedPayload.getValue();

        Assertions.assertEquals(createdEndpoint.getId(), sentPayload.endpointUuid, "the sent endpoint UUID in the payload doesn't match the one from the fixture");
        Assertions.assertEquals(DEFAULT_ORG_ID, sentPayload.orgId, "the sent org id in the payload doesn't match the one from the fixture");
        Assertions.assertEquals(customTestMessage, sentPayload.message, "the sent message does not match the one from the fixture");
    }

    /**
     * Tests that when a user specifies a blank custom message, then a bad
     * request response is returned.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testEndpointTestBlankMessageReturnsBadRequest(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        final Endpoint createdEndpoint = this.resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.CAMEL);

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        final EndpointTestRequest endpointTestRequest = new EndpointTestRequest();
        endpointTestRequest.message = "";

        // Call the endpoint under test.
        final String rawResponse = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("id", createdEndpoint.getId())
            .body(Json.encode(endpointTestRequest))
            .post("/endpoints/{id}/test")
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST)
            .extract()
            .asString();

        final JsonObject response = new JsonObject(rawResponse);
        final JsonArray constraintViolations = response.getJsonArray("violations");
        Assertions.assertEquals(1, constraintViolations.size(), "unexpected number of error messages received from the endpoint");

        final JsonObject constraintViolation = constraintViolations.getJsonObject(0);

        Assertions.assertEquals("testEndpoint.requestBody.message", constraintViolation.getString("field"), "unexpected field validated when sending a blank test message");
        Assertions.assertEquals("must not be blank", constraintViolation.getString("message"), "unexpected error message received when sending a blank custom message for testing the endpoint");
    }

    /**
     * Tests that when an endpoint has SSL verification disabled, testing it returns a bad request.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testEndpointTestDisableSslVerification(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        CamelProperties camelProperties = new CamelProperties();
        camelProperties.setUrl("https://webhook.site/b6179849-b71a-4388-9d0e-0184619b231e");
        camelProperties.setDisableSslVerification(true);

        final Endpoint createdEndpoint = this.resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.CAMEL, "slack", "disabledSSL", "description", camelProperties, true);

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        final EndpointTestRequest endpointTestRequest = new EndpointTestRequest();
        endpointTestRequest.message = "should not send because SSL verification is disabled";

        // Call the endpoint under test.

        final String rawResponse = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("id", createdEndpoint.getId())
            .body(Json.encode(endpointTestRequest))
            .post("/endpoints/{id}/test")
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST)
            .extract()
            .asString();

        Assertions.assertEquals("Endpoints are no longer permitted to disable SSL/TLS verification, and existing integrations which have disabled " +
                "verification will be removed soon. Please enable SSL/TLS verification to continue using this integration, or contact Red Hat Support for assistance.",
                rawResponse,
                "unexpected error message received when testing endpoint with SSL verification disabled");
    }

    /**
     * Tests that when an endpoint gets updated, if it didn't have any secrets
     * and the user provides new ones, then Sources gets called and the
     * references to those secrets are stored in the database.
    */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testUpdateEndpointCreateSecrets(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(TestConstants.DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Create the endpoint that we will attempt to modify.
        final WebhookProperties properties = new WebhookProperties();
        properties.setDisableSslVerification(false);
        properties.setMethod(POST);
        properties.setUrl(getMockServerUrl());

        final Endpoint endpoint = new Endpoint();
        endpoint.setDescription("test update endpoint delete secrets");
        endpoint.setEnabled(true);
        endpoint.setName("test update endpoint delete secrets");
        endpoint.setOrgId(DEFAULT_ORG_ID);
        endpoint.setProperties(properties);
        endpoint.setServerErrors(0);
        endpoint.setStatus(EndpointStatus.PROVISIONING);
        endpoint.setType(EndpointType.WEBHOOK);

        final String response = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(this.endpointMapper.toDTO(endpoint)))
            .post("/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .extract()
            .asString();

        // Extract the endpoint's UUID from the response.
        final JsonObject jsonResponse = new JsonObject(response);
        final String endpointUuidRaw = jsonResponse.getString("id");
        Assertions.assertTrue(endpointUuidRaw != null && !endpointUuidRaw.isBlank(), "the endpoint's UUID is not present after creating it");
        final UUID endpointUuid = UUID.fromString(endpointUuidRaw);

        // Now update the endpoint by setting the "bearer authentication"
        // and the "secret token".
        properties.setSecretToken("my-super-secret-token");
        properties.setBearerAuthentication("my-super-bearer-token");

        // The below two secrets will be returned when we simulate that
        // we have called sources to create these secrets.
        final Secret mockedSecretTokenSecret = new Secret();
        mockedSecretTokenSecret.id = 50L;

        final Secret mockedSecretBearer = new Secret();
        mockedSecretBearer.id = 75L;

        when(
            this.sourcesServiceMock.create(
                eq(DEFAULT_ORG_ID),
                eq(this.sourcesPsk),
                any()
            )
        ).thenReturn(
            mockedSecretTokenSecret,
            mockedSecretBearer
        );

        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(this.endpointMapper.toDTO(endpoint)))
            .pathParam("id", endpointUuid)
            .put("/endpoints/{id}")
            .then()
            .statusCode(HttpStatus.SC_OK);

        // Verify that both secrets were created in Sources.
        Mockito.verify(this.sourcesServiceMock, Mockito.times(2)).create(
            eq(DEFAULT_ORG_ID),
            eq(this.sourcesPsk),
            any()
        );

        // Make sure that the properties got updated in the database too.
        final Endpoint databaseEndpoint = this.endpointRepository.getEndpoint(DEFAULT_ORG_ID, endpointUuid);
        final WebhookProperties webhookProperties = databaseEndpoint.getProperties(WebhookProperties.class);

        Assertions.assertEquals(50L, webhookProperties.getSecretTokenSourcesId(), "Sources was called to create the secret token secret, but the secret's ID wasn't present in the database");
        Assertions.assertEquals(75L, webhookProperties.getBearerAuthenticationSourcesId(), "Sources was called to create the bearer authentication secret, but the secret's ID wasn't present in the database");
    }

    /**
     * Tests that when an endpoint gets updated, if the user removes the
     * "secret token" secrets, then the secrets are deleted from Sources and
     * their references deleted from our database.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testUpdateEndpointDeleteSecrets(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(TestConstants.DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Create the endpoint that we will attempt to modify.
        final WebhookProperties properties = new WebhookProperties();
        properties.setDisableSslVerification(false);
        properties.setMethod(POST);
        properties.setSecretToken("my-super-secret-token");
        properties.setUrl(getMockServerUrl());

        final Endpoint endpoint = new Endpoint();
        endpoint.setDescription("test update endpoint delete secrets");
        endpoint.setEnabled(true);
        endpoint.setName("test update endpoint delete secrets");
        endpoint.setOrgId(DEFAULT_ORG_ID);
        endpoint.setProperties(properties);
        endpoint.setServerErrors(0);
        endpoint.setStatus(EndpointStatus.PROVISIONING);
        endpoint.setType(EndpointType.WEBHOOK);

        // The below two secrets will be returned when we simulate that
        // we have called sources to create these secrets.
        final Secret mockedSecretTokenSecret = new Secret();
        mockedSecretTokenSecret.id = 50L;

        when(
            this.sourcesServiceMock.create(
                eq(DEFAULT_ORG_ID),
                eq(this.sourcesPsk),
                any()
            )
        ).thenReturn(
            mockedSecretTokenSecret
        );

        final String response = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(this.endpointMapper.toDTO(endpoint)))
            .post("/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .extract()
            .asString();

        // Extract the endpoint's UUID from the response.
        final JsonObject jsonResponse = new JsonObject(response);
        final String endpointUuidRaw = jsonResponse.getString("id");
        Assertions.assertTrue(endpointUuidRaw != null && !endpointUuidRaw.isBlank(), "the endpoint's UUID is not present after creating it");
        final UUID endpointUuid = UUID.fromString(endpointUuidRaw);

        // Now update the endpoint by setting its "secret token" to
        // null, which triggers the secret deletion in Sources.
        properties.setSecretToken(null);

        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("id", endpointUuid)
            .body(Json.encode(endpoint))
            .put("/endpoints/{id}")
            .then()
            .statusCode(HttpStatus.SC_OK);

        // Verify that the secret token secret was deleted from Sources.
        Mockito.verify(this.sourcesServiceMock, Mockito.times(1)).delete(
            DEFAULT_ORG_ID,
            this.sourcesPsk,
            50L
        );

        // Make sure that the properties got updated in the database too.
        final Endpoint databaseEndpoint = this.endpointRepository.getEndpoint(DEFAULT_ORG_ID, endpointUuid);
        final WebhookProperties webhookProperties = databaseEndpoint.getProperties(WebhookProperties.class);

        assertNull(webhookProperties.getSecretTokenSourcesId(), "Sources was called to delete the secret token secret, but the secret's ID wasn't deleted from the database");
    }

    /**
     * Tests that when an endpoint gets updated, if the user updates both
     * secrets, then Sources gets called twice, and that the database
     * references for the secrets don't change.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testUpdateEndpointUpdateSecrets(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(TestConstants.DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Create the endpoint that we will attempt to modify.
        final WebhookProperties properties = new WebhookProperties();
        properties.setDisableSslVerification(false);
        properties.setMethod(POST);
        properties.setSecretToken("my-super-secret-token ");
        properties.setUrl(getMockServerUrl());

        final Endpoint endpoint = new Endpoint();
        endpoint.setDescription("test update endpoint delete secrets");
        endpoint.setEnabled(true);
        endpoint.setName("test update endpoint delete secrets");
        endpoint.setOrgId(DEFAULT_ORG_ID);
        endpoint.setProperties(properties);
        endpoint.setServerErrors(0);
        endpoint.setStatus(EndpointStatus.PROVISIONING);
        endpoint.setType(EndpointType.WEBHOOK);

        // The below two secrets will be returned when we simulate that
        // we have called sources to create these secrets.
        final Secret mockedSecretTokenSecret = new Secret();
        mockedSecretTokenSecret.id = 50L;

        when(
            this.sourcesServiceMock.create(
                eq(DEFAULT_ORG_ID),
                eq(this.sourcesPsk),
                any()
            )
        ).thenReturn(
            mockedSecretTokenSecret
        );

        final String response = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(this.endpointMapper.toDTO(endpoint)))
            .post("/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .extract()
            .asString();

        // Extract the endpoint's UUID from the response.
        final JsonObject jsonResponse = new JsonObject(response);
        final String endpointUuidRaw = jsonResponse.getString("id");
        Assertions.assertTrue(endpointUuidRaw != null && !endpointUuidRaw.isBlank(), "the endpoint's UUID is not present after creating it");
        final UUID endpointUuid = UUID.fromString(endpointUuidRaw);

        // Before updating the endpoint, grab the created secrets'
        // references from the database.
        final Endpoint databaseEndpointPreUpdate = this.endpointRepository.getEndpoint(DEFAULT_ORG_ID, endpointUuid);
        final WebhookProperties databasePropertiesPreUpdate = databaseEndpointPreUpdate.getProperties(WebhookProperties.class);
        final long secretTokenReferencePreUpdate = databasePropertiesPreUpdate.getSecretTokenSourcesId();

        // Now update the endpoint by updating its "secret token".
        properties.setSecretToken("my-super-secret-token");

        // The below two secrets will be returned when we simulate that
        // we have called sources to update these secrets. This should
        // never ever happen in real life, but I want to make sure that our
        // logic doesn't, by mistake, update the references.
        final Secret mockedSecretTokenUpdatedSecret = new Secret();
        mockedSecretTokenSecret.id = 100L;

        when(
            this.sourcesServiceMock.update(
                eq(DEFAULT_ORG_ID),
                eq(this.sourcesPsk),
                Mockito.anyLong(),
                any()
            )
        ).thenReturn(
            mockedSecretTokenUpdatedSecret
        );

        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("id", endpointUuid)
            .body(Json.encode(this.endpointMapper.toDTO(endpoint)))
            .put("/endpoints/{id}")
            .then()
            .statusCode(HttpStatus.SC_OK);

        // Verify that the secret token was updated in Sources.
        Mockito.verify(this.sourcesServiceMock, Mockito.times(1)).update(
            eq(DEFAULT_ORG_ID),
            eq(this.sourcesPsk),
            Mockito.anyLong(),
            any()
        );

        // Make sure that the references to the secrets didn't get updated
        // in the database.
        final Endpoint databaseEndpoint = this.endpointRepository.getEndpoint(DEFAULT_ORG_ID, endpointUuid);
        final WebhookProperties webhookProperties = databaseEndpoint.getProperties(WebhookProperties.class);
        final long secretTokenReferenceAfterUpdate = webhookProperties.getSecretTokenSourcesId();

        Assertions.assertEquals(secretTokenReferencePreUpdate, secretTokenReferenceAfterUpdate, "the ID of the secret token's secret got updated after updating the secret in Sources, which should never happen");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testAnsibleEndpointCRUD(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselPermission(INTEGRATIONS_VIEW, ALLOWED_TRUE);
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(POST);
        properties.setUrl("https://redhat.com");
        properties.setDisableSslVerification(false);

        Endpoint endpoint = new Endpoint();
        endpoint.setOrgId(DEFAULT_ORG_ID);
        endpoint.setType(ANSIBLE);
        endpoint.setName("ansible-endpoint");
        endpoint.setDescription("My Ansible endpoint");
        endpoint.setProperties(properties);

        // POST the endpoint.
        String responseBody = given()
                .header(identityHeader)
                .contentType(JSON)
                .body(Json.encode(endpoint))
                .when()
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().asString();

        // Check the POST response.
        JsonObject jsonEndpoint = new JsonObject(responseBody);
        jsonEndpoint.mapTo(EndpointDTO.class);
        assertNotNull(jsonEndpoint.getString("id"));
        assertEquals(READY.toString(), jsonEndpoint.getString("status"));
        assertEquals(properties.getUrl(), jsonEndpoint.getJsonObject("properties").getString("url"));

        // GET the endpoint and check the properties.url field value.
        jsonEndpoint = fetchSingle(jsonEndpoint.getString("id"), identityHeader);
        assertEquals(properties.getUrl(), jsonEndpoint.getJsonObject("properties").getString("url"));

        // PUT the endpoint (update).

        properties.setUrl("https://console.redhat.com");
        given()
                .header(identityHeader)
                .contentType(JSON)
                .pathParam("id", jsonEndpoint.getString("id"))
                .body(Json.encode(endpoint))
                .when()
                .put("/endpoints/{id}")
                .then()
                .statusCode(HttpStatus.SC_OK);

        // GET the endpoint and check the properties.url field value.
        jsonEndpoint = fetchSingle(jsonEndpoint.getString("id"), identityHeader);
        assertEquals(properties.getUrl(), jsonEndpoint.getJsonObject("properties").getString("url"));

        // DELETE the endpoint.

        given()
                .header(identityHeader)
                .pathParam("id", jsonEndpoint.getString("id"))
                .when()
                .delete("/endpoints/{id}")
                .then()
                .statusCode(HttpStatus.SC_NO_CONTENT);

        // GET the endpoint and check that it no longer exists.
        given()
                .header(identityHeader)
                .pathParam("id", jsonEndpoint.getString("id"))
                .when()
                .get("/endpoints/{id}")
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void addPagerDutyEndpoint(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselPermission(INTEGRATIONS_VIEW, ALLOWED_TRUE);
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Add new endpoints
        PagerDutyProperties pagerDutyProperties = new PagerDutyProperties();
        pagerDutyProperties.setSeverity(PagerDutySeverity.WARNING);
        pagerDutyProperties.setSecretToken("my-super-secret-token");

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.PAGERDUTY);
        ep.setName("Paging floor fire chief");
        ep.setDescription("Liquid cooling pump failed, server is on fire again");
        ep.setEnabled(true);
        ep.setProperties(pagerDutyProperties);

        // Mock the Sources service calls.
        final Secret secretTokenSecret = new Secret();
        secretTokenSecret.id = new Random().nextLong(1, Long.MAX_VALUE);
        secretTokenSecret.password = pagerDutyProperties.getSecretToken();

        when(this.sourcesServiceMock.create(anyString(), anyString(), any()))
                .thenReturn(secretTokenSecret);

        // Make sure that when the secrets are loaded when we fetch the
        // endpoint again for checking the assertions, we simulate fetching
        // the secrets from Sources too.
        when(this.sourcesServiceMock.getById(anyString(), anyString(), eq(secretTokenSecret.id))).thenReturn(secretTokenSecret);

        Response response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(this.endpointMapper.toDTO(ep)))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        JsonObject responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(EndpointDTO.class);
        String id = responsePoint.getString("id");
        assertNotNull(id);

        try {
            // Fetch single endpoint also and verify
            JsonObject endpoint = fetchSingle(responsePoint.getString("id"), identityHeader);
            assertTrue(endpoint.getBoolean("enabled"));

            JsonObject properties = responsePoint.getJsonObject("properties");
            assertNotNull(properties);
            String severity = properties.getString("severity");
            assertNotNull(severity);
            assertEquals(Json.decodeValue(Json.encode(PagerDutySeverity.WARNING), String.class), severity);

            assertNotNull(properties.getString("secret_token"));
            assertEquals(pagerDutyProperties.getSecretToken(), properties.getString("secret_token"));
        } finally {
            given()
                    .header(identityHeader)
                    .when().delete("/endpoints/" + id)
                    .then()
                    .statusCode(HttpStatus.SC_NO_CONTENT)
                    .extract().body().asString();
        }
    }

    /**
     * Test that when an endpoint creation fails, and therefore it doesn't get
     * stored in the database, its created secrets in Sources are cleaned up.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testSourcesSecretsDeletedWhenEndpointCreationFails(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        WebhookProperties properties = new WebhookProperties();
        properties.setBearerAuthentication("bearer-authentication");
        properties.setDisableSslVerification(false);
        properties.setMethod(POST);
        properties.setSecretToken("my-super-secret-token");
        properties.setUrl("https://redhat.com");

        final Endpoint webhookEndpoint = new Endpoint();
        webhookEndpoint.setDescription("My Ansible endpoint");
        webhookEndpoint.setName("ansible-endpoint");
        webhookEndpoint.setOrgId(DEFAULT_ORG_ID);
        webhookEndpoint.setProperties(properties);
        webhookEndpoint.setType(WEBHOOK);

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(TestConstants.DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Mock the Sources service calls.
        final Secret bearerAuthentication = new Secret();
        bearerAuthentication.id = new Random().nextLong(1, Long.MAX_VALUE);
        bearerAuthentication.authenticationType = Secret.TYPE_BEARER_AUTHENTICATION;
        bearerAuthentication.password = properties.getBearerAuthentication();

        final Secret secretToken = new Secret();
        secretToken.id = new Random().nextLong(1, Long.MAX_VALUE);
        secretToken.authenticationType = Secret.TYPE_SECRET_TOKEN;
        secretToken.password = properties.getSecretToken();

        Mockito.when(this.sourcesServiceMock.create(Mockito.anyString(), Mockito.anyString(), Mockito.any())).thenReturn(
            bearerAuthentication,
            secretToken
        );

        // Simulate that creating the endpoint in the database fails.
        Mockito.doThrow(new RuntimeException()).when(this.endpointRepository).createEndpoint(Mockito.any());

        // Call the endpoint under test.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(this.endpointMapper.toDTO(webhookEndpoint)))
            .post("/endpoints")
            .then()
            .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
            .contentType(JSON);

        // Verify that all the secrets were created in Sources.
        Mockito.verify(this.sourcesServiceMock, Mockito.times(2)).create(
            eq(DEFAULT_ORG_ID),
            eq(this.sourcesPsk),
            any()
        );

        // Assert that the bearer authentication secret is deleted.
        Mockito.verify(this.sourcesServiceMock, Mockito.times(1)).delete(
            eq(DEFAULT_ORG_ID),
            eq(this.sourcesPsk),
            eq(bearerAuthentication.id)
        );

        // Assert that the secret token secret is deleted.
        Mockito.verify(this.sourcesServiceMock, Mockito.times(1)).delete(
            eq(DEFAULT_ORG_ID),
            eq(this.sourcesPsk),
            eq(secretToken.id)
        );
    }

    /**
     * Tests that when using Kessel, if the user is not authorized to send
     * requests to the endpoints from the "EndpointResource", a "forbidden"
     * response is returned for those endpoints that require a workspace
     * permission.
     */
    @Test
    void testKesselUnauthorized() {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(true);
        mockDefaultKesselPermission(INTEGRATIONS_VIEW, ALLOWED_FALSE);
        mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_FALSE);
        mockDefaultKesselUpdatePermission(CREATE_DRAWER_INTEGRATION, ALLOWED_FALSE);
        mockDefaultKesselUpdatePermission(CREATE_EMAIL_SUBSCRIPTION_INTEGRATION, ALLOWED_FALSE);

        // Create an identity header.
        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(TestConstants.DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        // Create an endpoint so that we don't get a 404 instead of an
        // unauthorized response from the endpoints.
        final Endpoint createdEndpoint = this.resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, WEBHOOK);

        // Call the notifications history endpoint.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("endpointId", createdEndpoint.getId())
            .queryParam("includeDetail", false)
            .get("/endpoints/{endpointId}/history")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        try {
            RestAssured.basePath = TestConstants.API_INTEGRATIONS_V_2_0;

            // Call the notifications history endpoint in the V2 path.
            given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .pathParam("endpointId", createdEndpoint.getId())
                .queryParam("includeDetail", false)
                .get("/endpoints/{endpointId}/history")
                .then()
                .statusCode(HttpStatus.SC_FORBIDDEN);
        } finally {
            RestAssured.basePath = TestConstants.API_INTEGRATIONS_V_1_0;
        }

        mockDefaultKesselPermission(INTEGRATIONS_VIEW, ALLOWED_TRUE);

        // Get the list of endpoints.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .get("/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .body("data", Matchers.hasSize(1))
            .body("links", Matchers.anEmptyMap())
            .body("meta.count", Matchers.is(1));

        // Create an endpoint.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(this.endpointMapper.toDTO(createdEndpoint)))
            .post("/endpoints")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        // Create a drawer subscription.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(new RequestSystemSubscriptionProperties()))
            .post("/endpoints/system/drawer_subscription")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        // Create an email subscription.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(new RequestSystemSubscriptionProperties()))
            .post("/endpoints/system/email_subscription")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        mockDefaultKesselPermission(INTEGRATIONS_VIEW, ALLOWED_FALSE);

        try {
            RestAssured.basePath = TestConstants.API_INTEGRATIONS_V_2_0;

            // Call the "get endpoint" endpoint in the V2 path.
            given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .pathParam("id", createdEndpoint.getId())
                .get("/endpoints/{id}")
                .then()
                .statusCode(HttpStatus.SC_FORBIDDEN);
        } finally {
            RestAssured.basePath = TestConstants.API_INTEGRATIONS_V_1_0;
        }
        // Get an endpoint.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("id", createdEndpoint.getId())
            .get("/endpoints/{id}")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        // Delete an endpoint.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("id", createdEndpoint.getId())
            .delete("/endpoints/{id}")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        // Enable an endpoint.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("id", createdEndpoint.getId())
            .put("/endpoints/{id}/enable")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        // Disable an endpoint.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("id", createdEndpoint.getId())
            .delete("/endpoints/{id}/enable")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        // Update an endpoint.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("id", createdEndpoint.getId())
            .body(Json.encode(this.endpointMapper.toDTO(createdEndpoint)))
            .put("/endpoints/{id}")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        // Retrieve the history details.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("endpointId", createdEndpoint.getId())
            .pathParam("historyId", UUID.randomUUID())
            .get("/endpoints/{endpointId}/history/{historyId}/details")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        // Test an endpoint.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("id", createdEndpoint.getId())
            .post("/endpoints/{id}/test")
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);
    }

    private void assertSystemEndpointTypeError(String message, EndpointType endpointType) {
        assertTrue(message.contains(String.format(
                "Is not possible to create or alter endpoint with type %s, check API for alternatives",
                endpointType
        )));
    }

    /**
     * Creates endpoints via the REST endpoint.
     * @param count the number of endpoints to create.
     * @param identityHeader the identity header to use to create th
     *                       endpoints.
     * @return the set of IDs of the created endpoints.
     */
    private Set<UUID> addEndpoints(final int count, final Header identityHeader, final Set<UUID>... eventTypes) {
        final Set<UUID> createdEndpoints = new HashSet<>(count);

        for (int i = 0; i < count; i++) {
            // Add new endpoints
            WebhookPropertiesDTO properties = new WebhookPropertiesDTO();
            properties.setMethod(POST.name());
            properties.setDisableSslVerification(false);
            properties.setSecretToken("my-super-secret-token");
            properties.setUrl(getMockServerUrl() + "/" + i);

            EndpointDTO ep = new EndpointDTO();
            ep.setType(EndpointTypeDTO.WEBHOOK);
            ep.setName(String.format("Endpoint %d", i));
            ep.setDescription("Try to find me!");
            ep.setEnabled(true);
            ep.setProperties(properties);
            if (eventTypes.length > 0) {
                ep.eventTypes = eventTypes[0];
            }
            mockSources(properties.getSecretToken());

            Response response = given()
                    .header(identityHeader)
                    .when()
                    .contentType(JSON)
                    .body(Json.encode(ep))
                    .post("/endpoints")
                    .then()
                    .statusCode(HttpStatus.SC_OK)
                    .contentType(JSON)
                    .extract().response();

            JsonObject responsePoint = new JsonObject(response.getBody().asString());
            responsePoint.mapTo(EndpointDTO.class);
            assertNotNull(responsePoint.getString("id"));

            createdEndpoints.add(UUID.fromString(responsePoint.getString("id")));
        }

        return createdEndpoints;
    }

    @Test
    void testCreateThenUpdateEndpointWthEventTypeRelationship() {
        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(TestConstants.DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "username");
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        final Bundle bundle1 = resourceHelpers.createBundle(RandomStringUtils.randomAlphabetic(10).toLowerCase(), "bundle 1");
        final Bundle bundle2 = resourceHelpers.createBundle(RandomStringUtils.randomAlphabetic(10).toLowerCase(), "bundle 2");

        // Create event type and endpoint
        final Application application1 = resourceHelpers.createApplication(bundle1.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "application 1");
        final EventType eventType1 = resourceHelpers.createEventType(application1.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "event type 1", "description");
        final EventType eventType2 = resourceHelpers.createEventType(application1.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "event type 2", "description");
        final EventType retictedRecipientsIntegrationEventType = resourceHelpers.createEventType(application1.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "restricted event type 2", "description", true);

        final Application application2 = resourceHelpers.createApplication(bundle2.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "application 1");
        final EventType eventType1App2 = resourceHelpers.createEventType(application2.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "event type 1", "description");
        final EventType eventType2App2 = resourceHelpers.createEventType(application2.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "event type 2", "description");

        // Check that org id don't have any behavior group
        List<BehaviorGroup> behaviorGroups = resourceHelpers.findBehaviorGroupsByOrgId(DEFAULT_ORG_ID);
        assertEquals(0, behaviorGroups.size());

        UUID endpointUUID = addEndpoints(1, identityHeader, Set.of(eventType1.getId(), eventType1App2.getId(), eventType2.getId())).stream().findFirst().get();

        // Check that created endpoint have event types associated
        Endpoint endpointFromDb = resourceHelpers.getEndpoint(endpointUUID);
        assertEquals(3, endpointFromDb.getEventTypes().size());
        String expectedBgName = String.format(AUTO_CREATED_BEHAVIOR_GROUP_NAME_TEMPLATE, endpointFromDb.getName());

        // Check that one behavior group by bundle were created
        behaviorGroups = resourceHelpers.findBehaviorGroupsByOrgId(DEFAULT_ORG_ID);
        assertEquals(2, behaviorGroups.size());
        assertEquals(2, behaviorGroups.stream().filter(bg -> expectedBgName.equals(bg.getDisplayName())).count());

        EndpointDTO endpointDTO = fetchSingleEndpointV2(endpointUUID, identityHeader);
        endpointDTO.setName("Endpoint1-updated");

        given()
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("id", endpointUUID)
            .body(Json.encode(endpointDTO))
            .when()
            .put("/endpoints/{id}")
            .then()
            .statusCode(HttpStatus.SC_OK);

        // Check that behavior group names were updated
        String newExpectedBgName = String.format(AUTO_CREATED_BEHAVIOR_GROUP_NAME_TEMPLATE, endpointDTO.getName());
        behaviorGroups = resourceHelpers.findBehaviorGroupsByOrgId(DEFAULT_ORG_ID);
        assertEquals(2, behaviorGroups.size());
        assertEquals(2, behaviorGroups.stream().filter(bg -> newExpectedBgName.equals(bg.getDisplayName())).count());

        // update event types
        endpointDTO.eventTypes = Set.of(eventType2App2.getId(), retictedRecipientsIntegrationEventType.getId());

        // should fail because we try to update a webhook with an event type allowed for email and drawer integrations only
        given()
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("id", endpointUUID)
            .body(Json.encode(endpointDTO))
            .when()
            .put("/endpoints/{id}")
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);

        endpointDTO.eventTypes = Set.of(eventType2App2.getId());
        given()
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("id", endpointUUID)
            .body(Json.encode(endpointDTO))
            .when()
            .put("/endpoints/{id}")
            .then()
            .statusCode(HttpStatus.SC_OK);

        // Check that event type association has been updated
        endpointFromDb = resourceHelpers.getEndpoint(endpointUUID);
        assertEquals(1, endpointFromDb.getEventTypes().size());
        assertEquals(eventType2App2.getId(), endpointFromDb.getEventTypes().stream().findFirst().get().getId());

        behaviorGroups = resourceHelpers.findBehaviorGroupsByOrgId(DEFAULT_ORG_ID);
        assertEquals(1, behaviorGroups.size());
        assertEquals(1, behaviorGroups.stream().findFirst().get().getBehaviors().size());
        assertEquals(eventType2App2.getId(), behaviorGroups.stream().findFirst().get().getBehaviors().stream().findFirst().get().getId().eventTypeId);

       // drop associations
        endpointDTO.eventTypes = Set.of();

        given()
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("id", endpointUUID)
            .body(Json.encode(endpointDTO))
            .when()
            .put("/endpoints/{id}")
            .then()
            .statusCode(HttpStatus.SC_OK);

        // Check that endpoint don't hava any associated event type
        behaviorGroups = resourceHelpers.findBehaviorGroupsByOrgId(DEFAULT_ORG_ID);
        assertEquals(0, behaviorGroups.size());

        endpointFromDb = resourceHelpers.getEndpoint(endpointUUID);
        assertEquals(0, endpointFromDb.getEventTypes().size());
    }

    @Test
    void testCreateThenDeleteEndpointToEventTypeRelationship() {
        final Bundle bundle1 = resourceHelpers.createBundle(RandomStringUtils.randomAlphabetic(10).toLowerCase(), "bundle 1");

        // Create event type and endpoint
        final Application application1 = resourceHelpers.createApplication(bundle1.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "application 1");
        final EventType eventType1 = resourceHelpers.createEventType(application1.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "event type 1", "description");
        final Endpoint endpoint = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.WEBHOOK);

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(TestConstants.DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "username");
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Check that created endpoint don't have any linked behavior group
        List<BehaviorGroup> behaviorGroups = resourceHelpers.findBehaviorGroupsByOrgId(DEFAULT_ORG_ID);
        assertEquals(0, behaviorGroups.size());

        // Check that created endpoint don't have any event type associated
        Endpoint endpointFromDb = resourceHelpers.getEndpoint(endpoint.getId());
        assertEquals(0, endpointFromDb.getEventTypes().size());

        // Check that created endpoint don't have any event type associated using API
        EndpointDTO endpointDTO = fetchSingleEndpointV2(endpoint.getId(), identityHeader);
        assertNull(endpointDTO.getEventTypesGroupByBundlesAndApplications());

        // test event type or endpoint not exists
        given()
            .header(identityHeader)
            .pathParam("eventTypeUuid", UUID.randomUUID())
            .pathParam("endpointUuid", endpoint.getId())
            .when()
            .put("/endpoints/{endpointUuid}/eventType/{eventTypeUuid}")
            .then()
            .statusCode(404);
        given()
            .header(identityHeader)
            .pathParam("eventTypeUuid", eventType1.getId())
            .pathParam("endpointUuid", UUID.randomUUID())
            .when()
            .put("/endpoints/{endpointUuid}/eventType/{eventTypeUuid}")
            .then()
            .statusCode(404);

        // link endpoint and event type
        given()
            .header(identityHeader)
            .pathParam("eventTypeUuid", eventType1.getId())
            .pathParam("endpointUuid", endpoint.getId())
            .when()
            .put("/endpoints/{endpointUuid}/eventType/{eventTypeUuid}")
            .then()
            .statusCode(204);

        endpointFromDb = resourceHelpers.getEndpoint(endpoint.getId());
        assertEquals(1, endpointFromDb.getEventTypes().size());
        assertEquals(eventType1.getId(), endpointFromDb.getEventTypes().stream().findFirst().get().getId());

        // Check endpoint has one associated event type using get single endpoint api
        endpointDTO = fetchSingleEndpointV2(endpoint.getId(), identityHeader);
        assertEquals(1, endpointDTO.getEventTypesGroupByBundlesAndApplications().size());
        assertEquals(1, endpointDTO.getEventTypesGroupByBundlesAndApplications().stream().findFirst().get().getApplications().size());
        assertEquals(1, endpointDTO.getEventTypesGroupByBundlesAndApplications().stream().findFirst().get().getApplications().stream().findFirst().get().getEventTypes().size());
        EventTypeDTO eventTypeDTO = endpointDTO.getEventTypesGroupByBundlesAndApplications().stream().findFirst().get().getApplications().stream().findFirst().get().getEventTypes().stream().findFirst().get();
        assertEquals(eventType1.getId(), eventTypeDTO.getId());

        // Check endpoint has one associated event type using get all endpoints api
        EndpointPage endpointPage = fetchEndpoints(identityHeader, TestConstants.API_INTEGRATIONS_V_2);
        assertEquals(1L, endpointPage.getMeta().getCount());
        assertEquals(1, endpointPage.getData().size());
        assertEquals(1, endpointPage.getData().getFirst().getEventTypesGroupByBundlesAndApplications().size());

        // Check get all endpoints api V1 has not been updated
        endpointPage = fetchEndpoints(identityHeader, TestConstants.API_INTEGRATIONS_V_1);
        assertEquals(1L, endpointPage.getMeta().getCount());
        assertEquals(1, endpointPage.getData().size());
        assertNull(endpointPage.getData().getFirst().getEventTypesGroupByBundlesAndApplications());

        behaviorGroups = resourceHelpers.findBehaviorGroupsByOrgId(DEFAULT_ORG_ID);
        assertEquals(1, behaviorGroups.size());
        assertEquals(1, behaviorGroups.getFirst().getActions().size());
        assertEquals(1, behaviorGroups.getFirst().getBehaviors().size());

        // Try to link again same endpoint and event type
        given()
            .header(identityHeader)
            .pathParam("eventTypeUuid", eventType1.getId())
            .pathParam("endpointUuid", endpoint.getId())
            .when()
            .put("/endpoints/{endpointUuid}/eventType/{eventTypeUuid}")
            .then()
            .statusCode(204);

        // Check that endpoint is linked to the event type only once
        endpointFromDb = resourceHelpers.getEndpoint(endpoint.getId());
        assertEquals(1, endpointFromDb.getEventTypes().size());
        assertEquals(eventType1.getId(), endpointFromDb.getEventTypes().stream().findFirst().get().getId());

        endpointDTO = fetchSingleEndpointV2(endpoint.getId(), identityHeader);
        assertEquals(1, endpointDTO.getEventTypesGroupByBundlesAndApplications().size());
        assertEquals(1, endpointDTO.getEventTypesGroupByBundlesAndApplications().stream().findFirst().get().getApplications().size());
        assertEquals(1, endpointDTO.getEventTypesGroupByBundlesAndApplications().stream().findFirst().get().getApplications().stream().findFirst().get().getEventTypes().size());
        eventTypeDTO = endpointDTO.getEventTypesGroupByBundlesAndApplications().stream().findFirst().get().getApplications().stream().findFirst().get().getEventTypes().stream().findFirst().get();
        assertEquals(eventType1.getId(), eventTypeDTO.getId());

        behaviorGroups = resourceHelpers.findBehaviorGroupsByOrgId(DEFAULT_ORG_ID);
        assertEquals(1, behaviorGroups.size());
        assertEquals(1, behaviorGroups.getFirst().getActions().size());
        assertEquals(1, behaviorGroups.getFirst().getBehaviors().size());

        // create an new endpoint and link it to existing BG
        final Endpoint endpoint2 = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.WEBHOOK);
        behaviorGroupRepository.updateBehaviorGroupActions(DEFAULT_ORG_ID, behaviorGroups.getFirst().getId(), List.of(endpoint.getId(), endpoint2.getId()));

        // Delete endpoint to event type relationship
        given()
            .header(identityHeader)
            .pathParam("endpointUuid", endpoint.getId())
            .pathParam("eventTypeUuid", eventType1.getId())
            .when()
            .delete("/endpoints/{endpointUuid}/eventType/{eventTypeUuid}")
            .then()
            .statusCode(204);

        // test event type or endpoint not exists
        endpointFromDb = resourceHelpers.getEndpoint(endpoint.getId());
        assertEquals(0, endpointFromDb.getEventTypes().size());

        endpointDTO = fetchSingleEndpointV2(endpoint.getId(), identityHeader);
        assertNull(endpointDTO.getEventTypesGroupByBundlesAndApplications());

        endpointPage = fetchEndpoints(identityHeader, TestConstants.API_INTEGRATIONS_V_2);
        assertEquals(2L, endpointPage.getMeta().getCount());
        assertEquals(2, endpointPage.getData().size());
        assertNull(endpointPage.getData().getFirst().getEventTypesGroupByBundlesAndApplications());

        // we still have 1 BG, because endpoint 2 is still linked to it
        behaviorGroups = resourceHelpers.findBehaviorGroupsByOrgId(DEFAULT_ORG_ID);
        assertEquals(1, behaviorGroups.size());
        assertEquals(1, behaviorGroups.getFirst().getActions().size());
        assertEquals(1, behaviorGroups.getFirst().getBehaviors().size());

        // Delete endpoint 2 to event type relationship
        given()
            .header(identityHeader)
            .pathParam("endpointUuid", endpoint2.getId())
            .pathParam("eventTypeUuid", eventType1.getId())
            .when()
            .delete("/endpoints/{endpointUuid}/eventType/{eventTypeUuid}")
            .then()
            .statusCode(204);

        behaviorGroups = resourceHelpers.findBehaviorGroupsByOrgId(DEFAULT_ORG_ID);
        assertEquals(0, behaviorGroups.size());

        final EventType eventType2 = resourceHelpers.createEventType(application1.getId(), "name1", "event type 2", "description1");
        final EventType eventType3 = resourceHelpers.createEventType(application1.getId(), "name2", "event type 3", "description1");
        final EventType retictedRecipientsIntegrationEventType = resourceHelpers.createEventType(application1.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "restricted event type 2", "description", true);

        // should fail because we try to update a webhook with an event type allowed for email and drawer integrations only
        Set<UUID> eventsIdsToLink = Set.of(eventType1.getId(), eventType2.getId(), eventType3.getId(), retictedRecipientsIntegrationEventType.getId());
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(eventsIdsToLink))
            .pathParam("endpointUuid", endpoint.getId())
            .put("/endpoints/{endpointUuid}/eventTypes")
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);

        eventsIdsToLink = Set.of(eventType1.getId(), eventType2.getId(), eventType3.getId());
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(eventsIdsToLink))
            .pathParam("endpointUuid", endpoint.getId())
            .put("/endpoints/{endpointUuid}/eventTypes")
            .then()
            .statusCode(HttpStatus.SC_NO_CONTENT);

        endpointFromDb = resourceHelpers.getEndpoint(endpoint.getId());
        assertEquals(3, endpointFromDb.getEventTypes().size());

        endpointDTO = fetchSingleEndpointV2(endpoint.getId(), identityHeader);
        assertEquals(1, endpointDTO.getEventTypesGroupByBundlesAndApplications().size());
        assertEquals(1, endpointDTO.getEventTypesGroupByBundlesAndApplications().stream().findFirst().get().getApplications().size());
        assertEquals(3, endpointDTO.getEventTypesGroupByBundlesAndApplications().stream().findFirst().get().getApplications().stream().findFirst().get().getEventTypes().size());

        // check that return of endpoint V1 does not contain event types
        JsonObject endpointJsonObject = fetchSingle(endpoint.getId().toString(), identityHeader);
        endpointDTO = endpointJsonObject.mapTo(EndpointDTO.class);
        assertNull(endpointDTO.getEventTypesGroupByBundlesAndApplications());

        behaviorGroups = resourceHelpers.findBehaviorGroupsByOrgId(DEFAULT_ORG_ID);
        assertEquals(1, behaviorGroups.size());
        assertEquals(1, Set.of(behaviorGroups.getFirst().getActions()).size());
        assertEquals(3, behaviorGroups.getFirst().getBehaviors().size());

        // test delete one event type
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(Set.of(eventType1.getId(), eventType3.getId())))
            .pathParam("endpointUuid", endpoint.getId())
            .put("/endpoints/{endpointUuid}/eventTypes")
            .then()
            .statusCode(204);

        endpointFromDb = resourceHelpers.getEndpoint(endpoint.getId());
        assertEquals(2, endpointFromDb.getEventTypes().size());

        behaviorGroups = resourceHelpers.findBehaviorGroupsByOrgId(DEFAULT_ORG_ID);
        assertEquals(1, behaviorGroups.size());
        assertEquals(1, Set.of(behaviorGroups.getFirst().getActions()).size());
        assertEquals(2, behaviorGroups.getFirst().getBehaviors().size());

        endpointDTO = fetchSingleEndpointV2(endpoint.getId(), identityHeader);
        assertEquals(1, endpointDTO.getEventTypesGroupByBundlesAndApplications().size());
        assertEquals(1, endpointDTO.getEventTypesGroupByBundlesAndApplications().stream().findFirst().get().getApplications().size());
        assertEquals(2, endpointDTO.getEventTypesGroupByBundlesAndApplications().stream().findFirst().get().getApplications().stream().findFirst().get().getEventTypes().size());

        // test orders
        final Bundle bundle3 = resourceHelpers.createBundle(RandomStringUtils.randomAlphabetic(10).toLowerCase(), "bundle 3");
        final Bundle bundle2 = resourceHelpers.createBundle(RandomStringUtils.randomAlphabetic(10).toLowerCase(), "bundle 2");

        // Create event type and endpoint
        final Application application2 = resourceHelpers.createApplication(bundle1.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "application 2");
        final Application application3 = resourceHelpers.createApplication(bundle1.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "application 3");
        final EventType eventType21 = resourceHelpers.createEventType(application2.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "event type 1", "description1");
        final EventType eventType31 = resourceHelpers.createEventType(application3.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "event type 1", "description1");

        final Application application21 = resourceHelpers.createApplication(bundle2.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "application 1");
        final EventType eventType211 = resourceHelpers.createEventType(application21.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "event type 1", "description1");

        final Application application31 = resourceHelpers.createApplication(bundle3.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "application 1");
        final EventType eventType311 = resourceHelpers.createEventType(application31.getId(), RandomStringUtils.randomAlphabetic(10).toLowerCase(), "event type 1", "description1");

        // Bundle1
        //      application 1
        //          event type 1
        //          event type 2
        //          event type 3
        //      application 2
        //          event type 1
        //      application 3
        //          event type 1
        // Bundle2
        //      application 1
        //          event type 1
        // Bundle3
        //      application 1
        //          event type 1

        List<UUID> eventType = Arrays.asList(eventType1.getId(), eventType2.getId(), eventType3.getId(), eventType21.getId(), eventType211.getId(), eventType31.getId(), eventType311.getId());

        for (int attempt = 0; attempt < 10; attempt++) {
            List<UUID> previousEventTypeList = new ArrayList(eventType);
            Collections.shuffle(eventType);
            assertNotEquals(previousEventTypeList, eventType);

            given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(eventType))
                .pathParam("endpointUuid", endpoint.getId())
                .put("/endpoints/{endpointUuid}/eventTypes")
                .then()
                .statusCode(204);

            String result = fetchSingleEndpointV2Raw(endpoint.getId(), identityHeader);

            // check bundles order
            assertTrue(result.indexOf(bundle1.getId().toString()) < result.indexOf(bundle2.getId().toString()));
            assertTrue(result.indexOf(bundle2.getId().toString()) < result.indexOf(bundle3.getId().toString()));

            // check applications order
            assertTrue(result.indexOf(application1.getId().toString()) < result.indexOf(application2.getId().toString()));
            assertTrue(result.indexOf(application2.getId().toString()) < result.indexOf(application3.getId().toString()));
            assertTrue(result.indexOf(application3.getId().toString()) < result.indexOf(application21.getId().toString()));
            assertTrue(result.indexOf(application21.getId().toString()) < result.indexOf(application31.getId().toString()));

            // check eventTypes order
            assertTrue(result.indexOf(eventType1.getId().toString()) < result.indexOf(eventType2.getId().toString()));
            assertTrue(result.indexOf(eventType2.getId().toString()) < result.indexOf(eventType3.getId().toString()));
            assertTrue(result.indexOf(eventType3.getId().toString()) < result.indexOf(eventType21.getId().toString()));
            assertTrue(result.indexOf(eventType21.getId().toString()) < result.indexOf(eventType31.getId().toString()));
            assertTrue(result.indexOf(eventType31.getId().toString()) < result.indexOf(eventType211.getId().toString()));
            assertTrue(result.indexOf(eventType211.getId().toString()) < result.indexOf(eventType311.getId().toString()));

            // test delete all event types
            given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(new HashSet<>()))
                .pathParam("endpointUuid", endpoint.getId())
                .put("/endpoints/{endpointUuid}/eventTypes")
                .then()
                .statusCode(204);

            endpointFromDb = resourceHelpers.getEndpoint(endpoint.getId());
            assertEquals(0, endpointFromDb.getEventTypes().size());

            behaviorGroups = resourceHelpers.findBehaviorGroupsByOrgId(DEFAULT_ORG_ID);
            assertEquals(0, behaviorGroups.size());
        }
    }

    @Test
    void testUpdateExistingBehaviorGroupUsingEndpointToEventTypeRelationship() {
        final Bundle bundle = resourceHelpers.createBundle();
        final Application application = resourceHelpers.createApplication(bundle.getId());
        final EventType eventType = resourceHelpers.createEventType(application.getId(), "name", "display-name", "description");
        final Endpoint endpoint = resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.WEBHOOK);

        final String behaviorGroupName = String.format("Integration \"%s\" behavior group", endpoint.getName());
        final BehaviorGroup behaviorGroup = resourceHelpers.createBehaviorGroup(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, behaviorGroupName, bundle.getId());

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(TestConstants.DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "username");
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Check that created endpoint don't have any event type associated
        Endpoint endpointFromDb = resourceHelpers.getEndpoint(endpoint.getId());
        assertEquals(0, endpointFromDb.getEventTypes().size());

        // Check that created endpoint don't have any linked behavior group
        List<BehaviorGroup> behaviorGroups = resourceHelpers.findBehaviorGroupsByOrgId(DEFAULT_ORG_ID);
        assertEquals(1, behaviorGroups.size());
        assertEquals(behaviorGroup.getId(), behaviorGroups.get(0).getId());

        assertEquals(0, behaviorGroups.get(0).getBehaviors().size());
        assertEquals(0, behaviorGroups.get(0).getActions().size());

        // link endpoint and event type
        given()
            .header(identityHeader)
            .pathParam("eventTypeUuid", eventType.getId())
            .pathParam("endpointUuid", endpoint.getId())
            .when()
            .put("/endpoints/{endpointUuid}/eventType/{eventTypeUuid}")
            .then()
            .statusCode(204);

        behaviorGroups = resourceHelpers.findBehaviorGroupsByOrgId(DEFAULT_ORG_ID);
        assertEquals(1, behaviorGroups.size());
        assertEquals(1, behaviorGroups.get(0).getBehaviors().size());
        assertEquals(1, behaviorGroups.get(0).getActions().size());

        // Try to link again same endpoint and event type
        given()
            .header(identityHeader)
            .pathParam("eventTypeUuid", eventType.getId())
            .pathParam("endpointUuid", endpoint.getId())
            .when()
            .put("/endpoints/{endpointUuid}/eventType/{eventTypeUuid}")
            .then()
            .statusCode(204);

        behaviorGroups = resourceHelpers.findBehaviorGroupsByOrgId(DEFAULT_ORG_ID);
        assertEquals(1, behaviorGroups.size());
        assertEquals(1, behaviorGroups.get(0).getBehaviors().size());
        assertEquals(1, behaviorGroups.get(0).getActions().size());

        resourceHelpers.deleteBehaviorGroup(behaviorGroup.getId());
    }

    private EndpointDTO fetchSingleEndpointV2(UUID endpointUuid, Header identityHeader) {
        String response = fetchSingleEndpointV2Raw(endpointUuid, identityHeader);

        JsonObject endpoint = new JsonObject(response);
        return endpoint.mapTo(EndpointDTO.class);
    }

    private String fetchSingleEndpointV2Raw(UUID endpointUuid, Header identityHeader) {
        String response = given()
            .basePath(TestConstants.API_INTEGRATIONS_V_2_0)
            .header(identityHeader)
            .pathParam("id", endpointUuid)
            .when().get("/endpoints/{id}")
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().asString();

        return response;
    }

    private EndpointPage fetchEndpoints(Header identityHeader, String basePath) {
        EndpointPage response = given()
            .basePath(basePath)
            .header(identityHeader)
            .when().get("/endpoints")
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(EndpointPage.class);

        Log.info(response);
        return response;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testNotFoundResponsesUnknownEndpointId(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselPermission(INTEGRATIONS_VIEW, ALLOWED_TRUE);
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        // Add RBAC access.
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        final UUID nonExistentEndpointId = UUID.randomUUID();

        try {
            RestAssured.basePath = TestConstants.API_INTEGRATIONS_V_2_0;

            // Call the notifications history endpoint in the V2 path.
            given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .pathParam("endpointId", nonExistentEndpointId)
                .get("/endpoints/{endpointId}/history")
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND);
        } finally {
            RestAssured.basePath = TestConstants.API_INTEGRATIONS_V_1_0;
        }

        // Call the notifications history endpoint in the V1 path.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("endpointId", nonExistentEndpointId)
            .get("/endpoints/{endpointId}/history")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);

        try {
            RestAssured.basePath = TestConstants.API_INTEGRATIONS_V_2_0;

            // Call the "get endpoint" endpoint in the V2 path.
            given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .pathParam("endpointId", nonExistentEndpointId)
                .get("/endpoints/{endpointId}")
                .then()
                .statusCode(HttpStatus.SC_NOT_FOUND);
        } finally {
            RestAssured.basePath = TestConstants.API_INTEGRATIONS_V_1_0;
        }
        // Get an endpoint.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("endpointId", nonExistentEndpointId)
            .get("/endpoints/{endpointId}")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);

        // Delete an endpoint.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("endpointId", nonExistentEndpointId)
            .delete("/endpoints/{endpointId}")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);

        // Enable an endpoint.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("endpointId", nonExistentEndpointId)
            .put("/endpoints/{endpointId}/enable")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);

        // Disable an endpoint.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("endpointId", nonExistentEndpointId)
            .delete("/endpoints/{endpointId}/enable")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);

        // Update an endpoint. Create an endpoint so that we can avoid
        // receiving a "bad request" response for not including a body.
        final Endpoint createdEndpoint = this.resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.WEBHOOK);
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(this.endpointMapper.toDTO(createdEndpoint)))
            .pathParam("endpointId", nonExistentEndpointId)
            .delete("/endpoints/{endpointId}/enable")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);

        // Get the history details.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("endpointId", nonExistentEndpointId)
            .pathParam("historyId", UUID.randomUUID())
            .get("/endpoints/{endpointId}/history/{historyId}/details")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);

        // Test an endpoint.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("endpointId", nonExistentEndpointId)
            .post("/endpoints/{endpointId}/test")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);

        // Delete an event type.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("endpointId", nonExistentEndpointId)
            .pathParam("eventTypeId", UUID.randomUUID())
            .delete("/endpoints/{endpointId}/eventType/{eventTypeId}")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);

        // Add a link between an endpoint and an event type.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("endpointId", nonExistentEndpointId)
            .pathParam("eventTypeId", UUID.randomUUID())
            .put("/endpoints/{endpointId}/eventType/{eventTypeId}")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);

        // Update links between an endpoint and event types.
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("endpointId", nonExistentEndpointId)
            .put("/endpoints/{endpointId}/eventTypes")
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testSystemEndpointsCreationKesselInventory(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselUpdatePermission(CREATE_DRAWER_INTEGRATION, ALLOWED_TRUE);
            mockDefaultKesselUpdatePermission(CREATE_EMAIL_SUBSCRIPTION_INTEGRATION, ALLOWED_TRUE);
        }

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        RequestSystemSubscriptionProperties requestProps = new RequestSystemSubscriptionProperties();

        Response response = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(requestProps))
            .post("/endpoints/system/email_subscription")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().response();

        JsonObject responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(EndpointDTO.class);
        String emailEndpointId = responsePoint.getString("id");
        assertNotNull(emailEndpointId);

        response = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(requestProps))
            .post("/endpoints/system/drawer_subscription")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().response();

        responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(EndpointDTO.class);
        String drawerEndpointId = responsePoint.getString("id");
        assertNotNull(drawerEndpointId);
    }

    /**
     * Tests that when we attempt to remove an integration from our database,
     * if the Sources integration throws an exception when deleting the
     * secrets, the whole transaction is rolled back, the integration does not
     * get deleted from our database, and that the integration gets recreated
     * in Kessel's Inventory.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testInventoryDeleteIntegrationSourcesFail(boolean kesselEnabled) {

        when(backendConfig.isKesselEnabled(anyString())).thenReturn(kesselEnabled);
        if (kesselEnabled) {
            mockDefaultKesselPermission(INTEGRATIONS_VIEW, ALLOWED_TRUE);
            mockDefaultKesselUpdatePermission(INTEGRATIONS_CREATE, ALLOWED_TRUE);
        }

        // Create the integration we are going to attempt to delete.
        final WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setDisableSslVerification(false);
        webhookProperties.setMethod(POST);
        webhookProperties.setSecretToken("my-super-secret-token");
        webhookProperties.setSecretTokenSourcesId(new Random().nextLong());
        webhookProperties.setUrl(getMockServerUrl());

        final Endpoint integration = this.resourceHelpers.createEndpoint(
            DEFAULT_ACCOUNT_ID,
            DEFAULT_ORG_ID,
            WEBHOOK,
            null,
            "integration-name",
            "integration-description",
            webhookProperties,
            false,
            LocalDateTime.now()
        );

        // Simulate that Sources throws an exception when attempting to delete
        // the secrets.
        Mockito.doThrow(new ClientWebApplicationException()).when(this.sourcesServiceMock).delete(Mockito.anyString(), Mockito.anyString(), Mockito.anyLong());

        // Create the identity header to be used in the request.
        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Attempt deleting the integration.
        given()
            .header(identityHeader)
            .pathParam("id", integration.getId())
            .when()
            .delete("/endpoints/{id}")
            .then()
            .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);

        // The integration assertion triggers a call to Sources to fetch the
        // secret token, so we need to make sure a secret is returned so that
        // the code doesn't break.
        final Secret secret = new Secret();
        secret.password = webhookProperties.getSecretToken();

        Mockito
            .when(sourcesServiceMock.getById(Mockito.anyString(), Mockito.anyString(), Mockito.eq(webhookProperties.getSecretTokenSourcesId())))
            .thenReturn(secret);

        // Assert that the transaction was rolled back and that the integration
        // was not deleted.
        this.assertIntegrationExists(identityHeader, integration);
    }

    /**
     * Tests that when creating webhook endpoints, only the {@code POST} method
     * can be specified in the endpoint's properties. It's a regression test of
     * <a href="https://issues.redhat.com/browse/RHCLOUD-32168">RHCLOUD-32168</a>.
     */
    @Test
    void testWebhookEndpointOnlyAllowsPostMethod() {
        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Build a list with the methods that we want to set in the webhook's
        // properties.
        final List<String> methodsUnderTest = List.of(
            HttpMethod.GET,
            HttpMethod.HEAD,
            HttpMethod.OPTIONS,
            HttpMethod.PATCH,
            HttpMethod.POST, // Only this one should go through!
            HttpMethod.PUT
        );

        for (final String method : methodsUnderTest) {
            final WebhookPropertiesDTO properties = new WebhookPropertiesDTO();
            properties.setMethod(method);
            properties.setUrl(getMockServerUrl());

            final EndpointDTO requestBody = new EndpointDTO();
            requestBody.setType(EndpointTypeDTO.WEBHOOK);
            requestBody.setName("Webhook integration");
            requestBody.setDescription("A webhook integration");
            requestBody.setEnabled(true);
            requestBody.setProperties(properties);
            requestBody.setServerErrors(0);

            if (HttpMethod.POST.equals(method)) {
                given()
                    .header(TestHelpers.createRHIdentityHeader(identityHeaderValue))
                    .when()
                    .contentType(JSON)
                    .body(Json.encode(requestBody))
                    .post("/endpoints")
                    .then()
                    .statusCode(HttpStatus.SC_OK)
                    .contentType(JSON)
                    .body("properties.method", Matchers.is(HttpMethod.POST));
            } else {
                given()
                    .header(TestHelpers.createRHIdentityHeader(identityHeaderValue))
                    .when()
                    .contentType(JSON)
                    .body(Json.encode(requestBody))
                    .post("/endpoints")
                    .then()
                    .statusCode(HttpStatus.SC_BAD_REQUEST)
                    .contentType(JSON)
                    .body("violations.size()", Matchers.is(1))
                    .body("violations[0].message", Matchers.is("Only \"POST\" methods are allowed for the properties of a webhook"));
            }
        }
    }

    /**
     * Asserts that no integrations are present in the database by issuing a
     * query directly and by calling the "/endpoints" REST endpoint.
     * @param identityHeader the identity header to be used when fetching the
     *                       list of integrations via the REST endpoint.
     */
    void assertNoIntegrationsInDatabase(final Header identityHeader) {
        Assertions.assertTrue(this.entityManager.createQuery("FROM Endpoint").getResultList().isEmpty(), "when fetching all the integrations from the database, the list was not empty");

        // It does not make sense to attempt fetching all the integrations from
        // the back end when using the Relations API, because we would need to
        // mock which integrations the principal is authorized to list, and
        // since we are just trying to fetch them all to verify that none got
        // created, it kind of defeats the purpose. We also do not have an ID
        // we could use to even mock the Relations API, because the integration
        // does not get created in the database.
        if (this.backendConfig.isKesselEnabled(anyString())) {
            return;
        }

        given()
            .header(identityHeader)
            .when()
            .get("/endpoints")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .body("data", Matchers.hasSize(0))
            .body("links", Matchers.anEmptyMap())
            .body("meta.count", Matchers.is(0));
    }

    /**
     * Assert that the given integration is present in the database by issuing
     * a query directly and by calling the "/endpoints/{id}" REST endpoint.
     * @param identityHeader the identity header to be used when fetching the
     *                       integration.
     * @param endpoint the integration that we want to make sure that still
     *                 exists on our end.
     */
    void assertIntegrationExists(final Header identityHeader, final Endpoint endpoint) {
        Assertions.assertEquals(1, this.entityManager.createQuery("FROM Endpoint WHERE id=:id").setParameter("id", endpoint.getId()).getResultList().size(), "the integration was unexpectedly deleted from the database");

        // Mock the Kessel permission to be able to fetch the endpoint. Also,
        // simulate that the principal has edit permissions so that the Sources
        // secrets are not redacted. We are not interested in testing any
        // Sources' stuff in this assertion, but we need to give the principal
        // this permission so that the check evaluates to something instead of
        // throwing a "null pointer" exception.

        given()
            .header(identityHeader)
            .when()
            .pathParam("id", endpoint.getId())
            .get("/endpoints/{id}")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .body("id", Matchers.is(endpoint.getId().toString()));
    }

    private static Stream<Arguments> kesselFlagsEmailOrDrawerEndpoints() {
        return Stream.of(
                Arguments.of(false, EndpointType.EMAIL_SUBSCRIPTION), // Should use RBAC
                Arguments.of(true, EndpointType.EMAIL_SUBSCRIPTION), // Should use Kessel
                Arguments.of(false, EndpointType.DRAWER), // Should use RBAC
                Arguments.of(true, EndpointType.DRAWER) // Should use Kessel
        );
    }

    private void mockDefaultKesselPermission(WorkspacePermission permission, Allowed allowed) {
        when(kesselCheckClient
            .check(kesselTestHelper.buildCheckRequest(DEFAULT_ORG_ID, DEFAULT_USER, permission)))
            .thenReturn(kesselTestHelper.buildCheckResponse(allowed));
    }

    private void mockDefaultKesselUpdatePermission(WorkspacePermission permission, Allowed allowed) {
        when(kesselCheckClient
            .checkForUpdate(kesselTestHelper.buildCheckForUpdateRequest(DEFAULT_USER, permission)))
            .thenReturn(kesselTestHelper.buildCheckForUpdateResponse(allowed));
    }
}
