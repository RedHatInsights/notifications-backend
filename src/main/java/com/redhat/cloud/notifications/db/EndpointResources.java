package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.WebhookAttributes;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlResult;
import io.r2dbc.postgresql.api.PostgresqlStatement;
import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.converters.uni.UniReactorConverters;
import io.vertx.core.json.Json;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.BadRequestException;
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
                                    return Flux.just(ep);
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
        PostgresqlStatement bind = conn.createStatement("INSERT INTO public.endpoint_webhooks (endpoint_id, url, method, disable_ssl_verification, secret_token, basic_authentication) VALUES ($1, $2, $3, $4, $5, $6)")
                .bind("$1", endpoint.getId())
                .bind("$2", attr.getUrl())
                .bind("$3", attr.getMethod().toString())
                .bind("$4", attr.isDisableSSLVerification());

        if (attr.getSecretToken() != null) {
            bind.bind("$5", attr.getSecretToken());
        } else {
            bind.bindNull("$5", String.class);
        }

        if (attr.getBasicAuthentication() != null) {
            String encodedJson = Json.encode(attr.getBasicAuthentication());
            bind.bind("$6", io.r2dbc.postgresql.codec.Json.of(encodedJson));
        } else {
            bind.bindNull("$6", io.r2dbc.postgresql.codec.Json.class);
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

    private static final String basicEndpointSelectQuery = "SELECT e.account_id, e.id AS endpoint_id, e.endpoint_type, e.enabled, e.name, e.description, e.created, e.updated";
    private static final String webhookEndpointSelectQuery = ", ew.id AS webhook_id, ew.url, ew.method, ew.disable_ssl_verification, ew.secret_token, ew.basic_authentication";
    private static final String basicEndpointGetQuery = basicEndpointSelectQuery + webhookEndpointSelectQuery + " FROM public.endpoints AS e LEFT JOIN public.endpoint_webhooks AS ew ON ew.endpoint_id = e.id ";
    private static final String basicEndpointCountQuery = "SELECT count(e.id) as count FROM public.endpoints AS e ";

    public Multi<Endpoint> getEndpointsPerType(String tenant, Endpoint.EndpointType type, Boolean activeOnly, Query limiter) {
        // TODO Modify the parameter to take a vararg of Functions that modify the query
        // TODO Modify to take account selective joins (JOIN (..) UNION (..)) based on the type, same for getEndpoints
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder
                .append(basicEndpointGetQuery)
                .append("WHERE e.account_id = $1 AND e.endpoint_type = $2");

        if (activeOnly != null) {
            queryBuilder.append(" AND e.enabled = $3");
        }

        final String query = limiter == null ? queryBuilder.toString() : limiter.getModifiedQuery(queryBuilder.toString());

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            PostgresqlStatement statement = c.createStatement(query)
                                    .bind("$1", tenant)
                                    .bind("$2", type.ordinal());

                            if (activeOnly != null) {
                                statement = statement.bind("$3", activeOnly);
                            }

                            Flux<PostgresqlResult> execute = statement.execute();
                            return this.mapResultSetToEndpoint(execute);
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }));
    }

    public Uni<Integer> getEndpointsCountPerType(String tenant, Endpoint.EndpointType type, Boolean activeOnly) {
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder
                .append(basicEndpointCountQuery)
                .append("WHERE e.account_id = $1 AND e.endpoint_type = $2");

        if (activeOnly != null) {
            queryBuilder.append(" AND e.enabled = $3");
        }

        final String query = queryBuilder.toString();

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            PostgresqlStatement statement = c.createStatement(query)
                                    .bind("$1", tenant)
                                    .bind("$2", type.ordinal());

                            if (activeOnly != null) {
                                statement = statement.bind("$3", activeOnly);
                            }

                            Flux<PostgresqlResult> execute = statement.execute();
                            return execute.flatMap(r -> r.map((row, rowMetadata) -> row.get(0, Integer.class)));
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }))
                .toUni();
    }

    public Multi<Endpoint> getTargetEndpoints(String tenant, String bundleName, String applicationName, String eventTypeName) {
        // TODO Add UNION JOIN for different endpoint types here
        String query = "WITH accepted_event_types AS ( " +
                "SELECT et.id FROM public.event_type et " +
                "JOIN public.applications a ON a.id = et.application_id " +
                "JOIN public.bundles b ON b.id = a.bundle_id " +
                "WHERE a.name = $1 AND et.name = $2 AND b.name = $4) " +
                basicEndpointGetQuery +
                "JOIN public.endpoint_targets et ON et.endpoint_id = e.id " +
                "JOIN accepted_event_types aet ON aet.id = et.event_type_id " +
                "WHERE et.account_id = $3 AND e.enabled = true";

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", applicationName)
                                    .bind("$2", eventTypeName)
                                    .bind("$3", tenant)
                                    .bind("$4", bundleName)
                                    .execute();

                            return this.mapResultSetToEndpoint(execute);
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }));
    }

    public Multi<Endpoint> getEndpoints(String tenant, Query limiter) {
        // TODO Add the ability to modify the getEndpoints to return also with JOIN to application_eventtypes_endpoints link table
        //      or should I just create a new method for it?
        String allAccountEndpointsQuery = basicEndpointGetQuery + " WHERE e.account_id = $1";

        String query = limiter.getModifiedQuery(allAccountEndpointsQuery);
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

    public Uni<Integer> getEndpointsCount(String tenant) {
        String query = basicEndpointCountQuery + " WHERE e.account_id = $1";

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c.createStatement(query)
                                    .bind("$1", tenant)
                                    .execute();
                            return execute.flatMap(r -> r.map((row, rowMetadata) -> row.get(0, Integer.class)));
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }))
                .toUni();
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

                    String basicAuthentication = row.get("basic_authentication", String.class);
                    if (basicAuthentication != null) {
                        attr.setBasicAuthentication(Json.decodeValue(basicAuthentication, WebhookAttributes.BasicAuthentication.class));
                    }

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

    public Uni<Boolean> linkEndpoint(String tenant, UUID endpointId, UUID eventTypeId) {
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

    public Uni<Boolean> unlinkEndpoint(String tenant, UUID endpointId, UUID eventTypeId) {
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

    public Multi<Endpoint> getLinkedEndpoints(String tenant, UUID eventTypeId, Query limiter) {
        String basicQuery = basicEndpointGetQuery +
                "JOIN public.endpoint_targets et ON et.endpoint_id = e.id " +
                "WHERE et.account_id = $1 AND et.event_type_id = $2";

        String query = limiter.getModifiedQuery(basicQuery);

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

    public Uni<Boolean> endpointInDefaults(String tenant, UUID endpointId) {
        String query = "SELECT count(endpoint_id) FROM public.endpoint_defaults WHERE account_id = $1 and endpoint_id = $2";

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", tenant)
                                    .bind("$2", endpointId)
                                    .execute();

                            return execute.flatMap(postgresqlResult -> postgresqlResult.map((row, rowMetadata) -> row.get(0, Integer.class) > 0));
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        })).toUni();
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
                .toUni()
                .onFailure()
                .transform(t -> {
                    if (t instanceof R2dbcDataIntegrityViolationException) {
                        return new BadRequestException("Given endpoint id can not be linked to default");
                    }
                    return t;
                });
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
                        conn -> updateEndpointStatement(endpoint, conn)
                                .flatMap(ep -> {
                                    if (endpoint.getProperties() != null && endpoint.getType() == Endpoint.EndpointType.WEBHOOK) {
                                        return updateWebhooksStatement(endpoint, conn);
                                    }
                                    return Mono.empty();
                                }),
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
