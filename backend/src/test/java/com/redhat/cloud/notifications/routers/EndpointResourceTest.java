package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.model.Stats;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.BasicAuthentication;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointStatus;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.SourcesSecretable;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointDTO;
import com.redhat.cloud.notifications.models.mappers.v1.endpoint.EndpointMapper;
import com.redhat.cloud.notifications.models.validation.ValidNonPrivateUrlValidator;
import com.redhat.cloud.notifications.models.validation.ValidNonPrivateUrlValidatorTest;
import com.redhat.cloud.notifications.routers.endpoints.EndpointTestRequest;
import com.redhat.cloud.notifications.routers.endpoints.InternalEndpointTestRequest;
import com.redhat.cloud.notifications.routers.engine.EndpointTestService;
import com.redhat.cloud.notifications.routers.models.EndpointPage;
import com.redhat.cloud.notifications.routers.models.RequestSystemSubscriptionProperties;
import com.redhat.cloud.notifications.routers.sources.Secret;
import com.redhat.cloud.notifications.routers.sources.SourcesService;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.configuration.ProfileManager;
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
import jakarta.ws.rs.core.UriBuilder;
import org.apache.http.HttpStatus;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.net.URI;
import java.util.ArrayList;
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

    @Test
    void testEndpointAdding() {
        String accountId = "empty";
        String orgId = "empty";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Test empty tenant
        given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints")
                .then()
                .statusCode(200) // TODO Maybe 204 here instead?
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
                .statusCode(200)
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
                .statusCode(200)
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
                        .statusCode(204)
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
                .statusCode(200);

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
                        .statusCode(204)
                        .extract().body().asString();
        assertEquals(0, body.length());

        // Fetch single
        given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints/" + responsePoint.getString("id"))
                .then()
                .statusCode(404)
                .contentType(TEXT);

        // Fetch all, nothing should be left
        given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body(is("{\"data\":[],\"links\":{},\"meta\":{\"count\":0}}"));
    }

    @Test
    void testRepeatedEndpointName() {
        when(backendConfig.isUniqueIntegrationNameEnabled()).thenReturn(true);
        String orgId = "repeatEndpoint";
        String userName = "user";

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(orgId, orgId, userName);
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
                .statusCode(200)
                .extract().response();

        String endpoint1Id = new JsonObject(response.getBody().asString()).getString("id");
        assertNotNull(endpoint1Id);

        // Trying to add the same endpoint name again results in a 400 error
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(endpoint1))
                .post("/endpoints")
                .then()
                .statusCode(400);

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
                .statusCode(200);

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
                .statusCode(400);

        // Updating endpoint1 name is possible
        endpoint1.setName("Endpoint1-updated");
        given()
                .header(identityHeader)
                .contentType(JSON)
                .body(Json.encode(endpoint1))
                .when()
                .put("/endpoints/" + endpoint1Id)
                .then()
                .statusCode(200);

        // Updating to the name of an already existing endpoint is not possible
        endpoint1.setName("Endpoint2");
        given()
                .header(identityHeader)
                .contentType(JSON)
                .body(Json.encode(endpoint1))
                .when()
                .put("/endpoints/" + endpoint1Id)
                .then()
                .statusCode(400);
    }

    private JsonObject fetchSingle(String id, Header identityHeader) {
        Response response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints/" + id)
                .then()
                .statusCode(200)
                .contentType(JSON)
                .body("id", equalTo(id))
                .extract().response();

        JsonObject endpoint = new JsonObject(response.getBody().asString());
        endpoint.mapTo(EndpointDTO.class);
        return endpoint;
    }

    @Test
    void testEndpointValidation() {
        String accountId = "validation";
        String orgId = "validation2";
        String userName = "testEndpointValidation";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Add new endpoint without properties
        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("endpoint with missing properties");
        ep.setDescription("Destined to fail");
        ep.setEnabled(true);

        expectReturn400(identityHeader, ep);

        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(POST);
        properties.setDisableSslVerification(false);
        properties.setSecretToken("my-super-secret-token");
        properties.setUrl(getMockServerUrl());

        // Test with properties, but without endpoint type
        ep.setProperties(properties);
        ep.setType(null);

        expectReturn400(identityHeader, ep);

        // Test with incorrect webhook properties
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("endpoint with incorrect webhook properties");
        properties.setMethod(null);
        expectReturn400(identityHeader, ep);

        // Type and attributes don't match
        properties.setMethod(POST);
        ep.setType(EndpointType.EMAIL_SUBSCRIPTION);
        expectReturn400(identityHeader, ep);

        ep.setType(EndpointType.DRAWER);
        expectReturn400(identityHeader, ep);

        ep.setName("endpoint with subtype too long");
        ep.setType(EndpointType.CAMEL);
        ep.setSubType("something-longer-than-20-chars");
        expectReturn400(identityHeader, ep);
    }

    private void expectReturn400(Header identityHeader, Endpoint ep) {
        given()
                 .header(identityHeader)
                 .when()
                 .contentType(JSON)
                 .body(Json.encode(ep))
                 .post("/endpoints")
                 .then()
                 .statusCode(400);
    }

    @Test
    void addCamelEndpoint() {

        String accountId = "empty";
        String orgId = "empty";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
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

        String responseBody = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(this.endpointMapper.toDTO(ep)))
                .post("/endpoints")
                .then()
                .statusCode(200)
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

            given()
                    .header(identityHeader)
                    .when().delete("/endpoints/" + id)
                    .then()
                    .statusCode(204)
                    .extract().body().asString();
        }
    }

    @Test
    void addBogusCamelEndpoint() {

        String accountId = "empty";
        String orgId = "empty";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
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

        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(400);

        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(400);

    }

    @Test
    void testForbidSlackChannelUsage() {

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("account-id", "org-id", "user");
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
            .statusCode(400)
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
            .statusCode(200)
            .extract().asString();

        final JsonObject jsonResponse = new JsonObject(createdEndpoint);
        final String endpointUuidRaw = jsonResponse.getString("id");

        // try to update endpoint without channel
        given()
            .header(identityHeader)
            .contentType(JSON)
            .pathParam("id", endpointUuidRaw)
            .body(Json.encode(this.endpointMapper.toDTO(endpoint)))
            .when()
            .put("/endpoints/{id}")
            .then()
            .statusCode(200);

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
            .statusCode(400);

        // test create slack integration without extras object
        camelProperties.setExtras(null);
        endpoint.setProperties(camelProperties);
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(this.endpointMapper.toDTO(endpoint)))
            .post("/endpoints")
            .then()
            .statusCode(200)
            .extract().asString();
    }

    @Test
    void testRequireHttpsSchemeServiceNow() {
        URI sNowUri = URI.create("http://webhook.site/3074bfbb-366c-4f54-8513-b66a483985aa");
        testRequireHttpsScheme("servicenow", sNowUri);
    }

    @Test
    void testRequireHttpsSchemeSplunk() {
        URI splunkUri = URI.create("http://webhook.site/20832c6c-6493-487d-bbf5-d4f8cbfd1fc6");
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
                .statusCode(400)
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
                .statusCode(200)
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
                .statusCode(200)
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
                .statusCode(400)
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
                .statusCode(200);
    }

    @Test
    void addSlackEndpoint() {

        String accountId = "empty";
        String orgId = "empty";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
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
                .statusCode(200)
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
                    .statusCode(200)
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
                    .statusCode(204);

            given()
                    .header(identityHeader)
                    .when().get("/endpoints/" + id)
                    .then().statusCode(404);
        }
    }

    @Test
    void testEndpointUpdates() {
        String accountId = "updates";
        String orgId = "updates2";
        String userName = "testEndpointUpdates";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Test empty tenant
        given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints")
                .then()
                .statusCode(200) // TODO Maybe 204 here instead?
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
                .statusCode(200)
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
                .statusCode(200)
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
                .statusCode(400);

        // With payload
        given()
                .header(identityHeader)
                .contentType(JSON)
                .when()
                .body(responsePointSingle.encode())
                .put(String.format("/endpoints/%s", responsePointSingle.getString("id")))
                .then()
                .statusCode(200);

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
                Arguments.of(Set.of(EndpointType.WEBHOOK)),
                Arguments.of(Set.of(EndpointType.CAMEL)),
                        Arguments.of(Set.of(EndpointType.WEBHOOK, EndpointType.CAMEL))
        );
    }

    @ParameterizedTest
    @MethodSource
    void testEndpointTypeQuery(Set<EndpointType> types) {
        String accountId = "limiter";
        String orgId = "limiter2";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
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

        Response response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(this.endpointMapper.toDTO(ep)))
                .post("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        JsonObject responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(EndpointDTO.class);
        assertNotNull(responsePoint.getString("id"));

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
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(EndpointDTO.class);
        assertNotNull(responsePoint.getString("id"));

        // Fetch the list to ensure everything was inserted correctly.
        response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints")
                .then()
                .statusCode(200)
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
                .statusCode(200)
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

    @Test
    void testEndpointLimiter() {
        String accountId = "limiter";
        String orgId = "limiter2";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);
        addEndpoints(29, identityHeader);

        // Fetch the list, page 1
        Response response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .queryParam("limit", "10")
                .queryParam("offset", "0")
                .when().get("/endpoints")
                .then()
                .statusCode(200)
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
                .statusCode(200)
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

    @Test
    void testSortingOrder() {
        String accountId = "testSortingOrder";
        String orgId = "testSortingOrder2";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Create 50 test-ones with sanely sortable name & enabled & disabled & type
        final Stats stats = helpers.createTestEndpoints(accountId, orgId, 50);

        Response response = given()
                .header(identityHeader)
                .when()
                .get("/endpoints?limit=100")
                .then()
                .statusCode(200)
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
                .statusCode(200)
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
                .statusCode(200)
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
                .statusCode(400);

    }

    @Test
    void testWebhookAttributes() {
        String accountId = "testWebhookAttributes";
        String orgId = "testWebhookAttributes2";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
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

        Response response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(this.endpointMapper.toDTO(ep)))
                .post("/endpoints")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        JsonObject responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(EndpointDTO.class);
        assertNotNull(responsePoint.getString("id"));

        // Fetch single endpoint also and verify
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

    @Test
    void testAddEndpointEmailSubscription() {
        String accountId = "adding-email-subscription";
        String orgId = "adding-email-subscription2";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
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

        String stringResponse = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(this.endpointMapper.toDTO(ep)))
                .post("/endpoints")
                .then()
                .statusCode(400)
                .extract().asString();

        assertSystemEndpointTypeError(stringResponse, EndpointType.EMAIL_SUBSCRIPTION);

        RequestSystemSubscriptionProperties requestProps = new RequestSystemSubscriptionProperties();

        // EmailSubscription can be fetch from the properties
        Response response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(requestProps))
                .post("/endpoints/system/email_subscription")
                .then()
                .statusCode(200)
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
                .statusCode(200)
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
                .statusCode(200)
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
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(EndpointDTO.class);
        assertTrue(endpointIds.contains(responsePoint.getString("id")));

        // It is not possible to delete it
        stringResponse = given()
                .header(identityHeader)
                .when().delete("/endpoints/" + defaultEndpointId)
                .then()
                .statusCode(400)
                .extract().asString();
        assertSystemEndpointTypeError(stringResponse, EndpointType.EMAIL_SUBSCRIPTION);

        // It is not possible to disable or enable it
        stringResponse = given()
                .header(identityHeader)
                .when().delete("/endpoints/" + defaultEndpointId + "/enable")
                .then()
                .statusCode(400)
                .extract().asString();
        assertSystemEndpointTypeError(stringResponse, EndpointType.EMAIL_SUBSCRIPTION);

        stringResponse = given()
                .header(identityHeader)
                .when().put("/endpoints/" + defaultEndpointId + "/enable")
                .then()
                .statusCode(400)
                .extract().asString();
        assertSystemEndpointTypeError(stringResponse, EndpointType.EMAIL_SUBSCRIPTION);

        // It is not possible to update it
        stringResponse = given()
                .header(identityHeader)
                .contentType(JSON)
                .body(Json.encode(ep))
                .when().put("/endpoints/" + defaultEndpointId)
                .then()
                .statusCode(400)
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
                .statusCode(400)
                .extract().asString();
        assertSystemEndpointTypeError(stringResponse, EndpointType.EMAIL_SUBSCRIPTION);
    }

    @Test
    void testAddEndpointDrawerSubscription() {
        String accountId = "adding-drawer-subscription";
        String orgId = "adding-drawer-subscription2";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
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

        String stringResponse = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(ep))
            .post("/endpoints")
            .then()
            .statusCode(400)
            .extract().asString();

        assertSystemEndpointTypeError(stringResponse, EndpointType.DRAWER);

        RequestSystemSubscriptionProperties requestProps = new RequestSystemSubscriptionProperties();

        // Drawer endpoints can be created from the dedicated endpoint
        Response response = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(requestProps))
            .post("/endpoints/system/drawer_subscription")
            .then()
            .statusCode(200)
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
            .statusCode(200)
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
            .statusCode(200)
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
            .statusCode(200)
            .contentType(JSON)
            .extract().response();

        responsePoint = new JsonObject(response.getBody().asString());
        responsePoint.mapTo(EndpointDTO.class);
        assertTrue(endpointIds.contains(responsePoint.getString("id")));

        // It is not possible to delete it
        stringResponse = given()
            .header(identityHeader)
            .when().delete("/endpoints/" + defaultEndpointId)
            .then()
            .statusCode(400)
            .extract().asString();
        assertSystemEndpointTypeError(stringResponse, EndpointType.DRAWER);

        // It is not possible to disable or enable it
        stringResponse = given()
            .header(identityHeader)
            .when().delete("/endpoints/" + defaultEndpointId + "/enable")
            .then()
            .statusCode(400)
            .extract().asString();
        assertSystemEndpointTypeError(stringResponse, EndpointType.DRAWER);

        stringResponse = given()
            .header(identityHeader)
            .when().put("/endpoints/" + defaultEndpointId + "/enable")
            .then()
            .statusCode(400)
            .extract().asString();
        assertSystemEndpointTypeError(stringResponse, EndpointType.DRAWER);

        // It is not possible to update it
        stringResponse = given()
            .header(identityHeader)
            .contentType(JSON)
            .body(Json.encode(ep))
            .when().put("/endpoints/" + defaultEndpointId)
            .then()
            .statusCode(400)
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
            .statusCode(400)
            .extract().asString();
        assertSystemEndpointTypeError(stringResponse, EndpointType.DRAWER);
    }

    @Test
    void testAddEndpointEmailSubscriptionRbac() {
        String accountId = "adding-email-subscription";
        String orgId = "adding-email-subscription2";
        String userName = "user";
        String validGroupId = "f85517d0-063b-4eed-a501-e79ffc1f5ad3";
        String unknownGroupId = "f44f50d5-acab-482c-a3cf-087faf2c709c";

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);
        MockServerConfig.addGroupResponse(identityHeaderValue, validGroupId, 200);
        MockServerConfig.addGroupResponse(identityHeaderValue, unknownGroupId, 404);

        // valid group id
        RequestSystemSubscriptionProperties requestProps = new RequestSystemSubscriptionProperties();
        requestProps.setGroupId(UUID.fromString(validGroupId));

        Response response = given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(requestProps))
                .post("/endpoints/system/email_subscription")
                .then()
                .statusCode(200)
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
                .statusCode(200)
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
                .statusCode(400)
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
                .statusCode(400)
                .contentType(JSON)
                .extract().response();
    }

    @Test
    void testUnknownEndpointTypes() {
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("test-tenant", "test-orgid", "test-user");
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        given()
                .header(identityHeader)
                .queryParam("type", "foo")
                .when().get("/endpoints")
                .then()
                .statusCode(400)
                .body(is("Unknown endpoint type: [foo]"));

        given()
                .header(identityHeader)
                .queryParam("type", EndpointType.WEBHOOK.toString())
                .queryParam("type", "bar")
                .when().get("/endpoints")
                .then()
                .statusCode(400)
                .body(is("Unknown endpoint type: [bar]"));
    }

    @Test
    void testConnectionCount() {
        String accountId = "count";
        String orgId = "count2";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Test empty tenant
        given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .when().get("/endpoints")
                .then()
                .statusCode(200) // TODO Maybe 204 here instead?
                .contentType(JSON)
                .body(is("{\"data\":[],\"links\":{},\"meta\":{\"count\":0}}"));

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
                    .statusCode(200)
                    .contentType(JSON)
                    .extract().response();

            JsonObject responsePoint = new JsonObject(response.getBody().asString());
            responsePoint.mapTo(EndpointDTO.class);
            assertNotNull(responsePoint.getString("id"));

            // Fetch the list
            given()
                    // Set header to x-rh-identity
                    .header(identityHeader)
                    .when().get("/endpoints")
                    .then()
                    .statusCode(200)
                    .contentType(JSON)
                    .extract().response();
        }
    }

    @Test
    void testActive() {
        String orgId = "queries-without-type";
        String username = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(TestConstants.DEFAULT_ACCOUNT_ID, orgId, username);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        final Stats stats = resourceHelpers.createTestEndpoints(TestConstants.DEFAULT_ACCOUNT_ID, orgId, 11);

        // Get all endpoints
        Response response = given()
                .header(identityHeader)
                .when()
                .get("/endpoints")
                .then()
                .statusCode(200)
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
                .statusCode(200)
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
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        assertEquals(stats.getDisabledCount(), endpointPage.getMeta().getCount());
        assertEquals(stats.getDisabledCount(), endpointPage.getData().size());

    }

    @Test
    void testSearch() {
        String accountId = "search";
        String orgId = "search2";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
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
                .statusCode(200)
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
                .statusCode(200)
                .contentType(JSON)
                .extract().response();

        endpointPage = Json.decodeValue(response.getBody().asString(), EndpointPage.class);
        assertEquals(0, endpointPage.getMeta().getCount());
        assertEquals(0, endpointPage.getData().size());
    }

    @Test
    void testSearchWithType() {
        String accountId = "search-type";
        String orgId = "search-type2";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, userName);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        addEndpoints(10, identityHeader);
        Response response = given()
                // Set header to x-rh-identity
                .header(identityHeader)
                .queryParam("limit", "20")
                .queryParam("offset", "0")
                .queryParam("name", "2")
                .queryParam("type", "WEBHOOK")
                .when().get("/endpoints")
                .then()
                .statusCode(200)
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
                .statusCode(200)
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
    @Test
    void testEndpointInvalidUrls() {
        // Set up the RBAC access for the test.
        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo("endpoint-invalid-urls", "endpoint-invalid-urls", "user");
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
            ProfileManager.setLaunchMode(LaunchMode.NORMAL);
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
                            .statusCode(400)
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
                            .statusCode(400)
                            .extract()
                            .asString();

                    final String webhookConstraintViolation = TestHelpers.extractConstraintViolationFromResponse(webhookResponse);

                    Assertions.assertEquals(testCase.expectedErrorMessage, webhookConstraintViolation, String.format("unexpected constraint violation for url \"%s\"", url));
                }
            }
        } finally {
            ProfileManager.setLaunchMode(LaunchMode.TEST);
        }
    }

    /**
     * Tests that when a valid URL is provided via the endpoint's properties, regardless if those properties are
     * {@link CamelProperties} or {@link WebhookProperties}, no constraint violations are raised.
     */
    @Test
    void testEndpointValidUrls() {
        // Set up the RBAC access for the test.
        final String orgId = "endpoint-invalid-urls";
        final String userName = "user";

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(orgId, orgId, userName);
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Set up the fixture data.
        final var disableSslVerification = false;
        final HttpType method = POST;
        final String password = "endpoint-invalid-urls-basic-authentication-password";
        final String username = "endpoint-invalid-urls-basic-authentication-username";
        final String secretToken = "endpoint-invalid-urls-secret-token";

        // Create the properties for the endpoint. Leave the URL so that we can set it afterwards.
        final var camelProperties = new CamelProperties();
        camelProperties.setBasicAuthentication(new BasicAuthentication(username, password));
        camelProperties.setDisableSslVerification(disableSslVerification);
        camelProperties.setSecretToken(secretToken);

        final var webhookProperties = new WebhookProperties();
        webhookProperties.setBasicAuthentication(new BasicAuthentication(username, password));
        webhookProperties.setDisableSslVerification(disableSslVerification);
        webhookProperties.setMethod(method);
        webhookProperties.setSecretToken(secretToken);

        // Create an endpoint without the type and the properties set.
        final var name = "endpoint-invalid-urls-name";
        final var description = "endpoint-invalid-urls-description";
        final var enabled = true;
        final var serverErrors = 0;
        final var subType = "slack";

        final var endpoint = new Endpoint();
        endpoint.setDescription(description);
        endpoint.setEnabled(enabled);
        endpoint.setName(name);
        endpoint.setServerErrors(serverErrors);

        // Mock the Sources service calls. In this test we don't assert for the
        // secrets' values, so we can simply return the same secret over and
        // over.
        final Secret secret = new Secret();
        secret.id = new Random().nextLong(1, Long.MAX_VALUE);
        secret.password = "test-endpoint-valid-urls-password";
        secret.username = "test-endpoint-valid-urls-username";

        when(this.sourcesServiceMock.create(anyString(), anyString(), any()))
            .thenReturn(secret);

        for (final var url : ValidNonPrivateUrlValidatorTest.validUrls) {
            // Test with a camel endpoint.
            camelProperties.setUrl(url);
            endpoint.setType(EndpointType.CAMEL);
            endpoint.setProperties(camelProperties);
            endpoint.setSubType(subType);

            EndpointDTO dto = this.endpointMapper.toDTO(endpoint);

            given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(dto))
                .post("/endpoints")
                .then()
                .statusCode(200);

            // Test with a webhook endpoint.
            webhookProperties.setUrl(url);
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
                .statusCode(200);
        }
    }

    /**
     * Test that endpoint.sub_type is only allowed when it's required.
     * If it's not required, then it should be rejected.
     */
    @Test
    public void testEndpointSubtypeIsOnlyAllowedWhenRequired() {
        String EMPTY = "empty";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(EMPTY, EMPTY, userName);
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
                .statusCode(400);

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
                .statusCode(400);
    }

    /**
     * Tests that when sending a payload to the "/test" REST endpoint, a Kafka message is sent with a test event for
     * that endpoint.
     */
    @Test
    void testEndpointTest() {
        final String accountId = "test-endpoint-test-account-number";
        final String orgId = "test-endpoint-test-org-id";

        final Endpoint createdEndpoint = this.resourceHelpers.createEndpoint(accountId, orgId, EndpointType.CAMEL);

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, "user-name");
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
            .statusCode(204);

        // Capture the sent payload to verify it.
        final ArgumentCaptor<InternalEndpointTestRequest> capturedPayload = ArgumentCaptor.forClass(InternalEndpointTestRequest.class);
        Mockito.verify(this.endpointTestService).testEndpoint(capturedPayload.capture());

        final InternalEndpointTestRequest sentPayload = capturedPayload.getValue();

        Assertions.assertEquals(createdEndpoint.getId(), sentPayload.endpointUuid, "the sent endpoint UUID in the payload doesn't match the one from the fixture");
        Assertions.assertEquals(orgId, sentPayload.orgId, "the sent org id in the payload doesn't match the one from the fixture");
        assertNull(sentPayload.message, "the sent message should be null since no custom message was specified");
    }

    /**
     * Tests that when the "test endpoint" handler is called with an endpoint
     * UUID that doesn't exist, a not found response is returned.
     */
    @Test
    void testEndpointTestNotFound() {
        final String accountId = "test-endpoint-test-account-number";
        final String orgId = "test-endpoint-test-org-id";

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, "user-name");
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        // Call the endpoint under test.
        final String path = String.format("/endpoints/%s/test", UUID.randomUUID());

        final String responseBody = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .post(path)
            .then()
            .statusCode(404)
            .extract()
            .body()
            .asString();

        Assertions.assertEquals("integration not found", responseBody, "unexpected not found error message returned");
    }

    /**
     * Tests that when a user specifies a custom message, then it gets properly
     * sent to the engine.
     */
    @Test
    void testEndpointTestCustomMessage() {
        final String accountId = "test-endpoint-test-account-number";
        final String orgId = "test-endpoint-test-org-id";

        final Endpoint createdEndpoint = this.resourceHelpers.createEndpoint(accountId, orgId, EndpointType.CAMEL);

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, "user-name");
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        final String customTestMessage = "Hello, World!";
        final EndpointTestRequest endpointTestRequest = new EndpointTestRequest();
        endpointTestRequest.message = customTestMessage;

        // Call the endpoint under test.
        final String path = String.format("/endpoints/%s/test", createdEndpoint.getId());
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(endpointTestRequest))
            .post(path)
            .then()
            .statusCode(204);

        // Capture the sent payload to verify it.
        final ArgumentCaptor<InternalEndpointTestRequest> capturedPayload = ArgumentCaptor.forClass(InternalEndpointTestRequest.class);
        Mockito.verify(this.endpointTestService).testEndpoint(capturedPayload.capture());

        final InternalEndpointTestRequest sentPayload = capturedPayload.getValue();

        Assertions.assertEquals(createdEndpoint.getId(), sentPayload.endpointUuid, "the sent endpoint UUID in the payload doesn't match the one from the fixture");
        Assertions.assertEquals(orgId, sentPayload.orgId, "the sent org id in the payload doesn't match the one from the fixture");
        Assertions.assertEquals(customTestMessage, sentPayload.message, "the sent message does not match the one from the fixture");
    }

    /**
     * Tests that when a user specifies a blank custom message, then a bad
     * request response is returned.
     */
    @Test
    void testEndpointTestBlankMessageReturnsBadRequest() {
        final String accountId = "test-endpoint-test-account-number";
        final String orgId = "test-endpoint-test-org-id";

        final Endpoint createdEndpoint = this.resourceHelpers.createEndpoint(accountId, orgId, EndpointType.CAMEL);

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, "user-name");
        final Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);

        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        final String blankTestMessage = "";
        final EndpointTestRequest endpointTestRequest = new EndpointTestRequest();
        endpointTestRequest.message = blankTestMessage;

        // Call the endpoint under test.
        final String path = String.format("/endpoints/%s/test", createdEndpoint.getId());
        final String rawResponse = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(endpointTestRequest))
            .post(path)
            .then()
            .statusCode(400)
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
     * Tests that when an endpoint gets updated, if it didn't have any secrets
     * and the user provides new ones, then Sources gets called and the
     * references to those secrets are stored in the database.
    */
    @Test
    public void testUpdateEndpointCreateSecrets() {
        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(TestConstants.DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "user");
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

        final String response = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(this.endpointMapper.toDTO(endpoint)))
            .post("/endpoints")
            .then()
            .statusCode(200)
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

        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(this.endpointMapper.toDTO(endpoint)))
            .put(String.format("/endpoints/%s", endpointUuid))
            .then()
            .statusCode(200);

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
    @Test
    public void testUpdateEndpointDeleteSecrets() {
        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(TestConstants.DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "user");
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

        final String response = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(this.endpointMapper.toDTO(endpoint)))
            .post("/endpoints")
            .then()
            .statusCode(200)
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

        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(endpoint))
            .put(String.format("/endpoints/%s", endpointUuid))
            .then()
            .statusCode(200);

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
    @Test
    public void testUpdateEndpointUpdateSecrets() {
        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(TestConstants.DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "user");
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

        final String response = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(this.endpointMapper.toDTO(endpoint)))
            .post("/endpoints")
            .then()
            .statusCode(200)
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

        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(this.endpointMapper.toDTO(endpoint)))
            .put(String.format("/endpoints/%s", endpointUuid))
            .then()
            .statusCode(200);

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

    @Test
    void testAnsibleEndpointCRUD() {

        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "username");
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
                .statusCode(200)
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
                .statusCode(200);

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
                .statusCode(204);

        // GET the endpoint and check that it no longer exists.
        given()
                .header(identityHeader)
                .pathParam("id", jsonEndpoint.getString("id"))
                .when()
                .get("/endpoints/{id}")
                .then()
                .statusCode(404);
    }

    /**
     * Test that when an endpoint creation fails, and therefore it doesn't get
     * stored in the database, its created secrets in Sources are cleaned up.
     */
    @Test
    void testSourcesSecretsDeletedWhenEndpointCreationFails() {
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

        final String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(TestConstants.DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, "username");
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

    private void addEndpoints(int count, Header identityHeader) {
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
                    .statusCode(200)
                    .contentType(JSON)
                    .extract().response();

            JsonObject responsePoint = new JsonObject(response.getBody().asString());
            responsePoint.mapTo(EndpointDTO.class);
            assertNotNull(responsePoint.getString("id"));
        }
    }
}
