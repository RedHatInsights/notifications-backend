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

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;
import static com.redhat.cloud.notifications.events.EventConsumer.INGRESS_CHANNEL;
import static com.redhat.cloud.notifications.events.FromCamelHistoryFiller.EGRESS_CHANNEL;
import static com.redhat.cloud.notifications.events.FromCamelHistoryFiller.FROMCAMEL_CHANNEL;
import static com.redhat.cloud.notifications.processors.email.EmailSubscriptionTypeProcessor.AGGREGATION_CHANNEL;
import static com.redhat.cloud.notifications.processors.eventing.EventingProcessor.TOCAMEL_CHANNEL;
import static com.redhat.cloud.notifications.routers.DailyDigestResource.AGGREGATION_OUT_CHANNEL;

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
        properties.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory(AGGREGATION_OUT_CHANNEL));
        properties.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory(TOCAMEL_CHANNEL));
        properties.putAll(InMemoryConnector.switchIncomingChannelsToInMemory(FROMCAMEL_CHANNEL));
        properties.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory(EGRESS_CHANNEL));

        properties.put("reinject.enabled", "true");

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
        postgreSQLContainer = new PostgreSQLContainer<>("postgres:11");
        postgreSQLContainer.start();
        // Now that postgres is started, we need to get its URL and tell Quarkus
        // quarkus.datasource.driver=io.opentelemetry.instrumentation.jdbc.OpenTelemetryDriver
        // Driver needs a 'otel' in the middle like jdbc:otel:postgresql://localhost:5432/postgres
        String jdbcUrl = postgreSQLContainer.getJdbcUrl();
        jdbcUrl = "jdbc:otel:" + jdbcUrl.substring(5);
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

    void setupMockEngine(Map<String, String> props) {
        MockServerLifecycleManager.start();
        props.put("quarkus.rest-client.rbac-s2s.url", getMockServerUrl());
        props.put("quarkus.rest-client.it-s2s.url", getMockServerUrl());
        props.put("quarkus.rest-client.internal-slack.url", "http://localhost:9087");
        props.put("quarkus.rest-client.internal-google-spaces.url", "http://localhost:9087");
    }
}
