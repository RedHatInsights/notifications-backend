package com.redhat.cloud.notifications.routers.sources;

import com.redhat.cloud.notifications.auth.OidcServerMockResource;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.MediaType;

import java.util.HashMap;
import java.util.Map;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class SourcesServerMockResource implements QuarkusTestResourceLifecycleManager {

    private static final String LOG_LEVEL_KEY = "mockserver.logLevel";
    private static ClientAndServer clientAndServer;

    @Override
    public Map<String, String> start() {

        setMockServerLogLevel();

        clientAndServer = startClientAndServer();
        String serverUrl = "http://localhost:" + clientAndServer.getPort();

        setupMockExpectations();

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.rest-client.sources-oidc.url", serverUrl);

        System.out.println("Sources server mock started");

        return config;
    }

    private static void setMockServerLogLevel() {
        if (System.getProperty(LOG_LEVEL_KEY) == null) {
            System.setProperty(LOG_LEVEL_KEY, "OFF");
            System.out.println("MockServer log is disabled. Use '-D" + LOG_LEVEL_KEY + "=WARN|INFO|DEBUG|TRACE' to enable it.");
        }
    }

    private static void setupMockExpectations() {

        // Mock Sources endpoints - Return 200 for correct Authorization header, 401 otherwise

        // Mock Sources getById endpoint - Success case
        clientAndServer.when(
            request()
                .withMethod("GET")
                .withPath("/internal/v2.0/secrets/123")
                .withHeader("Authorization", "Bearer " + OidcServerMockResource.TEST_ACCESS_TOKEN)
                .withHeader("x-rh-sources-org-id", "test-org-id")
        ).respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody("""
                    {
                      "id": 123,
                      "password": "test-password",
                      "authtype": "notifications-secret-token"
                    }
                    """)
        );

        // Mock Sources create endpoint - Success case
        clientAndServer.when(
            request()
                .withMethod("POST")
                .withPath("/api/sources/v3.1/secrets")
                .withHeader("Authorization", "Bearer " + OidcServerMockResource.TEST_ACCESS_TOKEN)
                .withHeader("x-rh-sources-org-id", "test-org-id")
        ).respond(
            response()
                .withStatusCode(201)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody("""
                    {
                      "id": 456,
                      "password": "new-secret",
                      "authtype": "notifications-secret-token"
                    }
                    """)
        );

        // Mock Sources update endpoint - Success case
        clientAndServer.when(
            request()
                .withMethod("PATCH")
                .withPath("/api/sources/v3.1/secrets/123")
                .withHeader("Authorization", "Bearer " + OidcServerMockResource.TEST_ACCESS_TOKEN)
                .withHeader("x-rh-sources-org-id", "test-org-id")
        ).respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody("""
                    {
                      "id": 123,
                      "password": "updated-secret",
                      "authtype": "notifications-secret-token"
                    }
                    """)
        );

        // Mock Sources delete endpoint - Success case
        clientAndServer.when(
            request()
                .withMethod("DELETE")
                .withPath("/api/sources/v3.1/secrets/123")
                .withHeader("Authorization", "Bearer " + OidcServerMockResource.TEST_ACCESS_TOKEN)
                .withHeader("x-rh-sources-org-id", "test-org-id")
        ).respond(
            response()
                .withStatusCode(204)
        );

        // Mock unauthorized responses for requests without proper Authorization header
        // This will catch any request without the correct Bearer token
        clientAndServer.when(
            request()
                .withPath("/internal/v2.0/secrets/.*")
        ).respond(
            response()
                .withStatusCode(401)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody("""
                    {
                      "error": "Unauthorized"
                    }
                    """)
        );

        clientAndServer.when(
            request()
                .withPath("/api/sources/v3.1/secrets/.*")
        ).respond(
            response()
                .withStatusCode(401)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody("""
                    {
                      "error": "Unauthorized"
                    }
                    """)
        );
    }

    @Override
    public void stop() {
        if (clientAndServer != null) {
            clientAndServer.stop();
            System.out.println("Sources server mock stopped");
        }
    }
}
