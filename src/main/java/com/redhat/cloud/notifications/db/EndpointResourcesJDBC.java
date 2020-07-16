package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.WebhookAttributes;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlResult;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.converters.multi.MultiReactorConverters;
import io.smallrye.mutiny.converters.uni.UniReactorConverters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@ApplicationScoped
public class EndpointResourcesJDBC {

    Mono<PostgresqlConnection> connectionPublisher;

    // TODO Modify to use PreparedStatements
    // TODO Pooling?

    @PostConstruct
    void init() {
        PostgresqlConnectionFactory connectionFactory = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                .host("192.168.1.139")
                .port(5432)
                .username("hook")
                .password("9FLK6cMm5px8vZ52")
                .database("notifications")
                .build());

        connectionPublisher = connectionFactory.create();
    }

    public Uni<Endpoint> createEndpoint(Endpoint endpoint) {
//        Mono<Endpoint> endpointMono = connectionPublisher.flatMap(conn -> {
//            return conn.beginTransaction()
//                    .flatMapMany(cr -> insertEndpointStatement(endpoint, conn))
//                    .flatMap(endpoint2 -> insertWebhooksStatement(endpoint2, conn))
//                    .then(conn.commitTransaction())
//                    .map(ignored -> endpoint);
//        });
        Mono<Endpoint> endpointMono = connectionPublisher.flatMap(conn -> {
            Flux<Endpoint> endpointFlux = insertEndpointStatement(endpoint, conn);
            Flux<Endpoint> endpointFlux1 = endpointFlux.flatMap(ep -> insertWebhooksStatement(ep, conn));
            return endpointFlux1.next();
        });

        return Uni.createFrom().converter(UniReactorConverters.fromMono(), endpointMono);
    }

    private Flux<Endpoint> insertEndpointStatement(Endpoint endpoint, PostgresqlConnection conn) {
        Flux<PostgresqlResult> execute = conn.createStatement("INSERT INTO public.endpoints (account_id, endpoint_type, enabled, name, description, created) VALUES ($1, $2, $3, $4, $5, $6)")
                .bind("$1", endpoint.getTenant())
                .bind("$2", 1)
                .bind("$3", endpoint.isEnabled())
                .bind("$4", endpoint.getName())
                .bind("$5", endpoint.getDescription())
                .bind("$6", LocalDateTime.now())
                .returnGeneratedValues("id", "created")
                .execute();

        Flux<Endpoint> endpointFlux = execute.flatMap(res -> res
                .map(((row, rowMetadata) -> {
                    endpoint.setId(row.get("id", UUID.class));
                    endpoint.setCreated(row.get("created", Date.class));
                    return endpoint;
                })));

        return endpointFlux;
    }

    private Flux<Endpoint> insertWebhooksStatement(Endpoint endpoint, PostgresqlConnection conn) {
        WebhookAttributes attr = (WebhookAttributes) endpoint.getProperties();
        Flux<PostgresqlResult> execute = conn.createStatement("INSERT INTO public.endpoint_webhooks (endpoint_id, url, method, disable_ssl_verification, secret_token) VALUES ($1, $2, $3, $4, $5)")
                .bind("$1", endpoint.getId())
                .bind("$2", attr.getUrl())
                .bind("$3", attr.getMethod().toString())
                .bind("$4", attr.isDisableSSLVerification())
                .bind("$5", attr.getSecretToken())
                .returnGeneratedValues("id")
                .execute();

        Flux<Endpoint> endpointFlux = execute.flatMap(res -> res
                .map(((row, rowMetadata) -> {
                    endpoint.setProperties(attr);
                    return endpoint;
                })));

        return endpointFlux;
    }

    public Multi<Endpoint> getEndpoints(String tenant) {
        // TODO Add JOIN ON clause to proper table, such as webhooks and then read the results
        Flux<PostgresqlResult> resultFlux = connectionPublisher.flatMapMany(conn ->
                conn.createStatement("SELECT id, endpoint_type, enabled, name, description, created, updated FROM public.endpoints WHERE account_id = $1")
                        .bind("$1", tenant)
                        .execute());
        Flux<Endpoint> endpointFlux = resultFlux.flatMap(postgresqlResult -> postgresqlResult.map((row, rowMetadata) -> {
            Endpoint endpoint = new Endpoint();
            endpoint.setId(row.get("id", UUID.class));
            endpoint.setEnabled(row.get("enabled", Boolean.class));
            endpoint.setType(Endpoint.EndpointType.values()[row.get("endpoint_type", Integer.class)]);
            endpoint.setName(row.get("name", String.class));
            endpoint.setDescription(row.get("description", String.class));
            endpoint.setCreated(row.get("created", Date.class));
            endpoint.setUpdated(row.get("updated", Date.class));

            return endpoint;
        }));

        return Multi.createFrom().converter(MultiReactorConverters.fromFlux(), endpointFlux);
    }
}
