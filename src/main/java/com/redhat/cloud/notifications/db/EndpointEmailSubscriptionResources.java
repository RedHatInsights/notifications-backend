package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.EmailSubscription.EmailSubscriptionType;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlResult;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import reactor.core.publisher.Flux;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Provider;

@ApplicationScoped
public class EndpointEmailSubscriptionResources extends DatasourceProvider {

    @Inject
    Provider<Uni<PostgresqlConnection>> connectionPublisherUni;

    public Uni<Boolean> subscribe(String accountNumber, String username, EmailSubscriptionType type) {
        String query = "INSERT INTO public.endpoint_email_subscriptions(account_id, user_id, subscription_type) VALUES($1, $2, $3) " +
                "ON CONFLICT (account_id, user_id, subscription_type) DO NOTHING"; // The value is already on the database, this is OK
        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", accountNumber)
                                    .bind("$2", username)
                                    .bind("$3", type.toString())
                                    .execute();
                            return execute.flatMap(PostgresqlResult::getRowsUpdated)
                                    .map(i -> true).next();
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        })
                ).toUni();
    }

    public Uni<Boolean> unsubscribe(String accountNumber, String username, EmailSubscriptionType type) {
        String query = "DELETE FROM public.endpoint_email_subscriptions where account_id = $1 AND user_id = $2 AND subscription_type = $3";

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", accountNumber)
                                    .bind("$2", username)
                                    .bind("$3", type.toString())
                                    .execute();
                            return execute.flatMap(PostgresqlResult::getRowsUpdated)
                                    .map(i -> true).next();
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        })
                ).toUni();
    }

    public Uni<EmailSubscription> getEmailSubscription(String accountNumber, String username, EmailSubscriptionType type) {
        String query = "SELECT account_id, user_id, subscription_type FROM public.endpoint_email_subscriptions where account_id = $1 AND user_id = $2 AND subscription_type = $3 LIMIT 1";

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute =  c2.createStatement(query)
                                    .bind("$1", accountNumber)
                                    .bind("$2", username)
                                    .bind("$3", type.toString())
                                    .execute();
                            return this.mapResultSetToEmailSubscription(execute);
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        })
                ).toUni();
    }

    public Multi<EmailSubscription> getEmailSubscribers(String accountNumber, EmailSubscriptionType type) {
        String query = "SELECT account_id, user_id, subscription_type FROM public.endpoint_email_subscriptions where account_id = $1 AND subscription_type = $2";

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute =  c2.createStatement(query)
                                    .bind("$1", accountNumber)
                                    .bind("$2", type.toString())
                                    .execute();
                            return this.mapResultSetToEmailSubscription(execute);
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        })
                );
    }

    private Flux<EmailSubscription> mapResultSetToEmailSubscription(Flux<PostgresqlResult> resultFlux) {
        return resultFlux.flatMap(postgresqlResult -> postgresqlResult.map((row, rowMetadata) -> {
            EmailSubscriptionType subscriptionType = EmailSubscriptionType.valueOf(row.get("subscription_type", String.class));
            EmailSubscription emailSubscription = new EmailSubscription();

            emailSubscription.setAccountId(row.get("account_id", String.class));
            emailSubscription.setUsername(row.get("user_id", String.class));
            emailSubscription.setType(subscriptionType);

            return emailSubscription;
        }));
    }

}
