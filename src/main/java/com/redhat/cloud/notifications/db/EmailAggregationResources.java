package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.EmailAggregation;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlResult;
import io.r2dbc.postgresql.codec.Json;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
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

    public Multi<String> getAccountIdsWithPendingAggregation(String application, Date start, Date end) {
        String query = "SELECT DISTINCT account_id FROM public.email_aggregation " +
                "WHERE application = $1 AND created > $2 AND created <= $3";

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", application)
                                    .bind("$2", start)
                                    .bind("$3", end)
                                    .execute();
                            return execute.flatMap(postgresqlResult -> postgresqlResult.map((row, rowMetadata) -> row.get("account_id", String.class)));
                        })
                        .withFinalizer(psqlConnection -> {
                            psqlConnection.close().subscribe();
                        })
                );
    }

    public Multi<EmailAggregation> getEmailAggregation(String accountId, String application, Date start, Date end) {
        String query = "SELECT id, account_id, application, created, payload FROM public.email_aggregation " +
                "WHERE account_id = $1 AND application = $2 AND created > $3 AND created <= $4 " +
                "ORDER BY created";

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", accountId)
                                    .bind("$2", application)
                                    .bind("$3", start)
                                    .bind("$4", end)
                                    .execute();
                            return execute.flatMap(postgresqlResult -> postgresqlResult.map((row, rowMetadata) -> {
                                EmailAggregation emailAggregation = new EmailAggregation();
                                emailAggregation.setId(row.get("id", Integer.class));
                                emailAggregation.setAccountId(row.get("account_id", String.class));
                                emailAggregation.setCreated(row.get("created", Date.class));
                                emailAggregation.setApplication(row.get("application", String.class));
                                emailAggregation.setPayload(new JsonObject(row.get("payload", String.class)));

                                return emailAggregation;
                            }));
                        })
                        .withFinalizer(psqlConnection -> {
                            psqlConnection.close().subscribe();
                        })
                );
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
