package com.redhat.cloud.notifications.routers.sources;

import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.models.BasicAuthenticationLegacy;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.SourcesSecretable;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.models.secrets.BasicAuthentication;
import com.redhat.cloud.notifications.models.secrets.BearerToken;
import com.redhat.cloud.notifications.models.secrets.SecretToken;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@QuarkusTest
public class SecretUtilsTest {

    static final Long BASIC_AUTH_SOURCES_ID = 50L;
    static final String BASIC_AUTH_PASSWORD = "basic-auth-test-password";
    static final String BASIC_AUTH_USERNAME = "basic-auth-test-username";
    static final String SECRET_TOKEN = "secret-token";
    static final String BEARER_AUTHENTICATION = "bearer-test-authentication";
    static final Long SECRET_TOKEN_SOURCES_ID = 100L;
    static final Long BEARER_TOKEN_SOURCES_ID = 150L;

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
     */
    @Test
    void getSecretsForEndpointTest() {
        // Create a "Basic Authentication" secret mock.
        final Secret basicAuthenticationMock = new Secret();
        basicAuthenticationMock.password = BASIC_AUTH_PASSWORD;
        basicAuthenticationMock.username = BASIC_AUTH_USERNAME;

        // Create a "secret token" secret mock.
        final Secret secretTokenMock = new Secret();
        secretTokenMock.password = SECRET_TOKEN;

        // Create a "bearer authentication" secret mock.
        final Secret secretBearerMock = new Secret();
        secretBearerMock.password = BEARER_AUTHENTICATION;

        // Create an endpoint that contains the expected data by the function under test.
        final String orgId = "get-secrets-for-endpoint-test-organization-id";

        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();

        webhookProperties.setBasicAuthenticationSourcesId(BASIC_AUTH_SOURCES_ID);
        webhookProperties.setSecretTokenSourcesId(SECRET_TOKEN_SOURCES_ID);
        webhookProperties.setBearerAuthenticationSourcesId(BEARER_TOKEN_SOURCES_ID);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Set up the mock calls for the "get by id" calls from the REST Client.
        Mockito.when(this.sourcesServiceMock.getById(orgId, this.sourcesPsk, BASIC_AUTH_SOURCES_ID)).thenReturn(basicAuthenticationMock);
        Mockito.when(this.sourcesServiceMock.getById(orgId, this.sourcesPsk, SECRET_TOKEN_SOURCES_ID)).thenReturn(secretTokenMock);
        Mockito.when(this.sourcesServiceMock.getById(orgId, this.sourcesPsk, BEARER_TOKEN_SOURCES_ID)).thenReturn(secretBearerMock);

        // Call the function under test.
        this.secretUtils.loadSecretsForEndpoint(endpoint);

        // Check that the endpoint properties are of the expected type.
        final var endpointProperties = endpoint.getProperties();
        if (!(endpointProperties instanceof SourcesSecretable)) {
            Assertions.fail("unexpected type of the properties found. Want " + SourcesSecretable.class + ", got " + endpointProperties.getClass());
        }

        final var props = (SourcesSecretable) endpoint.getProperties();

        // Assert the results.
        final BasicAuthenticationLegacy basicAuth = props.getBasicAuthenticationLegacy();
        Assertions.assertEquals(BASIC_AUTH_PASSWORD, basicAuth.getPassword(), "the basic authentication's password field doesn't match");
        Assertions.assertEquals(BASIC_AUTH_USERNAME, basicAuth.getUsername(), "the basic authentication's username field doesn't match");

        final String secretToken = props.getSecretTokenLegacy();
        Assertions.assertEquals(SECRET_TOKEN, secretToken, "the secret token doesn't match");

        final String secretBearer = props.getBearerAuthenticationLegacy();
        Assertions.assertEquals(BEARER_AUTHENTICATION, secretBearer, "the secret bearer doesn't match");

        // Assert that the underlying function was called exactly three times,
        // since we are expecting that "basic authentication", "secret token"
        // and "bearer authentication" secrets were fetched.
        final int wantedNumberOfInvocations = 1;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).getById(orgId, this.sourcesPsk, BASIC_AUTH_SOURCES_ID);
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).getById(orgId, this.sourcesPsk, SECRET_TOKEN_SOURCES_ID);
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
     */
    @Test
    void createSecretsForEndpointTest() {
        // Set the ID for the basic authentication secret that is supposed that is created in Sources.
        final Secret basicAuthenticationMock = new Secret();
        basicAuthenticationMock.id = BASIC_AUTH_SOURCES_ID;

        // Set the ID for the secret token secret that is supposed that is created in Sources.
        final Secret secretTokenMock = new Secret();
        secretTokenMock.id = SECRET_TOKEN_SOURCES_ID;

        // Create a "bearer authentication" secret mock.
        final Secret secretBearerMock = new Secret();
        secretBearerMock.id = BEARER_TOKEN_SOURCES_ID;

        // Create an endpoint that contains the expected data by the function under test.
        final String orgId = "create-secrets-for-endpoint-test-organization-id";

        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();
        BasicAuthenticationLegacy basicAuth = new BasicAuthenticationLegacy(BASIC_AUTH_USERNAME, BASIC_AUTH_PASSWORD);

        webhookProperties.setBasicAuthenticationLegacy(basicAuth);
        webhookProperties.setSecretTokenLegacy(SECRET_TOKEN);
        webhookProperties.setBearerAuthenticationLegacy(BEARER_AUTHENTICATION);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Set up the mock calls for the "create" calls from the REST Client.
        // Make sure we return the basic authentication's ID first, the secret
        // token's ID second, and the bearer authentication's ID third, since
        // we are expecting a successful create operation.
        Mockito.when(this.sourcesServiceMock.create(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.any())).thenReturn(basicAuthenticationMock, secretTokenMock, secretBearerMock);

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
        Assertions.assertEquals(BEARER_TOKEN_SOURCES_ID, props.getBearerAuthenticationSourcesId(), "the ID of the secret bearer from Sources doesn't match");

        // Assert that the underlying function was called exactly three times,
        // since we are expecting that the "basic authentication", "secret
        // token" and "bearer authentication" secrets were created.
        final int wantedNumberOfInvocations = 3;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).create(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.any());
    }

    /**
     * Tests that when there is a {@code NULL} "basic authentication" object on the endpoint's properties, the function
     * under test calls "create" upon Sources just for the "secret token" secret, and that the ID for that secret is
     * properly set on the endpoint's properties.
     */
    @Test
    void createSecretsForEndpointBasicAuthNullTest() {
        // Set the ID for the secret token secret that is supposed that is created in Sources.
        final Secret secretTokenMock = new Secret();
        secretTokenMock.id = SECRET_TOKEN_SOURCES_ID;

        // Set the ID for the secret bearer that is supposed that is created in Sources.
        final Secret secretBearerMock = new Secret();
        secretBearerMock.id = BEARER_TOKEN_SOURCES_ID;

        // Create an endpoint that contains the expected data by the function under test.
        final String orgId = "create-secrets-for-endpoint-basic-auth-null-test-organization-id";

        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setSecretTokenLegacy(SECRET_TOKEN);
        webhookProperties.setBearerAuthenticationLegacy(BEARER_AUTHENTICATION);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Set up the mock call for the "create" call from the REST Client. In
        // this case, since the basic authentication is null, only the secret
        // token should be created.
        Mockito.when(this.sourcesServiceMock.create(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.any())).thenReturn(secretTokenMock, secretBearerMock);

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
        Assertions.assertEquals(BEARER_TOKEN_SOURCES_ID, props.getBearerAuthenticationSourcesId(), "the ID of the bearer secret from Sources doesn't match");

        // Assert that the underlying function was called exactly two times:
        // one for the "secret token"'s secret creation and another one for the
        // "bearer authentication"'s secret creation.
        final int wantedNumberOfInvocations = 2;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).create(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.any());
    }

    /**
     * Tests that when there is a non-{@code NULL} "basic authentication" object with its fields with blank values, the
     * function under test calls "create" upon Sources just for the "secret token" secret, and that the ID for that
     * secret is properly set on the endpoint's properties.
     */
    @Test
    void createSecretsForEndpointBasicAuthBlankFieldsTest() {
        // Set the ID for the secret token secret that is supposed that is created in Sources.
        final Secret secretTokenMock = new Secret();
        secretTokenMock.id = SECRET_TOKEN_SOURCES_ID;

        // Create an endpoint that contains the expected data by the function under test.
        final String orgId = "create-secrets-for-endpoint-basic-auth-blank-fields-test-organization-id";

        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setSecretTokenLegacy(SECRET_TOKEN);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Create a basic authentication with blank fields.
        BasicAuthenticationLegacy basicAuthenticationLegacy = new BasicAuthenticationLegacy("     ", "     ");
        webhookProperties.setBasicAuthenticationLegacy(basicAuthenticationLegacy);

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
     */
    @Test
    void createSecretsForEndpointSecretTokenNullTest() {
        // Set the ID for the basic authentication secret that is supposed that is created in Sources.
        final Secret basicAuthenticationMock = new Secret();
        basicAuthenticationMock.id = BASIC_AUTH_SOURCES_ID;

        // Create an endpoint that contains the expected data by the function under test.
        final String orgId = "create-secrets-for-endpoint-secret-token-null-test-organization-id";

        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();
        BasicAuthenticationLegacy basicAuth = new BasicAuthenticationLegacy(BASIC_AUTH_USERNAME, BASIC_AUTH_PASSWORD);

        webhookProperties.setBasicAuthenticationLegacy(basicAuth);

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
     */
    @Test
    void createSecretsForEndpointSecretTokenBlankTest() {
        // Set the ID for the basic authentication secret that is supposed that is created in Sources.
        final Secret basicAuthenticationMock = new Secret();
        basicAuthenticationMock.id = BASIC_AUTH_SOURCES_ID;

        // Create an endpoint that contains the expected data by the function under test.
        final String orgId = "create-secrets-for-endpoint-secret-token-blank-test-organization-id";

        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();
        BasicAuthenticationLegacy basicAuth = new BasicAuthenticationLegacy(BASIC_AUTH_USERNAME, BASIC_AUTH_PASSWORD);

        webhookProperties.setBasicAuthenticationLegacy(basicAuth);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Set the secret token to blank.
        webhookProperties.setSecretTokenLegacy("     ");

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
     * Tests that the function under test creates a basic authentication secret
     * in Sources when there is no Sources reference stored for it.
     */
    @Test
    void testCreateBasicAuthenticationSecret() {
        final Endpoint endpoint = new Endpoint();
        endpoint.setOrgId(TestConstants.DEFAULT_ORG_ID);
        endpoint.setProperties(new WebhookProperties());

        final String username = "username";
        final String password = "password";

        // Mock the response from Sources.
        final Secret mockedSecret = new Secret();
        mockedSecret.id = new Random().nextLong(1, Long.MAX_VALUE);
        mockedSecret.authenticationType = Secret.TYPE_BASIC_AUTH;
        mockedSecret.username = username;
        mockedSecret.password = password;

        Mockito.when(
            this.sourcesServiceMock.create(Mockito.eq(TestConstants.DEFAULT_ORG_ID), Mockito.eq(this.sourcesPsk), Mockito.any())
        ).thenReturn(
            mockedSecret
        );

        // Call the function under test.
        this.secretUtils.createUpdateBasicAuthenticationSecret(
            endpoint,
            new BasicAuthentication(username, password)
        );

        // Capture the secret that was about to be sent to Sources.
        final ArgumentCaptor<Secret> secretArgumentCaptor = ArgumentCaptor.forClass(Secret.class);

        Mockito.verify(
            this.sourcesServiceMock,
            Mockito.times(1)
        ).create(
            Mockito.eq(TestConstants.DEFAULT_ORG_ID),
            Mockito.eq(this.sourcesPsk),
            secretArgumentCaptor.capture()
        );

        final Secret sentSecret = secretArgumentCaptor.getValue();

        // Assert that the values are the expected ones.
        Assertions.assertEquals(mockedSecret.id, endpoint.getProperties(WebhookProperties.class).getBasicAuthenticationSourcesId(), "unexpected ID stored for the created secret in Sources");
        Assertions.assertEquals(Secret.TYPE_BASIC_AUTH, sentSecret.authenticationType, "unexpected value for the authentication type when creating a new basic authentication secret in Sources");
        Assertions.assertEquals(username, sentSecret.username, "unexpected username for the basic authentication object to be created");
        Assertions.assertEquals(password, sentSecret.password, "unexpected password for the basic authentication object to be created");
    }

    /**
     * Tests that the function under test updates a basic authentication secret
     * in Sources when there is a Sources reference stored for it.
     */
    @Test
    void testUpdateBasicAuthenticationSecret() {
        final WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setBasicAuthenticationSourcesId(new Random().nextLong(1, Long.MAX_VALUE));

        final Endpoint endpoint = new Endpoint();
        endpoint.setOrgId(TestConstants.DEFAULT_ORG_ID);
        endpoint.setProperties(webhookProperties);

        final String username = "new-username";
        final String password = "new-password";

        // Call the function under test.
        this.secretUtils.createUpdateBasicAuthenticationSecret(
            endpoint,
            new BasicAuthentication(username, password)
        );

        // Capture the secret that was about to be sent to Sources.
        final ArgumentCaptor<Secret> secretArgumentCaptor = ArgumentCaptor.forClass(Secret.class);

        Mockito.verify(
            this.sourcesServiceMock,
            Mockito.times(1)
        ).update(
            Mockito.eq(TestConstants.DEFAULT_ORG_ID),
            Mockito.eq(this.sourcesPsk),
            Mockito.eq(webhookProperties.getBasicAuthenticationSourcesId()),
            secretArgumentCaptor.capture()
        );

        final Secret sentSecret = secretArgumentCaptor.getValue();

        // Assert that the values are the expected ones.
        Assertions.assertEquals(Secret.TYPE_BASIC_AUTH, sentSecret.authenticationType, "unexpected value for the authentication type when updating an existing basic authentication secret in Sources");
        Assertions.assertEquals(username, sentSecret.username, "unexpected username for the basic authentication object to be updated");
        Assertions.assertEquals(password, sentSecret.password, "unexpected password for the basic authentication object to be updated");
    }

    /**
     * Tests that the function under test deletes a basic authentication secret
     * in Sources when there is a Sources reference stored for it.
     */
    @Test
    void testDeleteBasicAuthenticationSecret() {
        // Set the Sources reference ID in a new statement because the function
        // under test removes it from the endpoint's properties.
        final long sourcesReferenceId = new Random().nextLong(1, Long.MAX_VALUE);

        final WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setBasicAuthenticationSourcesId(sourcesReferenceId);

        final Endpoint endpoint = new Endpoint();
        endpoint.setOrgId(TestConstants.DEFAULT_ORG_ID);
        endpoint.setProperties(webhookProperties);

        // Call the function under test.
        this.secretUtils.deleteBasicAuthenticationSecret(
            endpoint
        );

        Mockito.verify(
            this.sourcesServiceMock,
            Mockito.times(1)
        ).delete(
            Mockito.eq(TestConstants.DEFAULT_ORG_ID),
            Mockito.eq(this.sourcesPsk),
            Mockito.eq(sourcesReferenceId)
        );
    }

    /**
     * Tests that the function under test creates a bearer token secret in
     * Sources when there is no Sources reference stored for it.
     */
    @Test
    void testCreateBearerTokenSecret() {
        final Endpoint endpoint = new Endpoint();
        endpoint.setOrgId(TestConstants.DEFAULT_ORG_ID);
        endpoint.setProperties(new WebhookProperties());

        final String bearerToken = "bearer-token";

        // Mock the response from Sources.
        final Secret mockedSecret = new Secret();
        mockedSecret.id = new Random().nextLong(1, Long.MAX_VALUE);
        mockedSecret.authenticationType = Secret.TYPE_BEARER_AUTHENTICATION;
        mockedSecret.password = bearerToken;

        Mockito.when(
            this.sourcesServiceMock.create(Mockito.eq(TestConstants.DEFAULT_ORG_ID), Mockito.eq(this.sourcesPsk), Mockito.any())
        ).thenReturn(
            mockedSecret
        );

        // Call the function under test.
        this.secretUtils.createUpdateBearerTokenSecret(
            endpoint,
            new BearerToken(bearerToken)
        );

        // Capture the secret that was about to be sent to Sources.
        final ArgumentCaptor<Secret> secretArgumentCaptor = ArgumentCaptor.forClass(Secret.class);

        Mockito.verify(
            this.sourcesServiceMock,
            Mockito.times(1)
        ).create(
            Mockito.eq(TestConstants.DEFAULT_ORG_ID),
            Mockito.eq(this.sourcesPsk),
            secretArgumentCaptor.capture()
        );

        final Secret sentSecret = secretArgumentCaptor.getValue();

        // Assert that the values are the expected ones.
        Assertions.assertEquals(mockedSecret.id, endpoint.getProperties(WebhookProperties.class).getBearerAuthenticationSourcesId(), "unexpected ID stored for the created secret in Sources");
        Assertions.assertEquals(Secret.TYPE_BEARER_AUTHENTICATION, sentSecret.authenticationType, "unexpected value for the authentication type when creating a new bearer token secret in Sources");
        Assertions.assertEquals(bearerToken, sentSecret.password, "unexpected token for the bearer token object to be created");
    }

    /**
     * Tests that the function under test updates a bearer token secret in
     * Sources when there is a Sources reference stored for it.
     */
    @Test
    void testUpdateBearerTokenSecret() {
        final WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setBearerAuthenticationSourcesId(new Random().nextLong(1, Long.MAX_VALUE));

        final Endpoint endpoint = new Endpoint();
        endpoint.setOrgId(TestConstants.DEFAULT_ORG_ID);
        endpoint.setProperties(webhookProperties);

        final String bearerToken = "new-bearer-token";

        // Call the function under test.
        this.secretUtils.createUpdateBearerTokenSecret(
            endpoint,
            new BearerToken(bearerToken)
        );

        // Capture the secret that was about to be sent to Sources.
        final ArgumentCaptor<Secret> secretArgumentCaptor = ArgumentCaptor.forClass(Secret.class);

        Mockito.verify(
            this.sourcesServiceMock,
            Mockito.times(1)
        ).update(
            Mockito.eq(TestConstants.DEFAULT_ORG_ID),
            Mockito.eq(this.sourcesPsk),
            Mockito.eq(webhookProperties.getBearerAuthenticationSourcesId()),
            secretArgumentCaptor.capture()
        );

        final Secret sentSecret = secretArgumentCaptor.getValue();

        // Assert that the values are the expected ones.
        Assertions.assertEquals(Secret.TYPE_BEARER_AUTHENTICATION, sentSecret.authenticationType, "unexpected value for the authentication type when updating an existing bearer token secret in Sources");
        Assertions.assertEquals(bearerToken, sentSecret.password, "unexpected token for the bearer token object to be updated");
    }

    /**
     * Tests that the function under test deletes a bearer token secret in
     * Sources when there is a Sources reference stored for it.
     */
    @Test
    void testDeleteBearerTokenSecret() {
        // Set the Sources reference ID in a new statement because the function
        // under test removes it from the endpoint's properties.
        final long sourcesReferenceId = new Random().nextLong(1, Long.MAX_VALUE);

        final WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setBearerAuthenticationSourcesId(sourcesReferenceId);

        final Endpoint endpoint = new Endpoint();
        endpoint.setOrgId(TestConstants.DEFAULT_ORG_ID);
        endpoint.setProperties(webhookProperties);

        // Call the function under test.
        this.secretUtils.deleteBearerTokenSecret(
            endpoint
        );

        Mockito.verify(
            this.sourcesServiceMock,
            Mockito.times(1)
        ).delete(
            Mockito.eq(TestConstants.DEFAULT_ORG_ID),
            Mockito.eq(this.sourcesPsk),
            Mockito.eq(sourcesReferenceId)
        );
    }

    /**
     * Tests that the function under test creates a secret token secret in
     * Sources when there is no Sources reference stored for it.
     */
    @Test
    void testCreateSecretTokenSecret() {
        final Endpoint endpoint = new Endpoint();
        endpoint.setOrgId(TestConstants.DEFAULT_ORG_ID);
        endpoint.setProperties(new WebhookProperties());

        final String secretToken = "secret-token";

        // Mock the response from Sources.
        final Secret mockedSecret = new Secret();
        mockedSecret.id = new Random().nextLong(1, Long.MAX_VALUE);
        mockedSecret.authenticationType = Secret.TYPE_SECRET_TOKEN;
        mockedSecret.password = secretToken;

        Mockito.when(
            this.sourcesServiceMock.create(Mockito.eq(TestConstants.DEFAULT_ORG_ID), Mockito.eq(this.sourcesPsk), Mockito.any())
        ).thenReturn(
            mockedSecret
        );

        // Call the function under test.
        this.secretUtils.createUpdateSecretTokenSecret(
            endpoint,
            new SecretToken(secretToken)
        );

        // Capture the secret that was about to be sent to Sources.
        final ArgumentCaptor<Secret> secretArgumentCaptor = ArgumentCaptor.forClass(Secret.class);

        Mockito.verify(
            this.sourcesServiceMock,
            Mockito.times(1)
        ).create(
            Mockito.eq(TestConstants.DEFAULT_ORG_ID),
            Mockito.eq(this.sourcesPsk),
            secretArgumentCaptor.capture()
        );

        final Secret sentSecret = secretArgumentCaptor.getValue();

        // Assert that the values are the expected ones.
        Assertions.assertEquals(mockedSecret.id, endpoint.getProperties(WebhookProperties.class).getSecretTokenSourcesId(), "unexpected ID stored for the created secret in Sources");
        Assertions.assertEquals(Secret.TYPE_SECRET_TOKEN, sentSecret.authenticationType, "unexpected value for the authentication type when creating a new secret token secret in Sources");
        Assertions.assertEquals(secretToken, sentSecret.password, "unexpected token for the secret token object to be created");
    }

    /**
     * Tests that the function under test updates a secret token secret in
     * Sources when there is a Sources reference stored for it.
     */
    @Test
    void testUpdateSecretsTokenSecret() {
        final WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setSecretTokenSourcesId(new Random().nextLong(1, Long.MAX_VALUE));

        final Endpoint endpoint = new Endpoint();
        endpoint.setOrgId(TestConstants.DEFAULT_ORG_ID);
        endpoint.setProperties(webhookProperties);

        final String secretToken = "new-secret-token";

        // Call the function under test.
        this.secretUtils.createUpdateSecretTokenSecret(
            endpoint,
            new SecretToken(secretToken)
        );

        // Capture the secret that was about to be sent to Sources.
        final ArgumentCaptor<Secret> secretArgumentCaptor = ArgumentCaptor.forClass(Secret.class);

        Mockito.verify(
            this.sourcesServiceMock,
            Mockito.times(1)
        ).update(
            Mockito.eq(TestConstants.DEFAULT_ORG_ID),
            Mockito.eq(this.sourcesPsk),
            Mockito.eq(webhookProperties.getSecretTokenSourcesId()),
            secretArgumentCaptor.capture()
        );

        final Secret sentSecret = secretArgumentCaptor.getValue();

        // Assert that the values are the expected ones.
        Assertions.assertEquals(Secret.TYPE_SECRET_TOKEN, sentSecret.authenticationType, "unexpected value for the authentication type when updating an existing secret token secret in Sources");
        Assertions.assertEquals(secretToken, sentSecret.password, "unexpected token for the secret token object to be updated");
    }

    /**
     * Tests that the function under test deletes a secret token secret in
     * Sources when there is a Sources reference stored for it.
     */
    @Test
    void testDeleteSecretTokenSecret() {
        // Set the Sources reference ID in a new statement because the function
        // under test removes it from the endpoint's properties.
        final long sourcesReferenceId = new Random().nextLong(1, Long.MAX_VALUE);

        final WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setSecretTokenSourcesId(sourcesReferenceId);

        final Endpoint endpoint = new Endpoint();
        endpoint.setOrgId(TestConstants.DEFAULT_ORG_ID);
        endpoint.setProperties(webhookProperties);

        // Call the function under test.
        this.secretUtils.deleteSecretTokenSecret(
            endpoint
        );

        Mockito.verify(
            this.sourcesServiceMock,
            Mockito.times(1)
        ).delete(
            Mockito.eq(TestConstants.DEFAULT_ORG_ID),
            Mockito.eq(this.sourcesPsk),
            Mockito.eq(sourcesReferenceId)
        );
    }

    /**
     * Tests that the function under test calls "update" upon Sources two times, one for the "basic authentication" and
     * another one for the "secret token". It also checks that the responses are properly marshalled into the right
     * endpoint properties.
     * @deprecated because the function will get removed once the old model
     * gets removed too.
     */
    @Deprecated(forRemoval = true)
    @Test
    void updateSecretsForEndpointTest() {
        // Create a "Basic Authentication" secret mock.
        final Secret basicAuthenticationMock = new Secret();
        basicAuthenticationMock.password = BASIC_AUTH_PASSWORD;
        basicAuthenticationMock.username = BASIC_AUTH_USERNAME;

        // Create a "secret token" secret mock.
        final Secret secretTokenMock = new Secret();
        secretTokenMock.password = SECRET_TOKEN;

        // Create a "bearer authentication" secret mock.
        final Secret bearerMock = new Secret();
        bearerMock.password = BEARER_AUTHENTICATION;

        // Create an endpoint that contains the expected data by the function under test.
        final String orgId = "update-secrets-for-endpoint-test-organization-id";

        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();

        // Set the updated fields so that the function under test's logic picks them up for an update.
        final String updatedUsername = String.format("%s-updated", BASIC_AUTH_USERNAME);
        final String updatedPassword = String.format("%s-updated", BASIC_AUTH_PASSWORD);
        final String updatedSecretToken = String.format("%s-updated", SECRET_TOKEN);
        final String updatedBearer = String.format("%s-updated", BEARER_AUTHENTICATION);

        BasicAuthenticationLegacy basicAuth = new BasicAuthenticationLegacy(updatedUsername, updatedPassword);
        webhookProperties.setBasicAuthenticationLegacy(basicAuth);
        webhookProperties.setBasicAuthenticationSourcesId(BASIC_AUTH_SOURCES_ID);

        webhookProperties.setSecretTokenLegacy(updatedSecretToken);
        webhookProperties.setSecretTokenSourcesId(SECRET_TOKEN_SOURCES_ID);

        webhookProperties.setBearerAuthenticationLegacy(updatedBearer);
        webhookProperties.setBearerAuthenticationSourcesId(BEARER_TOKEN_SOURCES_ID);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Set up the mock calls to return the "basic authentication" and the "secret token" secrets which are supposed
        // to be updated.
        Mockito.when(this.sourcesServiceMock.getById(orgId, this.sourcesPsk, BASIC_AUTH_SOURCES_ID)).thenReturn(basicAuthenticationMock);
        Mockito.when(this.sourcesServiceMock.getById(orgId, this.sourcesPsk, SECRET_TOKEN_SOURCES_ID)).thenReturn(secretTokenMock);
        Mockito.when(this.sourcesServiceMock.getById(orgId, this.sourcesPsk, BEARER_TOKEN_SOURCES_ID)).thenReturn(bearerMock);

        // Call the function under test.
        this.secretUtils.updateSecretsForEndpoint(endpoint);

        // Check that the endpoint properties are of the expected type.
        final var endpointProperties = endpoint.getProperties();
        if (!(endpointProperties instanceof SourcesSecretable)) {
            Assertions.fail("unexpected type of the properties found. Want " + SourcesSecretable.class + ", got " + endpointProperties.getClass());
        }

        final var props = (SourcesSecretable) endpoint.getProperties();

        // Assert the results.
        final BasicAuthenticationLegacy basicAuthResult = props.getBasicAuthenticationLegacy();
        Assertions.assertEquals(updatedPassword, basicAuthResult.getPassword(), "the updated basic authentication's password doesn't match");
        Assertions.assertEquals(updatedUsername, basicAuthResult.getUsername(), "the updated basic authentication's username doesn't match");

        final String secretToken = props.getSecretTokenLegacy();
        Assertions.assertEquals(updatedSecretToken, secretToken, "the updated secret token doesn't match");

        final String bearerToken = props.getBearerAuthenticationLegacy();
        Assertions.assertEquals(updatedBearer, bearerToken, "the updated bearer doesn't match");

        // Assert that the underlying "update" function was called exactly two times, since we are expecting that both
        // the "basic authentication" and the "secret token" secrets were successfully updated.
        final int wantedNumberOfInvocationsUpdate = 1;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocationsUpdate)).update(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.eq(BASIC_AUTH_SOURCES_ID), Mockito.any());
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocationsUpdate)).update(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.eq(SECRET_TOKEN_SOURCES_ID), Mockito.any());
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocationsUpdate)).update(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.eq(BEARER_TOKEN_SOURCES_ID), Mockito.any());
    }

    /**
     * Tests that when the user provides a {@code null} "basic authentication" and "secret token" secret for an
     * endpoint which has secrets stored in Sources, the deletion process for those secrets is triggered.
     * @deprecated because the function will get removed once the old model
     * gets removed too.
     */
    @Deprecated(forRemoval = true)
    @Test
    void updateSecretsForEndpointDeleteTest() {
        // Create an endpoint that contains the expected data by the function under test.
        final String orgId = "update-secrets-for-endpoint-delete-test-organization-id";

        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();

        // Simulate that we are sending an empty "basic authentication" and "secret token", but that we have those
        // IDs on the database. This should trigger the deletion of these secrets on Sources.
        webhookProperties.setBasicAuthenticationSourcesId(BASIC_AUTH_SOURCES_ID);
        webhookProperties.setSecretTokenSourcesId(SECRET_TOKEN_SOURCES_ID);
        webhookProperties.setBearerAuthenticationSourcesId(BEARER_TOKEN_SOURCES_ID);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Call the function under test.
        this.secretUtils.updateSecretsForEndpoint(endpoint);

        // The IDs of the properties should be null now.
        Assertions.assertNull(webhookProperties.getBasicAuthenticationSourcesId(), "the basic authentication's Sources ID isn't null");
        Assertions.assertNull(webhookProperties.getSecretTokenSourcesId(), "the secret token's Sources ID isn't null");
        Assertions.assertNull(webhookProperties.getBearerAuthenticationSourcesId(), "the bearer Sources ID isn't null");

        // It should have triggered two "delete" calls to Sources to delete both of the secrets.
        final int wantedNumberOfInvocations = 1;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).delete(orgId, this.sourcesPsk, BASIC_AUTH_SOURCES_ID);
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).delete(orgId, this.sourcesPsk, SECRET_TOKEN_SOURCES_ID);
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).delete(orgId, this.sourcesPsk, BEARER_TOKEN_SOURCES_ID);
    }

    /**
     * Tests that when the client updates an endpoint, but there are no secrets stored in Sources, or no valid secrets
     * provided by the client, basically a NOP happens. For a valid secret we understand it as a secret token that is
     * not blank.
     * @deprecated because the function will get removed once the old model
     * gets removed too.
     */
    @Deprecated(forRemoval = true)
    @Test
    void updateSecretsForEndpointNopTest() {
        // Create an endpoint that contains the expected data by the function under test.
        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();
        endpoint.setProperties(webhookProperties);

        this.secretUtils.updateSecretsForEndpoint(endpoint);

        Mockito.verifyNoInteractions(this.sourcesServiceMock);

        // Set the basic authentication as having blank values. It should also be a NOOP.
        final BasicAuthenticationLegacy basicAuthenticationLegacy = new BasicAuthenticationLegacy("     ", "     ");
        webhookProperties.setBasicAuthenticationLegacy(basicAuthenticationLegacy);
        this.secretUtils.updateSecretsForEndpoint(endpoint);

        Mockito.verifyNoInteractions(this.sourcesServiceMock);

        // Set the secret token as a blank value. It should also be a NOOP.
        webhookProperties.setSecretTokenLegacy("     ");
        this.secretUtils.updateSecretsForEndpoint(endpoint);

        Mockito.verifyNoInteractions(this.sourcesServiceMock);
    }

    /**
     * @deprecated because the function will get removed once the old model
     * gets removed too.
     */
    @Deprecated(forRemoval = true)
    @Test
    void updateSecretsForEndpointCreateTest() {
        // Set the ID for the basic authentication secret that is supposed that is created in Sources.
        final Secret basicAuthenticationMock = new Secret();
        basicAuthenticationMock.id = BASIC_AUTH_SOURCES_ID;

        // Set the ID for the secret token secret that is supposed that is created in Sources.
        final Secret secretTokenMock = new Secret();
        secretTokenMock.id = SECRET_TOKEN_SOURCES_ID;

        // Set the ID for the bearer secret that is supposed that is created in Sources.
        final Secret bearerMock = new Secret();
        bearerMock.id = BEARER_TOKEN_SOURCES_ID;

        // Create an endpoint that contains the expected data by the function under test.
        final String orgId = "update-secrets-for-endpoint-create-test-organization-id";

        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();
        BasicAuthenticationLegacy basicAuth = new BasicAuthenticationLegacy(BASIC_AUTH_USERNAME, BASIC_AUTH_PASSWORD);

        webhookProperties.setBasicAuthenticationLegacy(basicAuth);
        webhookProperties.setSecretTokenLegacy(SECRET_TOKEN);
        webhookProperties.setBearerAuthenticationLegacy(BEARER_AUTHENTICATION);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Set up the mock calls for the "create" calls from the REST Client.
        // Make sure we return the basic authentication's ID first, the secret
        // token's ID second and the bearer authentication's ID third, since we
        // are expecting a successful create operation.
        Mockito.when(this.sourcesServiceMock.create(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.any())).thenReturn(basicAuthenticationMock, secretTokenMock, bearerMock);

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
        Assertions.assertEquals(BEARER_TOKEN_SOURCES_ID, props.getBearerAuthenticationSourcesId(), "the ID of the bearer secret from Sources doesn't match");

        // Assert that the underlying function was called exactly three times,
        // since we are expecting that the "basic authentication", "secret
        // token" and "bearer authentication" secrets were created.
        final int wantedNumberOfInvocations = 3;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).create(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.any());
    }

    /**
     * Tests that the "delete" function from the function under test gets called for both the "basic authentication"
     * and "secret token" secrets.
     */
    @Test
    void deleteSecretsForEndpointTest() {
        // Create an endpoint that contains the expected data by the function under test.
        final String orgId = "delete-secrets-for-endpoint-test-organization-id";

        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();

        webhookProperties.setBasicAuthenticationSourcesId(BASIC_AUTH_SOURCES_ID);
        webhookProperties.setSecretTokenSourcesId(SECRET_TOKEN_SOURCES_ID);
        webhookProperties.setBearerAuthenticationSourcesId(BEARER_TOKEN_SOURCES_ID);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Call the function under test.
        this.secretUtils.deleteSecretsForEndpoint(endpoint);

        // Assert that the underlying "delete" function was called exactly one time, since we are expecting a successful
        // deletion for both secrets.
        final int wantedNumberOfInvocations = 1;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).delete(orgId, this.sourcesPsk, BASIC_AUTH_SOURCES_ID);
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).delete(orgId, this.sourcesPsk, SECRET_TOKEN_SOURCES_ID);
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).delete(orgId, this.sourcesPsk, BEARER_TOKEN_SOURCES_ID);
    }

    /**
     * Tests that when the "basic authentication" and "secret token" don't have associated Sources IDs, the "delete"
     * function from the function under test doesn't get called.
     */
    @Test
    void deleteSecretsForEndpointZeroIdsTest() {
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
     * @deprecated because the check for the legacy version of the basic authentication model is going to be removed.
     */
    @Deprecated(forRemoval = true)
    @Test
    void isBasicAuthNullOrBlankTest() {
        final var basicAuth = new BasicAuthenticationLegacy("username", "password");

        Assertions.assertFalse(this.secretUtils.isBasicAuthNullOrBlank(basicAuth), "the basic authentication should have not been considered as blank");
    }

    /**
     * Tests that when a blank "password" and "username" combination is given, the "basic authentication" object is
     * considered blank.
     * @deprecated because the check for the legacy version of the basic authentication model is going to be removed.
     */
    @Deprecated(forRemoval = true)
    @Test
    void itIsBasicAuthNullOrBlankTest() {
        final var blankBasicAuths = new ArrayList<BasicAuthenticationLegacy>();

        blankBasicAuths.add(new BasicAuthenticationLegacy());
        blankBasicAuths.add(new BasicAuthenticationLegacy(null, null));
        blankBasicAuths.add(new BasicAuthenticationLegacy(null, ""));
        blankBasicAuths.add(new BasicAuthenticationLegacy("", null));
        blankBasicAuths.add(new BasicAuthenticationLegacy("", ""));
        blankBasicAuths.add(new BasicAuthenticationLegacy(null, "     "));
        blankBasicAuths.add(new BasicAuthenticationLegacy("     ", null));
        blankBasicAuths.add(new BasicAuthenticationLegacy("     ", "     "));

        for (final var basicAuth : blankBasicAuths) {
            Assertions.assertTrue(this.secretUtils.isBasicAuthNullOrBlank(basicAuth), "the basic authentication should have been considered as blank");
        }
    }

    /**
     * Tests that when a valid "basic authentication" object is passed, the
     * function returns that it isn't blank.
     */
    @Test
    void testIsBasicAuthenticationNullOrBlankIsNotBlank() {
        final BasicAuthentication basicAuth = new BasicAuthentication("username", "password");

        Assertions.assertFalse(this.secretUtils.isBasicAuthenticationNullOrBlank(basicAuth), "the basic authentication should have not been considered as blank");
    }

    /**
     * Tests that when a blank "password" and "username" combination is given, the "basic authentication" object is
     * considered blank.
     */
    @Test
    void testIsBasicAuthenticationNullOrBlankIsBlank() {
        final List<BasicAuthentication> blankBasicAuths = new ArrayList<>();

        blankBasicAuths.add(new BasicAuthentication());
        blankBasicAuths.add(new BasicAuthentication(null, null));
        blankBasicAuths.add(new BasicAuthentication(null, ""));
        blankBasicAuths.add(new BasicAuthentication("", null));
        blankBasicAuths.add(new BasicAuthentication("", ""));
        blankBasicAuths.add(new BasicAuthentication(null, "     "));
        blankBasicAuths.add(new BasicAuthentication("     ", null));
        blankBasicAuths.add(new BasicAuthentication("     ", "     "));

        for (final BasicAuthentication basicAuth : blankBasicAuths) {
            Assertions.assertTrue(this.secretUtils.isBasicAuthenticationNullOrBlank(basicAuth), "the basic authentication should have been considered as blank");
        }
    }

    /**
     * Tests that the properties that support Sources secrets are correctly
     * identified as such.
     */
    @Test
    void testInstanceOfSourcesSecretable() {
        final Endpoint camelEndpoint = new Endpoint();
        camelEndpoint.setProperties(new CamelProperties());

        final Endpoint webhookEndpoint = new Endpoint();
        webhookEndpoint.setProperties(new WebhookProperties());

        final List<Endpoint> testCases = List.of(
            camelEndpoint,
            webhookEndpoint
        );

        for (final Endpoint testCase : testCases) {
            Assertions.assertTrue(SecretUtils.isSourcesSecretable(testCase), "the endpoint should have been identified as 'SourcesSecretable'");
        }
    }

    /**
     * Tests that the properties that do not support Sources secrets are
     * correctly identified as such.
     */
    @Test
    void testNotInstanceOfSourcesSecretable() {
        final Endpoint systemEndpoint = new Endpoint();
        systemEndpoint.setProperties(new SystemSubscriptionProperties());

        Assertions.assertFalse(SecretUtils.isSourcesSecretable(systemEndpoint), "the system endpoint should have been flagged as non 'SourcesSecretable'");

        final Endpoint endpoint = new Endpoint();
        Assertions.assertFalse(SecretUtils.isSourcesSecretable(endpoint), "the endpoint with no properties should have been flagged as non 'SourcesSecretable'");
    }

    /**
     * Test that when syncing properties, and both the legacy and the new ones
     * are specified, the legacy ones get overridden by the new ones.
     * @deprecated because the function under test will go away as soon as we
     * remove the legacy properties from the model.
     */
    @Deprecated
    @Test
    void testSyncLegacyPropertiesBothSecretsPresent() {
        final SourcesSecretable sourcesSecretable = new WebhookProperties();

        final BasicAuthenticationLegacy bal = new BasicAuthenticationLegacy(
            "username",
            "password"
        );

        final BasicAuthentication ba = new BasicAuthentication(
            "new-username",
            "new-password"
        );

        sourcesSecretable.setBasicAuthenticationLegacy(bal);
        sourcesSecretable.setBasicAuthentication(ba);

        final String bearerTokenLegacy = "bearerToken";
        final BearerToken bearerToken = new BearerToken("new-bearer-token");

        sourcesSecretable.setBearerAuthenticationLegacy(bearerTokenLegacy);
        sourcesSecretable.setBearerToken(bearerToken);

        final String secretTokenLegacy = "secretToken";
        final SecretToken secretToken = new SecretToken("new-secret-token");

        sourcesSecretable.setSecretTokenLegacy(secretTokenLegacy);
        sourcesSecretable.setSecretToken(secretToken);

        // Call the function under test.
        this.secretUtils.legacySyncProperties((EndpointProperties) sourcesSecretable);

        // Assert that the legacy basic authentication got overridden.
        Assertions.assertEquals(sourcesSecretable.getBasicAuthentication().getPassword(), sourcesSecretable.getBasicAuthenticationLegacy().getPassword(), "the password property for the legacy basic authentication object should have the same value as the new object when both properties are specified");
        Assertions.assertEquals(sourcesSecretable.getBasicAuthentication().getUsername(), sourcesSecretable.getBasicAuthenticationLegacy().getUsername(), "the username property for the legacy basic authentication object should have the same value as the new object when both properties are specified");

        // Assert that the legacy bearer token got overridden.
        Assertions.assertEquals(sourcesSecretable.getBearerToken().getToken(), sourcesSecretable.getBearerAuthenticationLegacy(), "the value for the legacy bearer token object should have the same value as the new object when both properties are specified");

        // Assert that the legacy secret token got overridden.
        Assertions.assertEquals(sourcesSecretable.getSecretToken().getToken(), sourcesSecretable.getSecretTokenLegacy(), "the value for the legacy secret token object should have the same value as the new object when both properties are specified");
    }

    /**
     * Test that when syncing properties, and only the legacy ones are
     * specified, the new ones are created.
     * @deprecated because the function under test will go away as soon as we
     * remove the legacy properties from the model.
     */
    @Deprecated
    @Test
    void testSyncLegacyPropertiesOnlyLegacySecretsPresent() {
        final SourcesSecretable sourcesSecretable = new WebhookProperties();

        final BasicAuthenticationLegacy bal = new BasicAuthenticationLegacy(
            "username",
            "password"
        );

        sourcesSecretable.setBasicAuthenticationLegacy(bal);

        final String bearerTokenLegacy = "bearerToken";

        sourcesSecretable.setBearerAuthenticationLegacy(bearerTokenLegacy);

        final String secretTokenLegacy = "secretToken";

        sourcesSecretable.setSecretTokenLegacy(secretTokenLegacy);

        // Call the function under test.
        this.secretUtils.legacySyncProperties((EndpointProperties) sourcesSecretable);

        // Assert that the new basic authentication got created.
        Assertions.assertEquals(sourcesSecretable.getBasicAuthenticationLegacy().getPassword(), sourcesSecretable.getBasicAuthentication().getPassword(), "the password property for the new basic authentication object should have been created when only the legacy basic authentication object was specified");
        Assertions.assertEquals(sourcesSecretable.getBasicAuthenticationLegacy().getUsername(), sourcesSecretable.getBasicAuthentication().getUsername(), "the username property for the new basic authentication object should have been created when only the legacy basic authentication object was specified");

        // Assert that the new bearer token got created.
        Assertions.assertEquals(sourcesSecretable.getBearerAuthenticationLegacy(), sourcesSecretable.getBearerToken().getToken(), "the new bearer token object should have been created when only the legacy one was specified");

        // Assert that the legacy secret token got overridden.
        Assertions.assertEquals(sourcesSecretable.getSecretTokenLegacy(), sourcesSecretable.getSecretToken().getToken(), "the new secret token object should have been created when only the legacy one was specified");
    }

    /**
     * Test that when syncing properties, and only the new ones are specified,
     * the legacy ones get created.
     * @deprecated because the function under test will go away as soon as we
     * remove the legacy properties from the model.
     */
    @Deprecated
    @Test
    void testSyncLegacyPropertiesOnlyNewOnesPresent() {
        final SourcesSecretable sourcesSecretable = new WebhookProperties();

        final BasicAuthentication ba = new BasicAuthentication(
            "new-username",
            "new-password"
        );

        sourcesSecretable.setBasicAuthentication(ba);

        final BearerToken bearerToken = new BearerToken("new-bearer-token");

        sourcesSecretable.setBearerToken(bearerToken);

        final SecretToken secretToken = new SecretToken("new-secret-token");

        sourcesSecretable.setSecretToken(secretToken);

        // Call the function under test.
        this.secretUtils.legacySyncProperties((EndpointProperties) sourcesSecretable);

        // Assert that the new basic authentication got created.
        Assertions.assertEquals(sourcesSecretable.getBasicAuthentication().getPassword(), sourcesSecretable.getBasicAuthenticationLegacy().getPassword(), "the password property for the legacy basic authentication object should have been created when only the new basic authentication object was specified");
        Assertions.assertEquals(sourcesSecretable.getBasicAuthentication().getUsername(), sourcesSecretable.getBasicAuthenticationLegacy().getUsername(), "the password property for the legacy basic authentication object should have been created when only the new basic authentication object was specified");

        // Assert that the new bearer token got created.
        Assertions.assertEquals(sourcesSecretable.getBearerToken().getToken(), sourcesSecretable.getBearerAuthenticationLegacy(), "the legacy bearer token object should have been created when only the new one was specified");

        // Assert that the new secret token got created.
        Assertions.assertEquals(sourcesSecretable.getSecretToken().getToken(), sourcesSecretable.getSecretTokenLegacy(), "the legacy secret token object should have been created when only the new one was specified");
    }
}
