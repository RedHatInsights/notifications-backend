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

public class RbacServerMockResource implements QuarkusTestResourceLifecycleManager {

    private static MockWebServer mockWebServer;

    @Override
    public Map<String, String> start() {
        mockWebServer = new MockWebServer();

        final Dispatcher dispatcher = new Dispatcher() {
            @Override
            public @NotNull MockResponse dispatch(RecordedRequest request) {
                assert request.getRequestUrl() != null;
                String path = request.getRequestUrl().encodedPath();
                String authHeader = request.getHeader("Authorization");
                String expectedAuth = "Bearer " + OidcServerMockResource.TEST_ACCESS_TOKEN;

                if (path.equals("/api/rbac/v1/principals/")) {
                    return handleGetUsers(authHeader, expectedAuth);
                } else if (path.matches("/api/rbac/v1/groups/[^/]+/")) {
                    return handleGetGroup(authHeader, expectedAuth);
                } else if (path.matches("/api/rbac/v1/groups/[^/]+/principals/")) {
                    return handleGetGroupUsers(authHeader, expectedAuth);
                }

                return new MockResponse().setResponseCode(404);
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
        config.put("quarkus.rest-client.rbac-s2s-oidc.url", serverUrl);

        System.out.println("RBAC server mock started on port " + mockWebServer.getPort());

        return config;
    }

    private MockResponse handleGetUsers(String authHeader, String expectedAuth) {
        if (expectedAuth.equals(authHeader)) {
            String body = """
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
                """;

            return new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(body);
        } else {
            return unauthorizedResponse();
        }
    }

    private MockResponse handleGetGroup(String authHeader, String expectedAuth) {
        if (expectedAuth.equals(authHeader)) {
            String body = """
                {
                  "name": "test-group",
                  "description": "Test group for integration testing",
                  "principalCount": 3,
                  "roleCount": 2
                }
                """;

            return new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(body);
        } else {
            return unauthorizedResponse();
        }
    }

    private MockResponse handleGetGroupUsers(String authHeader, String expectedAuth) {
        if (expectedAuth.equals(authHeader)) {
            String body = """
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
                """;

            return new MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(body);
        } else {
            return unauthorizedResponse();
        }
    }

    private MockResponse unauthorizedResponse() {
        String body = """
            {
              "error": "Unauthorized - missing or invalid Authorization header"
            }
            """;

        return new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    @Override
    public void stop() {
        if (mockWebServer != null) {
            try {
                mockWebServer.shutdown();
                System.out.println("RBAC server mock stopped");
            } catch (IOException e) {
                System.err.println("Error stopping MockWebServer: " + e.getMessage());
            }
        }
    }

    public static MockWebServer getMockWebServer() {
        return mockWebServer;
    }
}
