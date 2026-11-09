package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.TestLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.http.HttpStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.Constants.API_INTERNAL_NOTIFICATIONS_ENGINE;
import static io.restassured.RestAssured.given;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class KafkaAdminResourceTest {

    /**
     * Basic routing test. The endpoints need a real Kafka cluster, so this uses a method that the resource does not
     * accept: "method not allowed" proves the path is served, while a missing path would return "not found".
     */
    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_ENGINE})
    void testConsumerGroupsPathIsServed(String basePath) {
        given()
            .when()
            .post(basePath + "/kafka-admin/consumer-groups")
            .then()
            .statusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
    }
}
