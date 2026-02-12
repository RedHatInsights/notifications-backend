package com.redhat.cloud.notifications.connector.authentication.v2;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.Optional;

import static com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationType.SECRET_TOKEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class AuthenticationDataExtractorTest {

    @Test
    void testValidAuthentication() {

        JsonObject authentication = buildAuthentication("SECRET_TOKEN", 123L);

        Optional<AuthenticationRequest> sourcesSecretRequest = AuthenticationDataExtractor.extract(authentication);
        assertTrue(sourcesSecretRequest.isPresent());
        assertEquals(SECRET_TOKEN, sourcesSecretRequest.get().authenticationType);
        assertEquals(123L, sourcesSecretRequest.get().secretId);
    }

    @Test
    void testNullType() {
        JsonObject authentication = buildAuthentication(null, 123L);

        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
            AuthenticationDataExtractor.extract(authentication));
        assertEquals("Invalid payload: the authentication type is missing", e.getMessage());
    }

    @Test
    void testUnknownType() {
        JsonObject authentication = buildAuthentication("UNKNOWN", 123L);

        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
            AuthenticationDataExtractor.extract(authentication));
        assertTrue(e.getMessage().startsWith("Invalid payload: the authentication type is unknown (UNKNOWN)"));
    }

    @Test
    void testNullSecretId() {
        JsonObject authentication = buildAuthentication("SECRET_TOKEN", null);

        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
            AuthenticationDataExtractor.extract(authentication));
        assertEquals("Invalid payload: the secret ID is missing", e.getMessage());
    }

    private static JsonObject buildAuthentication(String type, Long secretId) {
        JsonObject authentication = new JsonObject();
        if (type != null) {
            authentication.put("type", type);
        }
        if (secretId != null) {
            authentication.put("secretId", secretId);
        }
        JsonObject payload = new JsonObject();
        payload.put("authentication", authentication);
        return payload;
    }
}
