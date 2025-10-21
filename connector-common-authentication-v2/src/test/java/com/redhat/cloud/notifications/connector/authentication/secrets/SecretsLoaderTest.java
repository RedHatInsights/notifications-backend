package com.redhat.cloud.notifications.connector.secrets;

import com.redhat.cloud.notifications.connector.authentication.AuthenticationResult;
import com.redhat.cloud.notifications.connector.authentication.AuthenticationType;
import com.redhat.cloud.notifications.connector.authentication.secrets.SecretsLoader;
import com.redhat.cloud.notifications.connector.authentication.secrets.SourcesPskClient;
import com.redhat.cloud.notifications.connector.authentication.secrets.SourcesSecret;
import com.redhat.cloud.notifications.connector.v2.BaseConnectorIntegrationTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.ce.IncomingCloudEventMetadata;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

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
public class SecretsLoaderTest {

    @Inject
    SecretsLoader secretsLoader;

    @InjectMock
    @RestClient
    SourcesPskClient sourcesClient;

    @Test
    void testNoSecretId() {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEventMetadata = buildIncomingCloudEvent(new JsonObject());
        secretsLoader.fetchAuthenticationData(incomingCloudEventMetadata);

        verify(sourcesClient, never()).getById(anyString(), anyString(), anyLong());
    }

    @Test
    void testWithSecretId() {
        SourcesSecret sourcesSecret = new SourcesSecret();
        sourcesSecret.username = "john_doe";
        sourcesSecret.password = "passw0rd";
        when(sourcesClient.getById(anyString(), anyString(), anyLong())).thenReturn(sourcesSecret);

        JsonObject cePayload = new JsonObject();
        cePayload.put("org_id", "default_org");
        cePayload.mergeIn(buildAuthentication(AuthenticationType.SECRET_TOKEN.name(), 123L));
        IncomingCloudEventMetadata<JsonObject> incomingCloudEventMetadata = buildIncomingCloudEvent(cePayload);

        Optional<AuthenticationResult> authenticationResult = secretsLoader.fetchAuthenticationData(incomingCloudEventMetadata);

        verify(sourcesClient, times(1)).getById(anyString(), anyString(), anyLong());
        assertTrue(authenticationResult.isPresent());
        assertTrue(authenticationResult.get().username().isPresent());
        assertEquals(sourcesSecret.username, authenticationResult.get().username().get());
        assertTrue(authenticationResult.get().password().isPresent());
        assertEquals(sourcesSecret.password, authenticationResult.get().password().get());
        assertEquals(AuthenticationType.SECRET_TOKEN, authenticationResult.get().type());
    }

    @Test
    void testNullType() {
        JsonObject authentication = buildAuthentication(null, 123L);
        IncomingCloudEventMetadata<JsonObject> incomingCloudEventMetadata = buildIncomingCloudEvent(authentication);

        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
            secretsLoader.fetchAuthenticationData(incomingCloudEventMetadata));
        assertEquals("Invalid payload: the authentication type is missing", e.getMessage());
    }

    @Test
    void testUnknownType() {
        JsonObject authentication = buildAuthentication("UNKNOWN", 123L);
        IncomingCloudEventMetadata<JsonObject> incomingCloudEventMetadata = buildIncomingCloudEvent(authentication);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
            secretsLoader.fetchAuthenticationData(incomingCloudEventMetadata));
        assertTrue(e.getMessage().startsWith("Cannot deserialize value of type `com.redhat.cloud.notifications.connector.authentication.AuthenticationType` from String \"UNKNOWN\": not one of the values accepted for Enum class"));
    }

    @Test
    void testNullSecretId() {
        JsonObject authentication = buildAuthentication("SECRET_TOKEN", null);
        IncomingCloudEventMetadata<JsonObject> incomingCloudEventMetadata = buildIncomingCloudEvent(authentication);

        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
            secretsLoader.fetchAuthenticationData(incomingCloudEventMetadata));
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
        JsonObject authentication = new JsonObject();
        authentication.put("authentication", authenticationParameters);
        return authentication;
    }

    private static IncomingCloudEventMetadata<JsonObject> buildIncomingCloudEvent(JsonObject cePayload) {
        IncomingCloudEventMetadata<JsonObject> incomingCloudEventMetadata = BaseConnectorIntegrationTest.buildIncomingCloudEvent(UUID.randomUUID().toString(), "ce_type", cePayload);
        return incomingCloudEventMetadata;
    }
}
