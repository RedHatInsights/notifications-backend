package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.WebhookAttributes;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlResult;
import io.r2dbc.postgresql.api.PostgresqlStatement;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.converters.uni.UniReactorConverters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Provider;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@ApplicationScoped
public class EndpointResources extends DatasourceProvider {

    @Inject
    Provider<Mono<PostgresqlConnection>> connectionPublisher;

    @Inject
    Provider<Uni<PostgresqlConnection>> connectionPublisherUni;

    // TODO Modify to use PreparedStatements

    public Uni<Endpoint> createEndpoint(Endpoint endpoint) {
        Mono<Endpoint> endpointMono =
                Mono.usingWhen(
                        connectionPublisher.get(),
                        conn -> {
                            Flux<Endpoint> endpointFlux = insertEndpointStatement(endpoint, conn);
                            Flux<Endpoint> endpointFlux1 = endpointFlux.flatMap(ep -> {
                                if (endpoint.getProperties() != null && ep.getType() == Endpoint.EndpointType.WEBHOOK) {
                                    return insertWebhooksStatement(ep, conn);
                                } else {
                                    // Other types are not supported at this point
                                    return Flux.empty();
                                }
                            });
                            return endpointFlux1.next();
                        },
                        PostgresqlConnection::close);

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
                    // Should we update the id here? row.get("id", Integer.class); since it's the generated value
                    endpoint.setProperties(attr);
                    return endpoint;
                })));
    }

    private static final String basicEndpointSelectQuery = "SELECT e.account_id, e.id AS endpoint_id, e.endpoint_type, e.enabled, e.name, e.description, e.created, e.updated, ew.id AS webhook_id, ew.url, ew.method, ew.disable_ssl_verification, ew.secret_token";
    private static final String basicEndpointGetQuery = basicEndpointSelectQuery + " FROM public.endpoints AS e JOIN public.endpoint_webhooks AS ew ON ew.endpoint_id = e.id ";

    public Multi<Endpoint> getActiveEndpointsPerType(String tenant, Endpoint.EndpointType type) {
        // TODO Modify to take account selective joins (JOIN (..) UNION (..)) based on the type, same for getEndpoints
        String query = basicEndpointGetQuery + "WHERE e.account_id = $1 AND e.endpoint_type = $2 AND e.enabled = true";

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", tenant)
                                    .bind("$2", type.ordinal())
                                    .execute();
                            return this.mapResultSetToEndpoint(execute);
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }));
    }

    public Multi<Endpoint> getTargetEndpoints(String tenant, String applicationName, String eventTypeName) {
        // TODO Add UNION JOIN for different endpoint types here
        // TODO Add index for application_name & event_type_name
        String query = "WITH accepted_event_types AS ( " +
                "SELECT aev.event_type_id FROM public.application_event_type aev " +
                "JOIN public.applications a ON a.id = aev.application_id " +
                "JOIN public.event_type et ON et.id = aev.event_type_id " +
                "WHERE a.name = $1 AND et.name = $2) " +
                basicEndpointGetQuery +
                "JOIN public.endpoint_targets et ON et.endpoint_id = e.id " +
                "JOIN accepted_event_types aet ON aet.event_type_id = et.event_type_id " +
                "WHERE et.account_id = $3 AND e.enabled = true";

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", applicationName)
                                    .bind("$2", eventTypeName)
                                    .bind("$3", tenant)
                                    .execute();

                            return this.mapResultSetToEndpoint(execute);
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }));
    }

    public Multi<Endpoint> getEndpoints(String tenant, Query.Limit limiter) {
        // TODO Add the ability to modify the getEndpoints to return also with JOIN to application_eventtypes_endpoints link table
        //      or should I just create a new method for it?
        String allAccountEndpointsQuery = basicEndpointGetQuery + " WHERE e.account_id = $1";

        String query = Query.modifyQuery(allAccountEndpointsQuery, limiter);
        // TODO Add JOIN ON clause to proper table, such as webhooks and then read the results
        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", tenant)
                                    .execute();

                            return this.mapResultSetToEndpoint(execute);
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }));
    }

    private Flux<Endpoint> mapResultSetToEndpoint(Flux<PostgresqlResult> resultFlux) {
        return resultFlux.flatMap(postgresqlResult -> postgresqlResult.map((row, rowMetadata) -> {
            Endpoint.EndpointType endpointType = Endpoint.EndpointType.values()[row.get("endpoint_type", Integer.class)];

            Endpoint endpoint = new Endpoint();
            endpoint.setTenant(row.get("account_id", String.class));
            endpoint.setId(row.get("endpoint_id", UUID.class));
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
                    break;
                default:
            }

            return endpoint;
        }));
    }

    public Uni<Endpoint> getEndpoint(String tenant, UUID id) {
        String allAccountEndpointsQuery = basicEndpointGetQuery + " WHERE e.account_id = $1";
        String query = allAccountEndpointsQuery + " AND e.id = $2";

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", tenant)
                                    .bind("$2", id)
                                    .execute();
                            return this.mapResultSetToEndpoint(execute);
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }))
                .toUni();
    }

    public Uni<Boolean> deleteEndpoint(String tenant, UUID id) {
        String query = "DELETE FROM public.endpoints WHERE account_id = $1 AND id = $2";
        Mono<Boolean> monoResult =
                Mono.usingWhen(connectionPublisher.get(),
                        conn -> {
                            Flux<PostgresqlResult> resultFlux = conn.createStatement(query)
                                    .bind("$1", tenant)
                                    .bind("$2", id)
                                    .execute();

                            // Actually, the endpoint targeting this should be repeatable
                            return resultFlux.flatMap(PostgresqlResult::getRowsUpdated)
                                    .map(i -> i > 0).next();
                        },
                        PostgresqlConnection::close);

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

        Mono<Boolean> monoResult =
                Mono.usingWhen(connectionPublisher.get(),
                        conn -> {
                            Flux<PostgresqlResult> resultFlux = conn.createStatement(query)
                                    .bind("$1", enabled)
                                    .bind("$2", tenant)
                                    .bind("$3", id)
                                    .execute();

                            return resultFlux.flatMap(PostgresqlResult::getRowsUpdated)
                                    .map(i -> i > 0).next();
                        },
                        PostgresqlConnection::close);

        return Uni.createFrom().converter(UniReactorConverters.fromMono(), monoResult);
    }

    public Uni<Boolean> linkEndpoint(String tenant, UUID endpointId, long eventTypeId) {
        String query = "INSERT INTO public.endpoint_targets (account_id, event_type_id, endpoint_id) VALUES ($1, $2, $3)";

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", tenant)
                                    .bind("$2", eventTypeId)
                                    .bind("$3", endpointId)
                                    .execute();
                            return execute.flatMap(PostgresqlResult::getRowsUpdated)
                                    .map(i -> i > 0).next();
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }))
                .toUni();
    }

    public Uni<Boolean> unlinkEndpoint(String tenant, UUID endpointId, long eventTypeId) {
        String query = "DELETE FROM public.endpoint_targets WHERE account_id = $1 AND event_type_id = $2 AND endpoint_id = $3";

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", tenant)
                                    .bind("$2", eventTypeId)
                                    .bind("$3", endpointId)
                                    .execute();
                            return execute.flatMap(PostgresqlResult::getRowsUpdated)
                                    .map(i -> i > 0).next();
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }))
                .toUni();
    }

    public Multi<Endpoint> getLinkedEndpoints(String tenant, long eventTypeId, Query.Limit limiter) {
        String basicQuery = basicEndpointGetQuery +
                "JOIN public.endpoint_targets et ON et.endpoint_id = e.id " +
                "WHERE et.account_id = $1 AND et.event_type_id = $2";

        String query = Query.modifyQuery(basicQuery, limiter);

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", tenant)
                                    .bind("$2", eventTypeId)
                                    .execute();

                            return this.mapResultSetToEndpoint(execute);
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }));
    }

    public Multi<Endpoint> getDefaultEndpoints(String tenant) {
        String query = "WITH default_endpoints AS ( " +
                "SELECT endpoint_id " +
                "FROM public.endpoint_defaults " +
                "WHERE account_id = $1 " +
                ")" +
                basicEndpointGetQuery +
                "JOIN default_endpoints ae ON ae.endpoint_id = e.id";

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", tenant)
                                    .execute();

                            return this.mapResultSetToEndpoint(execute);
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }));
    }

    public Uni<Boolean> addEndpointToDefaults(String tenant, UUID endpointId) {
        String query = "INSERT INTO public.endpoint_defaults (account_id, endpoint_id) VALUES ($1, $2)";

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", tenant)
                                    .bind("$2", endpointId)
                                    .execute();
                            return execute.flatMap(PostgresqlResult::getRowsUpdated)
                                    .map(i -> i > 0).next();
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }))
                .toUni();
    }

    public Uni<Boolean> deleteEndpointFromDefaults(String tenant, UUID endpointId) {
        String query = "DELETE FROM public.endpoint_defaults WHERE account_id = $1 AND endpoint_id = $2";

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", tenant)
                                    .bind("$2", endpointId)
                                    .execute();
                            return execute.flatMap(PostgresqlResult::getRowsUpdated)
                                    .map(i -> i > 0).next();
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }))
                .toUni();
    }

    public Uni<Boolean> updateEndpoint(Endpoint endpoint) {
        // TODO Update could fail because the item did not exist, throw 404 in that case?
        // TODO Fix transaction so that we don't end up with half the updates applied
        Mono<Boolean> endpointMono =
                Mono.usingWhen(connectionPublisher.get(),
                        conn -> {
                            Mono<Boolean> endpointFlux = updateEndpointStatement(endpoint, conn);
                            Mono<Boolean> endpointFlux1 = endpointFlux.flatMap(ep -> {
                                if (endpoint.getProperties() != null && endpoint.getType() == Endpoint.EndpointType.WEBHOOK) {
                                    return updateWebhooksStatement(endpoint, conn);
                                }
                                return Mono.empty();
                            });
                            return endpointFlux1;
                        },
                        PostgresqlConnection::close);

        return Uni.createFrom().converter(UniReactorConverters.fromMono(), endpointMono);
    }

    private Mono<Boolean> updateEndpointStatement(Endpoint endpoint, PostgresqlConnection conn) {
        String endpointQuery = "UPDATE public.endpoints SET name = $3, description = $4, enabled = $5, updated = $6 WHERE account_id = $1 AND id = $2";
        PostgresqlStatement bindSt = conn.createStatement(endpointQuery)
                .bind("$1", endpoint.getTenant())
                .bind("$2", endpoint.getId())
                .bind("$3", endpoint.getName())
                .bind("$4", endpoint.getDescription())
                .bind("$5", endpoint.isEnabled())
                .bind("$6", LocalDateTime.now());

        return bindSt
                .execute()
                .flatMap(PostgresqlResult::getRowsUpdated)
                .map(i -> i > 0).next();
    }

    private Mono<Boolean> updateWebhooksStatement(Endpoint endpoint, PostgresqlConnection conn) {
        WebhookAttributes attr = (WebhookAttributes) endpoint.getProperties();
        String webhookQuery = "UPDATE public.endpoint_webhooks SET url = $2, method = $3, disable_ssl_verification = $4, secret_token = $5 WHERE endpoint_id = $1 ";

        PostgresqlStatement bindSt = conn.createStatement(webhookQuery)
                .bind("$1", endpoint.getId())
                .bind("$2", attr.getUrl())
                .bind("$3", attr.getMethod().toString())
                .bind("$4", attr.isDisableSSLVerification());

        if (attr.getSecretToken() != null) {
            bindSt.bind("$5", attr.getSecretToken());
        } else {
            bindSt.bindNull("$5", String.class);
        }

        return bindSt
                .execute()
                .flatMap(PostgresqlResult::getRowsUpdated)
                .map(i -> i > 0).next();
    }
}
