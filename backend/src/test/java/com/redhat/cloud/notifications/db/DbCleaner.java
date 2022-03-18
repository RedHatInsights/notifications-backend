package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.BundleRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.BehaviorGroupAction;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeBehavior;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.Status;
import com.redhat.cloud.notifications.models.WebhookProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class DbCleaner {

    private static final List<Class<?>> ENTITIES = List.of(
            EmailSubscription.class,
            NotificationHistory.class,
            Event.class,
            BehaviorGroupAction.class,
            WebhookProperties.class,
            Endpoint.class,
            EventTypeBehavior.class,
            BehaviorGroup.class,
            EventType.class,
            Application.class,
            Bundle.class
    );
    private static final String DEFAULT_BUNDLE_NAME = "rhel";
    private static final String DEFAULT_BUNDLE_DISPLAY_NAME = "Red Hat Enterprise Linux";
    private static final String DEFAULT_APP_NAME = "policies";
    private static final String DEFAULT_APP_DISPLAY_NAME = "Policies";
    private static final String DEFAULT_EVENT_TYPE_NAME = "policy-triggered";
    private static final String DEFAULT_EVENT_TYPE_DISPLAY_NAME = "Policy triggered";
    private static final String DEFAULT_EVENT_TYPE_DESCRIPTION = "Matching policy";

    @Inject
    EntityManager entityManager;

    @Inject
    BundleRepository bundleRepository;

    @Inject
    ApplicationRepository applicationRepository;

    /**
     * Deletes all records from all database tables (except for flyway_schema_history) and restores the default records.
     * This method should be called from a method annotated with <b>both</b> {@link BeforeEach} and {@link AfterEach} in
     * all test classes that involve SQL queries to guarantee the tests isolation in terms of stored data.
     */
    @Transactional
    public void clean() {
        for (Class<?> entity : ENTITIES) {
            entityManager.createQuery("DELETE FROM " + entity.getSimpleName()).executeUpdate();
        }

        Bundle bundle = new Bundle(DEFAULT_BUNDLE_NAME, DEFAULT_BUNDLE_DISPLAY_NAME);
        bundleRepository.createBundle(bundle);

        Application app = new Application();
        app.setBundleId(bundle.getId());
        app.setName(DEFAULT_APP_NAME);
        app.setDisplayName(DEFAULT_APP_DISPLAY_NAME);
        applicationRepository.createApp(app);

        EventType eventType = new EventType();
        eventType.setApplicationId(app.getId());
        eventType.setName(DEFAULT_EVENT_TYPE_NAME);
        eventType.setDisplayName(DEFAULT_EVENT_TYPE_DISPLAY_NAME);
        eventType.setDescription(DEFAULT_EVENT_TYPE_DESCRIPTION);
        applicationRepository.createEventType(eventType);

        entityManager.createQuery("UPDATE CurrentStatus SET status = :status")
                .setParameter("status", Status.UP)
                .executeUpdate();
    }
}
