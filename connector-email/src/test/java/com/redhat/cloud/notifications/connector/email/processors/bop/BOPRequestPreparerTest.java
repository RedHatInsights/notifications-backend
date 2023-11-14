package com.redhat.cloud.notifications.connector.email.processors.bop;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty;
import com.redhat.cloud.notifications.connector.email.model.settings.User;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.component.http.HttpMethods;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Set;

import static com.redhat.cloud.notifications.connector.email.TestUtils.createUsers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    @ValueSource(booleans = { true, false })
    @ParameterizedTest
    void testProcess(boolean skipBopUsersResolution) {
        emailConnectorConfig.setSkipBopUsersResolution(skipBopUsersResolution);

        try {
            // Prepare the properties that the processor expects.
            final String emailSubject = "this is a fake subject";
            final String emailBody = "this is a fake body";
            final Set<User> users = createUsers("a", "b", "c");
            final Set<String> emails = Set.of("foo@bar.com", "bar@foo.com");

            final Exchange exchange = this.createExchangeWithBody("");
            exchange.setProperty(ExchangeProperty.RENDERED_SUBJECT, emailSubject);
            exchange.setProperty(ExchangeProperty.RENDERED_BODY, emailBody);
            exchange.setProperty(ExchangeProperty.FILTERED_USERS, users);
            exchange.setProperty(ExchangeProperty.EMAIL_RECIPIENTS, emails);

            // Call the processor under test.
            this.bopRequestPreparer.process(exchange);

            // Assert that the headers are correct.
            final Map<String, Object> headers = exchange.getMessage().getHeaders();
            assertEquals(HttpMethods.POST, headers.get(Exchange.HTTP_METHOD));
            assertEquals("/v1/sendEmails", headers.get(Exchange.HTTP_PATH));
            assertEquals("application/json", headers.get(Exchange.CONTENT_TYPE));
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
            if (skipBopUsersResolution) {
                assertEquals(5, actualEmail.getJsonArray("bccList").size());
                assertTrue(Set.of("a-email", "b-email", "c-email", "foo@bar.com", "bar@foo.com").stream()
                    .allMatch(bcc -> actualEmail.getJsonArray("bccList").contains(bcc)));
            } else {
                assertEquals(3, actualEmail.getJsonArray("bccList").size());
                assertTrue(Set.of("a", "b", "c").stream()
                    .allMatch(bcc -> actualEmail.getJsonArray("bccList").contains(bcc)));
                assertTrue(Set.of("foo@bar.com", "bar@foo.com").stream()
                    .noneMatch(bcc -> actualEmail.getJsonArray("bccList").contains(bcc)));
            }
            assertEquals("html", actualEmail.getString("bodyType"));
            if (skipBopUsersResolution) {
                assertTrue(actualBody.getBoolean("skipUsersResolution"));
            } else {
                assertNull(actualBody.getBoolean("skipUsersResolution"));
            }
        } finally {
            emailConnectorConfig.setSkipBopUsersResolution(false);
        }
    }
}
