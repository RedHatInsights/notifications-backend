package com.redhat.cloud.notifications.processors.camel;

import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.MockServerLifecycleManager;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpResponse;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.UUID;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getClient;
import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.models.HttpType.POST;
import static com.redhat.cloud.notifications.processors.ConnectorSender.CLOUD_EVENT_TYPE_PREFIX;
import static com.redhat.cloud.notifications.processors.ConnectorSender.X_RH_NOTIFICATIONS_CONNECTOR_HEADER;
import static com.redhat.cloud.notifications.processors.camel.CamelNotificationProcessor.CLOUD_EVENT_DATA;
import static com.redhat.cloud.notifications.processors.camel.CamelNotificationProcessor.CLOUD_EVENT_ID;
import static com.redhat.cloud.notifications.processors.camel.CamelNotificationProcessor.CLOUD_EVENT_TYPE;
import static com.redhat.cloud.notifications.processors.camel.OutgoingCloudEventBuilder.CE_SPEC_VERSION;
import static com.redhat.cloud.notifications.processors.camel.OutgoingCloudEventBuilder.CE_TYPE;
import static com.redhat.cloud.notifications.processors.camel.ReturnRouteBuilder.RETURN_ROUTE_NAME;
import static org.apache.camel.builder.AdviceWith.adviceWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.VerificationTimes.atLeast;

public abstract class CamelRoutesTest extends CamelQuarkusTestSupport {

    private static final String KAFKA_SOURCE_MOCK = "direct:kafka-source-mock";
    private static final String REMOTE_SERVER_PATH = "/some/path";

    protected String routeEndpoint;
    protected String mockRouteEndpoint;

    @ConfigProperty(name = "mp.messaging.fromcamel.topic")
    String kafkaReturnTopic;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    protected abstract String getIncomingRoute();

    protected abstract String getEndpointSubtype();

    protected abstract String getRetryCounterName();

    @BeforeEach
    protected void beforeEach() {
        getClient().reset();

        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesFailuresHandled", "routeId", getIncomingRoute());
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesSucceeded", "routeId", getIncomingRoute());
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesTotal", "routeId", getIncomingRoute());

        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesFailuresHandled", "routeId", RETURN_ROUTE_NAME);
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesSucceeded", "routeId", RETURN_ROUTE_NAME);
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesTotal", "routeId", RETURN_ROUTE_NAME);

        micrometerAssertionHelper.saveCounterValuesBeforeTest(getRetryCounterName());
    }

    @AfterEach
    void afterEach() {
        getClient().reset();

        micrometerAssertionHelper.clearSavedValues();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    @Disabled
    void testCallOk() throws Exception {

        mockServerOk();
        mockKafkaSourceEndpoint();
        MockEndpoint kafkaSinkMockEndpoint = mockKafkaSinkEndpoint();

        CamelNotification notification = (CamelNotification) buildNotification(getMockServerUrl() + REMOTE_SERVER_PATH);
        String cloudEventId = sendMessageToKafkaSource(notification, getEndpointSubtype());

        assertKafkaSinkIsSatisfied(cloudEventId, notification, kafkaSinkMockEndpoint, true, "Event " + cloudEventId + " sent successfully");
        verifyMetricsCallOk();
    }

    protected Object buildNotification(String webhookUrl) {
        return buildCamelNotification(webhookUrl);
    }

    @Test
    @Disabled
    void testCallFailure() throws Exception {

        mockServerKo();
        mockKafkaSourceEndpoint();
        MockEndpoint kafkaSinkMockEndpoint = mockKafkaSinkEndpoint();

        CamelNotification notification = (CamelNotification) buildNotification(getMockServerUrl() + REMOTE_SERVER_PATH);
        String cloudEventId = sendMessageToKafkaSource(notification, getEndpointSubtype());

        assertKafkaSinkIsSatisfied(cloudEventId, notification, kafkaSinkMockEndpoint, false, "HTTP operation failed", "Error POSTing to Slack API");
        verifyMetricsCallKo();
    }

    @Test
    @Disabled
    void testRetriesFailure() throws Exception {

        mockServerFailure();
        mockKafkaSourceEndpoint();
        MockEndpoint kafkaSinkMockEndpoint = mockKafkaSinkEndpoint();

        CamelNotification notification = (CamelNotification) buildNotification(getMockServerUrl() + REMOTE_SERVER_PATH);
        String cloudEventId = sendMessageToKafkaSource(notification, getEndpointSubtype());

        assertKafkaSinkIsSatisfied(cloudEventId, notification, kafkaSinkMockEndpoint, false, "unexpected end of stream", "localhost:" + MockServerLifecycleManager.getClient().getPort() + " failed to respond");
        verifyRedeliveries();
        verifyMetricsCallRetries();
    }

    @Test
    @Disabled
    protected void testRoutes() throws Exception {
        adviceWith(getIncomingRoute(), context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpointsAndSkip(routeEndpoint + "*");
            }
        });

        CamelNotification notification = (CamelNotification) buildNotification(routeEndpoint);

        MockEndpoint testedEndpoint = getMockEndpoint(mockRouteEndpoint);
        testedEndpoint.expectedBodiesReceived(notification.message);

        mockKafkaSourceEndpoint();
        MockEndpoint kafkaEndpoint = mockKafkaSinkEndpoint();

        String cloudEventId = sendMessageToKafkaSource(notification, getEndpointSubtype());

