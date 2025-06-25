package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.connector.ConnectorRoutesTest;
import com.redhat.cloud.notifications.connector.TestLifecycleManager;
import com.redhat.cloud.notifications.connector.authentication.AuthenticationType;
import com.redhat.cloud.notifications.connector.authentication.secrets.SecretsLoader;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;

import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.AUTHENTICATION_TYPE;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_ID;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationType.SECRET_TOKEN;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTestUtils.buildExpectedOutgoingPayload;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTestUtils.createIncomingPayload;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.PAYLOAD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
        return createIncomingPayload(targetUrl);
    }

    @Override
    protected boolean useHttps() {
        return true;
    }

    @Override
    protected Predicate checkOutgoingPayload(JsonObject incomingPayload) {

        JsonObject expectedPayload = incomingPayload.copy();

        return exchange -> {
            JsonObject outgoingPayload = new JsonObject(exchange.getIn().getBody(String.class));

            assertEquals(123L, exchange.getProperty(SECRET_ID, Long.class));
            assertEquals(SECRET_TOKEN, exchange.getProperty(AUTHENTICATION_TYPE, AuthenticationType.class));

            assertNull(outgoingPayload.getJsonObject(PAYLOAD).getString("component"));
            assertNull(outgoingPayload.getJsonObject(PAYLOAD).getString("class"));
            assertNull(outgoingPayload.getString("dedup_key"));

            JsonObject expected = buildExpectedOutgoingPayload(expectedPayload);
            assertEquals(expected.encode(), exchange.getIn().getBody(String.class));

            // In case of assertion failure, this return value won't be used.
            return true;
        };
    }

    @Override
    protected void afterKafkaSinkSuccess() {
        verify(secretsLoader, times(1)).process(any(Exchange.class));
    }
}
