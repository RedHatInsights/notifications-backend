package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.WebhookAttributes;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlResult;
import io.r2dbc.postgresql.api.PostgresqlStatement;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.converters.multi.MultiReactorConverters;
import io.smallrye.mutiny.converters.uni.UniReactorConverters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@ApplicationScoped
public class EndpointResources extends DatasourceProvider {

    @Inject
    Mono<PostgresqlConnection> connectionPublisher;

    @Inject
    Uni<PostgresqlConnection> connectionPublisherUni;

    // TODO Modify to use PreparedStatements
    // TODO Pooling?

    public Uni<Endpoint> createEndpoint(Endpoint endpoint) {
        // TODO Fix transaction so that we don't end up with endpoint without properties (if validation fails)
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
                .bind("$2", endpoint.getType().ordinal())
                .bind("$3", endpoint.isEnabled())
                .bind("$4", endpoint.getName())
                .bind("$5", endpoint.getDescription())
                .bind("$6", LocalDateTime.now())
                .returnGeneratedValues("id", "created")
                .execute();

        return execute.flatMap(res -> res
                .map(((row, rowMetadata) -> {
                    endpoint.setId(row.get("id", UUID.class));
                    endpoint.setCreated(row.get("created", Date.class));
                    return endpoint;
                })));
    }

    private Flux<Endpoint> insertWebhooksStatement(Endpoint endpoint, PostgresqlConnection conn) {
        WebhookAttributes attr = (WebhookAttributes) endpoint.getProperties();
        PostgresqlStatement bind = conn.createStatement("INSERT INTO public.endpoint_webhooks (endpoint_id, url, method, disable_ssl_verification, secret_token) VALUES ($1, $2, $3, $4, $5)")
                .bind("$1", endpoint.getId())
                .bind("$2", attr.getUrl())
                .bind("$3", attr.getMethod().toString())
                .bind("$4", attr.isDisableSSLVerification());

        if (attr.getSecretToken() != null) {
            bind.bind("$5", attr.getSecretToken());
        } else {
            bind.bindNull("$5", String.class);
        }

        Flux<PostgresqlResult> execute = bind
                .returnGeneratedValues("id")
                .execute();

        return execute.flatMap(res -> res
                .map(((row, rowMetadata) -> {
                    endpoint.setProperties(attr);
                    return endpoint;
                })));
    }

    private static final String basicEndpointGetQuery = "SELECT e.account_id, e.id, e.endpoint_type, e.enabled, e.name, e.description, e.created, e.updated, ew.id AS webhook_id, ew.url, ew.method, ew.disable_ssl_verification, ew.secret_token FROM public.endpoints AS e JOIN public.endpoint_webhooks AS ew ON ew.endpoint_id = e.id  WHERE e.account_id = $1";

    public Multi<Endpoint> getActiveEndpointsPerType(String tenant, Endpoint.EndpointType type) {
        // TODO Modify to take account selective joins (JOIN (..) UNION (..)) based on the type, same for getEndpoints
        String query = basicEndpointGetQuery + " AND e.endpoint_type = $2 AND e.enabled = true";
        return connectionPublisherUni.toMulti()
                .onItem()
                .transform(conn -> conn.createStatement(query)
                        .bind("$1", tenant)
                        .bind("$2", type.ordinal())
                        .execute())
                .flatMap(this::mapResultSetToEndpoint);
    }

    public Multi<Endpoint> getEndpoints(String tenant) {
        // TODO Add JOIN ON clause to proper table, such as webhooks and then read the results
        Flux<PostgresqlResult> resultFlux = connectionPublisher.flatMapMany(conn ->
                conn.createStatement(basicEndpointGetQuery)
                        .bind("$1", tenant)
                        .execute());
        Flux<Endpoint> endpointFlux = mapResultSetToEndpoint(resultFlux);
        return Multi.createFrom().converter(MultiReactorConverters.fromFlux(), endpointFlux);
    }

    private Flux<Endpoint> mapResultSetToEndpoint(Flux<PostgresqlResult> resultFlux) {
        return resultFlux.flatMap(postgresqlResult -> postgresqlResult.map((row, rowMetadata) -> {
            Endpoint.EndpointType endpointType = Endpoint.EndpointType.values()[row.get("endpoint_type", Integer.class)];

            Endpoint endpoint = new Endpoint();
            endpoint.setTenant(row.get("account_id", String.class));
            endpoint.setId(row.get("id", UUID.class));
            endpoint.setEnabled(row.get("enabled", Boolean.class));
            endpoint.setType(endpointType);
            endpoint.setName(row.get("name", String.class));
            endpoint.setDescription(row.get("description", String.class));
            endpoint.setCreated(row.get("created", Date.class));
            endpoint.setUpdated(row.get("updated", Date.class));

            switch (endpointType) {
                case WEBHOOK:
                    WebhookAttributes attr = new WebhookAttributes();
                    attr.setId(row.get("webhook_id", Integer.class));
                    attr.setDisableSSLVerification(row.get("disable_ssl_verification", Boolean.class));
                    attr.setSecretToken(row.get("secret_token", String.class));
                    String method = row.get("method", String.class);
                    attr.setMethod(WebhookAttributes.HttpType.valueOf(method));
                    attr.setUrl(row.get("url", String.class));
                    endpoint.setProperties(attr);
                default:
            }

            return endpoint;
        }));
    }

    public Uni<Endpoint> getEndpoint(String tenant, UUID id) {
        String query = basicEndpointGetQuery + " AND e.id = $2";
        return connectionPublisherUni.toMulti()
                .onItem()
                .transform(conn -> conn.createStatement(query)
                        .bind("$1", tenant)
                        .bind("$2", id)
                        .execute())
                .flatMap(this::mapResultSetToEndpoint)
                .toUni();
    }

    public Uni<Boolean> deleteEndpoint(String tenant, UUID id) {
        String query = "DELETE FROM public.endpoints WHERE account_id = $1 AND id = $2";
        Flux<PostgresqlResult> resultFlux = connectionPublisher.flatMapMany(conn ->
                conn.createStatement(query)
                        .bind("$1", tenant)
                        .bind("$2", id)
                        .execute());

        // Actually, the endpoint targeting this should be repeatable
        Mono<Boolean> monoResult = resultFlux.flatMap(PostgresqlResult::getRowsUpdated)
                .map(i -> i > 0).next();

        return Uni.createFrom().converter(UniReactorConverters.fromMono(), monoResult);
    }

    public Uni<Boolean> disableEndpoint(String tenant, UUID id) {
        return modifyEndpointStatus(tenant, id, false);
    }

    public Uni<Boolean> enableEndpoint(String tenant, UUID id) {
        return modifyEndpointStatus(tenant, id, true);
    }

    public Uni<Boolean> modifyEndpointStatus(String tenant, UUID id, boolean enabled) {
        String query = "UPDATE public.endpoints SET enabled = $1 WHERE account_id = $2 AND id = $3";

        Flux<PostgresqlResult> resultFlux = connectionPublisher.flatMapMany(conn ->
                conn.createStatement(query)
                        .bind("$1", enabled)
                        .bind("$2", tenant)
                        .bind("$3", id)
                        .execute());

        Mono<Boolean> monoResult = resultFlux.flatMap(PostgresqlResult::getRowsUpdated)
                .map(i -> i > 0).next();

        return Uni.createFrom().converter(UniReactorConverters.fromMono(), monoResult);
    }
}
