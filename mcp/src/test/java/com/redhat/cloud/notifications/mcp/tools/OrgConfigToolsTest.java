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
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

/**
 * Tests for OrgConfigTools: getDailyDigestTimePreference
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class OrgConfigToolsTest extends McpTestBase {

    private static final String GET_DAILY_DIGEST_TIME_PREFERENCE_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 14,
                "params": {
                    "name": "getDailyDigestTimePreference",
                    "arguments": {}
                }
            }
            """;

    @Test
    public void testGetDailyDigestTimePreferenceWithValidIdentity() {
        String timeJson = "\"09:00\"";
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/org-config/daily-digest/time-preference"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(timeJson))
        );

        postMcp(validIdentity(), GET_DAILY_DIGEST_TIME_PREFERENCE_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("09:00"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                getRequestedFor(urlPathEqualTo("/api/notifications/v1.0/org-config/daily-digest/time-preference"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testGetDailyDigestTimePreferenceWithoutIdentityIsRejected() {
        assertAuthRejected(null, GET_DAILY_DIGEST_TIME_PREFERENCE_BODY, "missing_header");
    }

    @Test
    public void testGetDailyDigestTimePreferenceWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/org-config/daily-digest/time-preference"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), GET_DAILY_DIGEST_TIME_PREFERENCE_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- setDailyDigestTimePreference tests ---

    private static final String SET_DAILY_DIGEST_TIME_PREFERENCE_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 20,
                "params": {
                    "name": "setDailyDigestTimePreference",
                    "arguments": {
                        "time": "14:30"
                    }
                }
            }
            """;

    @Test
    public void testSetDailyDigestTimePreferenceWithValidIdentity() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/notifications/v1.0/org-config/daily-digest/time-preference"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse().withStatus(204))
        );

        postMcp(validIdentity(), SET_DAILY_DIGEST_TIME_PREFERENCE_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("Daily digest time preference set to 14:30 UTC"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                putRequestedFor(urlPathEqualTo("/api/notifications/v1.0/org-config/daily-digest/time-preference"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testSetDailyDigestTimePreferenceWithoutIdentityIsRejected() {
        assertAuthRejected(null, SET_DAILY_DIGEST_TIME_PREFERENCE_BODY, "missing_header");
    }

    @Test
    public void testSetDailyDigestTimePreferenceWhenBackendReturns400() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/notifications/v1.0/org-config/daily-digest/time-preference"))
                        .willReturn(aResponse().withStatus(400))
        );

        postMcp(validIdentity(), SET_DAILY_DIGEST_TIME_PREFERENCE_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Invalid request"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testSetDailyDigestTimePreferenceWhenBackendReturns403() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/notifications/v1.0/org-config/daily-digest/time-preference"))
                        .willReturn(aResponse().withStatus(403))
        );

        postMcp(validIdentity(), SET_DAILY_DIGEST_TIME_PREFERENCE_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Access denied"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }
}
