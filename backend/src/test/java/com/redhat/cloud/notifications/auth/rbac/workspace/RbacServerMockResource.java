package com.redhat.cloud.notifications.auth.rbac.workspace;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.redhat.cloud.notifications.auth.OidcServerMockResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

public class RbacServerMockResource implements QuarkusTestResourceLifecycleManager {

    private static WireMockServer wireMockServer;

    @Override
    public Map<String, String> start() {

        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig()
            .dynamicPort());
        wireMockServer.start();

        // Configure WireMock client to use the server
        WireMock.configureFor("localhost", wireMockServer.port());

        String serverUrl = "http://localhost:" + wireMockServer.port();

        setupMockExpectations();

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.rest-client.rbac-authentication-oidc.url", serverUrl);

        System.out.println("RBAC server mock started on port: " + wireMockServer.port());

        return config;
    }

    private static void setupMockExpectations() {

        // Mock RBAC endpoints - Return 200 for correct Authorization header, 401 otherwise

        // Mock RBAC getWorkspaces endpoint - Success case (higher priority)
        wireMockServer.stubFor(
            get(urlPathEqualTo("/api/rbac/v2/workspaces/"))
                .withQueryParam("type", equalTo("default"))
                .withQueryParam("offset", equalTo("0"))
                .withQueryParam("limit", equalTo("2"))
                .withHeader("Authorization", equalTo("Bearer " + OidcServerMockResource.TEST_ACCESS_TOKEN))
                .atPriority(1)
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "meta": {
                            "count": 1,
                            "limit": 2,
                            "offset": 0
                          },
                          "links": {
                            "first": "/api/rbac/v2/workspaces/?limit=2&offset=0",
                            "next": "/api/rbac/v2/workspaces/?limit=2&offset=2",
                            "previous": "/api/rbac/v2/workspaces/?limit=2&offset=0",
                            "last": "/api/rbac/v2/workspaces/?limit=2&offset=2"
                          },
                          "data": [
                            {
                              "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                              "parent_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                              "type": "default",
                              "name": "Test Workspace",
                              "description": "Default workspace for testing",
                              "created": "2024-01-01T00:00:00.000Z",
                              "modified": "2024-01-01T00:00:00.000Z"
                            }
                          ]
                        }
                        """))
        );

        // Mock RBAC getWorkspaces endpoint - Unauthorized case (lower priority)
        wireMockServer.stubFor(
            get(urlPathEqualTo("/api/rbac/v2/workspaces/"))
                .withQueryParam("type", equalTo("default"))
                .withQueryParam("offset", equalTo("0"))
                .withQueryParam("limit", equalTo("2"))
                .atPriority(2)
                .willReturn(aResponse()
                    .withStatus(401)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""
                        {
                          "error": "Unauthorized - missing or invalid Authorization header"
                        }
                        """))
        );

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
