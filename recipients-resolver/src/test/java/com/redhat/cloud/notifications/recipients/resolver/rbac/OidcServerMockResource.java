package com.redhat.cloud.notifications.recipients.resolver.rbac;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class OidcServerMockResource implements QuarkusTestResourceLifecycleManager {

    public static final String TEST_ACCESS_TOKEN = "test-access-token-12345";

    private static MockWebServer mockWebServer;

    @Override
    public Map<String, String> start() {
        mockWebServer = new MockWebServer();

        final Dispatcher dispatcher = new Dispatcher() {
            @Override
            public @NotNull MockResponse dispatch(RecordedRequest request) {
                assert request.getRequestUrl() != null;
                return switch (request.getRequestUrl().encodedPath()) {
                    case "/.well-known/openid-configuration" -> handleOidcDiscovery();
                    case "/token" -> handleTokenEndpoint();
                    default -> new MockResponse().setResponseCode(404);
                };
            }
        };

        // Set up dispatcher to handle different endpoints
        mockWebServer.setDispatcher(dispatcher);

        try {
            mockWebServer.start();
        } catch (IOException e) {
            throw new RuntimeException("Failed to start MockWebServer", e);
        }

        String serverUrl = mockWebServer.url("").toString().replaceAll("/$", "");

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.oidc-client.auth-server-url", serverUrl);

        System.out.println("OIDC server mock started on port " + mockWebServer.getPort());

        return config;
    }

    private MockResponse handleOidcDiscovery() {
        String serverUrl = mockWebServer.url("").toString().replaceAll("/$", "");
        String body = String.format("""
            {
              "issuer": "%s",
              "token_endpoint": "%s/token",
              "grant_types_supported": ["client_credentials"]
            }
            """, serverUrl, serverUrl);

        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private MockResponse handleTokenEndpoint() {
        String body = String.format("""
            {
              "access_token": "%s",
              "token_type": "Bearer",
              "expires_in": 3600
            }
            """, TEST_ACCESS_TOKEN);

        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    @Override
    public void stop() {
        if (mockWebServer != null) {
            try {
                mockWebServer.shutdown();
                System.out.println("OIDC server mock stopped");
            } catch (IOException e) {
                System.err.println("Error stopping MockWebServer: " + e.getMessage());
            }
        }
    }
}
