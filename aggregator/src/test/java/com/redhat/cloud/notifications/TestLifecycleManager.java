package com.redhat.cloud.notifications;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.providers.connectors.InMemoryConnector;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class TestLifecycleManager implements QuarkusTestResourceLifecycleManager {

    PostgreSQLContainer<?> postgreSQLContainer;

    @Override
    public Map<String, String> start() {
        System.out.println("++++  TestLifecycleManager start +++");
        System.out.println(" -- configuring ObjectMapper");
        Map<String, String> properties = new HashMap<>();
        try {
            System.out.println(" -- setting up postgres");
            setupPostgres(properties);
            System.out.println(" -- setting up InMemoryConnector");
            setupInMemoryConnector(properties);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.println(" -- Running with properties: " + properties);
        return properties;
    }

    public void setupInMemoryConnector(Map<String, String> props) {
        props.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory("aggregation"));
    }

    @Override
    public void stop() {
        postgreSQLContainer.stop();
        InMemoryConnector.clear();
    }

    void setupPostgres(Map<String, String> props) throws SQLException {
        postgreSQLContainer = new PostgreSQLContainer<>("postgres");
        postgreSQLContainer.start();

        String jdbcUrl = postgreSQLContainer.getJdbcUrl();
        props.put("quarkus.datasource.jdbc.url", jdbcUrl);
        props.put("quarkus.datasource.username", "test");
        props.put("quarkus.datasource.password", "test");
        props.put("quarkus.datasource.db-kind", "postgresql");
    }
}
