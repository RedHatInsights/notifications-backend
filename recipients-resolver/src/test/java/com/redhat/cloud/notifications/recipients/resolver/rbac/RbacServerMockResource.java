package com.redhat.cloud.notifications.recipients.resolver.rbac;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.MediaType;
import java.util.HashMap;
import java.util.Map;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

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
        config.put("quarkus.rest-client.rbac-s2s-oidc.url", serverUrl);

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

        // Mock RBAC getUsers endpoint - Success case
        clientAndServer.when(
            request()
                .withMethod("GET")
                .withPath("/api/rbac/v1/principals/")
                .withHeader("Authorization", "Bearer " + OidcServerMockResource.TEST_ACCESS_TOKEN)
        ).respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON)
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
                    """));

        // Mock RBAC getUsers endpoint - Unauthorized case
        clientAndServer.when(
            request()
                .withMethod("GET")
                .withPath("/api/rbac/v1/principals/")
        ).respond(
            response()
                .withStatusCode(401)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody("""
                    {
                      "error": "Unauthorized - missing or invalid Authorization header"
                    }
                    """));

        // Mock RBAC getGroup endpoint - Success case
        clientAndServer.when(
            request()
                .withMethod("GET")
                .withPath("/api/rbac/v1/groups/[^/]+/$")
                .withHeader("Authorization", "Bearer " + OidcServerMockResource.TEST_ACCESS_TOKEN)
        ).respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody("""
                    {
                      "name": "test-group",
                      "description": "Test group for integration testing",
                      "principalCount": 3,
                      "roleCount": 2
                    }
                    """));

        // Mock RBAC getGroup endpoint - Unauthorized case
        clientAndServer.when(
            request()
                .withMethod("GET")
                .withPath("/api/rbac/v1/groups/[^/]+/$")
        ).respond(
            response()
                .withStatusCode(401)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody("""
                    {
                      "error": "Unauthorized - missing or invalid Authorization header"
                    }
                    """));

        // Mock RBAC getGroupUsers endpoint - Success case
        clientAndServer.when(
            request()
                .withMethod("GET")
                .withPath("/api/rbac/v1/groups/[^/]+/principals/")
                .withHeader("Authorization", "Bearer " + OidcServerMockResource.TEST_ACCESS_TOKEN)
        ).respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON)
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
                    """));

        // Mock RBAC getGroupUsers endpoint - Unauthorized case
        clientAndServer.when(
            request()
                .withMethod("GET")
                .withPath("/api/rbac/v1/groups/[^/]+/principals/")
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

    public static MockServerClient getMockServerClient() {
        return clientAndServer;
    }
}
