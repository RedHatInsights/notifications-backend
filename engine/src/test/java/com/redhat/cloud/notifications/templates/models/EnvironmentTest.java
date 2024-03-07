package com.redhat.cloud.notifications.templates.models;

import com.redhat.cloud.notifications.models.Environment;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class EnvironmentTest {

    @Inject
    Environment environment;

    public static final String expectedTestEnvUrlValue = "https://localhost";

    /**
     * Tests that the default value for the "Environment" is "local-dev".
     */
    @Test
    void testEnvironmentDefaultValue() {
        Assertions.assertEquals(
            Environment.LOCAL_ENV,
            this.environment.name(),
            "unexpected default value for the environment's name"
        );
    }

    /**
     * Tests that the default value for the "URL" is "https://localhost".
     */
    @Test
    void testUrlDefaultValue() {
        Assertions.assertEquals(expectedTestEnvUrlValue, this.environment.url(), "unexpected default value for the environment's URL");
    }
}
