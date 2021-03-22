package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
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

    private final String COMMON_WHERE_FILTER = " WHERE account_id = $1 AND bundle = $2 AND application = $3 AND subscription_type = $4 ";

    @Inject
    Provider<Uni<PostgresqlConnection>> connectionPublisherUni;

    public Uni<Boolean> subscribe(String accountNumber, String username, String bundle, String application, EmailSubscriptionType type) {
        String query = "INSERT INTO public.endpoint_email_subscriptions(account_id, user_id, bundle, application, subscription_type) VALUES($1, $2, $3, $4, $5) " +
                "ON CONFLICT (account_id, user_id, bundle, application, subscription_type) DO NOTHING"; // The value is already on the database, this is OK
        return this.executeBooleanQuery(query, accountNumber, username, bundle, application, type);
    }

    public Uni<Boolean> unsubscribe(String accountNumber, String username, String bundle, String application, EmailSubscriptionType type) {
        String query = "DELETE FROM public.endpoint_email_subscriptions where account_id = $1 AND user_id = $2 AND bundle = $3 AND application = $4 AND subscription_type = $5";

        return this.executeBooleanQuery(query, accountNumber, username, bundle, application, type);
    }

    public Uni<EmailSubscription> getEmailSubscription(String accountNumber, String username, String bundle, String application, EmailSubscriptionType type) {
        String query = "SELECT account_id, bundle, application, user_id, subscription_type FROM public.endpoint_email_subscriptions " + COMMON_WHERE_FILTER + " AND user_id = $5 LIMIT 1";
        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute =  c2.createStatement(query)
                                    .bind("$1", accountNumber)
                                    .bind("$2", bundle)
                                    .bind("$3", application)
                                    .bind("$4", type.toString())
                                    .bind("$5", username)
                                    .execute();
                            return this.mapResultSetToEmailSubscription(execute);
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        })
                ).toUni();
    }

    public Multi<EmailSubscription> getEmailSubscriptionsForUser(String accountNumber, String username) {
        String query = "SELECT account_id, bundle, application, user_id, subscription_type FROM public.endpoint_email_subscriptions WHERE account_id = $1 AND user_id = $2";

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute =  c2.createStatement(query)
                                    .bind("$1", accountNumber)
                                    .bind("$2", username)
                                    .execute();
                            return this.mapResultSetToEmailSubscription(execute);
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        })
                );
    }

    public Uni<Integer> getEmailSubscribersCount(String accountNumber, String bundle, String application, EmailSubscriptionType type) {
        String query = "SELECT count(user_id) FROM public.endpoint_email_subscriptions " + COMMON_WHERE_FILTER;

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                    c2 -> {
                        Flux<PostgresqlResult> execute =  c2.createStatement(query)
                                .bind("$1", accountNumber)
                                .bind("$2", bundle)
                                .bind("$3", application)
                                .bind("$4", type.toString())
                                .execute();
                        return execute.flatMap(r -> r.map((row, rowMetadata) -> row.get(0, Integer.class)));
                    })
                    .withFinalizer(postgresqlConnection -> {
                        postgresqlConnection.close().subscribe();
                    })
        ).toUni();
    }

    public Multi<EmailSubscription> getEmailSubscribers(String accountNumber, String bundle, String application, EmailSubscriptionType type) {
        String query = "SELECT account_id, bundle, application, user_id, subscription_type FROM public.endpoint_email_subscriptions " + COMMON_WHERE_FILTER;

        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute =  c2.createStatement(query)
                                    .bind("$1", accountNumber)
                                    .bind("$2", bundle)
                                    .bind("$3", application)
                                    .bind("$4", type.toString())
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
            emailSubscription.setBundle(row.get("bundle", String.class));
            emailSubscription.setApplication(row.get("application", String.class));
            emailSubscription.setType(subscriptionType);

            return emailSubscription;
        }));
    }

    private Uni<Boolean> executeBooleanQuery(String query, String accountNumber, String username, String bundle, String application, EmailSubscriptionType type) {
        return connectionPublisherUni.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                        c2 -> {
                            Flux<PostgresqlResult> execute = c2.createStatement(query)
                                    .bind("$1", accountNumber)
                                    .bind("$2", username)
                                    .bind("$3", bundle)
                                    .bind("$4", application)
                                    .bind("$5", type.toString())
                                    .execute();
                            return execute.flatMap(PostgresqlResult::getRowsUpdated)
                                    .map(i -> true).next();
                        })
                        .withFinalizer(postgresqlConnection -> {
                            postgresqlConnection.close().subscribe();
                        })
                ).toUni();
    }

}
