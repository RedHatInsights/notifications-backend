package com.redhat.cloud.notifications.connector.webhook;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.AUTHENTICATION_TYPE;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_PASSWORD;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationType.BEARER;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationType.SECRET_TOKEN;
import static com.redhat.cloud.notifications.connector.webhook.AuthenticationProcessor.X_INSIGHT_TOKEN_HEADER;
import static org.apache.camel.test.junit5.TestSupport.createExchangeWithBody;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class AuthenticationProcessorTest extends CamelQuarkusTestSupport {

    @Inject
    AuthenticationProcessor authenticationProcessor;

    @Test
    void testBearerAuthWithNullProperties() {

        Exchange exchange = createExchangeWithBody(context, "");
        exchange.setProperty(AUTHENTICATION_TYPE, BEARER);

        authenticationProcessor.process(exchange);
        assertNull(exchange.getIn().getHeader(AUTHORIZATION, String.class));

        exchange.setProperty(SECRET_PASSWORD, null);

        authenticationProcessor.process(exchange);
        assertNull(exchange.getIn().getHeader(AUTHORIZATION, String.class));
    }

    @Test
    void testValidBearerAuth() {

        Exchange exchange = createExchangeWithBody(context, "");
        exchange.setProperty(AUTHENTICATION_TYPE, BEARER);

        exchange.setProperty(SECRET_PASSWORD, RandomStringUtils.randomAlphanumeric(10));

        authenticationProcessor.process(exchange);
        assertTrue(StringUtils.isNotBlank(exchange.getIn().getHeader(AUTHORIZATION, String.class)));
        assertEquals("Bearer " + exchange.getProperty(SECRET_PASSWORD), exchange.getIn().getHeader(AUTHORIZATION, String.class));
    }

    @Test
    void testSecretTokenAuthWithNullProperties() {

        Exchange exchange = createExchangeWithBody(context, "");
        exchange.setProperty(AUTHENTICATION_TYPE, SECRET_TOKEN);

        authenticationProcessor.process(exchange);
        assertNull(exchange.getIn().getHeader(X_INSIGHT_TOKEN_HEADER, String.class));

        exchange.setProperty(SECRET_PASSWORD, null);

        authenticationProcessor.process(exchange);
        assertNull(exchange.getIn().getHeader(X_INSIGHT_TOKEN_HEADER, String.class));
    }

    @Test
    void testValidSecretTokenAuth() {

        Exchange exchange = createExchangeWithBody(context, "");
        exchange.setProperty(AUTHENTICATION_TYPE, SECRET_TOKEN);

        exchange.setProperty(SECRET_PASSWORD, RandomStringUtils.randomAlphanumeric(10));

        authenticationProcessor.process(exchange);
        assertTrue(StringUtils.isNotBlank(exchange.getIn().getHeader(X_INSIGHT_TOKEN_HEADER, String.class)));
        assertEquals(exchange.getProperty(SECRET_PASSWORD), exchange.getIn().getHeader(X_INSIGHT_TOKEN_HEADER, String.class));
    }
}
