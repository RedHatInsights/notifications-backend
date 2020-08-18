package com.redhat.cloud.notifications;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EndpointTest extends AbstractITest {

    @Test
    void testGetEndpointsForGoodTenant() {
        given()
                .header(authHeader)
                .when()
                .get(API_BASE_V1_0 + "/endpoints/1234")
                .then()
                .statusCode(204);
    }
}
