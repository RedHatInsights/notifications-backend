package com.redhat.cloud.notifications.recipients.resolver.rbac;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

public class RbacServerMockResource implements QuarkusTestResourceLifecycleManager {

    private static WireMockServer wireMockServer;

    @Override
    public Map<String, String> start() {

        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig()
            .dynamicPort());
        wireMockServer.start();

        String serverUrl = "http://localhost:" + wireMockServer.port();

        setupMockExpectations();

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.rest-client.rbac-s2s-oidc.url", serverUrl);

        System.out.println("RBAC server mock started on port: " + wireMockServer.port());

        return config;
    }

    private static void setupMockExpectations() {

        // Mock RBAC endpoints - Return 200 for correct Authorization header, 401 otherwise

        // Mock RBAC getUsers endpoint - Success case (higher priority)
        wireMockServer.stubFor(
            get(urlPathMatching("/api/rbac/v1/principals/?.*"))
                .withHeader("Authorization", containing("Bearer " + OidcServerMockResource.TEST_ACCESS_TOKEN))
                .atPriority(1)
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "meta": {
                            "count": 2,
                            "limit": 50,
                            "offset": 0
                          },
                          "data": [
                            {
                              "username": "test-user-1",
                              "email": "user1@example.com",
                              "first_name": "Test",
                              "last_name": "User1"
                            },
                            {
                              "username": "test-user-2",
                              "email": "user2@example.com",
                              "first_name": "Test",
                              "last_name": "User2"
                            }
                          ]
                        }
                        """)));

        // Mock RBAC getUsers endpoint - Unauthorized case (lower priority)
        wireMockServer.stubFor(
            get(urlPathMatching("/api/rbac/v1/principals/?.*"))
                .atPriority(2)
                .willReturn(aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "error": "Unauthorized - missing or invalid Authorization header"
                        }
                        """)));

        // Mock RBAC getGroup endpoint - Success case (higher priority)
        wireMockServer.stubFor(
            get(urlPathMatching("/api/rbac/v1/groups/[^/]+/?"))
                .withHeader("Authorization", containing("Bearer " + OidcServerMockResource.TEST_ACCESS_TOKEN))
                .atPriority(1)
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "name": "test-group",
                          "description": "Test group for integration testing",
                          "principalCount": 3,
                          "roleCount": 2
                        }
                        """)));

        // Mock RBAC getGroup endpoint - Unauthorized case (lower priority)
        wireMockServer.stubFor(
            get(urlPathMatching("/api/rbac/v1/groups/[^/]+/?"))
                .atPriority(2)
                .willReturn(aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "error": "Unauthorized - missing or invalid Authorization header"
                        }
                        """)));

        // Mock RBAC getGroupUsers endpoint - Success case (higher priority)
        wireMockServer.stubFor(
            get(urlPathMatching("/api/rbac/v1/groups/[^/]+/principals/?.*"))
                .withHeader("Authorization", containing("Bearer " + OidcServerMockResource.TEST_ACCESS_TOKEN))
                .atPriority(1)
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "meta": {
                            "count": 1,
                            "limit": 50,
                            "offset": 0
                          },
                          "data": [
                            {
                              "username": "group-admin",
                              "email": "admin@example.com",
                              "first_name": "Group",
                              "last_name": "Admin"
                            }
                          ]
                        }
                        """)));

        // Mock RBAC getGroupUsers endpoint - Unauthorized case (lower priority)
        wireMockServer.stubFor(
            get(urlPathMatching("/api/rbac/v1/groups/[^/]+/principals/?.*"))
                .atPriority(2)
                .willReturn(aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "error": "Unauthorized - missing or invalid Authorization header"
                        }
                        """)));

        System.out.println("Mock expectations configured successfully for RBAC endpoints");
    }

    @Override
    public void stop() {
        if (wireMockServer != null) {
            wireMockServer.stop();
            System.out.println("RBAC server mock stopped");
        }
    }

    public static WireMockServer getWireMockServer() {
        return wireMockServer;
    }
}
