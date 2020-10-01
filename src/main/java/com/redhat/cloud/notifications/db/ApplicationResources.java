package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EventType;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Provider;

@ApplicationScoped
public class ApplicationResources {
    @Inject
    Provider<Uni<PostgresqlConnection>> connectionPublisher;

    public Uni<Application> createApplication(Application app) {
        // Return filled with id
        return Uni.createFrom().nullItem();
    }

    public Uni<Void> addEventTypeToApplication(long applicationId, EventType type) {
        return Uni.createFrom().nullItem();
    }

    public Multi<Application> getApplications() {
        // TODO We need separate to get the eventTypes as joined in and one without for admin use - or do we?
        return Multi.createFrom().empty();
    }

    public Uni<Application> getApplication(Integer applicationId) {
        return Uni.createFrom().nullItem();
    }

    public Multi<EventType> getEventTypes(Integer applicationId) {
        return Multi.createFrom().nothing();
    }

}
