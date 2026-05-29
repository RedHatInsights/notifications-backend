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
 * Tests for UserConfigTools: getUserNotificationPreferences, getUserNotificationPreferencesByApplication
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class UserConfigToolsTest extends McpToolTestBase {

    private static final String GET_USER_NOTIFICATION_PREFERENCES_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 15,
                "params": {
                    "name": "getUserNotificationPreferences",
                    "arguments": {}
                }
            }
            """;

    private static final String GET_USER_NOTIFICATION_PREFERENCES_BY_APP_BODY = """
            {
                "jsonrpc": "2.0",
                "method": "tools/call",
                "id": 16,
                "params": {
                    "name": "getUserNotificationPreferencesByApplication",
                    "arguments": {
                        "bundleName": "rhel",
                        "applicationName": "patch"
                    }
                }
            }
            """;

    @Test
    public void testGetUserNotificationPreferencesWithValidIdentity() {
        String prefsJson = "{\"bundles\":{\"rhel\":{\"display_name\":\"Red Hat Enterprise Linux\",\"applications\":{}}}}";
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/user-config/notification-event-type-preference"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(prefsJson))
        );

        postMcp(validIdentity(), GET_USER_NOTIFICATION_PREFERENCES_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("rhel"))
                .body("result.content[0].text", containsString("Red Hat Enterprise Linux"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                getRequestedFor(urlPathEqualTo("/api/notifications/v1.0/user-config/notification-event-type-preference"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testGetUserNotificationPreferencesWithoutIdentityIsRejected() {
        assertAuthRejected(null, GET_USER_NOTIFICATION_PREFERENCES_BODY, "missing_header");
    }

    @Test
    public void testGetUserNotificationPreferencesWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/user-config/notification-event-type-preference"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), GET_USER_NOTIFICATION_PREFERENCES_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }

    @Test
    public void testGetUserNotificationPreferencesByApplicationWithValidIdentity() {
        String appPrefsJson = "{\"display_name\":\"Patch\",\"event_types\":{\"new-advisory\":{\"display_name\":\"New advisory\"}}}";
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/user-config/notification-event-type-preference/rhel/patch"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(appPrefsJson))
        );

        postMcp(validIdentity(), GET_USER_NOTIFICATION_PREFERENCES_BY_APP_BODY)
                .statusCode(200)
                .body("result.content[0].text", containsString("Patch"))
                .body("result.content[0].text", containsString("New advisory"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);

        MockServerLifecycleManager.getClient().verify(
                getRequestedFor(urlPathEqualTo("/api/notifications/v1.0/user-config/notification-event-type-preference/rhel/patch"))
                        .withHeader("x-rh-identity", equalTo(validIdentity()))
        );
    }

    @Test
    public void testGetUserNotificationPreferencesByApplicationWithoutIdentityIsRejected() {
        assertAuthRejected(null, GET_USER_NOTIFICATION_PREFERENCES_BY_APP_BODY, "missing_header");
    }

    @Test
    public void testGetUserNotificationPreferencesByApplicationWhenBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/user-config/notification-event-type-preference/rhel/patch"))
                        .willReturn(aResponse().withStatus(404))
        );

        postMcp(validIdentity(), GET_USER_NOTIFICATION_PREFERENCES_BY_APP_BODY)
                .statusCode(200)
                .body("result.isError", is(true))
                .body("result.content[0].text", containsString("Resource not found"));
        micrometerAssertionHelper.assertCounterIncrement(AUTH_SUCCESS_COUNTER, 1);
    }
}
