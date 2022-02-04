package com.redhat.cloud.notifications;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.connectors.InMemoryConnector;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.lang.reflect.Field;
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

    // Keep the version synced with pom.xml.
    private static final DockerImageName MOCK_SERVER_DOCKER_IMAGE = DockerImageName.parse("jamesdbloom/mockserver").withTag("mockserver-5.5.4");

    PostgreSQLContainer<?> postgreSQLContainer;
    MockServerContainer mockEngineServer;
    MockServerClientConfig configurator;

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

        System.out.println(" -- Running with properties: " + properties);
        return properties;
    }

    @Override
    public void stop() {
        postgreSQLContainer.stop();
        mockEngineServer.stop();
        InMemoryConnector.clear();
    }


    @Override
    public void inject(Object testInstance) {
        Class<?> c = testInstance.getClass();
        while (c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getAnnotation(MockServerConfig.class) != null) {
                    if (!MockServerClientConfig.class.isAssignableFrom(f.getType())) {
                        throw new RuntimeException("@MockRbacConfig can only be used on fields of type RbacConfigurator");
                    }

                    f.setAccessible(true);
                    try {
                        f.set(testInstance, configurator);
                        return;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
            c = c.getSuperclass();
        }
    }

    void setupPostgres(Map<String, String> props) throws SQLException {
        postgreSQLContainer = new PostgreSQLContainer<>("postgres");
        postgreSQLContainer.start();
        // Now that postgres is started, we need to get its URL and tell Quarkus
        // quarkus.datasource.driver=io.opentracing.contrib.jdbc.TracingDriver
        // Driver needs a 'tracing' in the middle like jdbc:tracing:postgresql://localhost:5432/postgres
        String jdbcUrl = postgreSQLContainer.getJdbcUrl();
        String dbUrl = jdbcUrl.substring(jdbcUrl.indexOf(':') + 1).replace("jdbc:", "");
        props.put("quarkus.datasource.reactive.url", dbUrl);
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
        mockEngineServer = new MockServerContainer(MOCK_SERVER_DOCKER_IMAGE);

        // set up mock engine
        mockEngineServer.start();
        String mockServerUrl = "http://" + mockEngineServer.getContainerIpAddress() + ":" + mockEngineServer.getServerPort();

        configurator = new MockServerClientConfig(mockEngineServer.getContainerIpAddress(), mockEngineServer.getServerPort());

        props.put("rbac-s2s/mp-rest/url", mockServerUrl);
    }
}
