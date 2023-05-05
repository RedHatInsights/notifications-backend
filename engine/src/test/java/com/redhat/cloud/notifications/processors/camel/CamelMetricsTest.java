package com.redhat.cloud.notifications.processors.camel;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.processors.slack.SlackNotification;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpResponse;
import javax.inject.Inject;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getClient;
import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;
import static com.redhat.cloud.notifications.models.HttpType.POST;
import static com.redhat.cloud.notifications.processors.slack.SlackRouteBuilderTest.buildNotification;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.mockserver.model.HttpRequest.request;

public class CamelMetricsHelper {

    protected String restPath;
    protected String mockPath;
    protected String mockPathKo;
    protected String camelIncomingRouteName;
    protected String camelOutgoingRouteName;
    protected String retryCounterName;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @Test
    void testCallOk() {
        saveMetrics();
        SlackNotification notification = buildNotification(getMockServerUrl() + mockPath);
        mockSlackServerOk();
        given()
            .contentType(JSON)
            .body(Json.encode(notification))
            .when().post(restPath)
            .then().statusCode(200);

        verifyMetricsCallOk();
    }

    @Test
    void testCallFailure() {
        saveMetrics();
        SlackNotification notification = buildNotification(getMockServerUrl() + mockPathKo);
        mockSlackServerKo();
        given()
            .contentType(JSON)
            .body(Json.encode(notification))
            .when().post(restPath)
            .then().statusCode(500);

        verifyMetricsCallKo();
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
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled", "routeId", camelIncomingRouteName, 0);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled", "routeId", camelOutgoingRouteName, 0);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", camelIncomingRouteName, 0);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", camelOutgoingRouteName, 0);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesTotal", "routeId", camelIncomingRouteName, 1);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesTotal", "routeId", camelOutgoingRouteName, 1);
        micrometerAssertionHelper.assertCounterIncrement(retryCounterName, 0);
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

    private void mockSlackServerOk() {
        getClient()
                .when(request().withMethod(POST.name()).withPath(mockPath)).respond(new HttpResponse().withStatusCode(200));
    }

    private void mockSlackServerKo() {
        getClient()
            .when(request().withMethod(POST.name()).withPath(mockPathKo)).respond(new HttpResponse().withStatusCode(500));
    }
}
