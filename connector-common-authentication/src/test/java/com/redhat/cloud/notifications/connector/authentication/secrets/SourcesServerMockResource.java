package com.redhat.cloud.notifications.connector.authentication.secrets;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

public class SourcesServerMockResource implements QuarkusTestResourceLifecycleManager {

    private static WireMockServer wireMockServer;

    @Override
    public Map<String, String> start() {

        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();

        String serverUrl = "http://localhost:" + wireMockServer.port();

        setupMockExpectations();

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.rest-client.sources-oidc.url", serverUrl);

        System.out.println("Sources server mock started on port: " + wireMockServer.port());

        return config;
    }

    private static void setupMockExpectations() {

        // Mock Sources endpoints - Return 200 for correct Authorization header, 401 otherwise

        // Mock Sources getById endpoint - Generic success case for any secret ID with valid auth
        // Priority 1 (higher priority) to match before the catch-all 401 stub
        wireMockServer.stubFor(get(urlMatching("/internal/v2.0/secrets/[0-9]+"))
            .atPriority(1)
            .withHeader("Authorization", equalTo("Bearer " + OidcServerMockResource.TEST_ACCESS_TOKEN))
            .withHeader("x-rh-sources-org-id", matching(".*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "username": "test-username",
                      "password": "test-password"
                    }
                    """)));

        // Mock Sources getById endpoint - Generic unauthorized case for any secret ID without auth
        // Priority 2 (lower priority) acts as catch-all for requests without proper auth
        wireMockServer.stubFor(get(urlMatching("/internal/v2.0/secrets/[0-9]+"))
            .atPriority(2)
            .withHeader("x-rh-sources-org-id", matching(".*"))
            .willReturn(aResponse()
                .withStatus(401)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "error": "Unauthorized - missing or invalid Authorization header"
                    }
                    """)));

        System.out.println("Mock expectations configured successfully for Sources endpoints");
    }

    @Override
    public void stop() {
        if (wireMockServer != null) {
            wireMockServer.stop();
            System.out.println("Sources server mock stopped");
        }
    }
}
