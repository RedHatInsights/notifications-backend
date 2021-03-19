package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.NotificationHistory;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlResult;
import io.r2dbc.postgresql.api.PostgresqlStatement;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.converters.multi.MultiReactorConverters;
import io.smallrye.mutiny.converters.uni.UniReactorConverters;
import io.vertx.core.json.JsonObject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Date;
import java.util.UUID;

@ApplicationScoped
public class NotificationResources {

    @Inject
    Provider<Mono<PostgresqlConnection>> connectionPublisher;

    @Inject
    Provider<Uni<PostgresqlConnection>> connectionPublisherUni;

    public Uni<NotificationHistory> createNotificationHistory(NotificationHistory history) {
        Flux<NotificationHistory> notificationHistoryFlux = Flux.usingWhen(connectionPublisher.get(),
                conn -> {
                    String query = "INSERT INTO public.notification_history (account_id, endpoint_id, invocation_time, invocation_result, event_id, details) VALUES ($1, $2, $3, $4, $5, $6)";

                    PostgresqlStatement st = conn.createStatement(query)
                            .bind("$1", history.getTenant())
                            .bind("$2", history.getEndpoint().getId())
                            .bind("$3", history.getInvocationTime())
                            .bind("$4", history.isInvocationResult())
                            .bind("$5", history.getEventId());

                    if (history.getDetails() != null) {
                        st.bind("$6", new JsonObject(history.getDetails()).encode());
                    } else {
                        st.bindNull("$6", String.class);
                    }
                    Flux<PostgresqlResult> execute = st.returnGeneratedValues("id", "created").execute();
                    return execute.flatMap(res -> res.map((row, rowMetadata) -> {
                        history.setCreated(row.get("created", Date.class));
                        history.setId(row.get("id", Integer.class));
                        return history;
                    }));
                },
                PostgresqlConnection::close);

        return Uni.createFrom().converter(UniReactorConverters.fromMono(), notificationHistoryFlux.next());
    }

    public Multi<NotificationHistory> getNotificationHistory(String tenant, UUID endpoint) {
        String query = "SELECT id, endpoint_id, created, invocation_time, invocation_result, event_id FROM public.notification_history WHERE account_id = $1 AND endpoint_id = $2";
        Flux<NotificationHistory> endpointFlux = Flux.usingWhen(connectionPublisher.get(),
                conn -> {
                    Flux<PostgresqlResult> resultFlux = conn.createStatement(query)
                            .bind("$1", tenant)
                            .bind("$2", endpoint)
                            .execute();
                    return mapResultSetToNotificationHistory(resultFlux);
                },
                PostgresqlConnection::close);

        return Multi.createFrom().converter(MultiReactorConverters.fromFlux(), endpointFlux);
    }

    private Flux<NotificationHistory> mapResultSetToNotificationHistory(Flux<PostgresqlResult> resultFlux) {
        return resultFlux.flatMap(postgresqlResult -> postgresqlResult.map((row, rowMetadata) -> {
            NotificationHistory history = new NotificationHistory();
            history.setId(row.get("id", Integer.class));
            history.setCreated(row.get("created", Date.class));
            history.setEndpointId(row.get("endpoint_id", UUID.class));
            history.setInvocationResult(row.get("invocation_result", Boolean.class));
            history.setInvocationTime(row.get("invocation_time", Integer.class));
            history.setEventId(row.get("event_id", String.class));

            return history;
        }));
    }

    public Uni<JsonObject> getNotificationDetails(String tenant, Query limiter, UUID endpoint, Integer historyId) {
        String basicQuery = "SELECT details FROM public.notification_history WHERE account_id = $1 AND endpoint_id = $2 AND id = $3";
        String query = limiter.getModifiedQuery(basicQuery);

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", tenant)
                                    .bind("$2", endpoint)
                                    .bind("$3", historyId)
                                    .execute();
                            return execute.flatMap(res -> res.map((row, rowMetadata) -> {
                                String json = row.get("details", String.class);
                                // TODO json field is not necessarily available!
                                return new JsonObject(json);
                            }));
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        }))
                .toUni();
    }
}
