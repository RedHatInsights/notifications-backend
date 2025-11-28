package com.redhat.cloud.notifications;

import com.redis.testcontainers.RedisContainer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import org.eclipse.microprofile.config.ConfigProvider;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;
import static com.redhat.cloud.notifications.TestConstants.POSTGRES_MAJOR_VERSION;
import static com.redhat.cloud.notifications.TestConstants.VALKEY_MAJOR_VERSION;

public class TestLifecycleManager implements QuarkusTestResourceLifecycleManager {

    Boolean quarkusDatasourceDevServiceEnabled = true;
    Boolean quarkusRedisDevServiceEnabled = true;

    PostgreSQLContainer<?> postgreSQLContainer;
    RedisContainer valkeyContainer;

    @Override
    public Map<String, String> start() {
        System.out.println("++++  TestLifecycleManager start +++");
        ConfigProvider.getConfig().getOptionalValue("quarkus.datasource.devservices.enabled", Boolean.class)
                .ifPresent(flag -> quarkusDatasourceDevServiceEnabled = flag);
        System.out.println(" -- quarkusDatasourceDevServiceEnabled is " + quarkusDatasourceDevServiceEnabled);

        ConfigProvider.getConfig().getOptionalValue("quarkus.redis.devservices.enabled", Boolean.class)
                .ifPresent(flag -> quarkusRedisDevServiceEnabled = flag);
        System.out.println(" -- quarkusRedisDevServiceEnabled is " + quarkusRedisDevServiceEnabled);

        Map<String, String> properties = new HashMap<>();
        if (quarkusDatasourceDevServiceEnabled) {
            try {
                setupPostgres(properties);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        if (quarkusRedisDevServiceEnabled) {
            try {
                setupRedis(properties);
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
        if (quarkusDatasourceDevServiceEnabled) {
            postgreSQLContainer.stop();
        }
        if (quarkusRedisDevServiceEnabled) {
            valkeyContainer.stop();
        }
        MockServerLifecycleManager.stop();
        InMemoryConnector.clear();
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
        PGSimpleDataSource ds = new PGSimpleDataSource();
        // We need the simple url, not the otel one, as PG driver does not understand the otel one.
        ds.setURL(postgreSQLContainer.getJdbcUrl());
        Connection connection = ds.getConnection("test", "test");
        Statement statement = connection.createStatement();
        statement.execute("CREATE EXTENSION pgcrypto;");
        statement.close();
        connection.close();
    }

    void setupRedis(Map<String, String> props) {
        valkeyContainer = new RedisContainer(DockerImageName.parse("valkey/valkey:" + VALKEY_MAJOR_VERSION));
        valkeyContainer.start();
        props.put("quarkus.redis.hosts", "redis://" + valkeyContainer.getRedisHost() + ":" + valkeyContainer.getRedisPort());
    }

    void setupMockEngine(Map<String, String> props) {
        MockServerLifecycleManager.start();
        props.put("quarkus.rest-client.export-service.url", getMockServerUrl());
    }
}
