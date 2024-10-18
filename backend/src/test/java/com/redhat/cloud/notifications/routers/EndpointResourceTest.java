package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.auth.kessel.KesselTestHelper;
import com.redhat.cloud.notifications.auth.kessel.ResourceType;
import com.redhat.cloud.notifications.auth.kessel.permission.IntegrationPermission;
import com.redhat.cloud.notifications.auth.kessel.permission.WorkspacePermission;
import com.redhat.cloud.notifications.auth.principal.ConsoleIdentity;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.model.Stats;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BasicAuthentication;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointStatus;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.PagerDutyProperties;
import com.redhat.cloud.notifications.models.PagerDutySeverity;
import com.redhat.cloud.notifications.models.SourcesSecretable;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.models.dto.v1.EventTypeDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointMapper;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointTypeDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.properties.WebhookPropertiesDTO;
import com.redhat.cloud.notifications.models.validation.ValidNonPrivateUrlValidator;
import com.redhat.cloud.notifications.models.validation.ValidNonPrivateUrlValidatorTest;
import com.redhat.cloud.notifications.routers.endpoints.EndpointTestRequest;
import com.redhat.cloud.notifications.routers.endpoints.InternalEndpointTestRequest;
import com.redhat.cloud.notifications.routers.engine.EndpointTestService;
import com.redhat.cloud.notifications.routers.models.EndpointPage;
import com.redhat.cloud.notifications.routers.models.RequestSystemSubscriptionProperties;
import com.redhat.cloud.notifications.routers.sources.Secret;
import com.redhat.cloud.notifications.routers.sources.SourcesService;
import io.quarkus.logging.Log;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.restassured.response.Response;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriBuilder;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.project_kessel.api.relations.v1beta1.CheckResponse;
import org.project_kessel.relations.client.CheckClient;
import org.project_kessel.relations.client.LookupClient;

