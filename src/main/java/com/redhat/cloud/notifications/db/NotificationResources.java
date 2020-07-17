package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.NotificationHistory;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlResult;
import io.r2dbc.postgresql.api.PostgresqlStatement;
import io.r2dbc.postgresql.codec.Json;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.converters.multi.MultiReactorConverters;
import io.smallrye.mutiny.converters.uni.UniReactorConverters;
import io.vertx.core.json.JsonObject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import java.util.Date;
import java.util.UUID;

@ApplicationScoped
public class NotificationResources {

    Mono<PostgresqlConnection> connectionPublisher;

    @PostConstruct
    void getConnectionPublisher() {
        // TODO Pooling and unify with other resourceConnectors
        PostgresqlConnectionFactory connectionFactory = new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                .host("192.168.1.139")
                .port(5432)
                .username("hook")
                .password("9FLK6cMm5px8vZ52")
                .database("notifications")
                .build());

        connectionPublisher = connectionFactory.create();
    }

    public Uni<Void> createNotificationHistory(NotificationHistory history) {
        Flux<PostgresqlResult> resultFlux = connectionPublisher.flatMapMany(conn -> {
            String query = "INSERT INTO public.notification_history (endpoint_id, invocation_time, invocation_result) VALUES ($1, $2, $3)";
            if (!history.isInvocationResult()) {
                // Positive result
                query = "INSERT INTO public.notification_history (endpoint_id, invocation_time, invocation_result, details) VALUES ($1, $2, $3, $4)";
            }

            PostgresqlStatement st = conn.createStatement(query)
                    .bind("$1", history.getEndpointId())
                    .bind("$2", history.getInvocationTime())
                    .bind("$3", history.isInvocationResult());

            if (!history.isInvocationResult()) {
                st = st.bind("$4", Json.of(history.getDetails().encode()));
            }
            return st.returnGeneratedValues("id", "created").execute();
        });

        return Uni.createFrom().converter(UniReactorConverters.fromMono(), resultFlux.next()).onItem().apply(r -> null);
    }

    public Multi<NotificationHistory> getNotificationHistory(String tenant, UUID endpoint) {
        String query = "SELECT id, endpoint_id, created, invocation_time, invocation_result FROM public.notification_history WHERE account_id = $1 AND endpoint_id = $2";
        Flux<PostgresqlResult> resultFlux = connectionPublisher.flatMapMany(conn ->
                conn.createStatement(query)
                        .bind("$1", tenant)
                        .bind("$2", endpoint)
                        .execute());
        Flux<NotificationHistory> endpointFlux = mapResultSetToNotificationHistory(resultFlux);
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

            return history;
        }));
    }

    public Uni<JsonObject> getNotificationDetails(String tenant, UUID endpoint, Integer historyId) {
        String query = "SELECT details FROM public.notification_history WHERE account_id = $1 AND endpoint_id = $2 AND id = $3";
        return Uni.createFrom().nullItem();
    }
}
