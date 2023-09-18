package com.redhat.cloud.notifications;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import org.eclipse.microprofile.config.ConfigProvider;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TestLifecycleManager implements QuarkusTestResourceLifecycleManager {

    Boolean quarkusDevServiceEnabled = true;

    PostgreSQLContainer<?> postgreSQLContainer;

    @Override
    public Map<String, String> start() {

        System.out.println("++++  TestLifecycleManager start +++");
        Optional<Boolean> quarkusDevServiceEnabledFlag = ConfigProvider.getConfig().getOptionalValue("quarkus.devservices.enabled", Boolean.class);
        if (quarkusDevServiceEnabledFlag.isPresent()) {
            quarkusDevServiceEnabled = quarkusDevServiceEnabledFlag.get();
        }
        System.out.println(" -- quarkusDevServiceEnabled is " + quarkusDevServiceEnabled);

        Map<String, String> properties = new HashMap<>();
        try {
            if (quarkusDevServiceEnabled) {
                System.out.println(" -- setting up postgres");
                setupPostgres(properties);
            }
            System.out.println(" -- setting up InMemoryConnector");
            setupInMemoryConnector(properties);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.println(" -- Running with properties: " + properties);
        return properties;
    }

    public void setupInMemoryConnector(Map<String, String> props) {
        props.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory(DailyEmailAggregationJob.AGGREGATION_CHANNEL));
        props.putAll(InMemoryConnector.switchOutgoingChannelsToInMemory(DailyEmailAggregationJob.EGRESS_CHANNEL));
    }

    @Override
    public void stop() {
        if (quarkusDevServiceEnabled) {
            postgreSQLContainer.stop();
        }
        InMemoryConnector.clear();
    }

    void setupPostgres(Map<String, String> props) throws SQLException {
        postgreSQLContainer = new PostgreSQLContainer<>("postgres:15");
        postgreSQLContainer.start();

        String jdbcUrl = postgreSQLContainer.getJdbcUrl();
        props.put("quarkus.datasource.jdbc.url", jdbcUrl);
        props.put("quarkus.datasource.username", "test");
        props.put("quarkus.datasource.password", "test");
        props.put("quarkus.datasource.db-kind", "postgresql");
    }
}
