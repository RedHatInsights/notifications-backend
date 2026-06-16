package com.redhat.cloud.notifications.mcp.tools;

import com.redhat.cloud.notifications.MockServerLifecycleManager;
import com.redhat.cloud.notifications.mcp.McpAssuredTestBase;
import com.redhat.cloud.notifications.mcp.TestLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class McpAssuredUserConfigToolsTest extends McpAssuredTestBase {

    @Test
    public void testListToolsContainsUserConfigTools() {
        client.when()
                .toolsList(page -> {
                    assertNotNull(page.findByName("getUserNotificationPreferences"));
                    assertNotNull(page.findByName("getUserNotificationPreferencesByApplication"));
                    assertNotNull(page.findByName("saveUserNotificationPreferences"));
                })
                .thenAssertResults();
    }

    @Test
    public void testGetUserNotificationPreferences() {
        String prefsJson = "{\"bundles\":{\"rhel\":{\"display_name\":\"Red Hat Enterprise Linux\",\"applications\":{}}}}";
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/user-config/notification-event-type-preference"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(prefsJson))
        );

        client.when()
                .toolsCall("getUserNotificationPreferences", Map.of(), response -> {
                    assertFalse(response.isError());
                    String text = response.firstContent().asText().text();
                    assertTrue(text.contains("rhel"));
                    assertTrue(text.contains("Red Hat Enterprise Linux"));
                })
                .thenAssertResults();
    }

    @Test
    public void testGetUserNotificationPreferencesBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/user-config/notification-event-type-preference"))
                        .willReturn(aResponse().withStatus(404))
        );

        client.when()
                .toolsCall("getUserNotificationPreferences", Map.of(), response -> {
                    assertTrue(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("Resource not found"));
                })
                .thenAssertResults();
    }

    @Test
    public void testGetUserNotificationPreferencesByApplication() {
        String appPrefsJson = "{\"display_name\":\"Patch\",\"event_types\":{\"new-advisory\":{\"display_name\":\"New advisory\"}}}";
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/user-config/notification-event-type-preference/rhel/patch"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(appPrefsJson))
        );

        client.when()
                .toolsCall("getUserNotificationPreferencesByApplication",
                        Map.of("bundleName", "rhel", "applicationName", "patch"), response -> {
                            assertFalse(response.isError());
                            String text = response.firstContent().asText().text();
                            assertTrue(text.contains("Patch"));
                            assertTrue(text.contains("New advisory"));
                        })
                .thenAssertResults();
    }

    @Test
    public void testGetUserNotificationPreferencesByApplicationBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/user-config/notification-event-type-preference/rhel/patch"))
                        .willReturn(aResponse().withStatus(404))
        );

        client.when()
                .toolsCall("getUserNotificationPreferencesByApplication",
                        Map.of("bundleName", "rhel", "applicationName", "patch"), response -> {
                            assertTrue(response.isError());
                            assertTrue(response.firstContent().asText().text().contains("Resource not found"));
                        })
                .thenAssertResults();
    }

    @Test
    public void testSaveUserNotificationPreferences() {
        MockServerLifecycleManager.getClient().stubFor(
                post(urlPathEqualTo("/api/notifications/v1.0/user-config/notification-event-type-preference"))
                        .willReturn(aResponse().withStatus(200))
        );

        Map<String, Object> args = Map.of(
                "bundleName", "rhel", "applicationName", "patch",
                "eventTypeName", "new-advisory", "subscriptionType", "INSTANT",
                "subscribe", true);

        client.when()
                .toolsCall("saveUserNotificationPreferences", args, response -> {
                    assertFalse(response.isError());
                    String text = response.firstContent().asText().text();
                    assertTrue(text.contains("enabled successfully"));
                    assertTrue(text.contains("rhel/patch/new-advisory"));
                    assertTrue(text.contains("INSTANT"));
                })
                .thenAssertResults();

        MockServerLifecycleManager.getClient().verify(
                postRequestedFor(urlPathEqualTo("/api/notifications/v1.0/user-config/notification-event-type-preference"))
        );
    }

    @Test
    public void testSaveUserNotificationPreferencesBackendReturns500() {
        MockServerLifecycleManager.getClient().stubFor(
                post(urlPathEqualTo("/api/notifications/v1.0/user-config/notification-event-type-preference"))
                        .willReturn(aResponse().withStatus(500))
        );

        Map<String, Object> args = Map.of(
                "bundleName", "rhel", "applicationName", "patch",
                "eventTypeName", "new-advisory", "subscriptionType", "INSTANT",
                "subscribe", true);

        client.when()
                .toolsCall("saveUserNotificationPreferences", args, response -> {
                    assertTrue(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("Backend service error"));
                })
                .thenAssertResults();
    }

    @Test
    public void testSaveUserNotificationPreferencesInvalidSubscriptionType() {
        Map<String, Object> args = Map.of(
                "bundleName", "rhel", "applicationName", "patch",
                "eventTypeName", "new-advisory", "subscriptionType", "WEEKLY",
                "subscribe", true);

        client.when()
                .toolsCall("saveUserNotificationPreferences", args, response -> {
                    assertTrue(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("subscriptionType must be one of"));
                })
                .thenAssertResults();
    }
}
