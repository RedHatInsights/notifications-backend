package com.redhat.cloud.notifications.db;

import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import reactor.core.publisher.Mono;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

@ApplicationScoped
public class DatasourceProvider {

    @ConfigProperty(name = "quarkus.datasource.jdbc.url")
    String dataSourceUrl;

    @ConfigProperty(name = "quarkus.datasource.username")
    String username;

    @ConfigProperty(name = "quarkus.datasource.password")
    String password;

    @Produces
    Mono<PostgresqlConnection> getPostgresConnection() {
        // TODO We could use a pooling method here. However, the r2dbc-pool needs to do .close() after using a connection to return it..
        ConnectionFactoryOptions parse = ConnectionFactoryOptions.parse(dataSourceUrl.replaceFirst("jdbc", "r2dbc"));
        ConnectionFactoryOptions options = parse.mutate()
                .option(ConnectionFactoryOptions.USER, username)
                .option(ConnectionFactoryOptions.PASSWORD, password)
                .build();

        PostgresqlConnectionFactory connectionFactory = (PostgresqlConnectionFactory) ConnectionFactories.get(options);

        return Mono.from(connectionFactory.create());
    }

    @Produces
    Uni<PostgresqlConnection> getConnection() {
        ConnectionFactoryOptions parse = ConnectionFactoryOptions.parse(dataSourceUrl.replaceFirst("jdbc", "r2dbc"));
        ConnectionFactoryOptions options = parse.mutate()
                .option(ConnectionFactoryOptions.USER, username)
                .option(ConnectionFactoryOptions.PASSWORD, password)
                .build();

        PostgresqlConnectionFactory connectionFactory = (PostgresqlConnectionFactory) ConnectionFactories.get(options);

        return Uni.createFrom().publisher(connectionFactory.create());
    }
}
