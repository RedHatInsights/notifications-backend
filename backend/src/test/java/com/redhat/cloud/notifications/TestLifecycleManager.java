package com.redhat.cloud.notifications;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;

public class TestLifecycleManager implements QuarkusTestResourceLifecycleManager {

    PostgreSQLContainer<?> postgreSQLContainer;

    @Override
    public Map<String, String> start() {
        System.out.println("++++  TestLifecycleManager start +++");
        Map<String, String> properties = new HashMap<>();
        try {
            setupPostgres(properties);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        setupMockEngine(properties);

        properties.put("ob.backchannel.user", "ob-user");

        System.out.println(" -- Running with properties: " + properties);
        return properties;
    }

    @Override
    public void stop() {
        postgreSQLContainer.stop();
        MockServerLifecycleManager.stop();
    }

    void setupPostgres(Map<String, String> props) throws SQLException {
        postgreSQLContainer = new PostgreSQLContainer<>("postgres:11");
        postgreSQLContainer.start();
        // Now that postgres is started, we need to get its URL and tell Quarkus
        // quarkus.datasource.driver=io.opentelemetry.instrumentation.jdbc.OpenTelemetryDriver
        // JdbcURL needs a 'otel' in the middle like jdbc:otel:postgresql://localhost:5432/postgres
        String jdbcUrl = postgreSQLContainer.getJdbcUrl();
        jdbcUrl = "jdbc:otel:" + jdbcUrl.substring(5);
        props.put("quarkus.datasource.jdbc.url", jdbcUrl);
        props.put("quarkus.datasource.username", "test");
        props.put("quarkus.datasource.password", "test");
        props.put("quarkus.datasource.db-kind", "postgresql");

        // Install the pgcrypto extension
        // Could perhaps be done by a migration with a lower number than the 'live' ones.
        String pgJdbcUrl = postgreSQLContainer.getJdbcUrl();
        PGSimpleDataSource ds = new PGSimpleDataSource();
        // We need the simple url, not the otel one, as PG driver does not understand the otel one.
        ds.setURL(pgJdbcUrl);
        Connection connection = ds.getConnection("test", "test");
        Statement statement = connection.createStatement();
        statement.execute("CREATE EXTENSION pgcrypto;");
        statement.close();
        connection.close();
    }

    void setupMockEngine(Map<String, String> props) {
        MockServerLifecycleManager.start();
        props.put("quarkus.rest-client.rbac-authentication.url", getMockServerUrl());
        props.put("quarkus.rest-client.ob.url", getMockServerUrl());
        props.put("quarkus.rest-client.kc.url", getMockServerUrl());
    }
}
