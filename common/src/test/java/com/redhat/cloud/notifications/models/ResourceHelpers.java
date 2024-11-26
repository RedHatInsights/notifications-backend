package com.redhat.cloud.notifications.models;

import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;

public abstract class ResourceHelpers {

    protected abstract EntityManager getEntityManager();

    @Transactional
    public Bundle createBundle(String bundleName) {
        Bundle bundle = new Bundle(bundleName, "A bundle " + bundleName);
        getEntityManager().persist(bundle);
        return bundle;
    }

    public Bundle findBundle(String name) {
        return getEntityManager().createQuery("FROM Bundle WHERE name = :name", Bundle.class)
            .setParameter("name", name)
            .getSingleResult();
    }

    public Application findApp(String bundleName, String appName) {
        return getEntityManager().createQuery("FROM Application WHERE name = :appName AND bundle.name = :bundleName", Application.class)
            .setParameter("appName", appName)
            .setParameter("bundleName", bundleName)
            .getSingleResult();
    }

    @Transactional
    public Application createApp(UUID bundleId, String appName) {
        Application app = new Application();
        app.setBundle(getEntityManager().find(Bundle.class, bundleId));
        app.setBundleId(bundleId);
        app.setName(appName);
        app.setDisplayName("The best app in the life " + appName);
        getEntityManager().persist(app);
        return app;
    }

    @Transactional
    public void deleteApp(String bundleName, String appName) {
        getEntityManager().createQuery("DELETE FROM Application WHERE name = :appName AND bundle.name = :bundleName")
            .setParameter("appName", appName)
            .setParameter("bundleName", bundleName)
            .executeUpdate();
    }

    @Transactional
    public EventType createEventType(UUID appId, String eventTypeName) {
        EventType eventType = new EventType();
        eventType.setApplication(getEntityManager().find(Application.class, appId));
        eventType.setApplicationId(appId);
        eventType.setName(eventTypeName);
        eventType.setDisplayName("Policies will take care of the rules");
        eventType.setDescription("Policies is super cool, you should use it");
        getEntityManager().persist(eventType);
        return eventType;
    }

    public EventType findEventType(UUID appId, String eventTypeName) {
        String hql = "FROM EventType WHERE application.id = :applicationId AND name = :name";
        return getEntityManager().createQuery(hql, EventType.class)
                .setParameter("applicationId", appId)
                .setParameter("name", eventTypeName)
                .getSingleResult();
    }

    @Transactional
    public Event createEvent(EventType eventType) {
        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setAccountId("account-id");
        event.setOrgId(DEFAULT_ORG_ID);
        event.setEventType(eventType);
        event.setEventTypeDisplayName(eventType.getDisplayName());
        event.setApplicationId(eventType.getApplication().getId());
        event.setApplicationDisplayName(eventType.getApplication().getDisplayName());
        event.setBundleId(eventType.getApplication().getBundle().getId());
        event.setBundleDisplayName(eventType.getApplication().getBundle().getDisplayName());
        getEntityManager().persist(event);
        return event;
    }

    @Transactional
    public Event createEvent(Event event) {
        if (event.getId() == null) {
            event.setOrgId(DEFAULT_ORG_ID);
        }
        event.setAccountId("account-id");
        event.setEventTypeDisplayName(event.getEventType().getDisplayName());
        event.setApplicationId(event.getEventType().getApplication().getId());
        event.setApplicationDisplayName(event.getEventType().getApplication().getDisplayName());
        event.setBundleId(event.getEventType().getApplication().getBundle().getId());
        event.setBundleDisplayName(event.getEventType().getApplication().getBundle().getDisplayName());
        getEntityManager().persist(event);
        return event;
    }

    @Transactional
    public EventTypeEmailSubscription findOrCreateEventTypeEmailSubscription(String orgId, String userId, EventType eventType, SubscriptionType subscriptionType) {

        EventTypeEmailSubscriptionId subscriptionId = new EventTypeEmailSubscriptionId(orgId, userId, eventType.getId(), subscriptionType);
        EventTypeEmailSubscription eventTypeEmailSubscription = getEntityManager().find(EventTypeEmailSubscription.class, subscriptionId);
        if (eventTypeEmailSubscription == null) {
            eventTypeEmailSubscription = new EventTypeEmailSubscription();
            eventTypeEmailSubscription.setId(
                new EventTypeEmailSubscriptionId(orgId, userId, eventType.getId(), subscriptionType)
            );
            eventTypeEmailSubscription.setEventType(getEntityManager().find(EventType.class, eventType.getId()));
            eventTypeEmailSubscription.setSubscribed(true);
            getEntityManager().persist(eventTypeEmailSubscription);
        }
        return eventTypeEmailSubscription;
    }

    @Transactional
    public void deleteEventTypeEmailSubscription(String orgId, String userId, EventType eventType, SubscriptionType subscriptionType) {
        EventTypeEmailSubscriptionId eventTypeEmailSubscriptionId = new EventTypeEmailSubscriptionId(orgId, userId, eventType.getId(), subscriptionType);
        getEntityManager().createQuery("DELETE FROM EventTypeEmailSubscription WHERE id = :id").setParameter("id", eventTypeEmailSubscriptionId).executeUpdate();
    }

    @Transactional
    public void deleteEvent(Event event) {
        getEntityManager().remove(getEntityManager().find(Event.class, event.getId()));
    }

    public Application findOrCreateApplication(String bundleName, String appName) {
        Bundle bundle = null;
        try {
            bundle = findBundle(bundleName);
        } catch (NoResultException nre) {
            bundle = createBundle(bundleName);
        }

        Application app = null;
        try {
            app = findApp(bundleName, appName);
        } catch (NoResultException nre) {
            app = createApp(bundle.getId(), appName);
        }
        return app;
    }

    public EventType findOrCreateEventType(UUID applicationId, String eventTypeName) {
        EventType eventType = null;
        try {
            eventType = findEventType(applicationId, eventTypeName);
        } catch (NoResultException nre) {
            eventType = createEventType(applicationId, eventTypeName);
        }
        return eventType;
    }

}
