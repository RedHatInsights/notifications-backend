package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlResult;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import reactor.core.publisher.Flux;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Provider;
import java.time.LocalDateTime;

@ApplicationScoped
public class EmailAggregationResources extends DatasourceProvider {

    @Inject
    Provider<Uni<PostgresqlConnection>> connectionPublisherUni;


    public Uni<Boolean> addEmailAggregation(EmailAggregation aggregation) {
        String query = "INSERT INTO public.email_aggregation(account_id, bundle, application, payload) " +
                "VALUES ($1, $2, $3, $4)";

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                .bind("$1", aggregation.getAccountId())
                                .bind("$2", aggregation.getBundle())
                                .bind("$3", aggregation.getApplication())
                                .bind("$4", aggregation.getPayload().encode())
                                .execute();
                            return execute.flatMap(PostgresqlResult::getRowsUpdated).map(i -> i > 0);
                        })
                        .withFinalizer(psqlConnection -> {
                            psqlConnection.close().subscribe();
                        })
                ).toUni();
    }

    public Multi<EmailAggregationKey> getApplicationsWithPendingAggregation(LocalDateTime start, LocalDateTime end) {
        String query = "SELECT DISTINCT account_id, bundle, application FROM public.email_aggregation " +
                "WHERE created > $1 AND created <= $2 ";
        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", start)
                                    .bind("$2", end)
                                    .execute();
                            return execute.flatMap(postgresqlResult -> postgresqlResult.map((row, rowMetadata) -> {
                                EmailAggregationKey key = new EmailAggregationKey();
                                key.setApplication(row.get("application", String.class));
                                key.setBundle(row.get("bundle", String.class));
                                key.setAccountId(row.get("account_id", String.class));
                                return key;
                            }));
                        })
                        .withFinalizer(psqlConnection -> {
                            psqlConnection.close().subscribe();
                        })
                );
    }

    public Multi<EmailAggregation> getEmailAggregation(EmailAggregationKey key, LocalDateTime start, LocalDateTime end) {
        String query = "SELECT id, account_id, bundle, application, created, payload FROM public.email_aggregation " +
                "WHERE account_id = $1 AND bundle = $5 AND application = $2 AND created > $3 AND created <= $4 " +
                "ORDER BY created";

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", key.getAccountId())
                                    .bind("$2", key.getApplication())
                                    .bind("$3", start)
                                    .bind("$4", end)
                                    .bind("$5", key.getBundle())
                                    .execute();
                            return execute.flatMap(postgresqlResult -> postgresqlResult.map((row, rowMetadata) -> {
                                EmailAggregation emailAggregation = new EmailAggregation();
                                emailAggregation.setId(row.get("id", Integer.class));
                                emailAggregation.setAccountId(row.get("account_id", String.class));
                                emailAggregation.setCreated(row.get("created", LocalDateTime.class));
                                emailAggregation.setBundle(row.get("bundle", String.class));
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

    public Uni<Integer> purgeOldAggregation(EmailAggregationKey key, LocalDateTime lastUsedTime) {
        String query = "DELETE FROM public.email_aggregation WHERE account_id = $1 AND bundle = $4 AND application = $2 AND created <= $3";
        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", key.getAccountId())
                                    .bind("$2", key.getApplication())
                                    .bind("$3", lastUsedTime)
                                    .bind("$4", key.getBundle())
                                    .execute();
                            return execute.flatMap(PostgresqlResult::getRowsUpdated);
                        })
                        .withFinalizer(psqlConnection -> {
                            psqlConnection.close().subscribe();
                        })
                ).toUni();
    }

}
