package com.redhat.cloud.notifications.db;

import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.smallrye.mutiny.Uni;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Provider;

@ApplicationScoped
public class ApplicationResources {
    /*
        - Application name + id
            - Each application has a set of event types
                - Each appication+eventType combination has a set of endpoints
     */

    @Inject
    Provider<Uni<PostgresqlConnection>> connectionPublisher;

    public Uni<Void> createApplication() {
        return Uni.createFrom().nullItem();
    }

    public Uni<Void> addEventTypeToApplication() {
        return Uni.createFrom().nullItem();
    }
}
