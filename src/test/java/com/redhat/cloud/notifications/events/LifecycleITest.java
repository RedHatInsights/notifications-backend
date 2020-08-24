package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.WebhookAttributes;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.vertx.core.json.Json;
import org.apache.commons.io.IOUtils;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockserver.model.HttpRequest;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockserver.model.HttpResponse.response;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
@TestMethodOrder(MethodOrderer.Alphanumeric.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LifecycleITest {

    private Header identityHeader;

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @Channel("ingress")
    Emitter<String> ingressChan;

    @BeforeAll
    void setup() {
        // Create Rbacs
        String tenant = "tenant";
        String userName = "user";
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, userName);
        identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);

        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);
    }

    @Test
    void t01_testAdding() {
        // Add new endpoints
        WebhookAttributes webAttr = new WebhookAttributes();
        webAttr.setMethod(WebhookAttributes.HttpType.POST);
        webAttr.setDisableSSLVerification(true);
        webAttr.setSecretToken("super-secret-token");
        webAttr.setUrl(String.format("http://%s/test/lifecycle", mockServerConfig.getRunningAddress()));

        Endpoint ep = new Endpoint();
        ep.setType(Endpoint.EndpointType.WEBHOOK);
        ep.setName("positive feeling");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(webAttr);

        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(200);

        webAttr = new WebhookAttributes();
        webAttr.setMethod(WebhookAttributes.HttpType.POST);
        webAttr.setDisableSSLVerification(true);
        webAttr.setUrl(String.format("http://%s/test/lifecycle", mockServerConfig.getRunningAddress()));

        ep = new Endpoint();
        ep.setType(Endpoint.EndpointType.WEBHOOK);
        ep.setName("negative feeling");
        ep.setDescription("I feel like dying");
        ep.setEnabled(true);
        ep.setProperties(webAttr);

        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(ep))
                .post("/endpoints")
                .then()
                .statusCode(200);
    }

    @Test
    void t02_pushMessage() throws Exception {
        // These are succesful requests
        HttpRequest postReq = new HttpRequest()
                .withPath("/test/lifecycle")
                .withMethod("POST");
        CountDownLatch latch = new CountDownLatch(2);
        mockServerConfig.getMockServerClient()
                .withSecure(false)
                .when(postReq)
                .respond(req -> {
                    // Verify req parameters here, like the secret-token?
                    latch.countDown();
                    List<String> header = req.getHeader("X-Insight-Token");
                    if (header != null && header.size() == 1 && header.get(0).equals("super-secret-token")) {
                        return response()
                                .withStatusCode(200)
                                .withBody("Success");
                    }
                    return response()
                            .withStatusCode(400)
                            .withBody("{ \"message\": \"Time is running out\" }");
                });

        // Read the input file and send it
        InputStream is = getClass().getClassLoader().getResourceAsStream("input/platform.notifications.ingress.json");
        String inputJson = IOUtils.toString(is, StandardCharsets.UTF_8);
        ingressChan.send(inputJson);

        if(!latch.await(5, TimeUnit.SECONDS)) {
            fail("HttpServer never received the requests");
//            mockServerConfig.getMockServerClient().verify(postReq, VerificationTimes.exactly(2));
            HttpRequest[] httpRequests = mockServerConfig.getMockServerClient().retrieveRecordedRequests(postReq);
            assertEquals(2, httpRequests.length);
            // Verify calls were correct
        }
    }

    @Test
    void t03_fetchHistory() {
        // Fetch the notification history for the endpoints
    }
}
