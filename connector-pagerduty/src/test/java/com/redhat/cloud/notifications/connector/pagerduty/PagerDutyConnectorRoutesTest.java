package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.connector.ConnectorRoutesTest;
import com.redhat.cloud.notifications.connector.TestLifecycleManager;
import com.redhat.cloud.notifications.connector.authentication.AuthenticationType;
import com.redhat.cloud.notifications.connector.authentication.secrets.SecretsLoader;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.ORG_ID;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.TARGET_URL;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.AUTHENTICATION_TYPE;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_ID;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationType.SECRET_TOKEN;
import static com.redhat.cloud.notifications.connector.pagerduty.ExchangeProperty.ACCOUNT_ID;
import static com.redhat.cloud.notifications.connector.pagerduty.ExchangeProperty.TARGET_URL_NO_SCHEME;
import static com.redhat.cloud.notifications.connector.pagerduty.ExchangeProperty.TRUST_ALL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// TODO: rewrite for PagerDuty
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class PagerDutyConnectorRoutesTest extends ConnectorRoutesTest {

    @InjectMock
    SecretsLoader secretsLoader;

    @Override
    protected String getMockEndpointPattern() {
        return "https://foo.bar*";
    }

    @Override
    protected String getMockEndpointUri() {
        return "mock:https:foo.bar";
    }

    @Override
    protected JsonObject buildIncomingPayload(String targetUrl) {

        JsonObject authentication = new JsonObject();
        authentication.put("type", SECRET_TOKEN.name());
        authentication.put("secretId", 123L);

        JsonObject metadata = new JsonObject();
        metadata.put("url", targetUrl);
        metadata.put("trustAll", "true");
        metadata.put("authentication", authentication);

        JsonObject payload = new JsonObject();
        payload.put(PagerDutyCloudEventDataExtractor.NOTIF_METADATA, metadata);
        payload.put("org_id", DEFAULT_ORG_ID);
        payload.put("account_id", DEFAULT_ACCOUNT_ID);
        payload.put("events", JsonArray.of(
                JsonObject.of("event-1-key", "event-1-value"),
                JsonObject.of("event-2-key", "event-2-value")
        ));
        return payload;
    }

    @Override
    protected Predicate checkOutgoingPayload(JsonObject incomingPayload) {

        JsonObject expectedPayload = incomingPayload.copy();
        expectedPayload.remove(PagerDutyCloudEventDataExtractor.NOTIF_METADATA);

        return exchange -> {
            String outgoingPayload = exchange.getIn().getBody(String.class);

            assertEquals(DEFAULT_ORG_ID, exchange.getProperty(ORG_ID, String.class));
            assertEquals(DEFAULT_ACCOUNT_ID, exchange.getProperty(ACCOUNT_ID, String.class));
            assertTrue(exchange.getProperty(TRUST_ALL, Boolean.class));
            assertEquals(exchange.getProperty(TARGET_URL, String.class), "https://" + exchange.getProperty(TARGET_URL_NO_SCHEME, String.class));
            assertEquals(expectedPayload.encode(), outgoingPayload);
            assertEquals(123L, exchange.getProperty(SECRET_ID, Long.class));
            assertEquals(SECRET_TOKEN, exchange.getProperty(AUTHENTICATION_TYPE, AuthenticationType.class));

            // In case of assertion failure, this return value won't be used.
            return true;
        };
    }

    @Override
    protected boolean useHttps() {
        return true;
    }

    @Override
    protected void afterKafkaSinkSuccess() {
        verify(secretsLoader, times(1)).process(any(Exchange.class));
    }
}
