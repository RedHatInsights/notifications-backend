package com.redhat.cloud.notifications.processors.camel;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.MockServerLifecycleManager;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpResponse;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.UUID;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getClient;
import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.models.HttpType.POST;
import static com.redhat.cloud.notifications.processors.camel.OutgoingCloudEventBuilder.CE_SPEC_VERSION;
import static com.redhat.cloud.notifications.processors.camel.OutgoingCloudEventBuilder.CE_TYPE;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.apache.camel.builder.AdviceWith.adviceWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.VerificationTimes.atLeast;

public abstract class CamelRoutesTest extends CamelQuarkusTestSupport {

    @ConfigProperty(name = "notifications.camel.kafka-return-topic")
    protected String kafkaReturnTopic;

    protected String restPath;
    protected String mockPath;
    protected String mockPathKo;
    protected String routeEndpoint;
    protected String mockRouteEndpoint;
    protected String camelIncomingRouteName;
    protected String camelOutgoingRouteName;
    protected String retryCounterName;
    protected String mockPathRetries = RandomStringUtils.randomAlphabetic(20);

    @Inject
    protected MicrometerAssertionHelper micrometerAssertionHelper;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void testCallOk() throws Exception {
        saveMetrics();
        mockServerOk();
        MockEndpoint kafkaEndpoint = mockKafkaEndpoint();
        CamelNotification notification = (CamelNotification) buildNotification(getMockServerUrl() + mockPath);
        given()
            .contentType(JSON)
            .body(Json.encode(notification))
            .when().post(restPath)
            .then().statusCode(200);

        verifyMetricsCallOk();
        assertKafkaIsSatisfied(notification, kafkaEndpoint, true, "Event " + notification.historyId + " sent successfully");
    }

    protected Object buildNotification(String webhookUrl) {
        return buildCamelNotification(webhookUrl);
    }

    @Test
    void testCallFailure() throws Exception {
        saveMetrics();
        mockServerKo();
        MockEndpoint kafkaEndpoint = mockKafkaEndpoint();
        CamelNotification notification = (CamelNotification) buildNotification(getMockServerUrl() + mockPathKo);
        given()
            .contentType(JSON)
            .body(Json.encode(notification))
            .when().post(restPath)
            .then().statusCode(200);

        verifyMetricsCallKo();
        assertKafkaIsSatisfied(notification, kafkaEndpoint, false, "HTTP operation failed", "Error POSTing to Slack API");
    }

    @Test
    void testRetriesFailure() throws Exception {
        saveMetrics();

        mockServerFailure();
        MockEndpoint kafkaEndpoint = mockKafkaEndpoint();
        CamelNotification notification = (CamelNotification) buildNotification(getMockServerUrl() + mockPathRetries);
        given()
            .contentType(JSON)
            .body(Json.encode(notification))
            .when().post(restPath)
            .then().statusCode(200);
        verifyRedeliveries();

        verifyMetricsCallRetries();
        assertKafkaIsSatisfied(notification, kafkaEndpoint, false, "unexpected end of stream", "localhost:" + MockServerLifecycleManager.getClient().getPort() + " failed to respond");
    }

