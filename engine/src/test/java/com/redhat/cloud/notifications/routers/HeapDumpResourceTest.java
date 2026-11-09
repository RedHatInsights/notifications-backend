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
public class HeapDumpResourceTest {

    /**
     * Basic routing test. A request for a different host is answered with "see other" without generating any heap dump.
     */
    @ParameterizedTest
    @ValueSource(strings = {API_INTERNAL, API_INTERNAL_NOTIFICATIONS_ENGINE})
    void testHeapDumpOfAnotherHost(String basePath) {
        given()
            .redirects().follow(false)
            .when()
            .get(basePath + "/heap_dump/another-host")
            .then()
            .statusCode(HttpStatus.SC_SEE_OTHER);
    }
}
