package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointDefault;
import com.redhat.cloud.notifications.models.EndpointTarget;
import com.redhat.cloud.notifications.models.EndpointWebhook;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class DbCleaner {

    @Inject
    Mutiny.Session session;

    /**
     * Deletes all records from all database tables (except for flyway_schema_history). This method should be called
     * from a method annotated with <b>both</b> {@link BeforeEach} and {@link AfterEach} in all test classes that
     * involve SQL queries to guarantee the tests isolation in terms of stored data. Ideally, we should do that with
     * {@link io.quarkus.test.TestTransaction} but it doesn't work with Hibernate Reactive, so this is a temporary
     * workaround to make our tests more reliable and easy to maintain.
     */
    public void clean() {
        session.withTransaction(transaction -> deleteAllFrom(EmailAggregation.class)
                .chain(() -> deleteAllFrom(EmailSubscription.class))
                .chain(() -> deleteAllFrom(NotificationHistory.class))
                .chain(() -> deleteAllFrom(EndpointDefault.class))
                .chain(() -> deleteAllFrom(EndpointTarget.class))
                .chain(() -> deleteAllFrom(EndpointWebhook.class))
                .chain(() -> deleteAllFrom(Endpoint.class))
                .chain(() -> deleteAllFrom(EventType.class))
                .chain(() -> deleteAllFrom(Application.class))
                .chain(() -> deleteAllFrom(Bundle.class))
        ).await().indefinitely();
    }

    private Uni<Integer> deleteAllFrom(Class<?> classname) {
        return session.createQuery("DELETE FROM " + classname.getSimpleName()).executeUpdate();
    }
}
