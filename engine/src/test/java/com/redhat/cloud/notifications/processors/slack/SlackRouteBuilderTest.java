package com.redhat.cloud.notifications.processors.slack;

import com.redhat.cloud.notifications.Json;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.processors.slack.SlackRouteBuilder.REST_PATH;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;

@QuarkusTest
public class SlackRouteBuilderTest extends CamelQuarkusTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    public String isMockEndpointsAndSkip() {
        return "slack*";
    }

    @Test
    void testRoutes() throws Exception {
        SlackNotification notification = new SlackNotification();
        notification.webhookUrl = "https://foo.bar";
        notification.channel = "#notifications";
        notification.message = "This is a test!";

        // Camel encodes the '#' character into '%23' when building the mock endpoint URI.
        MockEndpoint slackEndpoint = getMockEndpoint("mock:slack:%23notifications");
        slackEndpoint.expectedBodiesReceived(notification.message);

        given()
                .contentType(JSON)
                .body(Json.encode(notification))
                .when().post(REST_PATH)
                .then().statusCode(200);

        slackEndpoint.assertIsSatisfied();
    }
}