        testedEndpoint.assertIsSatisfied();
        assertKafkaSinkIsSatisfied(cloudEventId, notification, kafkaEndpoint, true, "Event " + cloudEventId + " sent successfully");
    }

    private void verifyMetricsCallOk() {

        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled",  "routeId", getIncomingRoute(), 0);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", getIncomingRoute(), 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesTotal", "routeId", getIncomingRoute(), 1);

        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled", "routeId", RETURN_ROUTE_NAME, 0);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", RETURN_ROUTE_NAME, 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesTotal", "routeId", RETURN_ROUTE_NAME, 1);

        micrometerAssertionHelper.assertCounterIncrement(getRetryCounterName(), 0);
    }

    private void verifyMetricsCallKo() {

        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled",  "routeId", getIncomingRoute(), 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", getIncomingRoute(), 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesTotal", "routeId", getIncomingRoute(), 1);

        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled", "routeId", RETURN_ROUTE_NAME, 0);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", RETURN_ROUTE_NAME, 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesTotal", "routeId", RETURN_ROUTE_NAME, 1);

        micrometerAssertionHelper.assertCounterIncrement(getRetryCounterName(), 0);
    }

    private void verifyMetricsCallRetries() {

        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled",  "routeId", getIncomingRoute(), 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", getIncomingRoute(), 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesTotal", "routeId", getIncomingRoute(), 1);

        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled", "routeId", RETURN_ROUTE_NAME, 0);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", RETURN_ROUTE_NAME, 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesTotal", "routeId", RETURN_ROUTE_NAME, 1);

        micrometerAssertionHelper.assertCounterIncrement(getRetryCounterName(), 2);
    }

    private void mockServerOk() {
        getClient()
                .when(request().withMethod(POST.name()).withPath(REMOTE_SERVER_PATH))
                .respond(new HttpResponse().withStatusCode(200));
    }

    private void mockServerKo() {
        getClient()
                .when(request().withMethod(POST.name()).withPath(REMOTE_SERVER_PATH))
                .respond(new HttpResponse().withStatusCode(500).withBody("My custom internal error"));
    }

    private void mockServerFailure() {
        getClient()
            .when(request().withMethod(POST.name()).withPath(REMOTE_SERVER_PATH))
            .error(error().withDropConnection(true));
    }

    private void verifyRedeliveries() {
        getClient()
            .verify(request().withMethod(POST.name()).withPath(REMOTE_SERVER_PATH), atLeast(3));
    }

    public static CamelNotification buildCamelNotification(String webhookUrl) {
        CamelNotification notification = new CamelNotification();
        notification.orgId = DEFAULT_ORG_ID;
        notification.webhookUrl = webhookUrl;
        notification.message = "This is a test!";
        return notification;
    }

    protected void mockKafkaSourceEndpoint() throws Exception {
        adviceWith(getIncomingRoute(), context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                replaceFromWith(KAFKA_SOURCE_MOCK);
            }
        });
    }

    protected MockEndpoint mockKafkaSinkEndpoint() throws Exception {
        adviceWith(RETURN_ROUTE_NAME, context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpointsAndSkip("kafka:" + kafkaReturnTopic + "*");
            }
        });

        MockEndpoint kafkaEndpoint = getMockEndpoint("mock:kafka:" + kafkaReturnTopic);
        kafkaEndpoint.expectedMessageCount(1);

        return kafkaEndpoint;
    }

    protected String sendMessageToKafkaSource(CamelNotification notification, String endpointSubtype) {

        String cloudEventId = UUID.randomUUID().toString();

        JsonObject cloudEvent = new JsonObject();
        cloudEvent.put(CLOUD_EVENT_ID, cloudEventId);
        cloudEvent.put(CLOUD_EVENT_TYPE, CLOUD_EVENT_TYPE_PREFIX + endpointSubtype);
        // The 'data' field is sent as a String from SmallRye Reactive Messaging.
        cloudEvent.put(CLOUD_EVENT_DATA, JsonObject.mapFrom(notification).encode());

        template.sendBodyAndHeader(KAFKA_SOURCE_MOCK, cloudEvent.encode(), X_RH_NOTIFICATIONS_CONNECTOR_HEADER, endpointSubtype);

        return cloudEventId;
    }

    protected static void assertKafkaSinkIsSatisfied(String cloudEventId, CamelNotification notification, MockEndpoint mockEndpoint, boolean expectedSuccessful, String... expectedOutcomeStarts) throws InterruptedException {

        mockEndpoint.assertIsSatisfied();

        Exchange exchange = mockEndpoint.getReceivedExchanges().get(0);
        JsonObject payload = new JsonObject(exchange.getIn().getBody(String.class));

        assertEquals(CE_TYPE, payload.getString("type"));
        assertEquals(CE_SPEC_VERSION, payload.getString("specversion"));
        assertEquals(cloudEventId, payload.getString("id"));
        assertNotNull(payload.getString("source"));
        assertNotNull(payload.getString("time"));

        JsonObject data = new JsonObject(payload.getString("data"));

        assertEquals(expectedSuccessful, data.getBoolean("successful"));
        assertNotNull(data.getString("duration"));
        assertNotNull(data.getJsonObject("details").getString("type"));
        assertEquals(notification.webhookUrl, data.getJsonObject("details").getString("target"));

        String outcome = data.getJsonObject("details").getString("outcome");

        if (Arrays.stream(expectedOutcomeStarts).noneMatch(outcome::startsWith)) {
            fail(String.format("Expected outcome starts: %s - Actual outcome: %s", Arrays.toString(expectedOutcomeStarts), outcome));
        }
    }
}
