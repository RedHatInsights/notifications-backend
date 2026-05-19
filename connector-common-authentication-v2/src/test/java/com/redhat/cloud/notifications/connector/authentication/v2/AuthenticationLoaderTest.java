package com.redhat.cloud.notifications.connector.authentication.v2;

import com.redhat.cloud.notifications.connector.authentication.v2.sources.SourcesOidcClient;
import com.redhat.cloud.notifications.connector.authentication.v2.sources.SourcesPskClient;
import com.redhat.cloud.notifications.connector.authentication.v2.sources.SourcesSecretResponse;
import com.redhat.cloud.notifications.connector.v2.ConnectorConfig;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
public class AuthenticationLoaderTest {

    @Inject
    AuthenticationLoader secretsLoader;

    @InjectMock
    @RestClient
    SourcesPskClient sourcesClient;

    @InjectMock
    @RestClient
    SourcesOidcClient sourcesOidcClient;

    @InjectMock
    ConnectorConfig connectorConfig;

    @Test
    void testNoSecretId() {
        Optional<AuthenticationResult> secret = secretsLoader.fetchAuthenticationData("", null);
        verify(sourcesClient, never()).getById(anyString(), anyString(), anyLong());
        assertTrue(secret.isEmpty());
    }

    @Test
    void testWithSecretId() {
        SourcesSecretResponse sourcesSecret = new SourcesSecretResponse();
        sourcesSecret.username = "john_doe";
        sourcesSecret.password = "passw0rd";
        when(sourcesClient.getById(anyString(), anyString(), eq(123L))).thenReturn(sourcesSecret);

        Optional<AuthenticationResult> secretResult = secretsLoader.fetchAuthenticationData("default_org", buildAuthentication(AuthenticationType.SECRET_TOKEN.name(), 123L));

        verify(sourcesClient, times(1)).getById(anyString(), anyString(), eq(123L));
        assertTrue(secretResult.isPresent());
        assertEquals(sourcesSecret.username, secretResult.get().username);
        assertEquals(sourcesSecret.password, secretResult.get().password);
        assertEquals(AuthenticationType.SECRET_TOKEN, secretResult.get().authenticationType);
    }

    @Test
    void testNullType() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
            secretsLoader.fetchAuthenticationData("", buildAuthentication(null, 123L)));
        assertEquals("Invalid payload: the authentication type is missing or unknown", e.getMessage());
    }

    @Test
    void testUnknownType() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
            secretsLoader.fetchAuthenticationData("", buildAuthentication("UNKNOWN", 123L)));
        assertTrue(e.getMessage().contains("from String \"UNKNOWN\": not one of the values accepted for Enum class"));
    }

    @Test
    void testNullSecretId() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
            secretsLoader.fetchAuthenticationData("",  buildAuthentication("SECRET_TOKEN", null)));
        assertEquals("Invalid payload: the secret ID is missing", e.getMessage());
    }

    @Test
    void testBlankPassword() {
        SourcesSecretResponse sourcesSecret = new SourcesSecretResponse();
        sourcesSecret.username = "john_doe";
        sourcesSecret.password = "  ";
        when(sourcesClient.getById(anyString(), anyString(), eq(123L))).thenReturn(sourcesSecret);

        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
            secretsLoader.fetchAuthenticationData("default_org", buildAuthentication(AuthenticationType.SECRET_TOKEN.name(), 123L)));

        assertEquals("Invalid secret: password is missing", e.getMessage());
    }

    @Test
    void testNullPassword() {
        SourcesSecretResponse sourcesSecret = new SourcesSecretResponse();
        sourcesSecret.username = "john_doe";
        sourcesSecret.password = null;
        when(sourcesClient.getById(anyString(), anyString(), eq(123L))).thenReturn(sourcesSecret);

        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
            secretsLoader.fetchAuthenticationData("default_org", buildAuthentication(AuthenticationType.SECRET_TOKEN.name(), 123L)));

        assertEquals("Invalid secret: password is missing", e.getMessage());
    }

    @Test
    void testBlankUsername() {
        SourcesSecretResponse sourcesSecret = new SourcesSecretResponse();
        sourcesSecret.username = "  ";
        sourcesSecret.password = "passw0rd";
        when(sourcesClient.getById(anyString(), anyString(), eq(123L))).thenReturn(sourcesSecret);

        Optional<AuthenticationResult> secretResult = secretsLoader.fetchAuthenticationData("default_org", buildAuthentication(AuthenticationType.SECRET_TOKEN.name(), 123L));

        assertTrue(secretResult.isPresent());
        assertEquals("  ", secretResult.get().username);
        assertEquals("passw0rd", secretResult.get().password);
    }

    @Test
    void testNullUsername() {
        SourcesSecretResponse sourcesSecret = new SourcesSecretResponse();
        sourcesSecret.username = null;
        sourcesSecret.password = "passw0rd";
        when(sourcesClient.getById(anyString(), anyString(), eq(123L))).thenReturn(sourcesSecret);

        Optional<AuthenticationResult> secretResult = secretsLoader.fetchAuthenticationData("default_org", buildAuthentication(AuthenticationType.SECRET_TOKEN.name(), 123L));

        assertTrue(secretResult.isPresent());
        assertEquals(null, secretResult.get().username);
        assertEquals("passw0rd", secretResult.get().password);
    }

    @Test
    void testOidcAuthenticationFlow() {
        when(connectorConfig.isSourcesHccClusterEnabled(anyString())).thenReturn(true);

        SourcesSecretResponse sourcesSecret = new SourcesSecretResponse();
        sourcesSecret.username = "john_doe";
        sourcesSecret.password = "oidc_passw0rd";
        when(sourcesOidcClient.getById(anyString(), eq(123L))).thenReturn(sourcesSecret);

        Optional<AuthenticationResult> secretResult = secretsLoader.fetchAuthenticationData("oidc_org", buildAuthentication(AuthenticationType.BEARER.name(), 123L));

        verify(sourcesOidcClient, times(1)).getById(anyString(), eq(123L));
        verify(sourcesClient, never()).getById(anyString(), anyString(), anyLong());
        assertTrue(secretResult.isPresent());
        assertEquals(sourcesSecret.username, secretResult.get().username);
        assertEquals(sourcesSecret.password, secretResult.get().password);
        assertEquals(AuthenticationType.BEARER, secretResult.get().authenticationType);
    }

    private static JsonObject buildAuthentication(String type, Long secretId) {
        JsonObject authenticationParameters = new JsonObject();
        if (type != null) {
            authenticationParameters.put("type", type);
        }
        if (secretId != null) {
            authenticationParameters.put("secretId", secretId);
        }
        return authenticationParameters;
    }
}
