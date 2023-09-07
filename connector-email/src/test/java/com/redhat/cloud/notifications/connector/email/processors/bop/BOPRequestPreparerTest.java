package com.redhat.cloud.notifications.connector.email.processors.bop;

import com.google.common.io.Resources;
import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.component.http.HttpMethods;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@QuarkusTest
public class BOPRequestPreparerTest extends CamelQuarkusTestSupport {
    @Inject
    BOPRequestPreparer bopRequestPreparer;

    @Inject
    EmailConnectorConfig emailConnectorConfig;

    /**
     * Tests that the processor prepares the request as intended.
     * @throws IOException if the expected results' file could not be loaded.
     */
    @Test
    void testProcess() throws IOException {
        // Prepare the properties that the processor expects.
        final String emailSubject = "this is a fake subject";
        final String emailBody = "this is a fake body";
        // Manually create the hash set because the "Set.of" utility doesn't
        // respect the insertion ordering.
        final Set<String> usernames = new HashSet<>();
        usernames.add("a");
        usernames.add("b");
        usernames.add("c");

        final Exchange exchange = this.createExchangeWithBody("");
        exchange.setProperty(ExchangeProperty.RENDERED_SUBJECT, emailSubject);
        exchange.setProperty(ExchangeProperty.RENDERED_BODY, emailBody);
        exchange.setProperty(ExchangeProperty.USERNAMES, usernames);

        // Call the processor under test.
        this.bopRequestPreparer.process(exchange);

        // Assert that the headers are correct.
        final Map<String, Object> headers = exchange.getMessage().getHeaders();
        Assertions.assertEquals(HttpMethods.POST, headers.get(Exchange.HTTP_METHOD));
        Assertions.assertEquals("application/json", headers.get(Exchange.CONTENT_TYPE));
        Assertions.assertEquals(this.emailConnectorConfig.getBopApiToken(), headers.get(Constants.BOP_API_TOKEN_HEADER));
        Assertions.assertEquals(this.emailConnectorConfig.getBopClientId(), headers.get(Constants.BOP_CLIENT_ID_HEADER));
        Assertions.assertEquals(this.emailConnectorConfig.getBopEnv(), headers.get(Constants.BOP_ENV_HEADER));

        // Assert that the message's body is correct.
        final URL url = Resources.getResource("processors/bop/expectedBody.json");
        final String expectedBodyRaw = Resources.toString(url, StandardCharsets.UTF_8);
        final String expectedBody = new JsonObject(expectedBodyRaw).encode();

        Assertions.assertEquals(expectedBody, exchange.getMessage().getBody());
    }

    /**
     * Tests that the processor handles individual email sending properly.
     * @throws IOException if the expected results' file could not be loaded.
     */
    @Deprecated(forRemoval = true)
    @Test
    void testProcessSingleEmail() throws IOException {
        // Prepare the properties that the processor expects.
        final String emailSubject = "this is a fake subject";
        final String emailBody = "this is a fake body";

        // The "split" operation is going to leave the username in the
        // exchange's body, so we only need to pass a simple string in this
        // case.
        final Exchange exchange = this.createExchangeWithBody("a");
        exchange.setProperty(ExchangeProperty.RENDERED_SUBJECT, emailSubject);
        exchange.setProperty(ExchangeProperty.RENDERED_BODY, emailBody);
        exchange.setProperty(ExchangeProperty.SINGLE_EMAIL_PER_USER, true);

        // Call the processor under test.
        this.bopRequestPreparer.process(exchange);

        // Assert that the headers are correct.
        final Map<String, Object> headers = exchange.getMessage().getHeaders();
        Assertions.assertEquals(HttpMethods.POST, headers.get(Exchange.HTTP_METHOD));
        Assertions.assertEquals("application/json", headers.get(Exchange.CONTENT_TYPE));
        Assertions.assertEquals(this.emailConnectorConfig.getBopApiToken(), headers.get(Constants.BOP_API_TOKEN_HEADER));
        Assertions.assertEquals(this.emailConnectorConfig.getBopClientId(), headers.get(Constants.BOP_CLIENT_ID_HEADER));
        Assertions.assertEquals(this.emailConnectorConfig.getBopEnv(), headers.get(Constants.BOP_ENV_HEADER));

        // Assert that the message's body is correct.
        final URL url = Resources.getResource("processors/bop/expectedBodySingleUser.json");
        final String expectedBodyRaw = Resources.toString(url, StandardCharsets.UTF_8);
        final String expectedBody = new JsonObject(expectedBodyRaw).encode();

        Assertions.assertEquals(expectedBody, exchange.getMessage().getBody());
    }
}
