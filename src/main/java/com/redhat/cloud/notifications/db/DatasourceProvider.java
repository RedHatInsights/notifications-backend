package com.redhat.cloud.notifications.db;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
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

    private ConnectionFactory pool;

    @PostConstruct
    void init() {
        ConnectionFactoryOptions parse = ConnectionFactoryOptions.parse(dataSourceUrl.replaceFirst("jdbc", "r2dbc:pool"));
        ConnectionFactoryOptions options = parse.mutate()
                .option(ConnectionFactoryOptions.USER, username)
                .option(ConnectionFactoryOptions.PASSWORD, password)
                .build();

        this.pool = ConnectionFactories.get(options);
    }

    @Produces
    Mono<PostgresqlConnection> getPostgresConnection() {
        ConnectionPool connectionPool = (ConnectionPool) pool;
        PostgresqlConnectionFactory postgresqlConnectionFactory = (PostgresqlConnectionFactory) connectionPool.unwrap();

        return postgresqlConnectionFactory.create();
    }

    @Produces
    Uni<PostgresqlConnection> getConnection() {
        ConnectionPool connectionPool = (ConnectionPool) pool;
        PostgresqlConnectionFactory postgresqlConnectionFactory = (PostgresqlConnectionFactory) connectionPool.unwrap();

        return Uni.createFrom().publisher(postgresqlConnectionFactory.create());
    }
}
