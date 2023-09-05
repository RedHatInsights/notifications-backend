package com.redhat.cloud.notifications.connector.webhook.authentication;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.INSIGHT_TOKEN_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InsightsTokenAuthenticationTest {
    InsightsTokenAuthenticationProcessor testee = new InsightsTokenAuthenticationProcessor();

    @Test
    void testNullOrBlankToken() {
        CamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        testee.process(exchange);
        assertNull(exchange.getIn().getHeader(INSIGHT_TOKEN_HEADER, String.class));
        exchange.setProperty(INSIGHT_TOKEN_HEADER, StringUtils.EMPTY);
        testee.process(exchange);
        assertNull(exchange.getIn().getHeader(INSIGHT_TOKEN_HEADER, String.class));
    }

    @Test
    void testToken() {
        CamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        exchange.setProperty(INSIGHT_TOKEN_HEADER, RandomStringUtils.randomAlphanumeric(10));
        testee.process(exchange);
        assertTrue(StringUtils.isNotBlank(exchange.getIn().getHeader(INSIGHT_TOKEN_HEADER, String.class)));
        assertEquals(exchange.getProperty(INSIGHT_TOKEN_HEADER), exchange.getIn().getHeader(INSIGHT_TOKEN_HEADER, String.class));
    }
}
