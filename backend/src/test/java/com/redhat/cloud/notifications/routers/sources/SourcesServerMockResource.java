package com.redhat.cloud.notifications.routers.sources;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.redhat.cloud.notifications.auth.OidcServerMockResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

public class SourcesServerMockResource implements QuarkusTestResourceLifecycleManager {

    private static WireMockServer wireMockServer;

    @Override
    public Map<String, String> start() {

        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig()
            .dynamicPort());
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

        // Mock Sources getById endpoint - Success case (higher priority)
        wireMockServer.stubFor(
            get(urlEqualTo("/internal/v2.0/secrets/123"))
                .withHeader("Authorization", equalTo("Bearer " + OidcServerMockResource.TEST_ACCESS_TOKEN))
                .withHeader("x-rh-sources-org-id", equalTo("test-org-id"))
                .atPriority(1)
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "id": 123,
                          "password": "test-password",
                          "authtype": "notifications-secret-token"
                        }
                        """))
        );

        // Mock Sources create endpoint - Success case (higher priority)
        wireMockServer.stubFor(
            post(urlEqualTo("/api/sources/v3.1/secrets"))
                .withHeader("Authorization", equalTo("Bearer " + OidcServerMockResource.TEST_ACCESS_TOKEN))
                .withHeader("x-rh-sources-org-id", equalTo("test-org-id"))
                .atPriority(1)
                .willReturn(aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "id": 456,
                          "password": "new-secret",
                          "authtype": "notifications-secret-token"
                        }
                        """))
        );

        // Mock Sources update endpoint - Success case (higher priority)
        wireMockServer.stubFor(
            patch(urlEqualTo("/api/sources/v3.1/secrets/123"))
                .withHeader("Authorization", equalTo("Bearer " + OidcServerMockResource.TEST_ACCESS_TOKEN))
                .withHeader("x-rh-sources-org-id", equalTo("test-org-id"))
                .atPriority(1)
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "id": 123,
                          "password": "updated-secret",
                          "authtype": "notifications-secret-token"
                        }
                        """))
        );

        // Mock Sources delete endpoint - Success case (higher priority)
        wireMockServer.stubFor(
            delete(urlEqualTo("/api/sources/v3.1/secrets/123"))
                .withHeader("Authorization", equalTo("Bearer " + OidcServerMockResource.TEST_ACCESS_TOKEN))
                .withHeader("x-rh-sources-org-id", equalTo("test-org-id"))
                .atPriority(1)
                .willReturn(aResponse()
                    .withStatus(204))
        );

        // Mock unauthorized responses for requests without proper Authorization header (lower priority)
        // This will catch any request without the correct Bearer token
        wireMockServer.stubFor(
            get(urlPathMatching("/internal/v2\\.0/secrets/.*"))
                .atPriority(2)
                .willReturn(aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "error": "Unauthorized"
                        }
                        """))
        );

        wireMockServer.stubFor(
            post(urlPathMatching("/api/sources/v3\\.1/secrets.*"))
                .atPriority(2)
                .willReturn(aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "error": "Unauthorized"
                        }
                        """))
        );

        wireMockServer.stubFor(
            patch(urlPathMatching("/api/sources/v3\\.1/secrets/.*"))
                .atPriority(2)
                .willReturn(aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "error": "Unauthorized"
                        }
                        """))
        );

        wireMockServer.stubFor(
            delete(urlPathMatching("/api/sources/v3\\.1/secrets/.*"))
                .atPriority(2)
                .willReturn(aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "error": "Unauthorized"
                        }
                        """))
        );
    }

    @Override
    public void stop() {
        if (wireMockServer != null) {
            wireMockServer.stop();
            System.out.println("Sources server mock stopped");
        }
    }
}
