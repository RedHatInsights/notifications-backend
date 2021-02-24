package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EventType;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlResult;
import io.r2dbc.postgresql.api.PostgresqlStatement;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import reactor.core.publisher.Flux;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class ApplicationResources extends AbstractGenericResource {

    private static final String APPLICATION_QUERY = "SELECT a.id, a.name, a.display_name, a.created, a.updated, a.bundle_id FROM public.applications a";
    private static final String APPLICATION_QUERY_BY_BUNDLE_NAME = APPLICATION_QUERY + " JOIN bundles AS b ON a.bundle_id = b.id WHERE b.name = $1";
    @Inject
    Provider<Uni<PostgresqlConnection>> connectionPublisher;

    public Uni<Application> createApplication(Application app) {
        String query = "INSERT INTO public.applications (name, display_name, bundle_id) VALUES ($1, $2, $3)";
        // Return filled with id
        return connectionPublisher.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> c2.createStatement(query)
                                .bind("$1", app.getName())
                                .bind("$2", app.getDisplay_name())
                                .bind("$3", app.getBundleId())
                                .returnGeneratedValues("id", "created")
                                .execute()
                                .flatMap(res -> res.map((row, rowMetadata) -> {
                                    app.setId(row.get("id", UUID.class));
                                    app.setCreated(row.get("created", Date.class));
                                    return app;
                                })))
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }))
                .toUni();
    }

    public Uni<Boolean> deleteApplication(UUID applicationId) {
        String query = "DELETE FROM public.applications WHERE id = $1";

        return runDeleteQuery(applicationId, query);
    }

    public Uni<EventType> addEventTypeToApplication(UUID applicationId, EventType type) {
        String insertQuery = "INSERT INTO public.event_type (name, display_name, application_id, description) VALUES ($1, $2, $3, $4)";

        return connectionPublisher.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            PostgresqlStatement bind = c2.createStatement(insertQuery)
                                    .bind("$1", type.getName())
                                    .bind("$2", type.getDisplay_name())
                                    .bind("$3", applicationId)

                                    .returnGeneratedValues("id");

                            if (type.getDescription() != null) {
                                bind.bind("$4", type.getDescription());
                            } else {
                                bind.bindNull("$4", String.class);
                            }

                            return bind
                                    .execute()
                                    .flatMap(res -> res.map((row, rowMetadata) -> {
                                        type.setId(row.get("id", UUID.class));
                                        return type;
                                    }));
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }))
                .toUni();
    }

    public Multi<Application> getApplications(String bundleName) {
        return connectionPublisher.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(APPLICATION_QUERY_BY_BUNDLE_NAME)
                                    .bind("$1", bundleName)
                                    .execute();

                            return mapResultSetToApplication(execute);
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }));
    }

    private Flux<Application> mapResultSetToApplication(Flux<PostgresqlResult> resultFlux) {
        return resultFlux.flatMap(postgresqlResult -> postgresqlResult.map((row, rowMetadata) -> {
            Application app = new Application();
            app.setId(row.get("id", UUID.class));
            app.setBundleId(row.get("bundle_id", UUID.class));
            app.setName(row.get("name", String.class));
            app.setDisplay_name(row.get("display_name", String.class));
            app.setCreated(row.get("created", Date.class));
            app.setUpdated(row.get("updated", Date.class));
            return app;
        }));
    }

    public Uni<Application> getApplication(UUID applicationId) {
        String query = APPLICATION_QUERY + " WHERE id = $1";
        return connectionPublisher.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", applicationId)
                                    .execute();

                            return mapResultSetToApplication(execute);
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        })).toUni();
    }

    public Uni<Application> getApplication(String bundleName, String applicationName) {
        final String query = APPLICATION_QUERY_BY_BUNDLE_NAME + " AND a.name = $2";

        return connectionPublisher.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", bundleName)
                                    .bind("$2", applicationName)
                                    .execute();

                            return mapResultSetToApplication(execute);
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        })).toUni();
    }

    public Multi<EventType> getEventTypes(UUID applicationId) {
        String query = "SELECT et.id, et.name, et.display_name FROM public.event_type et " +
                "WHERE et.application_id = $1";

        return connectionPublisher.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", applicationId)
                                    .execute();

                            return execute.flatMap(res -> res.map((row, rowMetadata) -> {
                                EventType eventType = new EventType();
                                eventType.setId(row.get("id", UUID.class));
                                eventType.setName(row.get("name", String.class));
                                eventType.setDisplay_name(row.get("display_name", String.class));
                                return eventType;
                            }));
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }));
    }

    public Uni<Boolean> deleteEventTypeById(UUID eventTypeId) {
        String query = "DELETE FROM public.event_type WHERE id = $1";

        return runDeleteQuery(eventTypeId, query);

    }

    public Multi<EventType> getEventTypes(Query limiter) {
        return this.getEventTypes(limiter, null, null);
    }

    public Multi<EventType> getEventTypes(Query limiter, Set<UUID> applicationId, UUID bundleId) {
        String basicQuery = "SELECT et.id AS et_id, et.name AS et_name, et.display_name AS et_din, et.description AS et_description, a.id AS a_id, a.name AS a_name, a.display_name as a_displayName, a.bundle_id as a_bundle_id FROM public.event_type et " +
                "JOIN public.applications a ON a.id = et.application_id ";

        String whereClause = "";
        boolean hasWhere = false;
        String bundleIdParam = "$1";

        if (applicationId != null && applicationId.size() > 0) {
            whereClause += " a.id = ANY ($1) ";
            bundleIdParam = "$2";
            hasWhere = true;
        }

        if (bundleId != null) {
            if (hasWhere) {
                whereClause += " AND ";
            }
            whereClause += " a.bundle_id = " + bundleIdParam + " ";
        }

        if (!whereClause.equals("")) {
            basicQuery += " WHERE " + whereClause;
        }

        String query = limiter.getModifiedQuery(basicQuery);

        final String finalBundleIdParam = bundleIdParam;

        return connectionPublisher.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            PostgresqlStatement statement = c2.createStatement(query);

                            if (applicationId != null && applicationId.size() > 0) {
                                statement = statement.bind("$1", applicationId.toArray(new UUID[applicationId.size()]));
                            }

                            if (bundleId != null) {
                                statement = statement.bind(finalBundleIdParam, bundleId);
                            }

                            Flux<PostgresqlResult> execute = statement.execute();

                            return mapResultSetToEventTypes(execute);
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }));
    }

    public Multi<EventType> getEventTypesByEndpointId(@NotNull String accountId, @NotNull UUID endpointId) {
        String query = "SELECT et.id AS et_id, et.name AS et_name, et.display_name AS et_din, et.description AS et_description, a.id AS a_id, a.name AS a_name, a.display_name as a_displayName, a.bundle_id as a_bundle_id FROM public.event_type et " +
                "JOIN public.applications a ON a.id = et.application_id " +
                "JOIN public.endpoint_targets endt ON  endt.event_type_id = et.id " +
                "WHERE endt.endpoint_id = $1 AND endt.account_id = $2";

        return connectionPublisher.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", endpointId)
                                    .bind("$2", accountId)
                                    .execute();

                            return mapResultSetToEventTypes(execute);
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }));
    }

    private Flux<EventType> mapResultSetToEventTypes(Flux<PostgresqlResult> resultFlux) {
        return resultFlux.flatMap(postgresqlResult -> postgresqlResult.map((row, rowMetadata) -> {
            Application app = new Application();
            app.setBundleId(row.get("a_bundle_id", UUID.class));
            app.setId(row.get("a_id", UUID.class));
            app.setName(row.get("a_name", String.class));
            app.setDisplay_name(row.get("a_displayName", String.class));

            EventType eventType = new EventType();
            eventType.setId(row.get("et_id", UUID.class));
            eventType.setName(row.get("et_name", String.class));
            eventType.setDisplay_name(row.get("et_din", String.class));
            eventType.setDescription(row.get("et_description", String.class));

            eventType.setApplication(app);

            return eventType;
        }));
    }
}