    @Test
    protected void testRoutes() throws Exception {
        adviceWith(camelOutgoingRouteName, context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpointsAndSkip(routeEndpoint + "*");
            }
        });

        CamelNotification notification = (CamelNotification) buildNotification(routeEndpoint);

        MockEndpoint testedEndpoint = getMockEndpoint(mockRouteEndpoint);
        testedEndpoint.expectedBodiesReceived(notification.message);

        MockEndpoint kafkaEndpoint = mockKafkaEndpoint();

        given()
            .contentType(JSON)
            .body(Json.encode(notification))
            .when().post(restPath)
            .then().statusCode(200);

        testedEndpoint.assertIsSatisfied();
        assertKafkaIsSatisfied(notification, kafkaEndpoint, true, "Event " + notification.historyId + " sent successfully");
    }

    private void verifyMetricsCallOk() {
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled", "routeId", camelIncomingRouteName, 0);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled", "routeId", camelOutgoingRouteName, 0);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", camelIncomingRouteName, 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", camelOutgoingRouteName, 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesTotal", "routeId", camelIncomingRouteName, 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesTotal", "routeId", camelOutgoingRouteName, 1);
        micrometerAssertionHelper.assertCounterIncrement(retryCounterName, 0);
    }

    private void verifyMetricsCallKo() {
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled", "routeId", camelIncomingRouteName, 1);
        // The following metric has a different value depending on the integration type (Slack, Teams...)
        // TODO Uncomment and fix later!
        //micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled", "routeId", camelOutgoingRouteName, 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", camelIncomingRouteName, 1);
        // The following metric has a different value depending on the integration type (Slack, Teams...)
        // TODO Uncomment and fix later!
        //micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", camelOutgoingRouteName, 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesTotal", "routeId", camelIncomingRouteName, 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesTotal", "routeId", camelOutgoingRouteName, 1);
        micrometerAssertionHelper.assertCounterIncrement(retryCounterName, 0);
    }

    private void verifyMetricsCallRetries() {
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled", "routeId", camelIncomingRouteName, 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled", "routeId", camelOutgoingRouteName, 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", camelIncomingRouteName, 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", camelOutgoingRouteName, 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesTotal", "routeId", camelIncomingRouteName, 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesTotal", "routeId", camelOutgoingRouteName, 1);
        micrometerAssertionHelper.assertCounterIncrement(retryCounterName, 2);
    }

    private void saveMetrics() {
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesFailuresHandled", "routeId", camelIncomingRouteName);
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesSucceeded", "routeId", camelIncomingRouteName);
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesTotal", "routeId", camelIncomingRouteName);
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesFailuresHandled", "routeId", camelOutgoingRouteName);
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesSucceeded", "routeId", camelOutgoingRouteName);
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesTotal", "routeId", camelOutgoingRouteName);
        micrometerAssertionHelper.saveCounterValuesBeforeTest(retryCounterName);
    }

    private void mockServerOk() {
        getClient()
                .when(request().withMethod(POST.name()).withPath(mockPath)).respond(new HttpResponse().withStatusCode(200));
    }

    private void mockServerKo() {
        getClient()
            .when(request().withMethod(POST.name()).withPath(mockPathKo)).respond(new HttpResponse().withStatusCode(500).withBody("My custom internal error"));
    }

    private void mockServerFailure() {
        getClient()
            .when(request().withMethod(POST.name()).withPath(mockPathRetries))
            .error(error().withDropConnection(true));
    }

    private void verifyRedeliveries() {
        getClient()
            .verify(request().withMethod(POST.name()).withPath(mockPathRetries), atLeast(3));
    }

    public static CamelNotification buildCamelNotification(String webhookUrl) {
        CamelNotification notification = new CamelNotification();
        notification.orgId = DEFAULT_ORG_ID;
        notification.historyId = UUID.randomUUID();
        notification.webhookUrl = webhookUrl;
        notification.message = "This is a test!";
        return notification;
    }

    protected MockEndpoint mockKafkaEndpoint() throws Exception {
        adviceWith("return", context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpointsAndSkip("kafka:" + kafkaReturnTopic + "*");
            }
        });

        MockEndpoint kafkaEndpoint = getMockEndpoint("mock:kafka:" + kafkaReturnTopic);
        kafkaEndpoint.expectedMessageCount(1);

        return kafkaEndpoint;
    }

    protected static void assertKafkaIsSatisfied(CamelNotification notification, MockEndpoint mockEndpoint, boolean expectedSuccessful, String... expectedOutcomeStarts) throws InterruptedException {

        mockEndpoint.assertIsSatisfied();

        Exchange exchange = mockEndpoint.getReceivedExchanges().get(0);
        JsonObject payload = new JsonObject(exchange.getIn().getBody(String.class));

        assertEquals(CE_TYPE, payload.getString("type"));
        assertEquals(CE_SPEC_VERSION, payload.getString("specversion"));
        assertNotNull(payload.getString("source"));
        assertEquals(notification.historyId.toString(), payload.getString("id"));
        assertNotNull(payload.getString("time"));

        JsonObject data = new JsonObject(payload.getString("data"));

        assertEquals(expectedSuccessful, data.getBoolean("successful"));
        assertNotNull(data.getString("duration"));
        assertNotNull(data.getJsonObject("details").getString("type"));
        assertEquals(notification.webhookUrl, data.getJsonObject("details").getString("target"));

        String outcome = data.getJsonObject("details").getString("outcome");

        if (Arrays.stream(expectedOutcomeStarts).noneMatch(outcome::startsWith)) {
            Assertions.fail(String.format("Expected outcome starts: %s - Actual outcome: %s", Arrays.toString(expectedOutcomeStarts), outcome));
        }
    }
}
