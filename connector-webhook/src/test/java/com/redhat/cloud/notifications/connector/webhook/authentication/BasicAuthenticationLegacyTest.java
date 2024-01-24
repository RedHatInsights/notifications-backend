package com.redhat.cloud.notifications.connector.webhook.authentication;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.BASIC_AUTH_PASSWORD;
import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.BASIC_AUTH_USERNAME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicAuthenticationLegacyTest {
    BasicAuthenticationProcessor testee = new BasicAuthenticationProcessor();

    @Test
    void testNullOrBlankToken() {
        CamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        testee.process(exchange);
        assertNull(exchange.getIn().getHeader(AUTHORIZATION, String.class));
        exchange.setProperty(BASIC_AUTH_USERNAME, StringUtils.EMPTY);
        exchange.setProperty(BASIC_AUTH_PASSWORD, RandomStringUtils.randomAlphanumeric(10));
        testee.process(exchange);
        assertNull(exchange.getIn().getHeader(AUTHORIZATION, String.class));
        exchange.setProperty(BASIC_AUTH_USERNAME, RandomStringUtils.randomAlphanumeric(10));
        exchange.setProperty(BASIC_AUTH_PASSWORD, StringUtils.EMPTY);
        testee.process(exchange);
        assertNull(exchange.getIn().getHeader(AUTHORIZATION, String.class));
    }

    @Test
    void testToken() {
        CamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        String username = RandomStringUtils.randomAlphanumeric(10);
        String password = RandomStringUtils.randomAlphanumeric(10);
        exchange.setProperty(BASIC_AUTH_USERNAME, username);
        exchange.setProperty(BASIC_AUTH_PASSWORD, password);

        testee.process(exchange);
        assertTrue(StringUtils.isNotBlank(exchange.getIn().getHeader(AUTHORIZATION, String.class)));
        String basicAuth = exchange.getIn().getHeader(AUTHORIZATION, String.class);
        assertEquals(username + ":" + password, new String(Base64.getDecoder().decode(basicAuth.replace("Basic ", "").getBytes(UTF_8)), UTF_8));
    }
}
