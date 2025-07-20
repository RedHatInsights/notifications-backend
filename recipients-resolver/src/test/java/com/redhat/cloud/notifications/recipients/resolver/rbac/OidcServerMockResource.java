package com.redhat.cloud.notifications.recipients.resolver.rbac;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.MediaType;
import java.util.HashMap;
import java.util.Map;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class OidcServerMockResource implements QuarkusTestResourceLifecycleManager {

    public static final String TEST_ACCESS_TOKEN = "test-access-token-12345";

    private static final String LOG_LEVEL_KEY = "mockserver.logLevel";
    private static ClientAndServer clientAndServer;

    @Override
    public Map<String, String> start() {

        setMockServerLogLevel();

        clientAndServer = startClientAndServer();
        String serverUrl = "http://localhost:" + clientAndServer.getPort();

        setupMockExpectations(serverUrl);

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.oidc-client.auth-server-url", serverUrl);

        System.out.println("OIDC server mock started");

        return config;
    }

    private static void setMockServerLogLevel() {
        if (System.getProperty(LOG_LEVEL_KEY) == null) {
            System.setProperty(LOG_LEVEL_KEY, "OFF");
            System.out.println("MockServer log is disabled. Use '-D" + LOG_LEVEL_KEY + "=WARN|INFO|DEBUG|TRACE' to enable it.");
        }
    }

    private static void setupMockExpectations(String serverUrl) {

        // Mock OIDC server endpoints

        // Mock OIDC discovery endpoint
        clientAndServer.when(
            request()
                .withMethod("GET")
                .withPath("/.well-known/openid-configuration")
        ).respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody(String.format("""
                    {
                      "issuer": "%s",
                      "token_endpoint": "%s/token",
                      "grant_types_supported": ["client_credentials"]
                    }
                    """, serverUrl, serverUrl))
        );

        // Mock OIDC token endpoint
        clientAndServer.when(
            request()
                .withMethod("POST")
                .withPath("/token")
        ).respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody(String.format("""
                    {
                      "access_token": "%s",
                      "token_type": "Bearer",
                      "expires_in": 3600
                    }
                    """, TEST_ACCESS_TOKEN))
        );
    }

    @Override
    public void stop() {
        if (clientAndServer != null) {
            clientAndServer.stop();
            System.out.println("OIDC server mock stopped");
        }
    }
}
