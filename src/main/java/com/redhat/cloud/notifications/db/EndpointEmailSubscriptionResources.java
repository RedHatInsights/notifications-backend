package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.EmailSubscription;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlResult;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.inject.Inject;
import javax.inject.Provider;

public class EndpointEmailSubscriptionResources extends DatasourceProvider {

    @Inject
    Provider<Mono<PostgresqlConnection>> connectionPublisher;

    @Inject
    Provider<Uni<PostgresqlConnection>> connectionPublisherUni;

    public Uni<Boolean> subscribe(String accountNumber, String username, EmailSubscription eventType) {
        String query = "INSERT INTO public.endpoint_email_subscriptions(account_id, user_id, event_type) VALUES($1, $2, $3)";

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", accountNumber)
                                    .bind("$2", username)
                                    .bind("$3", eventType)
                                    .execute();
                            return execute.flatMap(PostgresqlResult::getRowsUpdated)
                                    .map(i -> i > 0).next();
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        })
                ).toUni();
    }

    public Uni<Boolean> unsubscribe(String accountNumber, String username, EmailSubscription eventType) {
        String query = "DELETE FROM public.endpoint_email_subscriptions where account_id = $1 AND user_id = $2 AND event_type = $3";

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", accountNumber)
                                    .bind("$2", username)
                                    .bind("$3", eventType)
                                    .execute();
                            return execute.flatMap(PostgresqlResult::getRowsUpdated)
                                    .map(i -> i > 0).next();
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        })
                ).toUni();
    }

    public
}
