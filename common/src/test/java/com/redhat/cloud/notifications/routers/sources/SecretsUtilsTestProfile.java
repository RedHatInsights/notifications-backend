package com.redhat.cloud.notifications.routers.sources;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

/**
 * Since the "common" module doesn't contain an "application.properties" file,
 * we are feeding the {@link SecretUtilsTest} tests with some default
 * configuration properties.
 */
public class SecretsUtilsTestProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        final var config = new HashMap<String, String>();
        // Set up an H2 database.
        config.put("quarkus.datasource.db-kind", "postgresql");
        config.put("quarkus.datasource.jdbc.url", "jdbc:h2:mem:default");

        // Required for Quarkus to run, as otherwise complains about not having these values.
        config.put("ob.token.client.id", "obTokenClientId");
        config.put("ob.bridge.name", "obBridgeName");
        config.put("ob.token.client.secret", "obTokenClientSecret");

        // Set up the service's URL for the tests.
        config.put("quarkus.rest-client.sources.url", "https://localhost:8000");
        config.put("quarkus.rest-client.sources.uri", "https://localhost:8000");

        return config;
    }

}
