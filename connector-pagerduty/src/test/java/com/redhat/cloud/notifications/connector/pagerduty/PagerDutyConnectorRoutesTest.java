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

import java.time.LocalDateTime;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.AUTHENTICATION_TYPE;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_ID;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationType.SECRET_TOKEN;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTestUtils.createCloudEventData;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.ACCOUNT_ID;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.APPLICATION;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.BUNDLE;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.CLIENT;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.CLIENT_URL;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.CONTEXT;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.CUSTOM_DETAILS;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.DISPLAY_NAME;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.ENVIRONMENT_URL;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.EVENTS;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.EVENT_ACTION;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.EVENT_TYPE;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.GROUP;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.ORG_ID;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.PAYLOAD;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.PD_DATE_TIME_FORMATTER;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.SEVERITY;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.SOURCE;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.SOURCE_NAMES;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.SUMMARY;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.TIMESTAMP;
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
        JsonObject cloudEventData = createCloudEventData(targetUrl);

        JsonObject payload = new JsonObject();
        payload.put(ACCOUNT_ID, DEFAULT_ACCOUNT_ID);
        payload.put(APPLICATION, "default-application");
        payload.put(BUNDLE, "default-bundle");
        payload.put(EVENT_TYPE, "default-event-type");
        payload.put(EVENTS, JsonArray.of(
                JsonObject.of("event-1-key", "event-1-value"),
                JsonObject.of("event-2-key", "event-2-value"),
                JsonObject.of("event-3-key", "event-3-value")
        ));
        payload.put(ORG_ID, DEFAULT_ORG_ID);
        payload.put(TIMESTAMP, LocalDateTime.of(2024, 8, 12, 17, 26, 19).toString());

        JsonObject source = JsonObject.of(
                APPLICATION, JsonObject.of(DISPLAY_NAME, "Default Application Name"),
                BUNDLE, JsonObject.of(DISPLAY_NAME, "Default Bundle Name"),
                EVENT_TYPE, JsonObject.of(DISPLAY_NAME, "Default Event Type Name")
        );

        payload.put(SOURCE, source);
        payload.put(ENVIRONMENT_URL, "https://console.redhat.com");
        payload.put(SEVERITY, PagerDutySeverity.WARNING.toString());
        cloudEventData.put(PAYLOAD, payload);

        return cloudEventData;
    }

    @Override
    protected Predicate checkOutgoingPayload(JsonObject incomingPayload) {

        JsonObject expectedPayload = incomingPayload.copy();
        expectedPayload.remove(PagerDutyCloudEventDataExtractor.URL);
        expectedPayload.remove(PagerDutyCloudEventDataExtractor.AUTHENTICATION);

        return exchange -> {
            JsonObject outgoingPayload = new JsonObject(exchange.getIn().getBody(String.class));

            assertEquals(123L, exchange.getProperty(SECRET_ID, Long.class));
            assertEquals(SECRET_TOKEN, exchange.getProperty(AUTHENTICATION_TYPE, AuthenticationType.class));

            assertNull(outgoingPayload.getJsonObject(PAYLOAD).getString("component"));
            assertNull(outgoingPayload.getJsonObject(PAYLOAD).getString("class"));
            assertNull(outgoingPayload.getString("dedup_key"));

            JsonObject expected = buildExpectedPayload(expectedPayload);
            assertEquals(expected.encode(), exchange.getIn().getBody(String.class));

            // In case of assertion failure, this return value won't be used.
            return true;
        };
    }

    private JsonObject buildExpectedPayload(JsonObject expected) {
        expected.remove(PagerDutyCloudEventDataExtractor.URL);
        expected.remove(PagerDutyCloudEventDataExtractor.AUTHENTICATION);

        JsonObject oldInnerPayload = expected.getJsonObject(PAYLOAD);
        expected.put(EVENT_ACTION, PagerDutyEventAction.TRIGGER);
        expected.put(CLIENT, String.format("Open %s", oldInnerPayload.getString(APPLICATION)));
        expected.put(CLIENT_URL, String.format("%s/insights/%s", oldInnerPayload.getString(ENVIRONMENT_URL), oldInnerPayload.getString(APPLICATION)));

        JsonObject newInnerPayload = new JsonObject();
        newInnerPayload.put(SUMMARY, oldInnerPayload.getString(EVENT_TYPE));
        newInnerPayload.put(TIMESTAMP, LocalDateTime.parse(oldInnerPayload.getString(TIMESTAMP)).format(PD_DATE_TIME_FORMATTER));
        newInnerPayload.put(SEVERITY, oldInnerPayload.getString(SEVERITY));
        newInnerPayload.put(SOURCE, oldInnerPayload.getString(APPLICATION));
        newInnerPayload.put(GROUP, oldInnerPayload.getString(BUNDLE));

        JsonObject sourceNames = JsonObject.of(
                APPLICATION, oldInnerPayload.getJsonObject(SOURCE).getJsonObject(APPLICATION).getString(DISPLAY_NAME),
                BUNDLE, oldInnerPayload.getJsonObject(SOURCE).getJsonObject(BUNDLE).getString(DISPLAY_NAME),
                EVENT_TYPE, oldInnerPayload.getJsonObject(SOURCE).getJsonObject(EVENT_TYPE).getString(DISPLAY_NAME)
        );

        JsonObject customDetails = JsonObject.of(
                ACCOUNT_ID, DEFAULT_ACCOUNT_ID,
                ORG_ID, DEFAULT_ORG_ID,
                CONTEXT, null,
                SOURCE_NAMES, sourceNames
        );
        customDetails.put(EVENTS, oldInnerPayload.getJsonArray(EVENTS));

        newInnerPayload.put(CUSTOM_DETAILS, customDetails);
        expected.remove(PAYLOAD);
        expected.put(PAYLOAD, newInnerPayload);
        return expected;
    }

    @Override
    protected void afterKafkaSinkSuccess() {
        verify(secretsLoader, times(1)).process(any(Exchange.class));
    }
}
