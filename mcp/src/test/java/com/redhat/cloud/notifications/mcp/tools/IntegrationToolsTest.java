package com.redhat.cloud.notifications.mcp.tools;

import com.redhat.cloud.notifications.MockServerLifecycleManager;
import com.redhat.cloud.notifications.mcp.McpTestBase;
import com.redhat.cloud.notifications.mcp.TestLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

/**
 * Tests for IntegrationTools: getIntegrations, getIntegration, getIntegrationHistory, getIntegrationHistoryDetails
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class IntegrationToolsTest extends McpTestBase {

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

    // --- enableIntegration tests ---

    private static final String ENABLE_INTEGRATION_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 17,
                "params": {
                    "name": "enableIntegration",
                    "arguments": {
                        "id": "12345678-abcd-1234-abcd-1234567890ab"
                    }
                }
            }
            """;

    @Test
    public void testEnableIntegrationWithValidIdentity() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/enable"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse().withStatus(200))
        );

        postMcp(validIdentity(), ENABLE_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("enabled successfully"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                putRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/enable"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testEnableIntegrationWithoutIdentityIsRejected() {
        assertAuthRejected(null, ENABLE_INTEGRATION_BODY, "missing_header");
    }

    @Test
    public void testEnableIntegrationWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/enable"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), ENABLE_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- disableIntegration tests ---

    private static final String DISABLE_INTEGRATION_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 18,
                "params": {
                    "name": "disableIntegration",
                    "arguments": {
                        "id": "12345678-abcd-1234-abcd-1234567890ab"
                    }
                }
            }
            """;

    @Test
    public void testDisableIntegrationWithValidIdentity() {
        MockServerLifecycleManager.getClient().stubFor(
                delete(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/enable"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse().withStatus(204))
        );

        postMcp(validIdentity(), DISABLE_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("disabled successfully"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                deleteRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/enable"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testDisableIntegrationWithoutIdentityIsRejected() {
        assertAuthRejected(null, DISABLE_INTEGRATION_BODY, "missing_header");
    }

    @Test
    public void testDisableIntegrationWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                delete(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/enable"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), DISABLE_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- testIntegration tests ---

    private static final String TEST_INTEGRATION_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 19,
                "params": {
                    "name": "testIntegration",
                    "arguments": {
                        "uuid": "12345678-abcd-1234-abcd-1234567890ab"
                    }
                }
            }
            """;

    @Test
    public void testTestIntegrationWithValidIdentity() {
        MockServerLifecycleManager.getClient().stubFor(
                post(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/test"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse().withStatus(204))
        );

        postMcp(validIdentity(), TEST_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("Test notification sent successfully"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                postRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/test"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testTestIntegrationWithoutIdentityIsRejected() {
        assertAuthRejected(null, TEST_INTEGRATION_BODY, "missing_header");
    }

    @Test
    public void testTestIntegrationWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                post(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/test"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), TEST_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testEnableIntegrationWhenBackendReturns403() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/enable"))
                        .willReturn(aResponse().withStatus(403))
        );

        postMcp(validIdentity(), ENABLE_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Access denied"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testDisableIntegrationWhenBackendReturns403() {
        MockServerLifecycleManager.getClient().stubFor(
                delete(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/enable"))
                        .willReturn(aResponse().withStatus(403))
        );

        postMcp(validIdentity(), DISABLE_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Access denied"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testTestIntegrationWhenBackendReturns403() {
        MockServerLifecycleManager.getClient().stubFor(
                post(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/test"))
                        .willReturn(aResponse().withStatus(403))
        );

        postMcp(validIdentity(), TEST_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Access denied"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- deleteIntegration tests ---

    private static final String DELETE_INTEGRATION_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 20,
                "params": {
                    "name": "deleteIntegration",
                    "arguments": {
                        "id": "12345678-abcd-1234-abcd-1234567890ab"
                    }
                }
            }
            """;

    @Test
    public void testDeleteIntegrationWithValidIdentity() {
        MockServerLifecycleManager.getClient().stubFor(
                delete(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse().withStatus(204))
        );

        postMcp(validIdentity(), DELETE_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("deleted successfully"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                deleteRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testDeleteIntegrationWithoutIdentityIsRejected() {
        assertAuthRejected(null, DELETE_INTEGRATION_BODY, "missing_header");
    }

    @Test
    public void testDeleteIntegrationWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                delete(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), DELETE_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testDeleteIntegrationWhenBackendReturns403() {
        MockServerLifecycleManager.getClient().stubFor(
                delete(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab"))
                        .willReturn(aResponse().withStatus(403))
        );

        postMcp(validIdentity(), DELETE_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Access denied"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- createIntegration tests ---

    private static final String CREATE_INTEGRATION_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 21,
                "params": {
                    "name": "createIntegration",
                    "arguments": {
                        "endpoint": {
                            "name": "Test Webhook",
                            "description": "Test webhook integration",
                            "type": "webhook",
                            "enabled": true,
                            "properties": {
                                "url": "https://example.com/webhook",
                                "method": "POST",
                                "disable_ssl_verification": false
                            }
                        }
                    }
                }
            }
            """;

    @Test
    public void testCreateIntegrationWithValidIdentity() {
        String createdEndpointJson = "{\"id\":\"12345678-abcd-1234-abcd-1234567890ab\",\"name\":\"Test Webhook\",\"type\":\"webhook\"}";
        MockServerLifecycleManager.getClient().stubFor(
                post(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "application/json")
                                .withBody(createdEndpointJson))
        );

        postMcp(validIdentity(), CREATE_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("Test Webhook"))
                .body("result.content[0].text", containsString("webhook"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                postRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testCreateIntegrationWithoutIdentityIsRejected() {
        assertAuthRejected(null, CREATE_INTEGRATION_BODY, "missing_header");
    }

    @Test
    public void testCreateIntegrationWhenBackendReturns400() {
        MockServerLifecycleManager.getClient().stubFor(
                post(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .willReturn(aResponse().withStatus(400))
        );

        postMcp(validIdentity(), CREATE_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Invalid request"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testCreateIntegrationWhenBackendReturns403() {
        MockServerLifecycleManager.getClient().stubFor(
                post(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .willReturn(aResponse().withStatus(403))
        );

        postMcp(validIdentity(), CREATE_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Access denied"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- updateIntegration tests ---

    private static final String UPDATE_INTEGRATION_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 22,
                "params": {
                    "name": "updateIntegration",
                    "arguments": {
                        "id": "12345678-abcd-1234-abcd-1234567890ab",
                        "endpoint": {
                            "name": "Updated Webhook",
                            "description": "Updated webhook integration",
                            "type": "webhook",
                            "enabled": true,
                            "properties": {
                                "url": "https://example.com/updated",
                                "method": "POST",
                                "disable_ssl_verification": false
                            }
                        }
                    }
                }
            }
            """;

    @Test
    public void testUpdateIntegrationWithValidIdentity() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse().withStatus(200))
        );

        postMcp(validIdentity(), UPDATE_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("updated successfully"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                putRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testUpdateIntegrationWithoutIdentityIsRejected() {
        assertAuthRejected(null, UPDATE_INTEGRATION_BODY, "missing_header");
    }

    @Test
    public void testUpdateIntegrationWhenBackendReturns400() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab"))
                        .willReturn(aResponse().withStatus(400))
        );

        postMcp(validIdentity(), UPDATE_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Invalid request"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testUpdateIntegrationWhenBackendReturns403() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab"))
                        .willReturn(aResponse().withStatus(403))
        );

        postMcp(validIdentity(), UPDATE_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Access denied"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // ========================================
    // Polymorphic Type Serialization Tests
    // ========================================
    // These tests verify that our @JsonSubTypes configuration correctly maps
    // endpoint types to their properties DTOs during JSON serialization.
    // Each test verifies the request body sent to backend contains the correct structure.

    @Test
    public void testCreateWebhookIntegrationSerializesCorrectly() {
        String webhookRequest = """
                {
                    "jsonrpc": "2.0",
                    "method": "tools/call",
                    "id": 100,
                    "params": {
                        "name": "createIntegration",
                        "arguments": {
                            "endpoint": {
                                "name": "Production Webhook",
                                "description": "Webhook for production alerts",
                                "type": "webhook",
                                "enabled": true,
                                "properties": {
                                    "url": "https://example.com/webhook",
                                    "method": "POST",
                                    "disable_ssl_verification": false,
                                    "secret_token": "webhook-secret-123"
                                }
                            }
                        }
                    }
                }
                """;

        MockServerLifecycleManager.getClient().stubFor(
                post(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withBody("{\"id\":\"webhook-id\",\"name\":\"Production Webhook\",\"type\":\"webhook\"}"))
        );

        postMcp(validIdentity(), webhookRequest).statusCode(200);

        // Verify request body structure (WebhookPropertiesDTO)
        MockServerLifecycleManager.getClient().verify(
                postRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .withRequestBody(matchingJsonPath("$.type", equalTo("webhook")))
                        .withRequestBody(matchingJsonPath("$.properties.url", equalTo("https://example.com/webhook")))
                        .withRequestBody(matchingJsonPath("$.properties.method", equalTo("POST")))
                        .withRequestBody(matchingJsonPath("$.properties.disable_ssl_verification", equalTo("false")))
                        .withRequestBody(matchingJsonPath("$.properties.secret_token", equalTo("webhook-secret-123")))
        );
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testCreateAnsibleIntegrationSerializesCorrectly() {
        String ansibleRequest = """
                {
                    "jsonrpc": "2.0",
                    "method": "tools/call",
                    "id": 101,
                    "params": {
                        "name": "createIntegration",
                        "arguments": {
                            "endpoint": {
                                "name": "Ansible Tower",
                                "description": "Ansible Tower webhook",
                                "type": "ansible",
                                "enabled": true,
                                "properties": {
                                    "url": "https://tower.example.com/api/webhook",
                                    "method": "POST",
                                    "disable_ssl_verification": false,
                                    "bearer_authentication": "bearer-token-456"
                                }
                            }
                        }
                    }
                }
                """;

        MockServerLifecycleManager.getClient().stubFor(
                post(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withBody("{\"id\":\"ansible-id\",\"name\":\"Ansible Tower\",\"type\":\"ansible\"}"))
        );

        postMcp(validIdentity(), ansibleRequest).statusCode(200);

        // Verify request body structure (WebhookPropertiesDTO - same as webhook)
        MockServerLifecycleManager.getClient().verify(
                postRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .withRequestBody(matchingJsonPath("$.type", equalTo("ansible")))
                        .withRequestBody(matchingJsonPath("$.properties.url", equalTo("https://tower.example.com/api/webhook")))
                        .withRequestBody(matchingJsonPath("$.properties.method", equalTo("POST")))
                        .withRequestBody(matchingJsonPath("$.properties.bearer_authentication", equalTo("bearer-token-456")))
        );
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testCreateCamelSlackIntegrationSerializesCorrectly() {
        String camelSlackRequest = """
                {
                    "jsonrpc": "2.0",
                    "method": "tools/call",
                    "id": 102,
                    "params": {
                        "name": "createIntegration",
                        "arguments": {
                            "endpoint": {
                                "name": "Slack Alerts",
                                "description": "Slack integration for #alerts channel",
                                "type": "camel",
                                "sub_type": "slack",
                                "enabled": true,
                                "properties": {
                                    "url": "https://hooks.slack.com/services/ABC/DEF/XYZ",
                                    "disable_ssl_verification": false,
                                    "secret_token": "slack-token-789",
                                    "extras": {
                                        "channel": "#alerts"
                                    }
                                }
                            }
                        }
                    }
                }
                """;

        MockServerLifecycleManager.getClient().stubFor(
                post(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withBody("{\"id\":\"slack-id\",\"name\":\"Slack Alerts\",\"type\":\"camel\",\"sub_type\":\"slack\"}"))
        );

        postMcp(validIdentity(), camelSlackRequest).statusCode(200);

        // Verify request body structure (CamelPropertiesDTO with sub_type)
        MockServerLifecycleManager.getClient().verify(
                postRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .withRequestBody(matchingJsonPath("$.type", equalTo("camel")))
                        .withRequestBody(matchingJsonPath("$.sub_type", equalTo("slack")))
                        .withRequestBody(matchingJsonPath("$.properties.url", equalTo("https://hooks.slack.com/services/ABC/DEF/XYZ")))
                        .withRequestBody(matchingJsonPath("$.properties.disable_ssl_verification", equalTo("false")))
                        .withRequestBody(matchingJsonPath("$.properties.extras.channel", equalTo("#alerts")))
        );
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testCreatePagerDutyIntegrationSerializesCorrectly() {
        String pagerDutyRequest = """
                {
                    "jsonrpc": "2.0",
                    "method": "tools/call",
                    "id": 103,
                    "params": {
                        "name": "createIntegration",
                        "arguments": {
                            "endpoint": {
                                "name": "PagerDuty Critical",
                                "description": "PagerDuty for critical incidents",
                                "type": "pagerduty",
                                "enabled": true,
                                "properties": {
                                    "severity": "critical",
                                    "secret_token": "pd-integration-key-12345"
                                }
                            }
                        }
                    }
                }
                """;

        MockServerLifecycleManager.getClient().stubFor(
                post(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withBody("{\"id\":\"pd-id\",\"name\":\"PagerDuty Critical\",\"type\":\"pagerduty\"}"))
        );

        postMcp(validIdentity(), pagerDutyRequest).statusCode(200);

        // Verify request body structure (PagerDutyPropertiesDTO)
        MockServerLifecycleManager.getClient().verify(
                postRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .withRequestBody(matchingJsonPath("$.type", equalTo("pagerduty")))
                        .withRequestBody(matchingJsonPath("$.properties.severity", equalTo("critical")))
                        .withRequestBody(matchingJsonPath("$.properties.secret_token", equalTo("pd-integration-key-12345")))
        );
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testCreateDrawerIntegrationSerializesCorrectly() {
        String drawerRequest = """
                {
                    "jsonrpc": "2.0",
                    "method": "tools/call",
                    "id": 104,
                    "params": {
                        "name": "createIntegration",
                        "arguments": {
                            "endpoint": {
                                "name": "Drawer Notifications",
                                "description": "In-app drawer notifications",
                                "type": "drawer",
                                "enabled": true,
                                "properties": {
                                    "only_admins": false,
                                    "ignore_preferences": false
                                }
                            }
                        }
                    }
                }
                """;

        MockServerLifecycleManager.getClient().stubFor(
                post(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withBody("{\"id\":\"drawer-id\",\"name\":\"Drawer Notifications\",\"type\":\"drawer\"}"))
        );

        postMcp(validIdentity(), drawerRequest).statusCode(200);

        // Verify request body structure (SystemSubscriptionPropertiesDTO)
        MockServerLifecycleManager.getClient().verify(
                postRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .withRequestBody(matchingJsonPath("$.type", equalTo("drawer")))
                        .withRequestBody(matchingJsonPath("$.properties.only_admins", equalTo("false")))
                        .withRequestBody(matchingJsonPath("$.properties.ignore_preferences", equalTo("false")))
        );
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testCreateEmailSubscriptionIntegrationSerializesCorrectly() {
        String emailRequest = """
                {
                    "jsonrpc": "2.0",
                    "method": "tools/call",
                    "id": 105,
                    "params": {
                        "name": "createIntegration",
                        "arguments": {
                            "endpoint": {
                                "name": "Email Subscription",
                                "description": "Email subscription endpoint",
                                "type": "email_subscription",
                                "enabled": true,
                                "properties": {
                                    "only_admins": true,
                                    "ignore_preferences": false
                                }
                            }
                        }
                    }
                }
                """;

        MockServerLifecycleManager.getClient().stubFor(
                post(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withBody("{\"id\":\"email-id\",\"name\":\"Email Subscription\",\"type\":\"email_subscription\"}"))
        );

        postMcp(validIdentity(), emailRequest).statusCode(200);

        // Verify request body structure (SystemSubscriptionPropertiesDTO - same as drawer)
        MockServerLifecycleManager.getClient().verify(
                postRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .withRequestBody(matchingJsonPath("$.type", equalTo("email_subscription")))
                        .withRequestBody(matchingJsonPath("$.properties.only_admins", equalTo("true")))
                        .withRequestBody(matchingJsonPath("$.properties.ignore_preferences", equalTo("false")))
        );
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testCreateCamelTeamsIntegrationSerializesCorrectly() {
        String camelTeamsRequest = """
                {
                    "jsonrpc": "2.0",
                    "method": "tools/call",
                    "id": 106,
                    "params": {
                        "name": "createIntegration",
                        "arguments": {
                            "endpoint": {
                                "name": "Teams Notifications",
                                "description": "Microsoft Teams integration",
                                "type": "camel",
                                "sub_type": "teams",
                                "enabled": true,
                                "properties": {
                                    "url": "https://outlook.office.com/webhook/...",
                                    "disable_ssl_verification": false
                                }
                            }
                        }
                    }
                }
                """;

        MockServerLifecycleManager.getClient().stubFor(
                post(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withBody("{\"id\":\"teams-id\",\"name\":\"Teams Notifications\",\"type\":\"camel\",\"sub_type\":\"teams\"}"))
        );

        postMcp(validIdentity(), camelTeamsRequest).statusCode(200);

        // Verify request body structure (CamelPropertiesDTO with teams sub_type)
        MockServerLifecycleManager.getClient().verify(
                postRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .withRequestBody(matchingJsonPath("$.type", equalTo("camel")))
                        .withRequestBody(matchingJsonPath("$.sub_type", equalTo("teams")))
                        .withRequestBody(matchingJsonPath("$.properties.url", equalTo("https://outlook.office.com/webhook/...")))
        );
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testCreateCamelServiceNowIntegrationSerializesCorrectly() {
        String camelServiceNowRequest = """
                {
                    "jsonrpc": "2.0",
                    "method": "tools/call",
                    "id": 107,
                    "params": {
                        "name": "createIntegration",
                        "arguments": {
                            "endpoint": {
                                "name": "ServiceNow Integration",
                                "description": "ServiceNow incident management",
                                "type": "camel",
                                "sub_type": "servicenow",
                                "enabled": true,
                                "properties": {
                                    "url": "https://instance.service-now.com/api/webhook",
                                    "disable_ssl_verification": false,
                                    "secret_token": "snow-token-999"
                                }
                            }
                        }
                    }
                }
                """;

        MockServerLifecycleManager.getClient().stubFor(
                post(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withBody("{\"id\":\"snow-id\",\"name\":\"ServiceNow Integration\",\"type\":\"camel\",\"sub_type\":\"servicenow\"}"))
        );

        postMcp(validIdentity(), camelServiceNowRequest).statusCode(200);

        // Verify request body structure (CamelPropertiesDTO with servicenow sub_type)
        MockServerLifecycleManager.getClient().verify(
                postRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints"))
                        .withRequestBody(matchingJsonPath("$.type", equalTo("camel")))
                        .withRequestBody(matchingJsonPath("$.sub_type", equalTo("servicenow")))
                        .withRequestBody(matchingJsonPath("$.properties.url", equalTo("https://instance.service-now.com/api/webhook")))
                        .withRequestBody(matchingJsonPath("$.properties.secret_token", equalTo("snow-token-999")))
        );
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- addEventTypeToIntegration tests ---

    private static final String ADD_EVENT_TYPE_TO_INTEGRATION_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 200,
                "params": {
                    "name": "addEventTypeToIntegration",
                    "arguments": {
                        "endpointId": "12345678-abcd-1234-abcd-1234567890ab",
                        "eventTypeId": "87654321-dcba-4321-dcba-ba0987654321"
                    }
                }
            }
            """;

    @Test
    public void testAddEventTypeToIntegrationWithValidIdentity() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/eventType/87654321-dcba-4321-dcba-ba0987654321"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse().withStatus(204))
        );

        postMcp(validIdentity(), ADD_EVENT_TYPE_TO_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("linked to integration successfully"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                putRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/eventType/87654321-dcba-4321-dcba-ba0987654321"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testAddEventTypeToIntegrationWithoutIdentityIsRejected() {
        assertAuthRejected(null, ADD_EVENT_TYPE_TO_INTEGRATION_BODY, "missing_header");
    }

    @Test
    public void testAddEventTypeToIntegrationWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/eventType/87654321-dcba-4321-dcba-ba0987654321"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), ADD_EVENT_TYPE_TO_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testAddEventTypeToIntegrationWhenBackendReturns403() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/eventType/87654321-dcba-4321-dcba-ba0987654321"))
                        .willReturn(aResponse().withStatus(403))
        );

        postMcp(validIdentity(), ADD_EVENT_TYPE_TO_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Access denied"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- deleteEventTypeFromIntegration tests ---

    private static final String DELETE_EVENT_TYPE_FROM_INTEGRATION_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 201,
                "params": {
                    "name": "deleteEventTypeFromIntegration",
                    "arguments": {
                        "endpointId": "12345678-abcd-1234-abcd-1234567890ab",
                        "eventTypeId": "87654321-dcba-4321-dcba-ba0987654321"
                    }
                }
            }
            """;

    @Test
    public void testDeleteEventTypeFromIntegrationWithValidIdentity() {
        MockServerLifecycleManager.getClient().stubFor(
                delete(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/eventType/87654321-dcba-4321-dcba-ba0987654321"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse().withStatus(204))
        );

        postMcp(validIdentity(), DELETE_EVENT_TYPE_FROM_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("unlinked from integration successfully"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                deleteRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/eventType/87654321-dcba-4321-dcba-ba0987654321"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testDeleteEventTypeFromIntegrationWithoutIdentityIsRejected() {
        assertAuthRejected(null, DELETE_EVENT_TYPE_FROM_INTEGRATION_BODY, "missing_header");
    }

    @Test
    public void testDeleteEventTypeFromIntegrationWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                delete(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/eventType/87654321-dcba-4321-dcba-ba0987654321"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), DELETE_EVENT_TYPE_FROM_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testDeleteEventTypeFromIntegrationWhenBackendReturns403() {
        MockServerLifecycleManager.getClient().stubFor(
                delete(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/eventType/87654321-dcba-4321-dcba-ba0987654321"))
                        .willReturn(aResponse().withStatus(403))
        );

        postMcp(validIdentity(), DELETE_EVENT_TYPE_FROM_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Access denied"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    // --- updateEventTypesLinkedToIntegration tests ---

    private static final String UPDATE_EVENT_TYPES_LINKED_TO_INTEGRATION_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 202,
                "params": {
                    "name": "updateEventTypesLinkedToIntegration",
                    "arguments": {
                        "endpointId": "12345678-abcd-1234-abcd-1234567890ab",
                        "eventTypeIds": ["87654321-dcba-4321-dcba-ba0987654321", "11111111-2222-3333-4444-555555555555"]
                    }
                }
            }
            """;

    @Test
    public void testUpdateEventTypesLinkedToIntegrationWithValidIdentity() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/eventTypes"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse().withStatus(204))
        );

        postMcp(validIdentity(), UPDATE_EVENT_TYPES_LINKED_TO_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("associations updated successfully"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                putRequestedFor(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/eventTypes"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .withRequestBody(matchingJsonPath("$[0]"))  // Verify array body
        );
    }

    @Test
    public void testUpdateEventTypesLinkedToIntegrationWithoutIdentityIsRejected() {
        assertAuthRejected(null, UPDATE_EVENT_TYPES_LINKED_TO_INTEGRATION_BODY, "missing_header");
    }

    @Test
    public void testUpdateEventTypesLinkedToIntegrationWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/eventTypes"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), UPDATE_EVENT_TYPES_LINKED_TO_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testUpdateEventTypesLinkedToIntegrationWhenBackendReturns403() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/integrations/v1.0/endpoints/12345678-abcd-1234-abcd-1234567890ab/eventTypes"))
                        .willReturn(aResponse().withStatus(403))
        );

        postMcp(validIdentity(), UPDATE_EVENT_TYPES_LINKED_TO_INTEGRATION_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Access denied"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }
}
