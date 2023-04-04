package com.redhat.cloud.notifications.routers.sources;

import com.redhat.cloud.notifications.models.BasicAuthentication;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.SourcesSecretable;
import com.redhat.cloud.notifications.models.WebhookProperties;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;

@QuarkusTest
public class SecretUtilsTest {

    static final Long BASIC_AUTH_SOURCES_ID = 50L;
    static final String BASIC_AUTH_PASSWORD = "basic-auth-test-password";
    static final String BASIC_AUTH_USERNAME = "basic-auth-test-username";
    static final String SECRET_TOKEN = "secret-token";
    static final Long SECRET_TOKEN_SOURCES_ID = 100L;

    @InjectMock
    @RestClient
    SourcesService sourcesServiceMock;

    @Inject
    SecretUtils secretUtils;

    /**
     * Required to set up the mock calls to the sources service mock.
     */
    @ConfigProperty(name = "sources.psk")
    String sourcesPsk;

    /**
     * Tests that the underlying "get by id" function gets called two times: one for the basic authentication and
     * another one for the secret token. It also tests that the endpoint's properties hold the secrets from the
     * returned payload from Sources.
     * @throws SourcesException if any unexpected Sources error occurs.
     */
    @Test
    void getSecretsForEndpointTest() throws SourcesException {
        // Create a "Basic Authentication" secret mock.
        final Secret basicAuthenticationMock = new Secret();
        basicAuthenticationMock.password = BASIC_AUTH_PASSWORD;
        basicAuthenticationMock.username = BASIC_AUTH_USERNAME;

        // Create a "secret token" secret mock.
        final Secret secretTokenMock = new Secret();
        secretTokenMock.password = SECRET_TOKEN;

        // Create an endpoint that contains the expected data by the function under test.
        final String orgId = "get-secrets-for-endpoint-test-organization-id";

        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();

        webhookProperties.setBasicAuthenticationSourcesId(BASIC_AUTH_SOURCES_ID);
        webhookProperties.setSecretTokenSourcesId(SECRET_TOKEN_SOURCES_ID);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Set up the mock calls for the "get by id" calls from the REST Client.
        Mockito.when(this.sourcesServiceMock.getById(orgId, this.sourcesPsk, BASIC_AUTH_SOURCES_ID)).thenReturn(basicAuthenticationMock);
        Mockito.when(this.sourcesServiceMock.getById(orgId, this.sourcesPsk, SECRET_TOKEN_SOURCES_ID)).thenReturn(secretTokenMock);

        // Call the function under test.
        this.secretUtils.loadSecretsForEndpoint(endpoint);

        // Check that the endpoint properties are of the expected type.
        final var endpointProperties = endpoint.getProperties();
        if (!(endpointProperties instanceof SourcesSecretable)) {
            Assertions.fail("unexpected type of the properties found. Want " + SourcesSecretable.class + ", got " + endpointProperties.getClass());
        }

        final var props = (SourcesSecretable) endpoint.getProperties();

        // Assert the results.
        final BasicAuthentication basicAuth = props.getBasicAuthentication();
        Assertions.assertEquals(BASIC_AUTH_PASSWORD, basicAuth.getPassword(), "the basic authentication's password field doesn't match");
        Assertions.assertEquals(BASIC_AUTH_USERNAME, basicAuth.getUsername(), "the basic authentication's username field doesn't match");

        final String secretToken = props.getSecretToken();
        Assertions.assertEquals(SECRET_TOKEN, secretToken, "the secret token doesn't match");

        // Assert that the underlying function was called exactly two times, since we are expecting that both the
        // "basic authentication" and the "secret token" secrets were fetched.
        final int wantedNumberOfInvocations = 1;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).getById(orgId, this.sourcesPsk, BASIC_AUTH_SOURCES_ID);
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).getById(orgId, this.sourcesPsk, SECRET_TOKEN_SOURCES_ID);
    }

    /**
     * Tests that when the endpoint's properties doesn't have a Sources ID for the "basic authentication" and "secret
     * token" secrets, the sources service simply doesn't perform any calls.
     */
    @Test
    void getSecretsForEndpointZeroIdsTest() {
        // Create an endpoint that contains the expected data by the function under test.
        final Endpoint endpoint = new Endpoint();
        final WebhookProperties webhookProperties = new WebhookProperties();
        endpoint.setProperties(webhookProperties);

        // Assert that the "get by id" function wasn't called, since the properties didn't have a positive ID.
        Mockito.verifyNoInteractions(this.sourcesServiceMock);
    }

    /**
     * Tests that when the endpoint's properties has the "basic authentication" and the "secret token" properties set,
     * the function under test calls "create" upon Sources two times, and that the returned IDs are properly set on the
     * endpoint's properties.
     * @throws SourcesException if any unexpected Sources error occurs.
     */
    @Test
    void createSecretsForEndpointTest() throws SourcesException {
        // Set the ID for the basic authentication secret that is supposed that is created in Sources.
        final Secret basicAuthenticationMock = new Secret();
        basicAuthenticationMock.id = BASIC_AUTH_SOURCES_ID;

        // Set the ID for the secret token secret that is supposed that is created in Sources.
        final Secret secretTokenMock = new Secret();
        secretTokenMock.id = SECRET_TOKEN_SOURCES_ID;

        // Create an endpoint that contains the expected data by the function under test.
        final String orgId = "create-secrets-for-endpoint-test-organization-id";

        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();
        BasicAuthentication basicAuth = new BasicAuthentication(BASIC_AUTH_USERNAME, BASIC_AUTH_PASSWORD);

        webhookProperties.setBasicAuthentication(basicAuth);
        webhookProperties.setSecretToken(SECRET_TOKEN);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Set up the mock calls for the "create" calls from the REST Client. Make sure we return the basic
        // authentication's ID first, and the secret token's ID second, since we are expecting a successful create
        // operation.
        Mockito.when(this.sourcesServiceMock.create(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.any())).thenReturn(basicAuthenticationMock, secretTokenMock);

        // Call the function under test.
        this.secretUtils.createSecretsForEndpoint(endpoint);

        // Check that the endpoint properties are of the expected type.
        final var endpointProperties = endpoint.getProperties();
        if (!(endpointProperties instanceof SourcesSecretable)) {
            Assertions.fail("unexpected type of the properties found. Want " + SourcesSecretable.class + ", got " + endpointProperties.getClass());
        }

        final var props = (SourcesSecretable) endpoint.getProperties();

        // Assert the results.
        Assertions.assertEquals(BASIC_AUTH_SOURCES_ID, props.getBasicAuthenticationSourcesId(), "the ID of the basic authentication secret from Sources doesn't match");
        Assertions.assertEquals(SECRET_TOKEN_SOURCES_ID, props.getSecretTokenSourcesId(), "the ID of the secret token's secret from Sources doesn't match");

        // Assert that the underlying function was called exactly two times, since we are expecting that both the
        // "basic authentication" and the "secret token" secrets were created.
        final int wantedNumberOfInvocations = 2;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).create(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.any());
    }

    /**
     * Tests that when there is a {@code NULL} "basic authentication" object on the endpoint's properties, the function
     * under test calls "create" upon Sources just for the "secret token" secret, and that the ID for that secret is
     * properly set on the endpoint's properties.
     * @throws SourcesException if any unexpected Sources error occurs.
     */
    @Test
    void createSecretsForEndpointBasicAuthNullTest() throws SourcesException {
        // Set the ID for the secret token secret that is supposed that is created in Sources.
        final Secret secretTokenMock = new Secret();
        secretTokenMock.id = SECRET_TOKEN_SOURCES_ID;

        // Create an endpoint that contains the expected data by the function under test.
        final String orgId = "create-secrets-for-endpoint-basic-auth-null-test-organization-id";

        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setSecretToken(SECRET_TOKEN);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Set up the mock call for the "create" call from the REST Client. In this case, since the basic
        // authentication is null, only the secret token should be created.
        Mockito.when(this.sourcesServiceMock.create(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.any())).thenReturn(secretTokenMock);

        // Call the function under test.
        this.secretUtils.createSecretsForEndpoint(endpoint);

        // Check that the endpoint properties are of the expected type.
        final var endpointProperties = endpoint.getProperties();
        if (!(endpointProperties instanceof SourcesSecretable)) {
            Assertions.fail("unexpected type of the properties found. Want " + SourcesSecretable.class + ", got " + endpointProperties.getClass());
        }

        final var props = (SourcesSecretable) endpoint.getProperties();

        // Assert the results.
        Assertions.assertEquals(SECRET_TOKEN_SOURCES_ID, props.getSecretTokenSourcesId(), "the ID of the secret token's secret from Sources doesn't match");

        // Assert that the underlying function was called exactly one time: just for the "secret token"'s secret
        // creation.
        final int wantedNumberOfInvocations = 1;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).create(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.any());
    }

    /**
     * Tests that when there is a non-{@code NULL} "basic authentication" object with its fields with blank values, the
     * function under test calls "create" upon Sources just for the "secret token" secret, and that the ID for that
     * secret is properly set on the endpoint's properties.
     * @throws SourcesException if any unexpected Sources error occurs.
     */
    @Test
    void createSecretsForEndpointBasicAuthBlankFieldsTest() throws SourcesException {
        // Set the ID for the secret token secret that is supposed that is created in Sources.
        final Secret secretTokenMock = new Secret();
        secretTokenMock.id = SECRET_TOKEN_SOURCES_ID;

        // Create an endpoint that contains the expected data by the function under test.
        final String orgId = "create-secrets-for-endpoint-basic-auth-blank-fields-test-organization-id";

        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setSecretToken(SECRET_TOKEN);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Create a basic authentication with blank fields.
        BasicAuthentication basicAuthentication = new BasicAuthentication("     ", "     ");
        webhookProperties.setBasicAuthentication(basicAuthentication);

        // Set up the mock call for the "create" call from the REST Client. In this case, since the basic
        // authentication is null, only the secret token should be created.
        Mockito.when(this.sourcesServiceMock.create(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.any())).thenReturn(secretTokenMock);

        // Call the function under test.
        this.secretUtils.createSecretsForEndpoint(endpoint);

        // Check that the endpoint properties are of the expected type.
        final var endpointProperties = endpoint.getProperties();
        if (!(endpointProperties instanceof SourcesSecretable)) {
            Assertions.fail("unexpected type of the properties found. Want " + SourcesSecretable.class + ", got " + endpointProperties.getClass());
        }

        final var props = (SourcesSecretable) endpoint.getProperties();

        // Assert the results.
        Assertions.assertEquals(SECRET_TOKEN_SOURCES_ID, props.getSecretTokenSourcesId(), "the ID of the secret token's secret from Sources doesn't match");

        // Assert that the underlying function was called exactly one time: just for the "secret token"'s secret
        // creation.
        final int wantedNumberOfInvocations = 1;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).create(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.any());
    }

    /**
     * Tests that when there is a {@code NULL} "secret token" property on the endpoint's properties, the function under
     * test only calls "create" on Sources just for the "basic authentication", and that the returned ID is properly
     * assigned to the endpoint's properties.
     * @throws SourcesException if any unexpected Sources error occurs.
     */
    @Test
    void createSecretsForEndpointSecretTokenNullTest() throws SourcesException {
        // Set the ID for the basic authentication secret that is supposed that is created in Sources.
        final Secret basicAuthenticationMock = new Secret();
        basicAuthenticationMock.id = BASIC_AUTH_SOURCES_ID;

        // Create an endpoint that contains the expected data by the function under test.
        final String orgId = "create-secrets-for-endpoint-secret-token-null-test-organization-id";

        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();
        BasicAuthentication basicAuth = new BasicAuthentication(BASIC_AUTH_USERNAME, BASIC_AUTH_PASSWORD);

        webhookProperties.setBasicAuthentication(basicAuth);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Set up the mock call for the "create" call from the REST Client. Since only the "basic authentication"
        // secret is supposed to be created, that's the one we are expecting to get from the mocked service.
        Mockito.when(this.sourcesServiceMock.create(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.any())).thenReturn(basicAuthenticationMock);

        // Call the function under test.
        this.secretUtils.createSecretsForEndpoint(endpoint);

        // Check that the endpoint properties are of the expected type.
        final var endpointProperties = endpoint.getProperties();
        if (!(endpointProperties instanceof SourcesSecretable)) {
            Assertions.fail("unexpected type of the properties found. Want " + SourcesSecretable.class + ", got " + endpointProperties.getClass());
        }

        final var props = (SourcesSecretable) endpoint.getProperties();

        // Assert the results.
        Assertions.assertEquals(BASIC_AUTH_SOURCES_ID, props.getBasicAuthenticationSourcesId(), "the ID of the basic authentication's secret from Sources doesn't match");

        // Assert that the underlying function was called exactly one time: just for the "basic authentication"'s
        // secret creation.
        final int wantedNumberOfInvocations = 1;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).create(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.any());
    }

    /**
     * Tests that when there is a non-{@code NULL} "secret token" property which is blank on the endpoint's properties,
     * the function under test only calls "create" on Sources just for the "basic authentication", and that the
     * returned ID is properly assigned to the endpoint's properties.
     * @throws SourcesException if any unexpected Sources error occurs.
     */
    @Test
    void createSecretsForEndpointSecretTokenBlankTest() throws SourcesException {
        // Set the ID for the basic authentication secret that is supposed that is created in Sources.
        final Secret basicAuthenticationMock = new Secret();
        basicAuthenticationMock.id = BASIC_AUTH_SOURCES_ID;

        // Create an endpoint that contains the expected data by the function under test.
        final String orgId = "create-secrets-for-endpoint-secret-token-blank-test-organization-id";

        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();
        BasicAuthentication basicAuth = new BasicAuthentication(BASIC_AUTH_USERNAME, BASIC_AUTH_PASSWORD);

        webhookProperties.setBasicAuthentication(basicAuth);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Set the secret token to blank.
        webhookProperties.setSecretToken("     ");

        // Set up the mock call for the "create" call from the REST Client. Since only the "basic authentication"
        // secret is supposed to be created, that's the one we are expecting to get from the mocked service.
        Mockito.when(this.sourcesServiceMock.create(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.any())).thenReturn(basicAuthenticationMock);

        // Call the function under test.
        this.secretUtils.createSecretsForEndpoint(endpoint);

        // Check that the endpoint properties are of the expected type.
        final var endpointProperties = endpoint.getProperties();
        if (!(endpointProperties instanceof SourcesSecretable)) {
            Assertions.fail("unexpected type of the properties found. Want " + SourcesSecretable.class + ", got " + endpointProperties.getClass());
        }

        final var props = (SourcesSecretable) endpoint.getProperties();

        // Assert the results.
        Assertions.assertEquals(BASIC_AUTH_SOURCES_ID, props.getBasicAuthenticationSourcesId(), "the ID of the basic authentication's secret from Sources doesn't match");

        // Assert that the underlying function was called exactly one time: just for the "basic authentication"'s
        // secret creation.
        final int wantedNumberOfInvocations = 1;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).create(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.any());
    }

    /**
     * Tests that the function under test calls "update" upon Sources two times, one for the "basic authentication" and
     * another one for the "secret token". It also checks that the responses are properly marshalled into the right
     * endpoint properties.
     * @throws SourcesException if any unexpected Sources error occurs.
     */
    @Test
    void updateSecretsForEndpointTest() throws SourcesException {
        // Create a "Basic Authentication" secret mock.
        final Secret basicAuthenticationMock = new Secret();
        basicAuthenticationMock.password = BASIC_AUTH_PASSWORD;
        basicAuthenticationMock.username = BASIC_AUTH_USERNAME;

        // Create a "secret token" secret mock.
        final Secret secretTokenMock = new Secret();
        secretTokenMock.password = SECRET_TOKEN;

        // Create an endpoint that contains the expected data by the function under test.
        final String orgId = "update-secrets-for-endpoint-test-organization-id";

        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();

        // Set the updated fields so that the function under test's logic picks them up for an update.
        final String updatedUsername = String.format("%s-updated", BASIC_AUTH_USERNAME);
        final String updatedPassword = String.format("%s-updated", BASIC_AUTH_PASSWORD);
        final String updatedSecretToken = String.format("%s-updated", SECRET_TOKEN);

        BasicAuthentication basicAuth = new BasicAuthentication(updatedUsername, updatedPassword);
        webhookProperties.setBasicAuthentication(basicAuth);
        webhookProperties.setBasicAuthenticationSourcesId(BASIC_AUTH_SOURCES_ID);

        webhookProperties.setSecretToken(updatedSecretToken);
        webhookProperties.setSecretTokenSourcesId(SECRET_TOKEN_SOURCES_ID);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Set up the mock calls to return the "basic authentication" and the "secret token" secrets which are supposed
        // to be updated.
        Mockito.when(this.sourcesServiceMock.getById(orgId, this.sourcesPsk, BASIC_AUTH_SOURCES_ID)).thenReturn(basicAuthenticationMock);
        Mockito.when(this.sourcesServiceMock.getById(orgId, this.sourcesPsk, SECRET_TOKEN_SOURCES_ID)).thenReturn(secretTokenMock);

        // Call the function under test.
        this.secretUtils.updateSecretsForEndpoint(endpoint);

        // Check that the endpoint properties are of the expected type.
        final var endpointProperties = endpoint.getProperties();
        if (!(endpointProperties instanceof SourcesSecretable)) {
            Assertions.fail("unexpected type of the properties found. Want " + SourcesSecretable.class + ", got " + endpointProperties.getClass());
        }

        final var props = (SourcesSecretable) endpoint.getProperties();

        // Assert the results.
        final BasicAuthentication basicAuthResult = props.getBasicAuthentication();
        Assertions.assertEquals(updatedPassword, basicAuthResult.getPassword(), "the updated basic authentication's password doesn't match");
        Assertions.assertEquals(updatedUsername, basicAuthResult.getUsername(), "the updated basic authentication's username doesn't match");

        final String secretToken = props.getSecretToken();
        Assertions.assertEquals(updatedSecretToken, secretToken, "the updated secret token doesn't match");

        // Assert that the underlying "update" function was called exactly two times, since we are expecting that both
        // the "basic authentication" and the "secret token" secrets were successfully updated.
        final int wantedNumberOfInvocationsUpdate = 1;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocationsUpdate)).update(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.eq(BASIC_AUTH_SOURCES_ID), Mockito.any());
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocationsUpdate)).update(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.eq(SECRET_TOKEN_SOURCES_ID), Mockito.any());
    }

    /**
     * Tests that when the user provides a {@code null} "basic authentication" and "secret token" secret for an
     * endpoint which has secrets stored in Sources, the deletion process for those secrets is triggered.
     * @throws SourcesException if any unexpected Sources error occurs.
     */
    @Test
    void updateSecretsForEndpointDeleteTest() throws SourcesException {
        // Create an endpoint that contains the expected data by the function under test.
        final String orgId = "update-secrets-for-endpoint-delete-test-organization-id";

        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();

        // Simulate that we are sending an empty "basic authentication" and "secret token", but that we have those
        // IDs on the database. This should trigger the deletion of these secrets on Sources.
        webhookProperties.setBasicAuthenticationSourcesId(BASIC_AUTH_SOURCES_ID);
        webhookProperties.setSecretTokenSourcesId(SECRET_TOKEN_SOURCES_ID);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Call the function under test.
        this.secretUtils.updateSecretsForEndpoint(endpoint);

        // The IDs of the properties should be null now.
        Assertions.assertNull(webhookProperties.getBasicAuthenticationSourcesId(), "the basic authentication's Sources ID isn't null");
        Assertions.assertNull(webhookProperties.getSecretTokenSourcesId(), "the secret token's Sources ID isn't null");

        // It should have triggered two "delete" calls to Sources to delete both of the secrets.
        final int wantedNumberOfInvocations = 1;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).delete(orgId, this.sourcesPsk, BASIC_AUTH_SOURCES_ID);
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).delete(orgId, this.sourcesPsk, SECRET_TOKEN_SOURCES_ID);
    }

    /**
     * Tests that when the client updates an endpoint, but there are no secrets stored in Sources, or no valid secrets
     * provided by the client, basically a NOP happens. For a valid secret we understand it as a secret token that is
     * not blank.
     * @throws SourcesException if any unexpected Sources error occurs.
     */
    @Test
    void updateSecretsForEndpointNopTest() throws SourcesException {
        // Create an endpoint that contains the expected data by the function under test.
        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();
        endpoint.setProperties(webhookProperties);

        this.secretUtils.updateSecretsForEndpoint(endpoint);

        Mockito.verifyNoInteractions(this.sourcesServiceMock);

        // Set the basic authentication as having blank values. It should also be a NOOP.
        final BasicAuthentication basicAuthentication = new BasicAuthentication("     ", "     ");
        webhookProperties.setBasicAuthentication(basicAuthentication);
        this.secretUtils.updateSecretsForEndpoint(endpoint);

        Mockito.verifyNoInteractions(this.sourcesServiceMock);

        // Set the secret token as a blank value. It should also be a NOOP.
        webhookProperties.setSecretToken("     ");
        this.secretUtils.updateSecretsForEndpoint(endpoint);

        Mockito.verifyNoInteractions(this.sourcesServiceMock);
    }

    /**
     * Tests that when the client updates an endpoint which has no associated secrets, and the client provides a basic
     * authentication and secret token secrets, then the secrets are created in Sources and that the references are
     * stored in the properties.
     * @throws SourcesException if any unexpected Sources error occurs.
     */
    @Test
    void updateSecretsForEndpointCreateTest() throws SourcesException {
        // Set the ID for the basic authentication secret that is supposed that is created in Sources.
        final Secret basicAuthenticationMock = new Secret();
        basicAuthenticationMock.id = BASIC_AUTH_SOURCES_ID;

        // Set the ID for the secret token secret that is supposed that is created in Sources.
        final Secret secretTokenMock = new Secret();
        secretTokenMock.id = SECRET_TOKEN_SOURCES_ID;

        // Create an endpoint that contains the expected data by the function under test.
        final String orgId = "update-secrets-for-endpoint-create-test-organization-id";

        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();
        BasicAuthentication basicAuth = new BasicAuthentication(BASIC_AUTH_USERNAME, BASIC_AUTH_PASSWORD);

        webhookProperties.setBasicAuthentication(basicAuth);
        webhookProperties.setSecretToken(SECRET_TOKEN);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Set up the mock calls for the "create" calls from the REST Client. Make sure we return the basic
        // authentication's ID first, and the secret token's ID second, since we are expecting a successful create
        // operation.
        Mockito.when(this.sourcesServiceMock.create(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.any())).thenReturn(basicAuthenticationMock, secretTokenMock);

        // Call the function under test.
        this.secretUtils.updateSecretsForEndpoint(endpoint);

        // Check that the endpoint properties are of the expected type.
        final var endpointProperties = endpoint.getProperties();
        if (!(endpointProperties instanceof SourcesSecretable)) {
            Assertions.fail("unexpected type of the properties found. Want " + SourcesSecretable.class + ", got " + endpointProperties.getClass());
        }

        final var props = (WebhookProperties) endpointProperties;

        // Assert the results.
        Assertions.assertEquals(BASIC_AUTH_SOURCES_ID, props.getBasicAuthenticationSourcesId(), "the ID of the basic authentication secret from Sources doesn't match");
        Assertions.assertEquals(SECRET_TOKEN_SOURCES_ID, props.getSecretTokenSourcesId(), "the ID of the secret token's secret from Sources doesn't match");

        // Assert that the underlying function was called exactly two times, since we are expecting that both the
        // "basic authentication" and the "secret token" secrets were created.
        final int wantedNumberOfInvocations = 2;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).create(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.any());
    }

    /**
     * Tests that the "delete" function from the function under test gets called for both the "basic authentication"
     * and "secret token" secrets.
     * @throws SourcesException if any unexpected Sources error occurs.
     */
    @Test
    void deleteSecretsForEndpointTest() throws SourcesException {
        // Create an endpoint that contains the expected data by the function under test.
        final String orgId = "delete-secrets-for-endpoint-test-organization-id";

        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();

        webhookProperties.setBasicAuthenticationSourcesId(BASIC_AUTH_SOURCES_ID);
        webhookProperties.setSecretTokenSourcesId(SECRET_TOKEN_SOURCES_ID);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Call the function under test.
        this.secretUtils.deleteSecretsForEndpoint(endpoint);

        // Assert that the underlying "delete" function was called exactly one time, since we are expecting a successful
        // deletion for both secrets.
        final int wantedNumberOfInvocations = 1;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).delete(orgId, this.sourcesPsk, BASIC_AUTH_SOURCES_ID);
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).delete(orgId, this.sourcesPsk, SECRET_TOKEN_SOURCES_ID);
    }

    /**
     * Tests that when the "basic authentication" and "secret token" don't have associated Sources IDs, the "delete"
     * function from the function under test doesn't get called.
     * @throws SourcesException if any unexpected Sources error occurs.
     */
    @Test
    void deleteSecretsForEndpointZeroIdsTest() throws SourcesException {
        // Create an endpoint that contains the expected data by the function under test.
        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();
        endpoint.setProperties(webhookProperties);

        // Call the function under test.
        this.secretUtils.deleteSecretsForEndpoint(endpoint);

        // Assert that the underlying "delete" function wasn't called at all, since neither the "basic authentication"
        // nor the "secret token" have valid IDs set.
        Mockito.verifyNoInteractions(this.sourcesServiceMock);
    }

    /**
     * Tests that when a valid "basic authentication" object is passed, the function returns that it isn't blank.
     */
    @Test
    void isBasicAuthNullOrBlankTest() {
        final var basicAuth = new BasicAuthentication("username", "password");

        Assertions.assertFalse(this.secretUtils.isBasicAuthNullOrBlank(basicAuth), "the basic authentication should have not been considered as blank");
    }

    /**
     * Tests that when a blank "password" and "username" combination is given, the "basic authentication" object is
     * considered blank.
     */
    @Test
    void itIsBasicAuthNullOrBlankTest() {
        final var blankBasicAuths = new ArrayList<BasicAuthentication>();

        blankBasicAuths.add(new BasicAuthentication());
        blankBasicAuths.add(new BasicAuthentication(null, null));
        blankBasicAuths.add(new BasicAuthentication(null, ""));
        blankBasicAuths.add(new BasicAuthentication("", null));
        blankBasicAuths.add(new BasicAuthentication("", ""));
        blankBasicAuths.add(new BasicAuthentication(null, "     "));
        blankBasicAuths.add(new BasicAuthentication("     ", null));
        blankBasicAuths.add(new BasicAuthentication("     ", "     "));

        for (final var basicAuth : blankBasicAuths) {
            Assertions.assertTrue(this.secretUtils.isBasicAuthNullOrBlank(basicAuth), "the basic authentication should have been considered as blank");
        }
    }

    /**
     * Tests that when calling the {@link SecretUtils#loadSecretsForEndpoint(Endpoint)}
     * function, if the {@link SourcesService} throws a {@link SourcesRuntimeException},
     * then the generated {@link SourcesException} contains the expected data.
     */
    @Test
    void exceptionsAreProperlyThrownOnLoadTest() throws NoSuchMethodException {
        // Create an endpoint fixture with both references to Sources'
        // secrets.
        final String orgId = "exceptions-properly-thrown-on-load";

        final Endpoint endpoint = new Endpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setOrgId(orgId);

        final WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setBasicAuthenticationSourcesId(new Random().nextLong(1, Long.MAX_VALUE));
        webhookProperties.setSecretTokenSourcesId(new Random().nextLong(1, Long.MAX_VALUE));
        endpoint.setProperties(webhookProperties);

        // Get the method from the interface for the stubbed exception.
        final Method method = SourcesService.class.getMethod("getById", String.class, String.class, long.class);

        // Generate a mocked response object for the stubbed exception.
        final Response response = Mockito.mock(Response.class);
        final String responseBody = "{\"error\": \"some error occurred\"}";
        final int statusCode = new Random().nextInt();
        Mockito.when(response.getStatus()).thenReturn(statusCode);
        Mockito.when(response.readEntity(String.class)).thenReturn(responseBody);

        // Simulate that the source service throws the stubbed exception.
        final SourcesRuntimeException re = new SourcesRuntimeException(method, response);
        Mockito
            .when(this.sourcesServiceMock.getById(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.anyLong()))
            .thenThrow(re);

        // Call the function under test.
        final SourcesException basicAuthEx = Assertions.assertThrows(
            SourcesException.class,
            () -> this.secretUtils.loadSecretsForEndpoint(endpoint)
        );

        final String expectedBasicAuthMessage = String.format(
            "[endpoint_uuid: %s][secret_id: %s][method: %s][response_status_code: %s] Sources returned an unexpected response: %s",
            endpoint.getId(),
            webhookProperties.getBasicAuthenticationSourcesId(),
            method.getName(),
            statusCode,
            responseBody
        );

        Assertions.assertEquals(expectedBasicAuthMessage, basicAuthEx.getMessage(), "the exception returned an unexpected message");

        // Nullify the basic authentication's reference so that the function
        // under test attempts to fetch the secret token.
        webhookProperties.setBasicAuthenticationSourcesId(null);

        // Call the function under test.
        final SourcesException secretTokenEx = Assertions.assertThrows(
            SourcesException.class,
            () -> this.secretUtils.loadSecretsForEndpoint(endpoint)
        );

        final String expectedSecretTokenMessage = String.format(
            "[endpoint_uuid: %s][secret_id: %s][method: %s][response_status_code: %s] Sources returned an unexpected response: %s",
            endpoint.getId(),
            webhookProperties.getSecretTokenSourcesId(),
            method.getName(),
            statusCode,
            responseBody
        );

        Assertions.assertEquals(expectedSecretTokenMessage, secretTokenEx.getMessage(), "the exception returned an unexpected message");
    }

    /**
     * Tests that when calling the {@link SecretUtils#createSecretsForEndpoint(Endpoint)}
     * function, if the {@link SourcesService} throws a {@link SourcesRuntimeException},
     * then the generated {@link SourcesException} contains the expected data.
     */
    @Test
    void exceptionsAreProperlyThrownOnCreationTest() throws NoSuchMethodException {
        // Create an endpoint fixture with both secrets.
        final String orgId = "exceptions-properly-thrown-on-creation";

        final Endpoint endpoint = new Endpoint();
        endpoint.setOrgId(orgId);

        final WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setBasicAuthentication(new BasicAuthentication("user", "password"));
        webhookProperties.setSecretToken("super-secret-token");
        endpoint.setProperties(webhookProperties);

        // Get the method from the interface for the stubbed exception.
        final Method method = SourcesService.class.getMethod("create", String.class, String.class, Secret.class);

        // Generate a mocked response object for the stubbed exception.
        final Response response = Mockito.mock(Response.class);
        final String responseBody = "{\"error\": \"some error occurred\"}";
        final int statusCode = new Random().nextInt();
        Mockito.when(response.getStatus()).thenReturn(statusCode);
        Mockito.when(response.readEntity(String.class)).thenReturn(responseBody);

        // Simulate that the source service throws the stubbed exception.
        final SourcesRuntimeException re = new SourcesRuntimeException(method, response);
        Mockito
            .when(this.sourcesServiceMock.create(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.any()))
            .thenThrow(re);

        // Call the function under test.
        final SourcesException basicAuthEx = Assertions.assertThrows(
            SourcesException.class,
            () -> this.secretUtils.createSecretsForEndpoint(endpoint)
        );

        final String expectedMessage = String.format(
            "[method: %s][response_status_code: %s] Sources returned an unexpected response: %s",
            method.getName(),
            statusCode,
            responseBody
        );

        Assertions.assertEquals(expectedMessage, basicAuthEx.getMessage(), "the exception returned an unexpected message");

        // Nullify the basic authentication so that the function under test
        // attempts to create the secret token instead.
        webhookProperties.setBasicAuthentication(null);

        // Call the function under test.
        final SourcesException secretTokenEx = Assertions.assertThrows(
            SourcesException.class,
            () -> this.secretUtils.createSecretsForEndpoint(endpoint)
        );

        Assertions.assertEquals(expectedMessage, secretTokenEx.getMessage(), "the exception returned an unexpected message");
    }

    /**
     * Tests that when calling the {@link SecretUtils#updateSecretsForEndpoint(Endpoint)}
     * function, if the {@link SourcesService} throws a {@link SourcesRuntimeException},
     * then the generated {@link SourcesException} contains the expected data.
     */
    @Test
    void exceptionsAreProperlyThrownOnUpdateTest() throws NoSuchMethodException {
        // Create an endpoint fixture.
        final String orgId = "exceptions-properly-thrown-on-creation";

        final Endpoint endpoint = new Endpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setOrgId(orgId);

        // Get the different methods from the interface for the stubbed
        // exceptions.
        final Method createMethod = SourcesService.class.getMethod("create", String.class, String.class, Secret.class);
        final Method deleteMethod = SourcesService.class.getMethod("delete", String.class, String.class, long.class);
        final Method updateMethod = SourcesService.class.getMethod("update", String.class, String.class, long.class, Secret.class);

        // Generate a mocked response object for the stubbed exceptions.
        final Response response = Mockito.mock(Response.class);
        final String responseBody = "{\"error\": \"some error occurred\"}";
        final int statusCode = new Random().nextInt();
        Mockito.when(response.getStatus()).thenReturn(statusCode);
        Mockito.when(response.readEntity(String.class)).thenReturn(responseBody);

        // Simulate that the source service throws the stubbed exception.
        final SourcesRuntimeException createRe = new SourcesRuntimeException(createMethod, response);
        final SourcesRuntimeException deleteRe = new SourcesRuntimeException(deleteMethod, response);
        final SourcesRuntimeException updateRe = new SourcesRuntimeException(updateMethod, response);

        // Set up the Mockito calls.
        Mockito
            .when(this.sourcesServiceMock.create(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.any()))
            .thenThrow(createRe);
        Mockito
            .when(this.sourcesServiceMock.update(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.anyLong(), Mockito.any()))
            .thenThrow(updateRe);
        Mockito
            .doThrow(deleteRe)
            .when(this.sourcesServiceMock).delete(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.anyLong());

        // Set up the expected messages for the exceptions.
        final String createExpectedMessage = String.format(
            "[endpoint_uuid: %s][method: %s][response_status_code: %s] Sources returned an unexpected response during an endpoint update operation: %s",
            endpoint.getId(),
            createMethod.getName(),
            statusCode,
            responseBody
        );

        // Test getting an exception when a basic authentication secret is
        // attempted to be deleted.
        final WebhookProperties deleteBasicAuth = new WebhookProperties();
        deleteBasicAuth.setBasicAuthentication(null);
        deleteBasicAuth.setBasicAuthenticationSourcesId(new Random().nextLong(1, Long.MAX_VALUE));
        endpoint.setProperties(deleteBasicAuth);

        // Call the function under test.
        final SourcesException deleteBasicAuthEx = Assertions.assertThrows(
            SourcesException.class,
            () -> this.secretUtils.updateSecretsForEndpoint(endpoint)
        );

        final String deleteBasicAuthExpectedMessage = String.format(
            "[endpoint_uuid: %s][secret_id: %s][method: %s][response_status_code: %s] Sources returned an unexpected response during an endpoint update operation: %s",
            endpoint.getId(),
            deleteBasicAuth.getBasicAuthenticationSourcesId(),
            deleteMethod.getName(),
            statusCode,
            responseBody
        );

        Assertions.assertEquals(deleteBasicAuthExpectedMessage, deleteBasicAuthEx.getMessage(), "the exception returned an unexpected message");

        // Test getting an exception when a basic authentication secret is
        // attempted to be updated.
        final WebhookProperties updateBasicAuth = new WebhookProperties();
        updateBasicAuth.setBasicAuthentication(new BasicAuthentication("username", "password"));
        updateBasicAuth.setBasicAuthenticationSourcesId(new Random().nextLong(1, Long.MAX_VALUE));
        endpoint.setProperties(updateBasicAuth);

        // Call the function under test.
        final SourcesException updateBasicAuthEx = Assertions.assertThrows(
            SourcesException.class,
            () -> this.secretUtils.updateSecretsForEndpoint(endpoint)
        );

        final String updateBasicAuthExpectedMessage = String.format(
            "[endpoint_uuid: %s][secret_id: %s][method: %s][response_status_code: %s] Sources returned an unexpected response during an endpoint update operation: %s",
            endpoint.getId(),
            updateBasicAuth.getBasicAuthenticationSourcesId(),
            updateMethod.getName(),
            statusCode,
            responseBody
        );

        Assertions.assertEquals(updateBasicAuthExpectedMessage, updateBasicAuthEx.getMessage(), "the exception returned an unexpected message");

        // Test getting an exception when a basic authentication secret is
        // attempted to be created.
        final WebhookProperties createBasicAuth = new WebhookProperties();
        createBasicAuth.setBasicAuthentication(new BasicAuthentication("username", "password"));
        createBasicAuth.setBasicAuthenticationSourcesId(null);
        endpoint.setProperties(createBasicAuth);

        // Call the function under test.
        final SourcesException createBasicAuthEx = Assertions.assertThrows(
            SourcesException.class,
            () -> this.secretUtils.updateSecretsForEndpoint(endpoint)
        );

        Assertions.assertEquals(createExpectedMessage, createBasicAuthEx.getMessage(), "the exception returned an unexpected message");

        // Test getting an exception when a secret token is attempted to be
        // deleted.
        final WebhookProperties deleteSecretToken = new WebhookProperties();
        deleteSecretToken.setSecretToken(null);
        deleteSecretToken.setSecretTokenSourcesId(new Random().nextLong(1, Long.MAX_VALUE));
        endpoint.setProperties(deleteSecretToken);

        // Call the function under test.
        final SourcesException deleteSecretTokenEx = Assertions.assertThrows(
            SourcesException.class,
            () -> this.secretUtils.updateSecretsForEndpoint(endpoint)
        );

        final String deleteSecretTokenExpectedMessage = String.format(
            "[endpoint_uuid: %s][secret_id: %s][method: %s][response_status_code: %s] Sources returned an unexpected response during an endpoint update operation: %s",
            endpoint.getId(),
            deleteSecretToken.getSecretTokenSourcesId(),
            deleteMethod.getName(),
            statusCode,
            responseBody
        );

        Assertions.assertEquals(deleteSecretTokenExpectedMessage, deleteSecretTokenEx.getMessage(), "the exception returned an unexpected message");

        // Test getting an exception when a secret token is attempted to be
        // deleted.
        final WebhookProperties updateSecretToken = new WebhookProperties();
        updateSecretToken.setSecretToken("my-super-secret-token");
        updateSecretToken.setSecretTokenSourcesId(new Random().nextLong(1, Long.MAX_VALUE));
        endpoint.setProperties(updateSecretToken);

        // Call the function under test.
        final SourcesException updateSecretTokenEx = Assertions.assertThrows(
            SourcesException.class,
            () -> this.secretUtils.updateSecretsForEndpoint(endpoint)
        );

        final String updateSecretTokenExpectedMessage = String.format(
            "[endpoint_uuid: %s][secret_id: %s][method: %s][response_status_code: %s] Sources returned an unexpected response during an endpoint update operation: %s",
            endpoint.getId(),
            updateSecretToken.getSecretTokenSourcesId(),
            updateMethod.getName(),
            statusCode,
            responseBody
        );

        Assertions.assertEquals(updateSecretTokenExpectedMessage, updateSecretTokenEx.getMessage(), "the exception returned an unexpected message");

        // Test getting an exception when a secret token is attempted to be
        // created.
        final WebhookProperties createSecretToken = new WebhookProperties();
        createSecretToken.setSecretToken("my-super-secret-token");
        createSecretToken.setSecretTokenSourcesId(null);
        endpoint.setProperties(createSecretToken);

        // Call the function under test.
        final SourcesException createSecretTokenEx = Assertions.assertThrows(
            SourcesException.class,
            () -> this.secretUtils.updateSecretsForEndpoint(endpoint)
        );

        Assertions.assertEquals(createExpectedMessage, createSecretTokenEx.getMessage(), "the exception returned an unexpected message");
    }

    /**
     * Tests that when calling the {@link SecretUtils#deleteSecretsForEndpoint(Endpoint)}
     * function, if the {@link SourcesService} throws a {@link SourcesRuntimeException},
     * then the generated {@link SourcesException} contains the expected data.
     */
    @Test
    void exceptionsAreProperlyThrownOnDeleteTest() throws NoSuchMethodException {
        // Create an endpoint fixture with both secrets
        final String orgId = "exceptions-properly-thrown-on-delete";

        final Endpoint endpoint = new Endpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setOrgId(orgId);

        final WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setBasicAuthenticationSourcesId(new Random().nextLong(1, Long.MAX_VALUE));
        webhookProperties.setSecretTokenSourcesId(new Random().nextLong(1, Long.MAX_VALUE));
        endpoint.setProperties(webhookProperties);

        // Get the method from the interface for the stubbed exception.
        final Method method = SourcesService.class.getMethod("delete", String.class, String.class, long.class);

        // Generate a mocked response object for the stubbed exception.
        final Response response = Mockito.mock(Response.class);
        final String responseBody = "{\"error\": \"some error occurred\"}";
        final int statusCode = new Random().nextInt();
        Mockito.when(response.getStatus()).thenReturn(statusCode);
        Mockito.when(response.readEntity(String.class)).thenReturn(responseBody);

        // Simulate that the source service throws the stubbed exception.
        final SourcesRuntimeException re = new SourcesRuntimeException(method, response);
        Mockito
            .doThrow(re)
            .when(this.sourcesServiceMock).delete(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.anyLong());

        // Call the function under test.
        final SourcesException basicAuthEx = Assertions.assertThrows(
            SourcesException.class,
            () -> this.secretUtils.deleteSecretsForEndpoint(endpoint)
        );

        final String expectedBasicAuthMessage = String.format(
            "[endpoint_uuid: %s][secret_id: %s][method: %s][response_status_code: %s] Sources returned an unexpected response: %s",
            endpoint.getId(),
            webhookProperties.getBasicAuthenticationSourcesId(),
            method.getName(),
            statusCode,
            responseBody
        );

        Assertions.assertEquals(expectedBasicAuthMessage, basicAuthEx.getMessage(), "the exception returned an unexpected message");

        // Nullify the basic authentication so that the function under test
        // attempts to create the secret token instead.
        webhookProperties.setBasicAuthenticationSourcesId(null);

        // Call the function under test.
        final SourcesException secretTokenEx = Assertions.assertThrows(
            SourcesException.class,
            () -> this.secretUtils.deleteSecretsForEndpoint(endpoint)
        );

        final String expectedSecretTokenMessage = String.format(
            "[endpoint_uuid: %s][secret_id: %s][method: %s][response_status_code: %s] Sources returned an unexpected response: %s",
            endpoint.getId(),
            webhookProperties.getSecretTokenSourcesId(),
            method.getName(),
            statusCode,
            responseBody
        );

        Assertions.assertEquals(expectedSecretTokenMessage, secretTokenEx.getMessage(), "the exception returned an unexpected message");
    }
}
