package com.redhat.cloud.notifications.connector.email.processors.bop;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.component.http.HttpMethods;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class BOPRequestPreparerTest extends CamelQuarkusTestSupport {
    @Inject
    BOPRequestPreparer bopRequestPreparer;

    @Inject
    EmailConnectorConfig emailConnectorConfig;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    /**
     * Tests that the processor prepares the request as intended.
     */

    @Test
    void testProcess() {

        // Prepare the properties that the processor expects.
        final String emailSubject = "this is a fake subject";
        final String emailBody = "this is a fake body";
        final Set<String> emails = Set.of("foo@bar.com", "bar@foo.com", "a@a.com", "b@b.com", "c@c.com");

        final Exchange exchange = this.createExchangeWithBody(emails);
        exchange.setProperty(ExchangeProperty.RENDERED_SUBJECT, emailSubject);
        exchange.setProperty(ExchangeProperty.RENDERED_BODY, emailBody);

        // Call the processor under test.
        this.bopRequestPreparer.process(exchange);

        // Assert that the headers are correct.
        final Map<String, Object> headers = exchange.getMessage().getHeaders();
        assertEquals(HttpMethods.POST, headers.get(Exchange.HTTP_METHOD));
        assertEquals("/v1/sendEmails", headers.get(Exchange.HTTP_PATH));
        assertEquals("application/json; charset=utf-8", headers.get(Exchange.CONTENT_TYPE));
        assertEquals(this.emailConnectorConfig.getBopApiToken(), headers.get(Constants.BOP_API_TOKEN_HEADER));
        assertEquals(this.emailConnectorConfig.getBopClientId(), headers.get(Constants.BOP_CLIENT_ID_HEADER));
        assertEquals(this.emailConnectorConfig.getBopEnv(), headers.get(Constants.BOP_ENV_HEADER));

        // Assert that the message's body is correct.
        final JsonObject actualBody = new JsonObject(exchange.getMessage().getBody(String.class));
        final JsonObject actualEmail = actualBody.getJsonArray("emails").getJsonObject(0);

        assertEquals("this is a fake subject", actualEmail.getString("subject"));
        assertEquals("this is a fake body", actualEmail.getString("body"));
        assertTrue(actualEmail.getJsonArray("recipients").isEmpty());
        assertTrue(actualEmail.getJsonArray("ccList").isEmpty());

        assertEquals(5, actualEmail.getJsonArray("bccList").size());
        assertTrue(Set.of("a@a.com", "b@b.com", "c@c.com", "foo@bar.com", "bar@foo.com").stream()
            .allMatch(bcc -> actualEmail.getJsonArray("bccList").contains(bcc)));

        assertEquals("html", actualEmail.getString("bodyType"));

        assertTrue(actualBody.getBoolean("skipUsersResolution"));
    }
}
