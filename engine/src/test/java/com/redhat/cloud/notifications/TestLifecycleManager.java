package com.redhat.cloud.notifications;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.providers.connectors.InMemoryConnector;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static com.redhat.cloud.notifications.events.EventConsumer.INGRESS_CHANNEL;
import static com.redhat.cloud.notifications.events.FromCamelHistoryFiller.EGRESS_CHANNEL;
import static com.redhat.cloud.notifications.events.FromCamelHistoryFiller.FROMCAMEL_CHANNEL;
import static com.redhat.cloud.notifications.processors.camel.CamelTypeProcessor.TOCAMEL_CHANNEL;
import static com.redhat.cloud.notifications.processors.email.EmailSubscriptionTypeProcessor.AGGREGATION_CHANNEL;

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

        /*
         * We'll use an in-memory Reactive Messaging connector to send payloads.
         * See https://smallrye.io/smallrye-reactive-messaging/smallrye-reactive-messaging/2/testing/testing.html
         */
        properties.putAll(InMemoryConnector.switchIncomingChannelsToInMemory(INGRESS_CHANNEL));
        properties.putAll(InMemoryConnector.switchIncomingChannelsToInMemory(AGGREGATION_CHANNEL));
        properties.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory(TOCAMEL_CHANNEL));
        properties.putAll(InMemoryConnector.switchIncomingChannelsToInMemory(FROMCAMEL_CHANNEL));
        properties.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory(EGRESS_CHANNEL));

        properties.put("reinject.enabled", "true");
        // TODO NOTIF-381 Remove this when the recipients retrieval from RBAC is removed.
        properties.put("recipient-provider.use-it-impl", "true");

        System.out.println(" -- Running with properties: " + properties);
        return properties;
    }

    @Override
    public void stop() {
        postgreSQLContainer.stop();
        MockServerLifecycleManager.stop();
        InMemoryConnector.clear();
    }

    void setupPostgres(Map<String, String> props) throws SQLException {
        postgreSQLContainer = new PostgreSQLContainer<>("postgres");
        postgreSQLContainer.start();
        // Now that postgres is started, we need to get its URL and tell Quarkus
        // quarkus.datasource.driver=io.opentracing.contrib.jdbc.TracingDriver
        // Driver needs a 'tracing' in the middle like jdbc:tracing:postgresql://localhost:5432/postgres
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

    void setupMockEngine(Map<String, String> props) {
        MockServerLifecycleManager.start();
        props.put("quarkus.rest-client.rbac-s2s.url", MockServerLifecycleManager.getContainerUrl());
        props.put("quarkus.rest-client.it-s2s.url", MockServerLifecycleManager.getContainerUrl());
    }
}
