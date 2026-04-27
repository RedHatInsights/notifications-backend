package com.redhat.cloud.notifications;

import io.github.ss_bhatt.testcontainers.valkey.ValkeyContainer;
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
import java.util.Optional;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;
import static com.redhat.cloud.notifications.TestConstants.POSTGRES_MAJOR_VERSION;
import static com.redhat.cloud.notifications.TestConstants.VALKEY_MAJOR_VERSION;

public class TestLifecycleManager implements QuarkusTestResourceLifecycleManager {

    Boolean quarkusDatasourceDevServiceEnabled = true;
    Boolean quarkusValkeyDevServiceEnabled = true;

    PostgreSQLContainer<?> postgreSQLContainer;
    ValkeyContainer valkeyContainer;

    @Override
    public Map<String, String> start() {
        System.out.println("++++  TestLifecycleManager start +++");
        Optional<Boolean> quarkusDatasourceDevServiceEnabledFlag = ConfigProvider.getConfig().getOptionalValue("quarkus.datasource.devservices.enabled", Boolean.class);
        quarkusDatasourceDevServiceEnabledFlag.ifPresent(flag -> quarkusDatasourceDevServiceEnabled = flag);
        System.out.println(" -- quarkusDatasourceDevServiceEnabled is " + quarkusDatasourceDevServiceEnabled);

        Optional<Boolean> quarkusValkeyDevServiceEnabledFlag = ConfigProvider.getConfig().getOptionalValue("quarkus.redis.devservices.enabled", Boolean.class);
        quarkusValkeyDevServiceEnabledFlag.ifPresent(flag -> quarkusValkeyDevServiceEnabled = flag);
        System.out.println(" -- quarkusValkeyDevServiceEnabled is " + quarkusValkeyDevServiceEnabled);

        Map<String, String> properties = new HashMap<>();
        if (quarkusDatasourceDevServiceEnabled) {
            try {
                setupPostgres(properties);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        if (quarkusValkeyDevServiceEnabled) {
            try {
                setupValkey(properties);
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
        if (quarkusValkeyDevServiceEnabled) {
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

    void setupValkey(Map<String, String> props) {
        valkeyContainer = new ValkeyContainer(DockerImageName.parse("valkey/valkey:" + VALKEY_MAJOR_VERSION)
                        .asCompatibleSubstituteFor("docker.io/valkey/valkey"));
        valkeyContainer.start();
        // Provide the connection credentials as a redis URI for compatibility
        String valkeyHost = valkeyContainer.getConnectionString().replace("valkey://", "redis://");
        props.put("quarkus.redis.hosts", valkeyHost);
        props.put("in-memory-db.enabled", "true");
    }

    void setupMockEngine(Map<String, String> props) {
        MockServerLifecycleManager.start();
        props.put("quarkus.rest-client.export-service.url", getMockServerUrl());
    }
}
