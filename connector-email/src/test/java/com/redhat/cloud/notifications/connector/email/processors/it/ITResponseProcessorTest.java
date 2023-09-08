package com.redhat.cloud.notifications.connector.email.processors.it;

import com.google.common.io.Resources;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

@QuarkusTest
public class ITResponseProcessorTest extends CamelQuarkusTestSupport {
    @Inject
    EmailConnectorConfig emailConnectorConfig;

    @Inject
    ITResponseProcessor itResponseProcessor;

    /**
     * Tests that a response from IT is correctly processed, and that when the
     * response contains fewer elements than the ones specified in the limit,
     * the offset property is not updated.
     *
     * @throws IOException if the incoming body's JSON file could not be read.
     */
    @Test
    void testProcessLessThanLimit() throws IOException {
        // Prepare the incoming body as if the IT service had responded.
        final URL url = Resources.getResource("processors/it/incomingBody.json");
        final String incomingBody = Resources.toString(url, StandardCharsets.UTF_8);

        // Prepare the rest of the properties.
        final int limit = this.emailConnectorConfig.getItElementsPerPage();
        final int offset = 0;

        final Exchange exchange = this.createExchangeWithBody(incomingBody);
        exchange.setProperty(ExchangeProperty.LIMIT, limit);
        exchange.setProperty(ExchangeProperty.OFFSET, offset);
        exchange.setProperty(ExchangeProperty.USERNAMES, new HashSet<String>());

        // Call the processor under test.
        this.itResponseProcessor.process(exchange);

        // Assert that the grabbed username is correct.
        final Set<String> usernames = exchange.getProperty(ExchangeProperty.USERNAMES, Set.class);
        Assertions.assertEquals(1, usernames.size());
        Assertions.assertEquals("foo", usernames.iterator().next());

        // Assert that only the expected element was read.
        Assertions.assertEquals(1, exchange.getProperty(ExchangeProperty.ELEMENTS_COUNT));

        // Assert that the offset keeps being the same.
        Assertions.assertEquals(offset, exchange.getProperty(ExchangeProperty.OFFSET));
    }

    /**
     * Tests that a response from IT is correctly processed, and that when the
     * response contains as many elements as the ones specified in the limit,
     * the offset property is updated.
     *
     * @throws IOException if the incoming body's JSON file could not be read.
     */
    @Test
    void testProcessExactAsLimit() throws IOException {
        // Prepare the incoming body as if the IT service had responded.
        final URL url = Resources.getResource("processors/it/incomingBody.json");
        final String incomingBody = Resources.toString(url, StandardCharsets.UTF_8);

        // Prepare the rest of the properties. The limit has to be "1" because
        // that's the elements we have prepared in the JSON file.
        final int limit = 1;
        final int offset = 0;

        final Exchange exchange = this.createExchangeWithBody(incomingBody);
        exchange.setProperty(ExchangeProperty.LIMIT, limit);
        exchange.setProperty(ExchangeProperty.OFFSET, offset);
        exchange.setProperty(ExchangeProperty.USERNAMES, new HashSet<String>());

        // Call the processor under test.
        this.itResponseProcessor.process(exchange);

        // Assert that the grabbed username is correct.
        final Set<String> usernames = exchange.getProperty(ExchangeProperty.USERNAMES, Set.class);
        Assertions.assertEquals(1, usernames.size());
        Assertions.assertEquals("foo", usernames.iterator().next());

        // Assert that only the expected element was read.
        Assertions.assertEquals(1, exchange.getProperty(ExchangeProperty.ELEMENTS_COUNT));

        // Assert that the offset was updated.
        Assertions.assertEquals(offset + limit, exchange.getProperty(ExchangeProperty.OFFSET));
    }
}
