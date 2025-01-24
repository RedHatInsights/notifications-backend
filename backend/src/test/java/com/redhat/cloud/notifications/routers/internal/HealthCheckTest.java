package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.unleash.utils.PodRestartRequestedChecker;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.MockitoConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.in;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test Health checks and admin-down from admin-interface
 */
@QuarkusTest
public class HealthCheckTest {

    private static final Pattern PATTERN = Pattern.compile("\"name\": \"Database connections health check\",\\R[ ]+\"status\": \"UP\"");

    @InjectMock
    @MockitoConfig(convertScopes = true)
    PodRestartRequestedChecker podRestartRequestedChecker;

    @Test
    void testNormalHealth() {
        normalHealthCheck();
    }

    private String normalHealthCheck() {
        String body =
                when()
                        .get("/health")
                        .then()
                        .statusCode(in(new Integer[]{200, 503})) // may be 503 as there is no Kafka we can talk to
                        .extract().asString();
        assertFalse(body.contains("restart-requested-from-unleash"));
        return body;
    }

    @Test
    void testAdminDown() {
        Mockito.when(podRestartRequestedChecker.isRestartRequestedFromUnleash()).thenReturn(true);

        String body =
                when()
                        .get("/health")
                        .then()
                        .statusCode(503)
                        .extract().asString();
        assertTrue(body.contains("restart-requested-from-unleash"));
    }

    // Make sure we don't leak connections here
    @Test
    void testRepeatedHealth() {
        for (int i = 0; i < 150; i++) {
            String body = normalHealthCheck();
            assertTrue(PATTERN.matcher(body).find());
        }
    }
}
