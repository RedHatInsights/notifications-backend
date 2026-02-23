package com.redhat.cloud.notifications.exports;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

public class ExportServiceServerMockResource implements QuarkusTestResourceLifecycleManager {

    private static WireMockServer wireMockServer;

    @Override
    public Map<String, String> start() {

        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig()
            .dynamicPort());
        wireMockServer.start();

        String serverUrl = "http://localhost:" + wireMockServer.port();

        setupMockExpectations();

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.rest-client.export-service-oidc.url", serverUrl);

        System.out.println("Export Service server mock started on port: " + wireMockServer.port());

        return config;
    }

    private static void setupMockExpectations() {

        // Mock Export Service upload JSON endpoint - Success case (higher priority)
        wireMockServer.stubFor(
            post(urlPathMatching("/app/export/v1/[^/]+/[^/]+/[^/]+/upload"))
                .withHeader("Authorization", containing("Bearer " + OidcServerMockResource.TEST_ACCESS_TOKEN))
                .atPriority(1)
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "status": "success",
                          "message": "Export uploaded successfully"
                        }
                        """)));

        // Mock Export Service upload endpoint - Unauthorized case (lower priority)
        wireMockServer.stubFor(
            post(urlPathMatching("/app/export/v1/[^/]+/[^/]+/[^/]+/upload"))
                .atPriority(2)
                .willReturn(aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "error": "Unauthorized - missing or invalid Authorization header"
                        }
                        """)));

        // Mock Export Service error notification endpoint - Success case (higher priority)
        wireMockServer.stubFor(
            post(urlPathMatching("/app/export/v1/[^/]+/[^/]+/[^/]+/error"))
                .withHeader("Authorization", containing("Bearer " + OidcServerMockResource.TEST_ACCESS_TOKEN))
                .atPriority(1)
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "status": "success",
                          "message": "Error notification received"
                        }
                        """)));

        // Mock Export Service error notification endpoint - Unauthorized case (lower priority)
        wireMockServer.stubFor(
            post(urlPathMatching("/app/export/v1/[^/]+/[^/]+/[^/]+/error"))
                .atPriority(2)
                .willReturn(aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "error": "Unauthorized - missing or invalid Authorization header"
                        }
                        """)));

        System.out.println("Mock expectations configured successfully for Export Service endpoints");
    }

    @Override
    public void stop() {
        if (wireMockServer != null) {
            wireMockServer.stop();
            System.out.println("Export Service server mock stopped");
        }
    }

    public static WireMockServer getWireMockServer() {
        return wireMockServer;
    }
}
