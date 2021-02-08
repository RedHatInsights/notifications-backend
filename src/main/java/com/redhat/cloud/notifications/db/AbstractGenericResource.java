package com.redhat.cloud.notifications.db;

import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.postgresql.api.PostgresqlResult;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import reactor.core.publisher.Flux;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.UUID;

/**
 * A base class for some of the resources stuff
 */
public abstract class AbstractGenericResource {

    @Inject
    Provider<Uni<PostgresqlConnection>> connectionPublisher;

    protected Uni<Boolean> runDeleteQuery(UUID eventTypeId, String query) {
        return connectionPublisher.get().onItem()
                .transformToMulti(c -> Multi.createFrom().resource(() -> c,
                    c2 -> {
                        Flux<PostgresqlResult> execute = c2.createStatement(query)
                                .bind("$1", eventTypeId)
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
