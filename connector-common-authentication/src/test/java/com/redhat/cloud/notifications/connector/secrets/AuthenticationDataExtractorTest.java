package com.redhat.cloud.notifications.connector.secrets;

import com.redhat.cloud.notifications.connector.authentication.AuthenticationDataExtractor;
import com.redhat.cloud.notifications.connector.authentication.AuthenticationType;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.AUTHENTICATION_TYPE;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_ID;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationType.BASIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class AuthenticationDataExtractorTest extends CamelQuarkusTestSupport {

    @Inject
    AuthenticationDataExtractor authenticationDataExtractor;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testValidAuthentication() {

        Exchange exchange = createExchangeWithBody("");
        JsonObject authentication = buildAuthentication("BASIC", 123L);

        authenticationDataExtractor.extract(exchange, authentication);
        assertEquals(BASIC, exchange.getProperty(AUTHENTICATION_TYPE, AuthenticationType.class));
        assertEquals(123L, exchange.getProperty(SECRET_ID, Long.class));
    }

    @Test
    void testNullType() {

        Exchange exchange = createExchangeWithBody("");
        JsonObject authentication = buildAuthentication(null, 123L);

        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                authenticationDataExtractor.extract(exchange, authentication));
        assertEquals("Invalid payload: the authentication type is missing", e.getMessage());
    }

    @Test
    void testUnknownType() {

        Exchange exchange = createExchangeWithBody("");
        JsonObject authentication = buildAuthentication("UNKNOWN", 123L);

        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                authenticationDataExtractor.extract(exchange, authentication));
        assertTrue(e.getMessage().startsWith("Invalid payload: the authentication type is unknown (UNKNOWN)"));
    }

    @Test
    void testNullSecretId() {

        Exchange exchange = createExchangeWithBody("");
        JsonObject authentication = buildAuthentication("BASIC", null);

        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                authenticationDataExtractor.extract(exchange, authentication));
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
        return authentication;
    }
}
