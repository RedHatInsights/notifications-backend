package com.redhat.cloud.notifications.processors.camel;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpResponse;
import javax.inject.Inject;
import java.util.UUID;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getClient;
import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.models.HttpType.POST;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.apache.camel.builder.AdviceWith.adviceWith;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.VerificationTimes.atLeast;

public abstract class CamelRoutesTest extends CamelQuarkusTestSupport {

    protected String restPath;
    protected String mockPath;
    protected String mockPathKo;
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
    void testCallOk() {
        saveMetrics();
        mockServerOk();
        given()
            .contentType(JSON)
            .body(Json.encode(buildNotification(getMockServerUrl() + mockPath)))
            .when().post(restPath)
            .then().statusCode(200);

        verifyMetricsCallOk();
    }

    protected Object buildNotification(String webhookUrl) {
        return buildCamelNotification(webhookUrl);
    }

    @Test
    void testCallFailure() {
        saveMetrics();
        mockServerKo();
        given()
            .contentType(JSON)
            .body(Json.encode(buildNotification(getMockServerUrl() + mockPathKo)))
            .when().post(restPath)
            .then().statusCode(500);

        verifyMetricsCallKo();
    }

    @Test
    void testRetriesFailure() {
        saveMetrics();

        mockServerFailure();
        given()
            .contentType(JSON)
            .body(Json.encode(buildNotification(getMockServerUrl() + mockPathRetries)))
            .when().post(restPath)
            .then().statusCode(500);
        verifyRedeliveries();

        verifyMetricsCallRetries();
    }


    @Test
    protected void testRoutes() throws Exception {
        adviceWith(camelOutgoingRouteName, context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpointsAndSkip(mockRouteEndpoint + "*");
            }
        });

        CamelNotification notification = (CamelNotification) buildNotification(mockRouteEndpoint);

        // Camel encodes the '#' character into '%23' when building the mock endpoint URI.
        MockEndpoint testedEndpoint = getMockEndpoint(mockRouteEndpoint);
        testedEndpoint.expectedBodiesReceived(notification.message);

        given()
            .contentType(JSON)
            .body(Json.encode(notification))
            .when().post(restPath)
            .then().statusCode(200);

        testedEndpoint.assertIsSatisfied();
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

    private void verifyMetricsCallRetries() {
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled", "routeId", camelIncomingRouteName, 0);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled", "routeId", camelOutgoingRouteName, 0);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", camelIncomingRouteName, 0);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", camelOutgoingRouteName, 0);
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
}
