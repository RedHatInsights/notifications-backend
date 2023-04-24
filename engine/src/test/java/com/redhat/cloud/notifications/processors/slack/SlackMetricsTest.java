package com.redhat.cloud.notifications.processors.slack;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpResponse;
import javax.inject.Inject;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getClient;
import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;
import static com.redhat.cloud.notifications.processors.slack.SlackRouteBuilder.REST_PATH;
import static com.redhat.cloud.notifications.processors.slack.SlackRouteBuilderTest.buildNotification;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.mockserver.model.HttpRequest.request;

@QuarkusTest
public class SlackMetricsTest {

    private static final String MOCK_PATH = "/camel/slack";
    private static final String MOCK_PATH_KO = "/camel/slack_ko";

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @Test
    void testCallOk() {

        saveMetrics();
        SlackNotification notification = buildNotification(getMockServerUrl() + MOCK_PATH);
        mockSlackServerOk();
        given()
            .contentType(JSON)
            .body(Json.encode(notification))
            .when().post(REST_PATH)
            .then().statusCode(200);

        verifyMetricsCallOk();
    }

    @Test
    void testCallFailure() {

        saveMetrics();
        SlackNotification notification = buildNotification(getMockServerUrl() + MOCK_PATH_KO);
        mockSlackServerKo();
        given()
            .contentType(JSON)
            .body(Json.encode(notification))
            .when().post(REST_PATH)
            .then().statusCode(500);

        verifyMetricsCallKo();
    }

    private void verifyMetricsCallOk() {
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled", "routeId", SlackRouteBuilder.SLACK_INCOMING_ROUTE, 0);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled", "routeId", SlackRouteBuilder.SLACK_OUTGOING_ROUTE, 0);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", SlackRouteBuilder.SLACK_INCOMING_ROUTE, 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", SlackRouteBuilder.SLACK_OUTGOING_ROUTE, 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesTotal", "routeId", SlackRouteBuilder.SLACK_INCOMING_ROUTE, 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesTotal", "routeId", SlackRouteBuilder.SLACK_OUTGOING_ROUTE, 1);
        micrometerAssertionHelper.assertCounterIncrement("camel.slack.retry.counter", 0);
    }

    private void verifyMetricsCallKo() {
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled", "routeId", SlackRouteBuilder.SLACK_INCOMING_ROUTE, 0);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled", "routeId", SlackRouteBuilder.SLACK_OUTGOING_ROUTE, 0);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", SlackRouteBuilder.SLACK_INCOMING_ROUTE, 0);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", SlackRouteBuilder.SLACK_OUTGOING_ROUTE, 0);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesTotal", "routeId", SlackRouteBuilder.SLACK_INCOMING_ROUTE, 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesTotal", "routeId", SlackRouteBuilder.SLACK_OUTGOING_ROUTE, 1);
        micrometerAssertionHelper.assertCounterIncrement("camel.slack.retry.counter", 0);
    }

    private void saveMetrics() {
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesFailuresHandled", "routeId", SlackRouteBuilder.SLACK_INCOMING_ROUTE);
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesSucceeded", "routeId", SlackRouteBuilder.SLACK_INCOMING_ROUTE);
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesTotal", "routeId", SlackRouteBuilder.SLACK_INCOMING_ROUTE);
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesFailuresHandled", "routeId", SlackRouteBuilder.SLACK_OUTGOING_ROUTE);
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesSucceeded", "routeId", SlackRouteBuilder.SLACK_OUTGOING_ROUTE);
        micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesTotal", "routeId", SlackRouteBuilder.SLACK_OUTGOING_ROUTE);
        micrometerAssertionHelper.saveCounterValuesBeforeTest("camel.slack.retry.counter");
    }

    private static void mockSlackServerOk() {
        getClient()
                .when(request().withMethod("POST").withPath(MOCK_PATH)).respond(new HttpResponse().withStatusCode(200));
    }

    private static void mockSlackServerKo() {
        getClient()
            .when(request().withMethod("POST").withPath(MOCK_PATH_KO)).respond(new HttpResponse().withStatusCode(500));
    }
}
