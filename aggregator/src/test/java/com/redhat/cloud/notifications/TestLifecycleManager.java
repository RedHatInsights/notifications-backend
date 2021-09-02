package com.redhat.cloud.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.connectors.InMemoryConnector;
import io.vertx.core.json.jackson.DatabindCodec;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

public class TestLifecycleManager implements QuarkusTestResourceLifecycleManager {

    PostgreSQLContainer<?> postgreSQLContainer;

    @Override
    public Map<String, String> start() {
        System.out.println("++++  TestLifecycleManager start +++");
        System.out.println(" -- configuring ObjectMapper");
        configureObjectMapper();
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
        props.putAll(InMemoryConnector.switchIncomingChannelsToInMemory("outgoing-aggregation"));
        props.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory("aggregation"));
    }

    private void configureObjectMapper() {
        ObjectMapper mapper = DatabindCodec.mapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
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

        // Install the pgcrypto extension
        // Could perhas be done by a migration with a lower number than the 'live' ones.
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(jdbcUrl);
        Connection connection = ds.getConnection("test", "test");
        Statement statement = connection.createStatement();
        statement.execute("CREATE EXTENSION pgcrypto;");
        statement.close();
        connection.close();
    }
}
