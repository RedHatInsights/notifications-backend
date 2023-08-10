package com.redhat.cloud.notifications.connector.email.processors.it;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.model.settings.RecipientSettings;
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
import java.util.Map;

@QuarkusTest
public class ITUserRequestPreparerTest extends CamelQuarkusTestSupport {
    @Inject
    ITUserRequestPreparer itUserRequestPreparer;

    /**
     * Tests that the processor prepares the headers and the payload in the
     * expected way.
     * @throws IOException if the expected body's file cannot be loaded.
     * @throws JsonProcessingException if the IT request cannot be serialized.
     */
    @Test
    void testProcess() throws IOException, JsonProcessingException {
        // Prepare the properties the processor expects.
        final String orgId = "f23e6149-90a3-47e5-9180-d613623aba57";
        final int offset = 543;
        final RecipientSettings recipientSettings = new RecipientSettings(
            true,
            true,
            null,
            null
        );

        final Exchange exchange = this.createExchangeWithBody("");
        exchange.setProperty(com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID, orgId);
        exchange.setProperty(ExchangeProperty.CURRENT_RECIPIENT_SETTINGS, recipientSettings);
        exchange.setProperty(ExchangeProperty.OFFSET, offset);

        // Call the processor under test.
        this.itUserRequestPreparer.process(exchange);

        // Assert that the headers are correct.
        final Map<String, Object> headers = exchange.getMessage().getHeaders();

        Assertions.assertEquals("application/json", headers.get("Accept"));
        Assertions.assertEquals("application/json", headers.get(Exchange.CONTENT_TYPE));
        Assertions.assertEquals("/v2/findUsers", headers.get(Exchange.HTTP_PATH));
        Assertions.assertEquals(HttpMethods.POST, headers.get(Exchange.HTTP_METHOD));

        // Assert that the message body is correct.
        final URL url = Resources.getResource("processors/it/expectedBody.json");
        final String expectedBodyRaw = Resources.toString(url, StandardCharsets.UTF_8);
        final String expectedBody = new JsonObject(expectedBodyRaw).encode();

        Assertions.assertEquals(expectedBody, exchange.getMessage().getBody());
    }
}
