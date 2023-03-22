package com.redhat.cloud.notifications.processors.slack;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.TestLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Test;

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
@QuarkusTestResource(TestLifecycleManager.class)
@TestProfile(IsolatedCamelContextTestProfile.class)
public class SlackRedeliveriesTest extends CamelQuarkusTestSupport {

    private static final String MOCK_PATH = "/camel/slack";

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void test() {
        SlackNotification notification = buildNotification(getMockServerUrl() + MOCK_PATH);
        mockSlackServerFailure();
        given()
                .contentType(JSON)
                .body(Json.encode(notification))
                .when().post(REST_PATH)
                .then().statusCode(200);
        verifyRedeliveries();
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
