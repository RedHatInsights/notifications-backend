package com.redhat.cloud.notifications.processors.slack;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getClient;
import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;
import static com.redhat.cloud.notifications.processors.slack.SlackRouteBuilder.REST_PATH;
import static com.redhat.cloud.notifications.processors.slack.SlackRouteBuilderTest.buildNotification;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.VerificationTimes.atLeast;

@QuarkusTest
public class SlackRedeliveriesTest {

    private static final String MOCK_PATH = "/camel/slack/retries";

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @Test
    void test() {
        saveMetrics();

        SlackNotification notification = buildNotification(getMockServerUrl() + MOCK_PATH);
        mockSlackServerFailure();
        given()
                .contentType(JSON)
                .body(Json.encode(notification))
                .when().post(REST_PATH)
                .then().statusCode(500);
        verifyRedeliveries();

        verifyMetrics();
    }

    private void verifyMetrics() {
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled", "routeId", SlackRouteBuilder.SLACK_INCOMING_ROUTE, 0);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", SlackRouteBuilder.SLACK_INCOMING_ROUTE, 0);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesTotal", "routeId", SlackRouteBuilder.SLACK_INCOMING_ROUTE, 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled", "routeId", SlackRouteBuilder.SLACK_OUTGOING_ROUTE, 0);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", SlackRouteBuilder.SLACK_OUTGOING_ROUTE, 0);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesTotal", "routeId", SlackRouteBuilder.SLACK_OUTGOING_ROUTE, 1);
        micrometerAssertionHelper.assertCounterIncrement("camel.slack.retry.counter", 2);
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

    private static void mockSlackServerFailure() {
        getClient()
                .when(request().withMethod("POST").withPath(MOCK_PATH))
                .error(error().withDropConnection(true));
    }

    private static void verifyRedeliveries() {
        getClient()
                .verify(request().withMethod("POST").withPath(MOCK_PATH), atLeast(3));
    }
}
