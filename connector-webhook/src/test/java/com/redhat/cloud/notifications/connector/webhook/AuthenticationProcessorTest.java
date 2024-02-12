package com.redhat.cloud.notifications.connector.webhook;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.AUTHENTICATION_TYPE;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_PASSWORD;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_USERNAME;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationType.BASIC;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationType.BEARER;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationType.SECRET_TOKEN;
import static com.redhat.cloud.notifications.connector.webhook.AuthenticationProcessor.X_INSIGHT_TOKEN_HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class AuthenticationProcessorTest extends CamelQuarkusTestSupport {

    @Inject
    AuthenticationProcessor authenticationProcessor;

    @Test
    void testBasicAuthWithNullProperties() {

        Exchange exchange = this.createExchangeWithBody("");
        exchange.setProperty(AUTHENTICATION_TYPE, BASIC);

        authenticationProcessor.process(exchange);
        assertNull(exchange.getIn().getHeader(AUTHORIZATION, String.class));

        exchange.setProperty(SECRET_USERNAME, null);
        exchange.setProperty(SECRET_PASSWORD, RandomStringUtils.randomAlphanumeric(10));

        authenticationProcessor.process(exchange);
        assertNull(exchange.getIn().getHeader(AUTHORIZATION, String.class));

        exchange.setProperty(SECRET_USERNAME, RandomStringUtils.randomAlphanumeric(10));
        exchange.setProperty(SECRET_PASSWORD, null);

        authenticationProcessor.process(exchange);
        assertNull(exchange.getIn().getHeader(AUTHORIZATION, String.class));
    }

    @Test
    void testValidBasicAuth() {

        Exchange exchange = this.createExchangeWithBody("");
        exchange.setProperty(AUTHENTICATION_TYPE, BASIC);

        String username = RandomStringUtils.randomAlphanumeric(10);
        String password = RandomStringUtils.randomAlphanumeric(10);
        exchange.setProperty(SECRET_USERNAME, username);
        exchange.setProperty(SECRET_PASSWORD, password);

        authenticationProcessor.process(exchange);
        assertTrue(StringUtils.isNotBlank(exchange.getIn().getHeader(AUTHORIZATION, String.class)));
        String basicAuth = exchange.getIn().getHeader(AUTHORIZATION, String.class);
        assertEquals(username + ":" + password, new String(Base64.getDecoder().decode(basicAuth.replace("Basic ", "").getBytes(UTF_8)), UTF_8));
    }

    @Test
    void testBearerAuthWithNullProperties() {

        Exchange exchange = this.createExchangeWithBody("");
        exchange.setProperty(AUTHENTICATION_TYPE, BEARER);

        authenticationProcessor.process(exchange);
        assertNull(exchange.getIn().getHeader(AUTHORIZATION, String.class));

        exchange.setProperty(SECRET_PASSWORD, null);

        authenticationProcessor.process(exchange);
        assertNull(exchange.getIn().getHeader(AUTHORIZATION, String.class));
    }

    @Test
    void testValidBearerAuth() {

        Exchange exchange = this.createExchangeWithBody("");
        exchange.setProperty(AUTHENTICATION_TYPE, BEARER);

        exchange.setProperty(SECRET_PASSWORD, RandomStringUtils.randomAlphanumeric(10));

        authenticationProcessor.process(exchange);
        assertTrue(StringUtils.isNotBlank(exchange.getIn().getHeader(AUTHORIZATION, String.class)));
        assertEquals("Bearer " + exchange.getProperty(SECRET_PASSWORD), exchange.getIn().getHeader(AUTHORIZATION, String.class));
    }

    @Test
    void testSecretTokenAuthWithNullProperties() {

        Exchange exchange = this.createExchangeWithBody("");
        exchange.setProperty(AUTHENTICATION_TYPE, SECRET_TOKEN);

        authenticationProcessor.process(exchange);
        assertNull(exchange.getIn().getHeader(X_INSIGHT_TOKEN_HEADER, String.class));

        exchange.setProperty(SECRET_PASSWORD, null);

        authenticationProcessor.process(exchange);
        assertNull(exchange.getIn().getHeader(X_INSIGHT_TOKEN_HEADER, String.class));
    }

    @Test
    void testValidSecretTokenAuth() {

        Exchange exchange = this.createExchangeWithBody("");
        exchange.setProperty(AUTHENTICATION_TYPE, SECRET_TOKEN);

        exchange.setProperty(SECRET_PASSWORD, RandomStringUtils.randomAlphanumeric(10));

        authenticationProcessor.process(exchange);
        assertTrue(StringUtils.isNotBlank(exchange.getIn().getHeader(X_INSIGHT_TOKEN_HEADER, String.class)));
        assertEquals(exchange.getProperty(SECRET_PASSWORD), exchange.getIn().getHeader(X_INSIGHT_TOKEN_HEADER, String.class));
    }
}
