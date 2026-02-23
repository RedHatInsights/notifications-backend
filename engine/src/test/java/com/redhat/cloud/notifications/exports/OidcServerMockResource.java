package com.redhat.cloud.notifications.exports;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

public class OidcServerMockResource implements QuarkusTestResourceLifecycleManager {

    public static final String TEST_ACCESS_TOKEN = "test-access-token-12345";

    private static WireMockServer wireMockServer;

    @Override
    public Map<String, String> start() {

        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig()
            .dynamicPort());
        wireMockServer.start();

        String serverUrl = "http://localhost:" + wireMockServer.port();

        setupMockExpectations(serverUrl);

        Map<String, String> config = new HashMap<>();
        config.put("quarkus.oidc-client.auth-server-url", serverUrl);

        System.out.println("OIDC server mock started on port: " + wireMockServer.port());

        return config;
    }

    private static void setupMockExpectations(String serverUrl) {

        // Mock OIDC server endpoints

        // Mock OIDC discovery endpoint
        wireMockServer.stubFor(
            get(urlEqualTo("/.well-known/openid-configuration"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(String.format("""
                        {
                          "issuer": "%s",
                          "token_endpoint": "%s/token",
                          "grant_types_supported": ["client_credentials"]
                        }
                        """, serverUrl, serverUrl)))
        );

        // Mock OIDC token endpoint
        wireMockServer.stubFor(
            post(urlEqualTo("/token"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(String.format("""
                        {
                          "access_token": "%s",
                          "token_type": "Bearer",
                          "expires_in": 3600
                        }
                        """, TEST_ACCESS_TOKEN)))
        );
    }

    @Override
    public void stop() {
        if (wireMockServer != null) {
            wireMockServer.stop();
            System.out.println("OIDC server mock stopped");
        }
    }
}
