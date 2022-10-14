package com.redhat.cloud.notifications.routers.sources;

import com.redhat.cloud.notifications.models.BasicAuthentication;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.SourcesSecretable;
import com.redhat.cloud.notifications.models.WebhookProperties;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.inject.Inject;

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

        // Set up the mock calls for the "get by id" calls from the REST Client.
        Mockito.when(this.sourcesServiceMock.getById(BASIC_AUTH_SOURCES_ID)).thenReturn(basicAuthenticationMock);
        Mockito.when(this.sourcesServiceMock.getById(SECRET_TOKEN_SOURCES_ID)).thenReturn(secretTokenMock);

        // Create an endpoint that contains the expected data by the function under test.
        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();

        webhookProperties.setBasicAuthenticationSourcesId(BASIC_AUTH_SOURCES_ID);
        webhookProperties.setSecretTokenSourcesId(SECRET_TOKEN_SOURCES_ID);

        endpoint.setProperties(webhookProperties);

        // Call the function under test.
        this.secretUtils.getSecretsForEndpoint(endpoint);

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
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).getById(BASIC_AUTH_SOURCES_ID);
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).getById(SECRET_TOKEN_SOURCES_ID);
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
        final int wantedNumberOfInvocations = 0;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).getById(Mockito.anyInt());
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

        // Set up the mock calls for the "create" calls from the REST Client. Make sure we return the basic
        // authentication's ID first, and the secret token's ID second, since we are expecting a successful create
        // operation.
        Mockito.when(this.sourcesServiceMock.create(Mockito.any())).thenReturn(basicAuthenticationMock, secretTokenMock);

        // Create an endpoint that contains the expected data by the function under test.
        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();
        BasicAuthentication basicAuth = new BasicAuthentication(BASIC_AUTH_USERNAME, BASIC_AUTH_PASSWORD);

        webhookProperties.setBasicAuthentication(basicAuth);
        webhookProperties.setSecretToken(SECRET_TOKEN);

        endpoint.setProperties(webhookProperties);

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
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).create(Mockito.any());
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

        // Set up the mock call for the "create" call from the REST Client. In this case, since the basic
        // authentication is null, only the secret token should be created.
        Mockito.when(this.sourcesServiceMock.create(Mockito.any())).thenReturn(secretTokenMock);

        // Create an endpoint that contains the expected data by the function under test.
        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();
        webhookProperties.setSecretToken(SECRET_TOKEN);

        endpoint.setProperties(webhookProperties);

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
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).create(Mockito.any());
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

        // Set up the mock call for the "create" call from the REST Client. Since only the "basic authentication"
        // secret is supposed to be created, that's the one we are expecting to get from the mocked service.
        Mockito.when(this.sourcesServiceMock.create(Mockito.any())).thenReturn(basicAuthenticationMock);

        // Create an endpoint that contains the expected data by the function under test.
        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();
        BasicAuthentication basicAuth = new BasicAuthentication(BASIC_AUTH_USERNAME, BASIC_AUTH_PASSWORD);

        webhookProperties.setBasicAuthentication(basicAuth);

        endpoint.setProperties(webhookProperties);

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
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).create(Mockito.any());
    }

    /**
     * Tests that the function under test calls "update" upon Sources two times, one for the "basic authentication" and
     * another one for the "secret token". It also checks that the responses are properly marshalled into the right
     * endpoint properties.
     */
    @Test
    void updateSecretsForEndpointTest() {
        // Create a "Basic Authentication" secret mock.
        final Secret basicAuthenticationMock = new Secret();
        basicAuthenticationMock.password = BASIC_AUTH_PASSWORD;
        basicAuthenticationMock.username = BASIC_AUTH_USERNAME;

        // Create a "secret token" secret mock.
        final Secret secretTokenMock = new Secret();
        secretTokenMock.password = SECRET_TOKEN;

        // Set up the mock calls to return the "basic authentication" and the "secret token" secrets which are supposed
        // to be updated.
        Mockito.when(this.sourcesServiceMock.getById(BASIC_AUTH_SOURCES_ID)).thenReturn(basicAuthenticationMock);
        Mockito.when(this.sourcesServiceMock.getById(SECRET_TOKEN_SOURCES_ID)).thenReturn(secretTokenMock);

        // Create an endpoint that contains the expected data by the function under test.
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
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocationsUpdate)).update(Mockito.eq(BASIC_AUTH_SOURCES_ID), Mockito.any());
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocationsUpdate)).update(Mockito.eq(SECRET_TOKEN_SOURCES_ID), Mockito.any());
    }

    /**
     * Tests that when the "basic authentication" object is {@code NULL} or its Sources ID is zero —which implies that
     * there is no ID for that object—, the "update" function gets called only for the "secret token" property.
     */
    @Test
    void updateSecretsForEndpointBasicAuthNullZeroIdsTest() {
        // Create a "secret token" secret mock.
        final Secret secretTokenMock = new Secret();
        secretTokenMock.password = SECRET_TOKEN;

        // Set up the mock call to return the "secret token" secret which is supposed to be updated.
        Mockito.when(this.sourcesServiceMock.getById(SECRET_TOKEN_SOURCES_ID)).thenReturn(secretTokenMock);

        final BasicAuthentication[] basicAuthTestValues = {null, new BasicAuthentication()};
        for (final BasicAuthentication tv : basicAuthTestValues) {
            // Create an endpoint that contains the expected data by the function under test.
            Endpoint endpoint = new Endpoint();
            WebhookProperties webhookProperties = new WebhookProperties();

            // Set the updated fields of the "secret token" field so that the function under test's logic picks it
            // up for an update.
            final String updatedSecretToken = String.format("%s-updated", SECRET_TOKEN);

            webhookProperties.setBasicAuthentication(tv);

            webhookProperties.setSecretToken(updatedSecretToken);
            webhookProperties.setSecretTokenSourcesId(SECRET_TOKEN_SOURCES_ID);

            endpoint.setProperties(webhookProperties);

            // Call the function under test.
            this.secretUtils.updateSecretsForEndpoint(endpoint);

            // Check that the endpoint properties are of the expected type.
            final var endpointProperties = endpoint.getProperties();
            if (!(endpointProperties instanceof SourcesSecretable)) {
                Assertions.fail("unexpected type of the properties found. Want " + SourcesSecretable.class + ", got " + endpointProperties.getClass());
            }

            final var props = (SourcesSecretable) endpoint.getProperties();

            // Assert the results.
            final String secretToken = props.getSecretToken();
            Assertions.assertEquals(updatedSecretToken, secretToken, "the updated secret token doesn't match");

            // Reset the "secret token" secret's password to the previous value, so that an update operation is
            // simulated again.
            secretTokenMock.password = SECRET_TOKEN;
        }

        // Assert that the underlying "update" function was called exactly two times, since only the "secret token"
        // secret should be updated.
        final int wantedNumberOfInvocations = 2;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).update(Mockito.eq(SECRET_TOKEN_SOURCES_ID), Mockito.any());
    }

    /**
     * Tests that when the "secret token" property is {@code NULL} or its Sources ID is zero —which implies that there
     * is no ID for that property—, the "update" function gets called only for the "basic authentication" property.
     */
    @Test
    void updateSecretsForEndpointSecretTokenNullZeroIdsTest() {
        // Create a "Basic Authentication" secret mock.
        final Secret basicAuthenticationMock = new Secret();
        basicAuthenticationMock.password = BASIC_AUTH_PASSWORD;
        basicAuthenticationMock.username = BASIC_AUTH_USERNAME;

        // Create a "secret token" secret mock.
        final Secret secretTokenMock = new Secret();
        secretTokenMock.password = SECRET_TOKEN;

        // Set up the mock calls to return the "basic authentication" and the "secret token" secrets which are supposed
        // to be updated.
        Mockito.when(this.sourcesServiceMock.getById(BASIC_AUTH_SOURCES_ID)).thenReturn(basicAuthenticationMock);
        Mockito.when(this.sourcesServiceMock.getById(SECRET_TOKEN_SOURCES_ID)).thenReturn(secretTokenMock);

        final String[] secretTokenTestValues = {null, SECRET_TOKEN};
        for (final String tv : secretTokenTestValues) {
            // Create an endpoint that contains the expected data by the function under test.
            Endpoint endpoint = new Endpoint();
            WebhookProperties webhookProperties = new WebhookProperties();

            // Set the updated fields of the "basic authentication" object so that the function under test's logic picks it
            // up for an update.
            final String updatedUsername = String.format("%s-updated", BASIC_AUTH_USERNAME);
            final String updatedPassword = String.format("%s-updated", BASIC_AUTH_PASSWORD);

            BasicAuthentication basicAuth = new BasicAuthentication(updatedUsername, updatedPassword);
            webhookProperties.setBasicAuthentication(basicAuth);
            webhookProperties.setBasicAuthenticationSourcesId(BASIC_AUTH_SOURCES_ID);

            webhookProperties.setSecretToken(tv);

            endpoint.setProperties(webhookProperties);

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

            // Reset the "basic authentication" secret's password and username fields to the previous values, so that
            // an update operation is simulated again.
            basicAuthenticationMock.password = BASIC_AUTH_PASSWORD;
            basicAuthenticationMock.username = BASIC_AUTH_USERNAME;
        }

        // Assert that the underlying "update" function was called exactly two times, since only the "basic
        // authentication" secret should be updated.
        final int wantedNumberOfInvocationsUpdate = 2;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocationsUpdate)).update(Mockito.eq(BASIC_AUTH_SOURCES_ID), Mockito.any());
    }

    /**
     * Tests that the "delete" function from the function under test gets called for both the "basic authentication"
     * and "secret token" secrets.
     */
    @Test
    void deleteSecretsForEndpointTest() {
        // Create an endpoint that contains the expected data by the function under test.
        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();

        webhookProperties.setBasicAuthenticationSourcesId(BASIC_AUTH_SOURCES_ID);
        webhookProperties.setSecretTokenSourcesId(SECRET_TOKEN_SOURCES_ID);

        endpoint.setProperties(webhookProperties);

        // Call the function under test.
        this.secretUtils.deleteSecretsForEndpoint(endpoint);

        // Assert that the underlying "delete" function was called exactly one time, since we are expecting a successful
        // deletion for both secrets.
        final int wantedNumberOfInvocations = 1;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).delete(BASIC_AUTH_SOURCES_ID);
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).delete(SECRET_TOKEN_SOURCES_ID);
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
        // nor the "secret token" have valid IDs set.s
        final int wantedNumberOfInvocations = 0;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).delete(Mockito.anyInt());
    }
}
