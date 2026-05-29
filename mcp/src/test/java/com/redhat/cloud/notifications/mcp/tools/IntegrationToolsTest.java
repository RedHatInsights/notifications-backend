package com.redhat.cloud.notifications.mcp.tools;

import com.redhat.cloud.notifications.MockServerLifecycleManager;
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
 * Tests for IntegrationTools: getIntegrations, getIntegration, getIntegrationHistory, getIntegrationHistoryDetails
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class IntegrationToolsTest extends McpToolTestBase {

    private static final String GET_INTEGRATIONS_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 9,
                "params": {
                    "name": "getIntegrations",
                    "arguments": {}
                }
            }
            """;

    private static final String GET_INTEGRATION_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 10,
                "params": {
                    "name": "getIntegration",
                    "arguments": {
                        "id": "12345678-abcd-1234-abcd-1234567890ab"
                    }
                }
            }
            """;

    private static final String GET_INTEGRATION_HISTORY_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 11,
                "params": {
                    "name": "getIntegrationHistory",
                    "arguments": {
                        "id": "12345678-abcd-1234-abcd-1234567890ab"
                    }
                }
            }
            """;

    private static final String GET_INTEGRATION_HISTORY_DETAILS_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 12,
                "params": {
                    "name": "getIntegrationHistoryDetails",
                    "arguments": {
                        "integrationId": "12345678-abcd-1234-abcd-1234567890ab",
                        "historyId": "abcd1234-abcd-1234-abcd-1234567890ab"
                    }
                }
            }
            """;

    // --- getIntegrations tests ---

    @Test
    public void testGetIntegrationsWithValidIdentity() {
        String integrationsJson = "{\"data\":[{\"id\":\"12345678-abcd-1234-abcd-1234567890ab\",\"name\":\"My Webhook\",\"type\":\"webhook\"}],\"meta\":{\"count\":1},\"links\":{}}";
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v2.0/endpoints"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(integrationsJson))
        );

        postMcp(validIdentity(), GET_INTEGRATIONS_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("My Webhook"))
                .body("result.content[0].text", containsString("webhook"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                getRequestedFor(urlPathEqualTo("/api/integrations/v2.0/endpoints"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testGetIntegrationsWithoutIdentityIsRejected() {
        assertAuthRejected(null, GET_INTEGRATIONS_BODY, "missing_header");
    }

    @Test
    public void testGetIntegrationsWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v2.0/endpoints"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), GET_INTEGRATIONS_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- getIntegration tests ---

    @Test
    public void testGetIntegrationWithValidIdentity() {
        String integrationJson = "{\"id\":\"12345678-abcd-1234-abcd-1234567890ab\",\"name\":\"My Webhook\",\"type\":\"webhook\",\"enabled\":true}";
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v2.0/endpoints/12345678-abcd-1234-abcd-1234567890ab"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(integrationJson))
        );

        postMcp(validIdentity(), GET_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("My Webhook"))
                .body("result.content[0].text", containsString("12345678-abcd-1234-abcd-1234567890ab"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                getRequestedFor(urlPathEqualTo("/api/integrations/v2.0/endpoints/12345678-abcd-1234-abcd-1234567890ab"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testGetIntegrationWithoutIdentityIsRejected() {
        assertAuthRejected(null, GET_INTEGRATION_BODY, "missing_header");
    }

    @Test
    public void testGetIntegrationWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v2.0/endpoints/12345678-abcd-1234-abcd-1234567890ab"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), GET_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- getIntegrationHistory tests ---

    @Test
    public void testGetIntegrationHistoryWithValidIdentity() {
        String historyJson = "{\"data\":[{\"id\":\"abcd1234-abcd-1234-abcd-1234567890ab\",\"status\":\"SUCCESS\"}],\"meta\":{\"count\":1},\"links\":{}}";
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v2.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/history"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(historyJson))
        );

        postMcp(validIdentity(), GET_INTEGRATION_HISTORY_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("SUCCESS"))
                .body("result.content[0].text", containsString("abcd1234"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                getRequestedFor(urlPathEqualTo("/api/integrations/v2.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/history"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testGetIntegrationHistoryWithoutIdentityIsRejected() {
        assertAuthRejected(null, GET_INTEGRATION_HISTORY_BODY, "missing_header");
    }

    @Test
    public void testGetIntegrationHistoryWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v2.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/history"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), GET_INTEGRATION_HISTORY_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- getIntegrationHistoryDetails tests ---

    @Test
    public void testGetIntegrationHistoryDetailsWithValidIdentity() {
        String detailsJson = "{\"type\":\"com.redhat.console.notification.toCamel.webhook\",\"target\":\"https://example.com/hook\",\"outcome\":\"SUCCESS\"}";
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/history/abcd1234-abcd-1234-abcd-1234567890ab/details"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(detailsJson))
        );

        postMcp(validIdentity(), GET_INTEGRATION_HISTORY_DETAILS_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("webhook"))
                .body("result.content[0].text", containsString("SUCCESS"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                getRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/history/abcd1234-abcd-1234-abcd-1234567890ab/details"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testGetIntegrationHistoryDetailsWithoutIdentityIsRejected() {
        assertAuthRejected(null, GET_INTEGRATION_HISTORY_DETAILS_BODY, "missing_header");
    }

    @Test
    public void testGetIntegrationHistoryDetailsWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/history/abcd1234-abcd-1234-abcd-1234567890ab/details"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), GET_INTEGRATION_HISTORY_DETAILS_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }
}
