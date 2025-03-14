package com.redhat.cloud.notifications;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.eclipse.microprofile.config.ConfigProvider;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;
import static com.redhat.cloud.notifications.TestConstants.POSTGRES_MAJOR_VERSION;

public class TestLifecycleManager implements QuarkusTestResourceLifecycleManager {

    Boolean quarkusDevServiceEnabled = true;

    PostgreSQLContainer<?> postgreSQLContainer;

    @Override
    public Map<String, String> start() {
        System.out.println("++++  TestLifecycleManager start +++");
        Map<String, String> properties = new HashMap<>();
        Optional<Boolean> quarkusDevServiceEnabledFlag = ConfigProvider.getConfig().getOptionalValue("quarkus.devservices.enabled", Boolean.class);
        if (quarkusDevServiceEnabledFlag.isPresent()) {
            quarkusDevServiceEnabled = quarkusDevServiceEnabledFlag.get();
        }
        System.out.println(" -- quarkusDevServiceEnabled is " + quarkusDevServiceEnabled);

        if (quarkusDevServiceEnabled) {
            try {
                setupPostgres(properties);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        setupMockEngine(properties);

        System.out.println(" -- Running with properties: " + properties);
        return properties;
    }

    @Override
    public void stop() {
        if (quarkusDevServiceEnabled) {
            postgreSQLContainer.stop();
        }
        MockServerLifecycleManager.stop();
    }

    void setupPostgres(Map<String, String> props) throws SQLException {
        postgreSQLContainer = new PostgreSQLContainer<>("postgres:" + POSTGRES_MAJOR_VERSION);
        postgreSQLContainer.start();
        // Now that postgres is started, we need to get its URL and tell Quarkus
        String jdbcUrl = postgreSQLContainer.getJdbcUrl();
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
    }
}
