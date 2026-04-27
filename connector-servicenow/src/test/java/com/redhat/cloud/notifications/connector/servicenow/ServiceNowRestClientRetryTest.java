package com.redhat.cloud.notifications.connector.servicenow;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.redhat.cloud.notifications.connector.v2.TestLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class ServiceNowRestClientRetryTest {

    @Inject
    @RestClient
    ServiceNowRestClient serviceNowRestClient;

    private static WireMockServer wireMockServer;
    private static final int WIREMOCK_PORT = 8089;
    private static final String TEST_PATH = "/retry-test";
    private static final String TEST_BODY = "{\"message\":\"test\"}";

    @BeforeAll
    static void setupWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().port(WIREMOCK_PORT));
        wireMockServer.start();
    }

    @AfterAll
    static void tearDownWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @BeforeEach
    void resetWireMock() {
        wireMockServer.resetAll();
    }

    @Test
    void testSuccessfulCallOnFirstAttempt() {
        wireMockServer.stubFor(post(urlEqualTo(TEST_PATH))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("Success")));

        Response response = serviceNowRestClient.post(getTestUrl(), TEST_BODY);

        assertEquals(200, response.getStatus());
        wireMockServer.verify(1, postRequestedFor(urlEqualTo(TEST_PATH)));
    }

    @Test
    void testRetryOnServerError() {
        wireMockServer.stubFor(post(urlEqualTo(TEST_PATH))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error")));

        assertThrows(ClientWebApplicationException.class, () -> {
            serviceNowRestClient.post(getTestUrl(), TEST_BODY);
        });

        // 1 initial + 2 retries = 3 attempts
        wireMockServer.verify(3, postRequestedFor(urlEqualTo(TEST_PATH)));
    }

    @Test
    void testSuccessfulRetryAfterInitialFailure() {
        wireMockServer.stubFor(post(urlEqualTo(TEST_PATH))
            .inScenario("Retry Success")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(500))
            .willSetStateTo("First Retry"));

        wireMockServer.stubFor(post(urlEqualTo(TEST_PATH))
            .inScenario("Retry Success")
            .whenScenarioStateIs("First Retry")
            .willReturn(aResponse().withStatus(200).withBody("Success")));

        Response response = serviceNowRestClient.post(getTestUrl(), TEST_BODY);

        assertEquals(200, response.getStatus());
        wireMockServer.verify(2, postRequestedFor(urlEqualTo(TEST_PATH)));
    }

    @Test
    void testRetryWithBasicAuthHeader() {
        wireMockServer.stubFor(post(urlEqualTo(TEST_PATH))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error")));

        String basicAuth = "Basic dGVzdDpwYXNzd29yZA==";

        assertThrows(ClientWebApplicationException.class, () -> {
            serviceNowRestClient.postWithBasicAuth(basicAuth, getTestUrl(), TEST_BODY);
        });

        wireMockServer.verify(3, postRequestedFor(urlEqualTo(TEST_PATH))
            .withHeader("Authorization", equalTo(basicAuth)));
    }

    private String getTestUrl() {
        return "http://localhost:" + WIREMOCK_PORT + TEST_PATH;
    }
}
