package com.redhat.cloud.notifications.connector.webhook.authentication;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.BEARER_TOKEN;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.junit.jupiter.api.Assertions.*;

class BearerTokenAuthenticationTest {
    BearerTokenAuthenticationProcessor testee = new BearerTokenAuthenticationProcessor();

    @Test
    void testNullOrBlankToken() {
        CamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        testee.process(exchange);
        assertNull(exchange.getIn().getHeader(BEARER_TOKEN, String.class));
        exchange.setProperty(BEARER_TOKEN, StringUtils.EMPTY);
        testee.process(exchange);
        assertNull(exchange.getIn().getHeader(AUTHORIZATION, String.class));
    }

    @Test
    void testToken() {
        CamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        exchange.setProperty(BEARER_TOKEN, RandomStringUtils.randomAlphanumeric(10));
        testee.process(exchange);
        assertTrue(StringUtils.isNotBlank(exchange.getIn().getHeader(AUTHORIZATION, String.class)));
        assertEquals("Bearer " + exchange.getProperty(BEARER_TOKEN), exchange.getIn().getHeader(AUTHORIZATION, String.class));
    }
}
