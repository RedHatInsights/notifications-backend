package com.redhat.cloud.notifications.connector.authentication.secrets;

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

        // Mock Sources getById endpoint - Generic success case for any secret ID with valid auth
        clientAndServer.when(
            request()
                .withMethod("GET")
                .withPath("/internal/v2.0/secrets/[0-9]+")
                .withHeader("Authorization", "Bearer " + OidcServerMockResource.TEST_ACCESS_TOKEN)
                .withHeader("x-rh-sources-org-id")
        ).respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody("""
                    {
                      "username": "test-username",
                      "password": "test-password"
                    }
                    """)
        );

        // Mock Sources getById endpoint - Generic unauthorized case for any secret ID without auth
        clientAndServer.when(
            request()
                .withMethod("GET")
                .withPath("/internal/v2.0/secrets/[0-9]+")
                .withHeader("x-rh-sources-org-id")
        ).respond(
            response()
                .withStatusCode(401)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody("""
                    {
                      "error": "Unauthorized - missing or invalid Authorization header"
                    }
                    """)
        );

        System.out.println("Mock expectations configured successfully for Sources endpoints");
    }

    @Override
    public void stop() {
        if (clientAndServer != null) {
            clientAndServer.stop();
            System.out.println("Sources server mock stopped");
        }
    }
}