import java.net.URI;
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
import static com.redhat.cloud.notifications.auth.kessel.Constants.WORKSPACE_ID_PLACEHOLDER;
import static com.redhat.cloud.notifications.models.EndpointStatus.READY;
import static com.redhat.cloud.notifications.models.EndpointType.ANSIBLE;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static com.redhat.cloud.notifications.models.HttpType.POST;
import static com.redhat.cloud.notifications.routers.EndpointResource.DEPRECATED_SLACK_CHANNEL_ERROR;
import static com.redhat.cloud.notifications.routers.EndpointResource.HTTPS_ENDPOINT_SCHEME_REQUIRED;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.TEXT;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

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
    }

    @Inject
    ResourceHelpers helpers;

    @Inject
    ResourceHelpers resourceHelpers;

    @InjectMock
    BackendConfig backendConfig;

    /**
     * Mocked Kessel's check client so that the {@link KesselTestHelper} can
     * be used.
     */
    @InjectMock
    CheckClient checkClient;

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
     * Mocked Kessel's lookup client so that the {@link KesselTestHelper} can
     * be used.
     */
    @InjectMock
    LookupClient lookupClient;

    /**
     * We mock the sources service's REST client because there are a few tests
     * that enable the integration, but we don't want to attempt to hit the
     * real service.
     */
    @InjectMock
    @RestClient
    SourcesService sourcesServiceMock;

    /**
     * Required to set up the mock calls to the sources service mock.
     */
    @ConfigProperty(name = "sources.psk")
    String sourcesPsk;

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    // Mock the Sources service calls.
    private Secret mockSources(SourcesSecretable properties) {

        final Secret secret = new Secret();
        secret.id = new Random().nextLong(1, Long.MAX_VALUE);
        secret.password = properties.getSecretToken();

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
    @ValueSource(booleans = {false, true})
    void testEndpointAdding(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        this.kesselTestHelper.mockAuthorizedIntegrationsLookup();

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

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

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

        this.kesselTestHelper.mockAuthorizedIntegrationsLookup(Set.of(UUID.fromString(responsePoint.getString("id"))));

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
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.DISABLE, ResourceType.INTEGRATION, responsePoint.getString("id"));

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
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.ENABLE, ResourceType.INTEGRATION, responsePoint.getString("id"));

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
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.DELETE, ResourceType.INTEGRATION, responsePoint.getString("id"));

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

        // Add new endpoints
        WebhookPropertiesDTO properties = new WebhookPropertiesDTO();
        properties.setMethod(POST);
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
        ep.eventTypes = Set.of(eventType1.getId(), eventType2.getId());

        mockSources(new WebhookProperties());

        Response response = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(ep))
            .post("/endpoints")
            .then()
            .statusCode(200)
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
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testRepeatedEndpointName(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

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

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

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
        camelProperties.setBasicAuthentication(new BasicAuthentication());
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
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.EDIT, ResourceType.INTEGRATION, endpoint1Id);

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
        this.kesselTestHelper.mockKesselPermission(identity.getName(), IntegrationPermission.VIEW, ResourceType.INTEGRATION, id);

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
    @ValueSource(booleans = {false, true})
    void testEndpointValidation(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

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
        properties.setMethod(POST);
        ep.setType(EndpointType.EMAIL_SUBSCRIPTION);
        expectReturn400(DEFAULT_USER, identityHeader, ep);

        ep.setType(EndpointType.DRAWER);
        expectReturn400(DEFAULT_USER, identityHeader, ep);

        ep.setName("endpoint with subtype too long");
        ep.setType(EndpointType.CAMEL);
        ep.setSubType("something-longer-than-20-chars");
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
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

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
    @ValueSource(booleans = {false, true})
    void addCamelEndpoint(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        CamelProperties cAttr = new CamelProperties();
        cAttr.setDisableSslVerification(false);
        cAttr.setUrl(getMockServerUrl());
        cAttr.setBasicAuthentication(new BasicAuthentication("testuser", "secret"));
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
        final Secret basicAuthSecret = new Secret();
        basicAuthSecret.id = new Random().nextLong(1, Long.MAX_VALUE);
        basicAuthSecret.password = cAttr.getBasicAuthentication().getPassword();
        basicAuthSecret.username = cAttr.getBasicAuthentication().getUsername();

        when(this.sourcesServiceMock.create(anyString(), anyString(), any()))
            .thenReturn(basicAuthSecret);

        // Make sure that when the secrets are loaded when we fetch the
        // endpoint again for checking the assertions, we simulate fetching
        // the secrets from Sources too.
        when(this.sourcesServiceMock.getById(anyString(), anyString(), eq(basicAuthSecret.id))).thenReturn(basicAuthSecret);

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

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

            JsonObject basicAuth = properties.getJsonObject("basic_authentication");
            assertNotNull(basicAuth);
            String user = basicAuth.getString("username");
            String pass = basicAuth.getString("password");
            assertEquals("testuser", user);
            assertEquals("secret", pass);

            assertEquals("secret-token", properties.getString("secret_token"));
        } finally {
            this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.DELETE, ResourceType.INTEGRATION, id);

            given()
                    .header(identityHeader)
                    .when().delete("/endpoints/" + id)
                    .then()
                    .statusCode(HttpStatus.SC_NO_CONTENT)
                    .extract().body().asString();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void addBogusCamelEndpoint(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        CamelProperties cAttr = new CamelProperties();
        cAttr.setDisableSslVerification(false);
        cAttr.setUrl(getMockServerUrl());
        cAttr.setBasicAuthentication(new BasicAuthentication("testuser", "secret"));
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

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

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
    @ValueSource(booleans = {false, true})
    void testForbidSlackChannelUsage(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("account-id", "org-id", DEFAULT_USER);
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

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

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
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.EDIT, ResourceType.INTEGRATION, endpointUuidRaw);

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
    @ValueSource(booleans = {false, true})
    void testRequireHttpsSchemeServiceNow(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        URI sNowUri = URI.create("http://redhat.com");
        testRequireHttpsScheme("servicenow", sNowUri);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testRequireHttpsSchemeSplunk(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

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

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

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
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.EDIT, ResourceType.INTEGRATION, endpointUuidRaw);

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
    @ValueSource(booleans = {false, true})
    void addSlackEndpoint(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

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

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

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

            this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.EDIT, ResourceType.INTEGRATION, id);

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
            this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.DELETE, ResourceType.INTEGRATION, id);

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
    @ValueSource(booleans = {false, true})
    void testEndpointUpdates(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        this.kesselTestHelper.mockAuthorizedIntegrationsLookup();

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

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

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
        this.kesselTestHelper.mockAuthorizedIntegrationsLookup(Set.of(UUID.fromString(responsePoint.getString("id"))));

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

        // Update without payload
        given()
                .header(identityHeader)
                .contentType(JSON)
                .when()
                .put(String.format("/endpoints/%s", responsePointSingle.getString("id")))
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST);

        // With payload
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.EDIT, ResourceType.INTEGRATION, responsePointSingle.getString("id"));

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
    @MethodSource
    void testEndpointTypeQuery(final boolean isKesselEnabled, final Set<EndpointType> types) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

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
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);
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
        this.kesselTestHelper.mockAuthorizedIntegrationsLookup(createdEndpointIds);
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
        this.kesselTestHelper.mockAuthorizedIntegrationsLookup(createdEndpointIds);
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
    @ValueSource(booleans = {false, true})
    void testEndpointLimiter(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

        final Set<UUID> createdEndpointsIdentifiers = addEndpoints(29, identityHeader);

        this.kesselTestHelper.mockAuthorizedIntegrationsLookup(createdEndpointsIdentifiers);

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

        this.kesselTestHelper.mockAuthorizedIntegrationsLookup(createdEndpointsIdentifiers);

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
    @ValueSource(booleans = {false, true})
    void testSortingOrder(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Create 50 test-ones with sanely sortable name & enabled & disabled & type
        final Stats stats = helpers.createTestEndpoints(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, 50);
        this.kesselTestHelper.mockAuthorizedIntegrationsLookup(stats.getEndpointIds());

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

        this.kesselTestHelper.mockAuthorizedIntegrationsLookup(stats.getEndpointIds());

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

        this.kesselTestHelper.mockAuthorizedIntegrationsLookup(stats.getEndpointIds());

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

        this.kesselTestHelper.mockAuthorizedIntegrationsLookup(stats.getEndpointIds());

        given()
                .header(identityHeader)
                .queryParam("sort_by", "hulla:desc")
                .when()
                .get("/endpoints?limit=100")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST);

    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testWebhookAttributes(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Add new endpoints
        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(POST);
        properties.setDisableSslVerification(false);
        properties.setSecretToken("my-super-secret-token");
        properties.setBasicAuthentication(new BasicAuthentication("myuser", "mypassword"));
        properties.setBearerAuthentication("my-test-bearer-token");
        properties.setUrl(getMockServerUrl());

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("endpoint to find");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(properties);

        // Mock the Sources service calls.
        final Secret basicAuthSecret = new Secret();
        basicAuthSecret.id = new Random().nextLong(1, Long.MAX_VALUE);
        basicAuthSecret.password = properties.getBasicAuthentication().getPassword();
        basicAuthSecret.username = properties.getBasicAuthentication().getUsername();

        final Secret secretTokenSecret = new Secret();
        secretTokenSecret.id = new Random().nextLong(1, Long.MAX_VALUE);
        secretTokenSecret.password = properties.getSecretToken();

        final Secret bearerTokenSecret = new Secret();
        bearerTokenSecret.id = new Random().nextLong(1, Long.MAX_VALUE);
        bearerTokenSecret.password = properties.getBearerAuthentication();

        // The SecretUtils class follows the "basic authentication", "secret
        // token" and "bearer token" order, so that is why we make the returns
        // in that order for the mock.
        when(this.sourcesServiceMock.create(anyString(), anyString(), any()))
            .thenReturn(basicAuthSecret, secretTokenSecret, bearerTokenSecret);

        // Make sure that when the secrets are loaded when we fetch the
        // endpoint again for checking the assertions, we simulate fetching
        // the secrets from Sources too.
        when(this.sourcesServiceMock.getById(anyString(), anyString(), eq(basicAuthSecret.id))).thenReturn(basicAuthSecret);
        when(this.sourcesServiceMock.getById(anyString(), anyString(), eq(secretTokenSecret.id))).thenReturn(secretTokenSecret);
        when(this.sourcesServiceMock.getById(anyString(), anyString(), eq(bearerTokenSecret.id))).thenReturn(bearerTokenSecret);

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

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
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.EDIT, ResourceType.INTEGRATION, responsePoint.getString("id"));

        JsonObject responsePointSingle = fetchSingle(responsePoint.getString("id"), identityHeader);

        assertNotNull(responsePoint.getJsonObject("properties"));
        assertTrue(responsePointSingle.getBoolean("enabled"));
        assertNotNull(responsePointSingle.getJsonObject("properties"));
        assertNotNull(responsePointSingle.getJsonObject("properties").getString("secret_token"));

        JsonObject attr = responsePointSingle.getJsonObject("properties");
        attr.mapTo(WebhookProperties.class);
        assertNotNull(attr.getJsonObject("basic_authentication"));
        assertEquals("mypassword", attr.getJsonObject("basic_authentication").getString("password"));
        assertEquals(properties.getBearerAuthentication(), attr.getString("bearer_authentication"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testAddEndpointEmailSubscription(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // EmailSubscription can't be created
        SystemSubscriptionProperties properties = new SystemSubscriptionProperties();

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.EMAIL_SUBSCRIPTION);
        ep.setName("Endpoint: EmailSubscription");
        ep.setDescription("Subscribe!");
        ep.setEnabled(true);
        ep.setProperties(properties);

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

        String stringResponse = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(this.endpointMapper.toDTO(ep)))
                .post("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .extract().asString();

        assertSystemEndpointTypeError(stringResponse, EndpointType.EMAIL_SUBSCRIPTION);

        RequestSystemSubscriptionProperties requestProps = new RequestSystemSubscriptionProperties();

        // EmailSubscription can be fetch from the properties
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.CREATE_EMAIL_SUBSCRIPTION_INTEGRATION, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

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
                .post("/endpoints/system/email_subscription")
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
                .post("/endpoints/system/email_subscription")
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
                .post("/endpoints/system/email_subscription")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .extract().response();

        responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(EndpointDTO.class);
        assertTrue(endpointIds.contains(responsePoint.getString("id")));

        // It is not possible to delete it
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.DELETE, ResourceType.INTEGRATION, defaultEndpointId);

        stringResponse = given()
                .header(identityHeader)
                .when().delete("/endpoints/" + defaultEndpointId)
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .extract().asString();
        assertSystemEndpointTypeError(stringResponse, EndpointType.EMAIL_SUBSCRIPTION);

        // It is not possible to disable or enable it
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.DISABLE, ResourceType.INTEGRATION, defaultEndpointId);

        stringResponse = given()
                .header(identityHeader)
                .when().delete("/endpoints/" + defaultEndpointId + "/enable")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .extract().asString();
        assertSystemEndpointTypeError(stringResponse, EndpointType.EMAIL_SUBSCRIPTION);

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.ENABLE, ResourceType.INTEGRATION, defaultEndpointId);

        stringResponse = given()
                .header(identityHeader)
                .when().put("/endpoints/" + defaultEndpointId + "/enable")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .extract().asString();
        assertSystemEndpointTypeError(stringResponse, EndpointType.EMAIL_SUBSCRIPTION);

        // It is not possible to update it
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.EDIT, ResourceType.INTEGRATION, defaultEndpointId);

        stringResponse = given()
                .header(identityHeader)
                .contentType(JSON)
                .body(Json.encode(ep))
                .when().put("/endpoints/" + defaultEndpointId)
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .extract().asString();
        assertSystemEndpointTypeError(stringResponse, EndpointType.EMAIL_SUBSCRIPTION);

        // It is not possible to update it to other type
        ep.setType(EndpointType.WEBHOOK);

        WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setMethod(POST);
        webhookProperties.setDisableSslVerification(false);
        webhookProperties.setSecretToken("my-super-secret-token");
        webhookProperties.setUrl(getMockServerUrl());
        ep.setProperties(webhookProperties);

        stringResponse = given()
                .header(identityHeader)
                .contentType(JSON)
                .body(Json.encode(ep))
                .when().put("/endpoints/" + defaultEndpointId)
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .extract().asString();
        assertSystemEndpointTypeError(stringResponse, EndpointType.EMAIL_SUBSCRIPTION);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testAddEndpointDrawerSubscription(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Drawer endpoints can't be created from the general endpoint
        SystemSubscriptionProperties properties = new SystemSubscriptionProperties();

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.DRAWER);
        ep.setName("Endpoint: Drawer");
        ep.setDescription("Subscribe!");
        ep.setEnabled(true);
        ep.setProperties(properties);

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

        String stringResponse = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(ep))
            .post("/endpoints")
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST)
            .extract().asString();

        assertSystemEndpointTypeError(stringResponse, EndpointType.DRAWER);

        RequestSystemSubscriptionProperties requestProps = new RequestSystemSubscriptionProperties();

        // Drawer endpoints can be created from the dedicated endpoint
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.CREATE_DRAWER_INTEGRATION, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

        Response response = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(requestProps))
            .post("/endpoints/system/drawer_subscription")
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
            .post("/endpoints/system/drawer_subscription")
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
            .post("/endpoints/system/drawer_subscription")
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
            .post("/endpoints/system/drawer_subscription")
            .then()
            .statusCode(HttpStatus.SC_OK)
            .contentType(JSON)
            .extract().response();

        responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(EndpointDTO.class);
        assertTrue(endpointIds.contains(responsePoint.getString("id")));

        // It is not possible to delete it
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.DELETE, ResourceType.INTEGRATION, defaultEndpointId);

        stringResponse = given()
            .header(identityHeader)
            .when().delete("/endpoints/" + defaultEndpointId)
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST)
            .extract().asString();
        assertSystemEndpointTypeError(stringResponse, EndpointType.DRAWER);

        // It is not possible to disable or enable it
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.DISABLE, ResourceType.INTEGRATION, defaultEndpointId);

        stringResponse = given()
            .header(identityHeader)
            .when().delete("/endpoints/" + defaultEndpointId + "/enable")
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST)
            .extract().asString();
        assertSystemEndpointTypeError(stringResponse, EndpointType.DRAWER);

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.ENABLE, ResourceType.INTEGRATION, defaultEndpointId);

        stringResponse = given()
            .header(identityHeader)
            .when().put("/endpoints/" + defaultEndpointId + "/enable")
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST)
            .extract().asString();
        assertSystemEndpointTypeError(stringResponse, EndpointType.DRAWER);

        // It is not possible to update it
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.EDIT, ResourceType.INTEGRATION, defaultEndpointId);

        stringResponse = given()
            .header(identityHeader)
            .contentType(JSON)
            .body(Json.encode(ep))
            .when().put("/endpoints/" + defaultEndpointId)
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST)
            .extract().asString();
        assertSystemEndpointTypeError(stringResponse, EndpointType.DRAWER);

        // It is not possible to update it to other type
        ep.setType(EndpointType.WEBHOOK);

        WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setMethod(POST);
        webhookProperties.setDisableSslVerification(false);
        webhookProperties.setSecretToken("my-super-secret-token");
        webhookProperties.setUrl(getMockServerUrl());
        ep.setProperties(webhookProperties);

        stringResponse = given()
            .header(identityHeader)
            .contentType(JSON)
            .body(Json.encode(ep))
            .when().put("/endpoints/" + defaultEndpointId)
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST)
            .extract().asString();
        assertSystemEndpointTypeError(stringResponse, EndpointType.DRAWER);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testAddEndpointEmailSubscriptionRbac(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        String validGroupId = "f85517d0-063b-4eed-a501-e79ffc1f5ad3";
        String unknownGroupId = "f44f50d5-acab-482c-a3cf-087faf2c709c";

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);
        MockServerConfig.addGroupResponse(identityHeaderValue, validGroupId, HttpStatus.SC_OK);
        MockServerConfig.addGroupResponse(identityHeaderValue, unknownGroupId, HttpStatus.SC_NOT_FOUND);

        // valid group id
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.CREATE_EMAIL_SUBSCRIPTION_INTEGRATION, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

        RequestSystemSubscriptionProperties requestProps = new RequestSystemSubscriptionProperties();
        requestProps.setGroupId(UUID.fromString(validGroupId));

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
        String endpointId = responsePoint.getString("id");
        assertNotNull(endpointId);

        // Same group again yields the same endpoint id
        response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(requestProps))
                .post("/endpoints/system/email_subscription")
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
                .post("/endpoints/system/email_subscription")
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
                .post("/endpoints/system/email_subscription")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .contentType(JSON)
                .extract().response();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testUnknownEndpointTypes(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("test-tenant", "test-orgid", "test-user");
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // If Kessel doesn't return any authorized IDs we return an empty page
        // to the user, so we need to simulate that there would be some
        // endpoints that the user would be able to fetch.
        this.kesselTestHelper.mockAuthorizedIntegrationsLookup(Set.of(UUID.randomUUID()));

        given()
                .header(identityHeader)
                .queryParam("type", "foo")
                .when().get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(is("Unknown endpoint type: [foo]"));

        // Same thing here.
        this.kesselTestHelper.mockAuthorizedIntegrationsLookup(Set.of(UUID.randomUUID()));

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
    @ValueSource(booleans = {false, true})
    void testConnectionCount(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Test empty tenant
        this.kesselTestHelper.mockAuthorizedIntegrationsLookup();

        given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints")
                .then()
                .statusCode(HttpStatus.SC_OK)
                .contentType(JSON)
                .body(is("{\"data\":[],\"links\":{},\"meta\":{\"count\":0}}"));

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);
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
            this.kesselTestHelper.mockAuthorizedIntegrationsLookup(createdEndpointIds);

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
    @ValueSource(booleans = {false, true})
    void testActive(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(TestConstants.DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        final Stats stats = resourceHelpers.createTestEndpoints(TestConstants.DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, 11);

        // Get all endpoints
        this.kesselTestHelper.mockAuthorizedIntegrationsLookup(stats.getEndpointIds());

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
        this.kesselTestHelper.mockAuthorizedIntegrationsLookup(stats.getEndpointIds());

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
        this.kesselTestHelper.mockAuthorizedIntegrationsLookup(stats.getEndpointIds());

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
    @ValueSource(booleans = {false, true})
    void testSearch(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

        final Set<UUID> createdEndpoints = addEndpoints(10, identityHeader);

        this.kesselTestHelper.mockAuthorizedIntegrationsLookup(createdEndpoints);

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
    @ValueSource(booleans = {false, true})
    void testSearchWithType(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

        final Set<UUID> createdEndpoints = addEndpoints(10, identityHeader);

        this.kesselTestHelper.mockAuthorizedIntegrationsLookup(createdEndpoints);

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
    @ValueSource(booleans = {false, true})
    void testEndpointInvalidUrls(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        // Set up the RBAC access for the test.
        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Create the properties for the endpoint. Leave the URL so that we can set it afterwards.
        final var camelProperties = new CamelProperties();
        camelProperties.setBasicAuthentication(new BasicAuthentication("endpoint-invalid-urls-basic-authentication-username", "endpoint-invalid-urls-basic-authentication-password"));
        camelProperties.setDisableSslVerification(false);
        camelProperties.setSecretToken("endpoint-invalid-urls-secret-token");

        final var webhookProperties = new WebhookProperties();
        webhookProperties.setBasicAuthentication(new BasicAuthentication("endpoint-invalid-urls-basic-authentication-username", "endpoint-invalid-urls-basic-authentication-password"));
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
            this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

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
    @ValueSource(booleans = {false, true})
    void testEndpointValidUrls(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        // Set up the RBAC access for the test.
        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Set up the fixture data.
        final boolean disableSslVerification = false;
        final String password = "endpoint-invalid-urls-basic-authentication-password";
        final String username = "endpoint-invalid-urls-basic-authentication-username";
        final String secretToken = "endpoint-invalid-urls-secret-token";

        // Create the properties for the endpoint. Leave the URL so that we can set it afterwards.
        final CamelProperties camelProperties = new CamelProperties();
        camelProperties.setBasicAuthentication(new BasicAuthentication(username, password));
        camelProperties.setDisableSslVerification(disableSslVerification);
        camelProperties.setSecretToken(secretToken);

        final WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setBasicAuthentication(new BasicAuthentication(username, password));
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

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);
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
    @ValueSource(booleans = {false, true})
    public void testEndpointSubtypeIsOnlyAllowedWhenRequired(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        String EMPTY = "empty";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(EMPTY, EMPTY, DEFAULT_USER);
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

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);
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
        cAttr.setBasicAuthentication(new BasicAuthentication("testuser", "secret"));
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
    @ValueSource(booleans = {false, true})
    void testEndpointTest(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        final Endpoint createdEndpoint = this.resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.CAMEL);

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Call the endpoint under test.
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.TEST, ResourceType.INTEGRATION, createdEndpoint.getId().toString());

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
    @ValueSource(booleans = {false, true})
    void testEndpointTestNotFound(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Call the endpoint under test.
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_VIEW, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

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

        if (this.backendConfig.isKesselRelationsEnabled()) {
            final JsonObject expectedBody = new JsonObject();
            expectedBody.put("error", "Integration not found");

            Assertions.assertEquals(expectedBody.encode(), responseBody, "unexpected not found error message returned");
        } else {
            Assertions.assertEquals("integration not found", responseBody, "unexpected not found error message returned");
        }
    }

    /**
     * Tests that when a user specifies a custom message, then it gets properly
     * sent to the engine.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testEndpointTestCustomMessage(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        final Endpoint createdEndpoint = this.resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.CAMEL);

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        final String customTestMessage = "Hello, World!";
        final EndpointTestRequest endpointTestRequest = new EndpointTestRequest();
        endpointTestRequest.message = customTestMessage;

        // Call the endpoint under test.
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.TEST, ResourceType.INTEGRATION, createdEndpoint.getId().toString());
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
    @ValueSource(booleans = {false, true})
    void testEndpointTestBlankMessageReturnsBadRequest(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        final Endpoint createdEndpoint = this.resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.CAMEL);

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        final EndpointTestRequest endpointTestRequest = new EndpointTestRequest();
        endpointTestRequest.message = "";

        // Call the endpoint under test.
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.TEST, ResourceType.INTEGRATION, createdEndpoint.getId().toString());
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

        Assertions.assertEquals("internalTestEndpoint.requestBody.message", constraintViolation.getString("field"), "unexpected field validated when sending a blank test message");
        Assertions.assertEquals("must not be blank", constraintViolation.getString("message"), "unexpected error message received when sending a blank custom message for testing the endpoint");
    }

    /**
     * Tests that when an endpoint gets updated, if it didn't have any secrets
     * and the user provides new ones, then Sources gets called and the
     * references to those secrets are stored in the database.
    */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testUpdateEndpointCreateSecrets(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(TestConstants.DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Create the endpoint that we will attempt to modify.
        final WebhookProperties properties = new WebhookProperties();
        properties.setDisableSslVerification(false);
        properties.setMethod(HttpType.GET);
        properties.setUrl(getMockServerUrl());

        final Endpoint endpoint = new Endpoint();
        endpoint.setDescription("test update endpoint delete basic authentication");
        endpoint.setEnabled(true);
        endpoint.setName("test update endpoint delete basic authentication");
        endpoint.setOrgId(DEFAULT_ORG_ID);
        endpoint.setProperties(properties);
        endpoint.setServerErrors(0);
        endpoint.setStatus(EndpointStatus.PROVISIONING);
        endpoint.setType(EndpointType.WEBHOOK);

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);
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

        // Now update the endpoint by setting the "basic authentication"
        // and the "secret token".
        properties.setBasicAuthentication(new BasicAuthentication("basic-auth-user", "basic-auth-password"));
        properties.setSecretToken("my-super-secret-token");
        properties.setBearerAuthentication("my-super-bearer-token");

        // The below two secrets will be returned when we simulate that
        // we have called sources to create these secrets.
        final Secret mockedBasicAuthenticationSecret = new Secret();
        mockedBasicAuthenticationSecret.id = 25L;

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
            mockedBasicAuthenticationSecret,
            mockedSecretTokenSecret,
            mockedSecretBearer
        );

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.EDIT, ResourceType.INTEGRATION, endpointUuid.toString());
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
        Mockito.verify(this.sourcesServiceMock, Mockito.times(3)).create(
            eq(DEFAULT_ORG_ID),
            eq(this.sourcesPsk),
            any()
        );

        // Make sure that the properties got updated in the database too.
        final Endpoint databaseEndpoint = this.endpointRepository.getEndpoint(DEFAULT_ORG_ID, endpointUuid);
        final WebhookProperties webhookProperties = databaseEndpoint.getProperties(WebhookProperties.class);

        Assertions.assertEquals(25L, webhookProperties.getBasicAuthenticationSourcesId(), "Sources was called to create the basic authentication secret, but the secret's ID wasn't present in the database");
        Assertions.assertEquals(50L, webhookProperties.getSecretTokenSourcesId(), "Sources was called to create the secret token secret, but the secret's ID wasn't present in the database");
        Assertions.assertEquals(75L, webhookProperties.getBearerAuthenticationSourcesId(), "Sources was called to create the bearer authentication secret, but the secret's ID wasn't present in the database");
    }

    /**
     * Tests that when an endpoint gets updated, if the user removes the "basic
     * authentication" and "secret token" secrets, then the secrets are deleted
     * from Sources and their references deleted from our database.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testUpdateEndpointDeleteSecrets(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(TestConstants.DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Create the endpoint that we will attempt to modify.
        final WebhookProperties properties = new WebhookProperties();
        properties.setDisableSslVerification(false);
        properties.setBasicAuthentication(new BasicAuthentication("basic-auth-user", "basic-auth-password"));
        properties.setMethod(HttpType.GET);
        properties.setSecretToken("my-super-secret-token");
        properties.setUrl(getMockServerUrl());

        final Endpoint endpoint = new Endpoint();
        endpoint.setDescription("test update endpoint delete basic authentication");
        endpoint.setEnabled(true);
        endpoint.setName("test update endpoint delete basic authentication");
        endpoint.setOrgId(DEFAULT_ORG_ID);
        endpoint.setProperties(properties);
        endpoint.setServerErrors(0);
        endpoint.setStatus(EndpointStatus.PROVISIONING);
        endpoint.setType(EndpointType.WEBHOOK);

        // The below two secrets will be returned when we simulate that
        // we have called sources to create these secrets.
        final Secret mockedBasicAuthenticationSecret = new Secret();
        mockedBasicAuthenticationSecret.id = 25L;

        final Secret mockedSecretTokenSecret = new Secret();
        mockedSecretTokenSecret.id = 50L;

        when(
            this.sourcesServiceMock.create(
                eq(DEFAULT_ORG_ID),
                eq(this.sourcesPsk),
                any()
            )
        ).thenReturn(
            mockedBasicAuthenticationSecret,
            mockedSecretTokenSecret
        );

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);
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

        // Now update the endpoint by setting its "basic authentication" to
        // null, which triggers the secret deletion in Sources.
        properties.setBasicAuthentication(null);
        properties.setSecretToken(null);

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.EDIT, ResourceType.INTEGRATION, endpointUuid.toString());
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("id", endpointUuid)
            .body(Json.encode(endpoint))
            .put("/endpoints/{id}")
            .then()
            .statusCode(HttpStatus.SC_OK);

        // Verify that the basic authentication secret was deleted from
        // Sources.
        Mockito.verify(this.sourcesServiceMock, Mockito.times(1)).delete(
            DEFAULT_ORG_ID,
            this.sourcesPsk,
            25L
        );

        // Verify that the secret token secret was deleted from Sources.
        Mockito.verify(this.sourcesServiceMock, Mockito.times(1)).delete(
            DEFAULT_ORG_ID,
            this.sourcesPsk,
            50L
        );

        // Make sure that the properties got updated in the database too.
        final Endpoint databaseEndpoint = this.endpointRepository.getEndpoint(DEFAULT_ORG_ID, endpointUuid);
        final WebhookProperties webhookProperties = databaseEndpoint.getProperties(WebhookProperties.class);

        assertNull(webhookProperties.getBasicAuthenticationSourcesId(), "Sources was called to delete the basic authentication secret, but the secret's ID wasn't deleted from the database");
        assertNull(webhookProperties.getSecretTokenSourcesId(), "Sources was called to delete the secret token secret, but the secret's ID wasn't deleted from the database");
    }

    /**
     * Tests that when an endpoint gets updated, if the user updates both
     * secrets, then Sources gets called twice, and that the database
     * references for the secrets don't change.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testUpdateEndpointUpdateSecrets(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(TestConstants.DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Create the endpoint that we will attempt to modify.
        final WebhookProperties properties = new WebhookProperties();
        properties.setBasicAuthentication(new BasicAuthentication("basic-auth-user", "basic-auth-password"));
        properties.setDisableSslVerification(false);
        properties.setMethod(HttpType.GET);
        properties.setSecretToken("my-super-secret-token ");
        properties.setUrl(getMockServerUrl());

        final Endpoint endpoint = new Endpoint();
        endpoint.setDescription("test update endpoint delete basic authentication");
        endpoint.setEnabled(true);
        endpoint.setName("test update endpoint delete basic authentication");
        endpoint.setOrgId(DEFAULT_ORG_ID);
        endpoint.setProperties(properties);
        endpoint.setServerErrors(0);
        endpoint.setStatus(EndpointStatus.PROVISIONING);
        endpoint.setType(EndpointType.WEBHOOK);

        // The below two secrets will be returned when we simulate that
        // we have called sources to create these secrets.
        final Secret mockedBasicAuthenticationSecret = new Secret();
        mockedBasicAuthenticationSecret.id = 25L;

        final Secret mockedSecretTokenSecret = new Secret();
        mockedSecretTokenSecret.id = 50L;

        when(
            this.sourcesServiceMock.create(
                eq(DEFAULT_ORG_ID),
                eq(this.sourcesPsk),
                any()
            )
        ).thenReturn(
            mockedBasicAuthenticationSecret,
            mockedSecretTokenSecret
        );

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);
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
        final long basicAuthReferencePreUpdate = databasePropertiesPreUpdate.getBasicAuthenticationSourcesId();
        final long secretTokenReferencePreUpdate = databasePropertiesPreUpdate.getSecretTokenSourcesId();

        // Now update the endpoint by updating its "basic authentication"
        // and "secret token".
        properties.setBasicAuthentication(new BasicAuthentication("basic-auth-user", "basic-auth-password"));
        properties.setSecretToken("my-super-secret-token");

        // The below two secrets will be returned when we simulate that
        // we have called sources to update these secrets. This should
        // never ever happen in real life, but I want to make sure that our
        // logic doesn't, by mistake, update the references.
        final Secret mockedBasicAuthenticationUpdatedSecret = new Secret();
        mockedBasicAuthenticationSecret.id = 75L;

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
            mockedBasicAuthenticationUpdatedSecret,
            mockedSecretTokenUpdatedSecret
        );

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.EDIT, ResourceType.INTEGRATION, endpointUuid.toString());
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .pathParam("id", endpointUuid)
            .body(Json.encode(this.endpointMapper.toDTO(endpoint)))
            .put("/endpoints/{id}")
            .then()
            .statusCode(HttpStatus.SC_OK);

        // Verify that both secrets were updated in Sources.
        Mockito.verify(this.sourcesServiceMock, Mockito.times(2)).update(
            eq(DEFAULT_ORG_ID),
            eq(this.sourcesPsk),
            Mockito.anyLong(),
            any()
        );

        // Make sure that the references to the secrets didn't get updated
        // in the database.
        final Endpoint databaseEndpoint = this.endpointRepository.getEndpoint(DEFAULT_ORG_ID, endpointUuid);
        final WebhookProperties webhookProperties = databaseEndpoint.getProperties(WebhookProperties.class);
        final long basicAuthReferenceAfterUpdate = webhookProperties.getBasicAuthenticationSourcesId();
        final long secretTokenReferenceAfterUpdate = webhookProperties.getSecretTokenSourcesId();

        Assertions.assertEquals(basicAuthReferencePreUpdate, basicAuthReferenceAfterUpdate, "the ID of the basic authentication's secret got updated after updating the secret in Sources, which should never happen");
        Assertions.assertEquals(secretTokenReferencePreUpdate, secretTokenReferenceAfterUpdate, "the ID of the secret token's secret got updated after updating the secret in Sources, which should never happen");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void testAnsibleEndpointCRUD(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

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
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);
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
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.EDIT, ResourceType.INTEGRATION, jsonEndpoint.getString("id"));

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
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.DELETE, ResourceType.INTEGRATION, jsonEndpoint.getString("id"));

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
    @ValueSource(booleans = {false, true})
    void addPagerDutyEndpoint(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

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

        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);
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
            this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, IntegrationPermission.DELETE, ResourceType.INTEGRATION, id);
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
    @ValueSource(booleans = {false, true})
    void testSourcesSecretsDeletedWhenEndpointCreationFails(final boolean isKesselEnabled) {
        this.kesselTestHelper.mockKesselRelations(isKesselEnabled);

        WebhookProperties properties = new WebhookProperties();
        properties.setBasicAuthentication(new BasicAuthentication("username", "password"));
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
        final Secret basicAuthSecret = new Secret();
        basicAuthSecret.id = new Random().nextLong(1, Long.MAX_VALUE);
        basicAuthSecret.authenticationType = Secret.TYPE_BASIC_AUTH;
        basicAuthSecret.password = properties.getBasicAuthentication().getPassword();
        basicAuthSecret.username = properties.getBasicAuthentication().getUsername();

        final Secret bearerAuthentication = new Secret();
        bearerAuthentication.id = new Random().nextLong(1, Long.MAX_VALUE);
        bearerAuthentication.authenticationType = Secret.TYPE_BEARER_AUTHENTICATION;
        bearerAuthentication.password = properties.getBearerAuthentication();

        final Secret secretToken = new Secret();
        secretToken.id = new Random().nextLong(1, Long.MAX_VALUE);
        secretToken.authenticationType = Secret.TYPE_SECRET_TOKEN;
        secretToken.password = properties.getSecretToken();

        Mockito.when(this.sourcesServiceMock.create(Mockito.anyString(), Mockito.anyString(), Mockito.any())).thenReturn(
            basicAuthSecret,
            bearerAuthentication,
            secretToken
        );

        // Simulate that creating the endpoint in the database fails.
        Mockito.doThrow(new RuntimeException()).when(this.endpointRepository).createEndpoint(Mockito.any());

        // Call the endpoint under test.
        this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_CREATE, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);
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
        Mockito.verify(this.sourcesServiceMock, Mockito.times(3)).create(
            eq(DEFAULT_ORG_ID),
            eq(this.sourcesPsk),
            any()
        );

        // Assert that the basic authentication secret is deleted.
        Mockito.verify(this.sourcesServiceMock, Mockito.times(1)).delete(
            eq(DEFAULT_ORG_ID),
            eq(this.sourcesPsk),
            eq(basicAuthSecret.id)
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
    private Set<UUID> addEndpoints(final int count, final Header identityHeader) {
        final Set<UUID> createdEndpoints = new HashSet<>(count);

        for (int i = 0; i < count; i++) {
            // Add new endpoints
            WebhookProperties properties = new WebhookProperties();
            properties.setMethod(POST);
            properties.setDisableSslVerification(false);
            properties.setSecretToken("my-super-secret-token");
            properties.setUrl(getMockServerUrl() + "/" + i);

            Endpoint ep = new Endpoint();
            ep.setType(EndpointType.WEBHOOK);
            ep.setName(String.format("Endpoint %d", i));
            ep.setDescription("Try to find me!");
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

            createdEndpoints.add(UUID.fromString(responsePoint.getString("id")));
        }

        return createdEndpoints;
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
        assertEquals(1L, endpointPage.getMeta().getCount());
        assertEquals(1, endpointPage.getData().size());
        assertNull(endpointPage.getData().getFirst().getEventTypesGroupByBundlesAndApplications());
        behaviorGroups = resourceHelpers.findBehaviorGroupsByOrgId(DEFAULT_ORG_ID);
        assertEquals(0, behaviorGroups.size());

        final EventType eventType2 = resourceHelpers.createEventType(application1.getId(), "name1", "event type 2", "description1");
        final EventType eventType3 = resourceHelpers.createEventType(application1.getId(), "name2", "event type 3", "description1");

        Set<UUID> eventsIdsToLink = Set.of(eventType1.getId(), eventType2.getId(), eventType3.getId());
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(eventsIdsToLink))
            .pathParam("endpointUuid", endpoint.getId())
            .put("/endpoints/{endpointUuid}/eventTypes")
            .then()
            .statusCode(204);

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

    /**
     * Tests the logic path from {@link com.redhat.cloud.notifications.auth.kessel.KesselAuthorization#isPrincipalAuthorizedAndDoesIntegrationExist(SecurityContext, IntegrationPermission, UUID)}.
     * For that, it performs the following tests:
     *
     * <ul>
     *     <li>
     *         Simulates that the principal does not have permission to perform
     *         the different actions, but that the integration exists in our
     *         database. Tests that the response is "forbidden".
     *     </li>
     *     <li>
     *         Simulates that the principal does not have permission to perform
     *         the different actions, and that the integration does not exist
     *         in our database. Since the principal is not given the workspace
     *         permission, it tests that the response is "forbidden".
     *     </li>
     *     <li>
     *         Simulates that the principal does not have permission to perform
     *         the different actions, that the integration does not exist
     *         in our database, but that the principal has the required
     *         workspace permission. Tests that the response is "not found".
     *     </li>
     * </ul>
     */
    @Test
    void testNotFoundResponsesUnknownEndpointId() {
        this.kesselTestHelper.mockKesselRelations(true);

        // Build an identity header for Quarkus.
        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        for (int i = 0; i < 3; i++) {
            final int expectedReturnedStatusCode;
            final UUID endpointId = UUID.randomUUID();

            if (i == 0) {
                // In the first test case the principal is not given any
                // permissions to perform the actions, but we simulate that the
                // integration exists in our database. Therefore, we simulate
                // that the user simply is not authorized.

                Mockito.when(this.endpointRepository.existsByUuidAndOrgId(endpointId, DEFAULT_ORG_ID)).thenReturn(true);

                expectedReturnedStatusCode = HttpStatus.SC_FORBIDDEN;
            } else if (i == 1) {
                // In the second test case the principal is not given any
                // permissions to perform the actions, and we simulate that the
                // integration does not exist in our database. Also, we make
                // sure that the principal does not have the required workspace
                // permission to know that the integration does not exist.

                Mockito.when(this.endpointRepository.existsByUuidAndOrgId(endpointId, DEFAULT_ORG_ID)).thenReturn(false);

                this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_VIEW, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER, CheckResponse.Allowed.ALLOWED_FALSE);

                expectedReturnedStatusCode = HttpStatus.SC_FORBIDDEN;
            } else {
                // In the final test the principal is not given any permissions
                // again, we simulate that the integration does not exist, but
                // we pretend that the principal has the required workspace
                // permission to know that the integration does indeed not
                // exist in our database.

                Mockito.when(this.endpointRepository.existsByUuidAndOrgId(endpointId, DEFAULT_ORG_ID)).thenReturn(false);

                this.kesselTestHelper.mockKesselPermission(DEFAULT_USER, WorkspacePermission.INTEGRATIONS_VIEW, ResourceType.WORKSPACE, WORKSPACE_ID_PLACEHOLDER);

                expectedReturnedStatusCode = HttpStatus.SC_NOT_FOUND;
            }

            try {
                RestAssured.basePath = TestConstants.API_INTEGRATIONS_V_2_0;

                // Call the notifications history endpoint in the V2 path.
                given()
                    .header(identityHeader)
                    .when()
                    .contentType(JSON)
                    .pathParam("endpointId", endpointId)
                    .get("/endpoints/{endpointId}/history")
                    .then()
                    .statusCode(expectedReturnedStatusCode);
            } finally {
                RestAssured.basePath = TestConstants.API_INTEGRATIONS_V_1_0;
            }

            // Call the notifications history endpoint in the V1 path.
            given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .pathParam("endpointId", endpointId)
                .get("/endpoints/{endpointId}/history")
                .then()
                .statusCode(expectedReturnedStatusCode);

            try {
                RestAssured.basePath = TestConstants.API_INTEGRATIONS_V_2_0;

                // Call the "get endpoint" endpoint in the V2 path.
                given()
                    .header(identityHeader)
                    .when()
                    .contentType(JSON)
                    .pathParam("endpointId", endpointId)
                    .get("/endpoints/{endpointId}")
                    .then()
                    .statusCode(expectedReturnedStatusCode);
            } finally {
                RestAssured.basePath = TestConstants.API_INTEGRATIONS_V_1_0;
            }
            // Get an endpoint.
            given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .pathParam("endpointId", endpointId)
                .get("/endpoints/{endpointId}")
                .then()
                .statusCode(expectedReturnedStatusCode);

            // Delete an endpoint.
            given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .pathParam("endpointId", endpointId)
                .delete("/endpoints/{endpointId}")
                .then()
                .statusCode(expectedReturnedStatusCode);

            // Enable an endpoint.
            given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .pathParam("endpointId", endpointId)
                .put("/endpoints/{endpointId}/enable")
                .then()
                .statusCode(expectedReturnedStatusCode);

            // Disable an endpoint.
            given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .pathParam("endpointId", endpointId)
                .delete("/endpoints/{endpointId}/enable")
                .then()
                .statusCode(expectedReturnedStatusCode);

            // Update an endpoint. Create an endpoint so that we can avoid
            // receiving a "bad request" response for not including a body.
            final Endpoint createdEndpoint = this.resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, EndpointType.WEBHOOK);
            given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(this.endpointMapper.toDTO(createdEndpoint)))
                .pathParam("endpointId", endpointId)
                .delete("/endpoints/{endpointId}/enable")
                .then()
                .statusCode(expectedReturnedStatusCode);

            // Get the history details.
            given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .pathParam("endpointId", endpointId)
                .pathParam("historyId", UUID.randomUUID())
                .get("/endpoints/{endpointId}/history/{historyId}/details")
                .then()
                .statusCode(expectedReturnedStatusCode);

            // Test an endpoint.
            given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .pathParam("endpointId", endpointId)
                .post("/endpoints/{endpointId}/test")
                .then()
                .statusCode(expectedReturnedStatusCode);

            // Delete an event type.
            given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .pathParam("endpointId", endpointId)
                .pathParam("eventTypeId", UUID.randomUUID())
                .delete("/endpoints/{endpointId}/eventType/{eventTypeId}")
                .then()
                .statusCode(expectedReturnedStatusCode);

            // Add a link between an endpoint and an event type.
            given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .pathParam("endpointId", endpointId)
                .pathParam("eventTypeId", UUID.randomUUID())
                .put("/endpoints/{endpointId}/eventType/{eventTypeId}")
                .then()
                .statusCode(expectedReturnedStatusCode);

            // Update links between an endpoint and event types.
            given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .pathParam("endpointId", endpointId)
                .put("/endpoints/{endpointId}/eventTypes")
                .then()
                .statusCode(expectedReturnedStatusCode);
        }
    }
}
