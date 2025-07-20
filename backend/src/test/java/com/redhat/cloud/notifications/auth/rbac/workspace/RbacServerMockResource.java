package com.redhat.cloud.notifications.auth.rbac.workspace;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.MediaType;
import java.util.HashMap;
import java.util.Map;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.Parameter.param;

public class RbacServerMockResource implements QuarkusTestResourceLifecycleManager {

    private static final String LOG_LEVEL_KEY = "mockserver.logLevel";
    private static ClientAndServer clientAndServer;

    @Override
    public Map<String, String> start() {

        setMockServerLogLevel();

        clientAndServer = startClientAndServer();
        String serverUrl = "http://localhost:" + clientAndServer.getPort();

        setupMockExpectations();

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.rest-client.rbac-authentication-oidc.url", serverUrl);

        System.out.println("RBAC server mock started");

        return config;
    }

    private static void setMockServerLogLevel() {
        if (System.getProperty(LOG_LEVEL_KEY) == null) {
            System.setProperty(LOG_LEVEL_KEY, "OFF");
            System.out.println("MockServer log is disabled. Use '-D" + LOG_LEVEL_KEY + "=WARN|INFO|DEBUG|TRACE' to enable it.");
        }
    }

    private static void setupMockExpectations() {

        // Mock RBAC endpoints - Return 200 for correct Authorization header, 401 otherwise

        // Mock RBAC getWorkspaces endpoint - Success case
        clientAndServer.when(
            request()
                .withMethod("GET")
                .withPath("/api/rbac/v2/workspaces/")
                .withQueryStringParameter(param("type", "default"))
                .withQueryStringParameter(param("offset", "0"))
                .withQueryStringParameter(param("limit", "2"))
                .withHeader("Authorization", "Bearer " + OidcServerMockResource.TEST_ACCESS_TOKEN)
        ).respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON)
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
                    """));

        // Mock RBAC getWorkspaces endpoint - Unauthorized case
        clientAndServer.when(
            request()
                .withMethod("GET")
                .withPath("/api/rbac/v2/workspaces/")
                .withQueryStringParameter(param("type", "default"))
                .withQueryStringParameter(param("offset", "0"))
                .withQueryStringParameter(param("limit", "2"))
        ).respond(
            response()
                .withStatusCode(401)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody("""
                    {
                      "error": "Unauthorized - missing or invalid Authorization header"
                    }
                    """));

        System.out.println("Mock expectations configured successfully for RBAC endpoints");
    }

    @Override
    public void stop() {
        if (clientAndServer != null) {
            clientAndServer.stop();
            System.out.println("RBAC server mock stopped");
        }
    }
}
