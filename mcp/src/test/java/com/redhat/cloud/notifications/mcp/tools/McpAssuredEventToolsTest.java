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
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class McpAssuredEventToolsTest extends McpAssuredTestBase {

    @Test
    public void testListToolsContainsGetEvents() {
        client.when()
                .toolsList(page -> assertNotNull(page.findByName("getEvents")))
                .thenAssertResults();
    }

    @Test
    public void testGetEvents() {
        String eventsJson = "{\"data\":[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"bundle\":\"rhel\",\"application\":\"patch\",\"event_type\":\"New advisory\",\"created\":\"2026-05-12T10:00:00\"}],\"meta\":{\"count\":1},\"links\":{}}";
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/notifications/events"))
                        .willReturn(aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(eventsJson))
        );

        client.when()
                .toolsCall("getEvents", Map.of(), response -> {
                    assertFalse(response.isError());
                    String text = response.firstContent().asText().text();
                    assertTrue(text.contains("rhel"));
                    assertTrue(text.contains("patch"));
                    assertTrue(text.contains("New advisory"));
                })
                .thenAssertResults();
    }

    @Test
    public void testGetEventsBackendReturns404() {
        MockServerLifecycleManager.getClient().stubFor(
                get(urlPathEqualTo("/api/notifications/v1.0/notifications/events"))
                        .willReturn(aResponse().withStatus(404))
        );

        client.when()
                .toolsCall("getEvents", Map.of(), response -> {
                    assertTrue(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("Resource not found"));
                })
                .thenAssertResults();
    }
}
