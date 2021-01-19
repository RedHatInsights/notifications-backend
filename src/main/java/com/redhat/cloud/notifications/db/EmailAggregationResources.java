package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlResult;
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
        String query = "INSERT INTO public.email_aggregation(account_id, application_id, payload)" +
                "VALUES ($account_id, $application_id, $payload);";

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                .bind("$account_id", aggregation.getAccountId())
                                .bind("$application_id", aggregation.getApplicationId())
                                .bind("$payload", aggregation.getPayload().encode())
                                .execute();
                            return execute;
                        })
                        .withFinalizer(psqlConnection -> {
                            psqlConnection.close().subscribe();
                        })
                ).toUni().onItem().ignore().andSwitchTo(Uni.createFrom().voidItem());
    }

    public Uni<Integer> purgeOldAggregation(Date lastUsedTime) {
        String query = "DELETE FROM public.email_aggregation WHERE created <= $lastUsedTime;";
        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$lastUsedTime", lastUsedTime)
                                    .execute();
                            return execute.flatMap(PostgresqlResult::getRowsUpdated);
                        })
                        .withFinalizer(psqlConnection -> {
                            psqlConnection.close().subscribe();
                        })
                ).toUni();
    }

}
