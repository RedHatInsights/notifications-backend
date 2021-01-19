package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlResult;
import io.r2dbc.postgresql.codec.Json;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import reactor.core.publisher.Flux;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Date;

@ApplicationScoped
public class EmailAggregationResources extends DatasourceProvider {

    @Inject
    Provider<Uni<PostgresqlConnection>> connectionPublisherUni;


    public Uni<Void> addEmailAggregation(EmailAggregation aggregation) {
        String query = "INSERT INTO public.email_aggregation(account_id, application, payload) " +
                "VALUES ($1, $2, $3)";

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                .bind("$1", aggregation.getAccountId())
                                .bind("$2", aggregation.getApplication())
                                .bind("$3", Json.of(aggregation.getPayload().encode()))
                                .execute();
                            return execute;
                        })
                        .withFinalizer(psqlConnection -> {
                            psqlConnection.close().subscribe();
                        })
                ).toUni().onItem().ignore().andSwitchTo(Uni.createFrom().voidItem());
    }

    public Uni<Integer> purgeOldAggregation(Date lastUsedTime) {
        String query = "DELETE FROM public.email_aggregation WHERE created <= $1";
        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", lastUsedTime)
                                    .execute();
                            return execute.flatMap(PostgresqlResult::getRowsUpdated);
                        })
                        .withFinalizer(psqlConnection -> {
                            psqlConnection.close().subscribe();
                        })
                ).toUni();
    }

}
