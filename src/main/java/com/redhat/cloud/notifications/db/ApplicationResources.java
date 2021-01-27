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
public class ApplicationResources {

    @Inject
    Provider<Uni<PostgresqlConnection>> connectionPublisher;

    public Uni<Application> createApplication(Application app) {
        String query = "INSERT INTO public.applications (name, display_name) VALUES ($1, $2)";
        // Return filled with id
        return connectionPublisher.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> c2.createStatement(query)
                                .bind("$1", app.getName())
                                .bind("$2", app.getDisplay_name())
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

    public Uni<EventType> addEventTypeToApplication(UUID applicationId, EventType type) {
        String insertQuery = "INSERT INTO public.event_type (name, display_name) VALUES ($1, $2)";
        String linkQuery = "INSERT INTO public.application_event_type (application_id, event_type_id) VALUES ($1, $2)";

        return connectionPublisher.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<EventType> eventTypeFlux = c2.createStatement(insertQuery)
                                    .bind("$1", type.getName())
                                    .bind("$2", type.getDisplay_name())
                                    .returnGeneratedValues("id")
                                    .execute()
                                    .flatMap(res -> res.map((row, rowMetadata) -> {
                                        type.setId(row.get("id", Integer.class));
                                        return type;
                                    }));

                            return eventTypeFlux.flatMap(eventType -> c2.createStatement(linkQuery)
                                    .bind("$1", applicationId)
                                    .bind("$2", eventType.getId())
                                    .execute()
                                    .flatMap(PostgresqlResult::getRowsUpdated)
                                    .map(i -> i > 0 ? eventType : null).next());
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }))
                .toUni();
    }

    private static final String APPLICATION_QUERY = "SELECT a.id, a.name, a.display_name, a.created, a.updated FROM public.applications a";


    public Multi<Application> getApplications() {
        return connectionPublisher.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(APPLICATION_QUERY)
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

    public Multi<EventType> getEventTypes(UUID applicationId) {
        String query = "SELECT et.id, et.name, et.display_name FROM public.event_type et " +
                "JOIN public.application_event_type aet ON aet.event_type_id = et.id " +
                "WHERE aet.application_id = $1";

        return connectionPublisher.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", applicationId)
                                    .execute();

                            return execute.flatMap(res -> res.map((row, rowMetadata) -> {
                                EventType eventType = new EventType();
                                eventType.setId(row.get("id", Integer.class));
                                eventType.setName(row.get("name", String.class));
                                eventType.setDisplay_name(row.get("display_name", String.class));
                                return eventType;
                            }));
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }));
    }

    public Multi<EventType> getEventTypes(Query limiter) {
        return this.getEventTypes(limiter, null);
    }

    public Multi<EventType> getEventTypes(Query limiter, Set<UUID> applicationId) {
        String basicQuery = "SELECT et.id AS et_id, et.name AS et_name, et.display_name AS et_din, a.id AS a_id, a.name AS a_name, a.display_name as a_displayName FROM public.event_type et " +
                "JOIN public.application_event_type aet ON aet.event_type_id = et.id " +
                "JOIN public.applications a ON a.id = aet.application_id";

        if (applicationId != null && applicationId.size() > 0) {
            basicQuery += " WHERE a.id = ANY ($1)";
        }

        String query = limiter.getModifiedQuery(basicQuery);

        return connectionPublisher.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            PostgresqlStatement statement = c2.createStatement(query);

                            if (applicationId != null && applicationId.size() > 0) {
                                statement = statement.bind("$1", applicationId.toArray(new UUID[applicationId.size()]));
                            }

                            Flux<PostgresqlResult> execute = statement.execute();

                            return mapResultSetToEventTypes(execute);
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }));
    }

    public Multi<EventType> getEventTypesByEndpointId(@NotNull String accountId, @NotNull UUID endpointId) {
        String query = "SELECT et.id AS et_id, et.name AS et_name, et.display_name AS et_din, a.id AS a_id, a.name AS a_name, a.display_name as a_displayName FROM public.event_type et " +
                "JOIN public.application_event_type aet ON aet.event_type_id = et.id " +
                "JOIN public.applications a ON a.id = aet.application_id " +
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
            app.setId(row.get("a_id", UUID.class));
            app.setName(row.get("a_name", String.class));
            app.setDisplay_name(row.get("a_displayName", String.class));

            EventType eventType = new EventType();
            eventType.setId(row.get("et_id", Integer.class));
            eventType.setName(row.get("et_name", String.class));
            eventType.setDisplay_name(row.get("et_din", String.class));

            eventType.setApplication(app);

            return eventType;
        }));
    }
}
