package com.redhat.cloud.notifications.connector.authentication.v2;

import com.redhat.cloud.notifications.connector.authentication.v2.sources.SourcesPskClient;
import com.redhat.cloud.notifications.connector.authentication.v2.sources.SourcesSecretResult;
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

    @Test
    void testNoSecretId() {
        secretsLoader.fetchAuthenticationData("", null);
        verify(sourcesClient, never()).getById(anyString(), anyString(), anyLong());
    }

    @Test
    void testWithSecretId() {
        SourcesSecretResult sourcesSecret = new SourcesSecretResult();
        sourcesSecret.username = "john_doe";
        sourcesSecret.password = "passw0rd";
        when(sourcesClient.getById(anyString(), anyString(), anyLong())).thenReturn(sourcesSecret);

        Optional<AuthenticationResult> secretResult = secretsLoader.fetchAuthenticationData("default_org", buildAuthentication(AuthenticationType.SECRET_TOKEN.name(), 123L));

        verify(sourcesClient, times(1)).getById(anyString(), anyString(), anyLong());
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
