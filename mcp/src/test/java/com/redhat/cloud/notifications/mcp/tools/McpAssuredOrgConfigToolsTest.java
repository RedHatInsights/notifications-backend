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
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class McpAssuredOrgConfigToolsTest extends McpAssuredTestBase {

    @Test
    public void testListToolsContainsOrgConfigTools() {
        client.when()
                .toolsList(page -> {
                    assertNotNull(page.findByName("getDailyDigestTimePreference"));
                    assertNotNull(page.findByName("setDailyDigestTimePreference"));
                })
                .thenAssertResults();
    }

    @Test
    public void testGetDailyDigestTimePreference() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/org-config/daily-digest/time-preference"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody("\"09:00\""))
        );

        client.when()
                .toolsCall("getDailyDigestTimePreference", Map.of(), response -> {
                    assertFalse(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("09:00"));
                })
                .thenAssertResults();
    }

    @Test
    public void testGetDailyDigestTimePreferenceBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/org-config/daily-digest/time-preference"))
                        .willReturn(aResponse().withStatus(404))
        );

        client.when()
                .toolsCall("getDailyDigestTimePreference", Map.of(), response -> {
                    assertTrue(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("Resource not found"));
                })
                .thenAssertResults();
    }

    @Test
    public void testSetDailyDigestTimePreference() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/notifications/v1.0/org-config/daily-digest/time-preference"))
                        .willReturn(aResponse().withStatus(204))
        );

        client.when()
                .toolsCall("setDailyDigestTimePreference", Map.of("time", "14:30"), response -> {
                    assertFalse(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("Daily digest time preference set to 14:30 UTC"));
                })
                .thenAssertResults();

        MockServerLifecycleManager.getClient().verify(
                putRequestedFor(urlPathEqualTo("/api/notifications/v1.0/org-config/daily-digest/time-preference"))
        );
    }

    @Test
    public void testSetDailyDigestTimePreferenceBackendReturns400() {
        MockServerLifecycleManager.getClient().stubFor(
                put(urlPathEqualTo("/api/notifications/v1.0/org-config/daily-digest/time-preference"))
                        .willReturn(aResponse().withStatus(400))
        );

        client.when()
                .toolsCall("setDailyDigestTimePreference", Map.of("time", "14:30"), response -> {
                    assertTrue(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("Invalid request"));
                })
                .thenAssertResults();
    }

    @Test
    public void testSetDailyDigestTimePreferenceWithInvalidMinute() {
        client.when()
                .toolsCall("setDailyDigestTimePreference", Map.of("time", "09:10"), response -> {
                    assertTrue(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("must match"));
                })
                .thenAssertResults();
    }
}
