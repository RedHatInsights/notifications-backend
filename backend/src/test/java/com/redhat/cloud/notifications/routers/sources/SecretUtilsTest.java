package com.redhat.cloud.notifications.routers.sources;

import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.SourcesSecretable;
import com.redhat.cloud.notifications.models.WebhookProperties;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@QuarkusTest
public class SecretUtilsTest {

    static final String SECRET_TOKEN = "secret-token";
    static final String BEARER_AUTHENTICATION = "bearer-test-authentication";
    static final Long SECRET_TOKEN_SOURCES_ID = 100L;
    static final Long BEARER_TOKEN_SOURCES_ID = 150L;

    @InjectMock
    @RestClient
    SourcesPskService sourcesServiceMock;

    @Inject
    SecretUtils secretUtils;

    /**
     * Required to set up the mock calls to the sources service mock.
     */
    @ConfigProperty(name = "sources.psk")
    String sourcesPsk;

    /**
     * Tests that the underlying "get by id" function gets called two times: one for the bearer authentication and
     * another one for the secret token. It also tests that the endpoint's properties hold the secrets from the
     * returned payload from Sources.
     */
    @Test
    void getSecretsForEndpointTest() {
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

        webhookProperties.setSecretTokenSourcesId(SECRET_TOKEN_SOURCES_ID);
        webhookProperties.setBearerAuthenticationSourcesId(BEARER_TOKEN_SOURCES_ID);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Set up the mock calls for the "get by id" calls from the REST Client.
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
        final String secretToken = props.getSecretToken();
        Assertions.assertEquals(SECRET_TOKEN, secretToken, "the secret token doesn't match");

        final String secretBearer = props.getBearerAuthentication();
        Assertions.assertEquals(BEARER_AUTHENTICATION, secretBearer, "the secret bearer doesn't match");

        // Assert that the underlying function was called exactly three times,
        // since we are expecting that "secret token" and "bearer authentication"
        // secrets were fetched.
        final int wantedNumberOfInvocations = 1;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).getById(orgId, this.sourcesPsk, SECRET_TOKEN_SOURCES_ID);
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).getById(orgId, this.sourcesPsk, SECRET_TOKEN_SOURCES_ID);
    }

    /**
     * Tests that when the endpoint's properties doesn't have a Sources ID for the "secret
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
     * Tests that when the endpoint's properties has the "bearer authentication" and the "secret token" properties set,
     * the function under test calls "create" upon Sources two times, and that the returned IDs are properly set on the
     * endpoint's properties.
     */
    @Test
    void createSecretsForEndpointTest() {
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

        webhookProperties.setSecretToken(SECRET_TOKEN);
        webhookProperties.setBearerAuthentication(BEARER_AUTHENTICATION);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Set up the mock calls for the "create" calls from the REST Client.
        // Make sure we return the secret token's ID first, and the bearer
        // authentication's ID second, since we are expecting a successful
        // create operation.
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
        Assertions.assertEquals(BEARER_TOKEN_SOURCES_ID, props.getBearerAuthenticationSourcesId(), "the ID of the secret bearer from Sources doesn't match");

        // Assert that the underlying function was called exactly two times,
        // since we are expecting that the "secret
        // token" and "bearer authentication" secrets were created.
        final int wantedNumberOfInvocations = 2;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).create(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.any());
    }

    /**
     * Tests that the function under test calls "update" upon Sources two times, one for the "bearer authentication" and
     * another one for the "secret token". It also checks that the responses are properly marshalled into the right
     * endpoint properties.
     */
    @Test
    void updateSecretsForEndpointTest() {
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
        final String updatedSecretToken = String.format("%s-updated", SECRET_TOKEN);
        final String updatedBearer = String.format("%s-updated", BEARER_AUTHENTICATION);

        webhookProperties.setSecretToken(updatedSecretToken);
        webhookProperties.setSecretTokenSourcesId(SECRET_TOKEN_SOURCES_ID);

        webhookProperties.setBearerAuthentication(updatedBearer);
        webhookProperties.setBearerAuthenticationSourcesId(BEARER_TOKEN_SOURCES_ID);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Set up the mock calls to return the "bearer authentication" and the "secret token" secrets which are supposed
        // to be updated.
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
        final String secretToken = props.getSecretToken();
        Assertions.assertEquals(updatedSecretToken, secretToken, "the updated secret token doesn't match");

        final String bearerToken = props.getBearerAuthentication();
        Assertions.assertEquals(updatedBearer, bearerToken, "the updated bearer doesn't match");

        // Assert that the underlying "update" function was called exactly two times, since we are expecting that both
        // the "bearer authentication" and the "secret token" secrets were successfully updated.
        final int wantedNumberOfInvocationsUpdate = 1;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocationsUpdate)).update(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.eq(SECRET_TOKEN_SOURCES_ID), Mockito.any());
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocationsUpdate)).update(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.eq(BEARER_TOKEN_SOURCES_ID), Mockito.any());
    }

    /**
     * Tests that when the user provides a {@code null} "bearer authentication" and "secret token" secret for an
     * endpoint which has secrets stored in Sources, the deletion process for those secrets is triggered.
     */
    @Test
    void updateSecretsForEndpointDeleteTest() {
        // Create an endpoint that contains the expected data by the function under test.
        final String orgId = "update-secrets-for-endpoint-delete-test-organization-id";

        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();

        // Simulate that we are sending an empty "bearer authentication" and "secret token", but that we have those
        // IDs on the database. This should trigger the deletion of these secrets on Sources.
        webhookProperties.setSecretTokenSourcesId(SECRET_TOKEN_SOURCES_ID);
        webhookProperties.setBearerAuthenticationSourcesId(BEARER_TOKEN_SOURCES_ID);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Call the function under test.
        this.secretUtils.updateSecretsForEndpoint(endpoint);

        // The IDs of the properties should be null now.
        Assertions.assertNull(webhookProperties.getSecretTokenSourcesId(), "the secret token's Sources ID isn't null");
        Assertions.assertNull(webhookProperties.getBearerAuthenticationSourcesId(), "the bearer Sources ID isn't null");

        // It should have triggered two "delete" calls to Sources to delete both of the secrets.
        final int wantedNumberOfInvocations = 1;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).delete(orgId, this.sourcesPsk, SECRET_TOKEN_SOURCES_ID);
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).delete(orgId, this.sourcesPsk, BEARER_TOKEN_SOURCES_ID);
    }

    /**
     * Tests that when the client updates an endpoint, but there are no secrets stored in Sources, or no valid secrets
     * provided by the client, basically a NOP happens. For a valid secret we understand it as a secret token that is
     * not blank.
     */
    @Test
    void updateSecretsForEndpointNopTest() {
        // Create an endpoint that contains the expected data by the function under test.
        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();
        endpoint.setProperties(webhookProperties);

        this.secretUtils.updateSecretsForEndpoint(endpoint);

        Mockito.verifyNoInteractions(this.sourcesServiceMock);

        // Set the secret token as a blank value. It should also be a NOOP.
        webhookProperties.setSecretToken("     ");
        this.secretUtils.updateSecretsForEndpoint(endpoint);

        Mockito.verifyNoInteractions(this.sourcesServiceMock);
    }

    @Test
    void updateSecretsForEndpointCreateTest() {
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

        webhookProperties.setSecretToken(SECRET_TOKEN);
        webhookProperties.setBearerAuthentication(BEARER_AUTHENTICATION);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Set up the mock calls for the "create" calls from the REST Client.
        // Make sure we return the secret token's ID first and the bearer
        // authentication's ID second, since we are expecting a successful
        // create operation.
        Mockito.when(this.sourcesServiceMock.create(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.any())).thenReturn(secretTokenMock, bearerMock);

        // Call the function under test.
        this.secretUtils.updateSecretsForEndpoint(endpoint);

        // Check that the endpoint properties are of the expected type.
        final var endpointProperties = endpoint.getProperties();
        if (!(endpointProperties instanceof SourcesSecretable)) {
            Assertions.fail("unexpected type of the properties found. Want " + SourcesSecretable.class + ", got " + endpointProperties.getClass());
        }

        final var props = (WebhookProperties) endpointProperties;

        // Assert the results.
        Assertions.assertEquals(SECRET_TOKEN_SOURCES_ID, props.getSecretTokenSourcesId(), "the ID of the secret token's secret from Sources doesn't match");
        Assertions.assertEquals(BEARER_TOKEN_SOURCES_ID, props.getBearerAuthenticationSourcesId(), "the ID of the bearer secret from Sources doesn't match");

        // Assert that the underlying function was called exactly two times,
        // since we are expecting that the "secret token" and "bearer authentication"
        // secrets were created.
        final int wantedNumberOfInvocations = 2;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).create(Mockito.eq(orgId), Mockito.eq(this.sourcesPsk), Mockito.any());
    }

    /**
     * Tests that the "delete" function from the function under test gets called for both the "bearer authentication"
     * and "secret token" secrets.
     */
    @Test
    void deleteSecretsForEndpointTest() {
        // Create an endpoint that contains the expected data by the function under test.
        final String orgId = "delete-secrets-for-endpoint-test-organization-id";

        Endpoint endpoint = new Endpoint();
        WebhookProperties webhookProperties = new WebhookProperties();

        webhookProperties.setSecretTokenSourcesId(SECRET_TOKEN_SOURCES_ID);
        webhookProperties.setBearerAuthenticationSourcesId(BEARER_TOKEN_SOURCES_ID);

        endpoint.setProperties(webhookProperties);
        endpoint.setOrgId(orgId);

        // Call the function under test.
        this.secretUtils.deleteSecretsForEndpoint(endpoint);

        // Assert that the underlying "delete" function was called exactly one time, since we are expecting a successful
        // deletion for both secrets.
        final int wantedNumberOfInvocations = 1;
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).delete(orgId, this.sourcesPsk, SECRET_TOKEN_SOURCES_ID);
        Mockito.verify(this.sourcesServiceMock, Mockito.times(wantedNumberOfInvocations)).delete(orgId, this.sourcesPsk, BEARER_TOKEN_SOURCES_ID);
    }

    /**
     * Tests that when the "bearer authentication" and "secret token" don't have associated Sources IDs, the "delete"
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

        // Assert that the underlying "delete" function wasn't called at all, since neither the "bearer authentication"
        // nor the "secret token" have valid IDs set.
        Mockito.verifyNoInteractions(this.sourcesServiceMock);
    }
}
