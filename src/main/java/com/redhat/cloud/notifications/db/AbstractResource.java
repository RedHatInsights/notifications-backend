package com.redhat.cloud.notifications.db;

import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import reactor.core.publisher.Mono;

import javax.enterprise.inject.Produces;

public class AbstractResource {

    @Produces
    Mono<PostgresqlConnection> getPostgresConnection() {
        // TODO We could use a pooling method here. However, the r2dbc-pool needs to do .close() after using a connection to return it..
        Config config = ConfigProvider.getConfig();
        String jdbcUrl = config.getValue("quarkus.datasource.jdbc.url", String.class);
        String user = config.getValue("quarkus.datasource.username", String.class);
        String pass = config.getValue("quarkus.datasource.password", String.class);

        ConnectionFactoryOptions parse = ConnectionFactoryOptions.parse(jdbcUrl.replaceFirst("jdbc", "r2dbc"));
        ConnectionFactoryOptions options = parse.mutate()
                .option(ConnectionFactoryOptions.USER, user)
                .option(ConnectionFactoryOptions.PASSWORD, pass)
                .build();

        PostgresqlConnectionFactory connectionFactory = (PostgresqlConnectionFactory) ConnectionFactories.get(options);

        return Mono.from(connectionFactory.create());
    }
}
