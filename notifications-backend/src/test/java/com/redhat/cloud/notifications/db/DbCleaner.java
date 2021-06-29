package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.BehaviorGroupAction;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointDefault;
import com.redhat.cloud.notifications.models.EndpointTarget;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeBehavior;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.Status;
import com.redhat.cloud.notifications.models.WebhookProperties;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.Path;

import static com.redhat.cloud.notifications.Constants.INTERNAL;

@Path(INTERNAL + "/db_cleaner")
public class DbCleaner {

    private static final String DEFAULT_BUNDLE_NAME = "rhel";
    private static final String DEFAULT_BUNDLE_DISPLAY_NAME = "Red Hat Enterprise Linux";
    private static final String DEFAULT_APP_NAME = "policies";
    private static final String DEFAULT_APP_DISPLAY_NAME = "Policies";
    private static final String DEFAULT_EVENT_TYPE_NAME = "policy-triggered";
    private static final String DEFAULT_EVENT_TYPE_DISPLAY_NAME = "Policy triggered";
    private static final String DEFAULT_EVENT_TYPE_DESCRIPTION = "Matching policy";

    @Inject
    Mutiny.Session session;

    @Inject
    BundleResources bundleResources;

    @Inject
    ApplicationResources appResources;

    /**
     * Deletes all records from all database tables (except for flyway_schema_history) and restores the default records.
     * This method should be called from a method annotated with <b>both</b> {@link BeforeEach} and {@link AfterEach} in
     * all test classes that involve SQL queries to guarantee the tests isolation in terms of stored data. Ideally, we
     * should do that with {@link io.quarkus.test.TestTransaction} but it doesn't work with Hibernate Reactive, so this
     * is a temporary workaround to make our tests more reliable and easy to maintain.
     */
    @DELETE
    public Uni<Void> clean() {
        return session.withTransaction(transaction -> deleteAllFrom(EmailAggregation.class)
                .chain(() -> deleteAllFrom(EmailSubscription.class))
                .chain(() -> deleteAllFrom(NotificationHistory.class))
                .chain(() -> deleteAllFrom(EndpointDefault.class)) // TODO [BG Phase 3] Delete this line
                .chain(() -> deleteAllFrom(EndpointTarget.class)) // TODO [BG Phase 3] Delete this line
                .chain(() -> deleteAllFrom(BehaviorGroupAction.class))
                .chain(() -> deleteAllFrom(WebhookProperties.class))
                .chain(() -> deleteAllFrom(Endpoint.class))
                .chain(() -> deleteAllFrom(EventTypeBehavior.class))
                .chain(() -> deleteAllFrom(BehaviorGroup.class))
                .chain(() -> deleteAllFrom(EventType.class))
                .chain(() -> deleteAllFrom(Application.class))
                .chain(() -> deleteAllFrom(Bundle.class))
                .chain(() -> {
                    Bundle bundle = new Bundle(DEFAULT_BUNDLE_NAME, DEFAULT_BUNDLE_DISPLAY_NAME);
                    return bundleResources.createBundle(bundle);
                })
                .onItem().transformToUni(bundle -> {
                    Application app = new Application();
                    app.setBundleId(bundle.getId());
                    app.setName(DEFAULT_APP_NAME);
                    app.setDisplayName(DEFAULT_APP_DISPLAY_NAME);
                    app.setBundleId(bundle.getId());
                    return appResources.createApp(app);
                })
                .onItem().transformToUni(app -> {
                    EventType eventType = new EventType();
                    eventType.setApplicationId(app.getId());
                    eventType.setName(DEFAULT_EVENT_TYPE_NAME);
                    eventType.setDisplayName(DEFAULT_EVENT_TYPE_DISPLAY_NAME);
                    eventType.setDescription(DEFAULT_EVENT_TYPE_DESCRIPTION);
                    return appResources.createEventType(eventType);
                })
                .onItem().transformToUni(ignored ->
                        session.createQuery("UPDATE CurrentStatus SET status = :status")
                                .setParameter("status", Status.UP)
                                .executeUpdate()
                )
                .replaceWith(Uni.createFrom().voidItem())
        );
    }

    private Uni<Integer> deleteAllFrom(Class<?> classname) {
        return session.createQuery("DELETE FROM " + classname.getSimpleName()).executeUpdate();
    }
}
