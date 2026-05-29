package com.redhat.cloud.notifications.mcp.tools;

import com.redhat.cloud.notifications.MockServerLifecycleManager;
import com.redhat.cloud.notifications.mcp.McpTestBase;
import com.redhat.cloud.notifications.mcp.TestLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

/**
 * Tests for EventTools: getEvents
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EventToolsTest extends McpTestBase {

    private static final String GET_EVENTS_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 13,
                "params": {
                    "name": "getEvents",
                    "arguments": {}
                }
            }
            """;

    @Test
    public void testGetEventsWithValidIdentity() {
        String eventsJson = "{\"data\":[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"bundle\":\"rhel\",\"application\":\"patch\",\"event_type\":\"New advisory\",\"created\":\"2026-05-12T10:00:00\"}],\"meta\":{\"count\":1},\"links\":{}}";
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/notifications/events"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(eventsJson))
        );

        postMcp(validIdentity(), GET_EVENTS_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("rhel"))
                .body("result.content[0].text", containsString("patch"))
                .body("result.content[0].text", containsString("New advisory"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                getRequestedFor(urlPathEqualTo("/api/notifications/v1.0/notifications/events"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testGetEventsWithoutIdentityIsRejected() {
        assertAuthRejected(null, GET_EVENTS_BODY, "missing_header");
    }

    @Test
    public void testGetEventsWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/notifications/events"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), GET_EVENTS_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }
}
