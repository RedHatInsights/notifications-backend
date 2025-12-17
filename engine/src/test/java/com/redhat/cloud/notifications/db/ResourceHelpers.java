package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscription;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscriptionId;
import com.redhat.cloud.notifications.models.SubscriptionType;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static java.time.ZoneOffset.UTC;

@ApplicationScoped
public class ResourceHelpers {

    @Inject
    EntityManager entityManager;

    public Bundle findBundle(String name) {
        return entityManager.createQuery("FROM Bundle WHERE name = :name", Bundle.class)
            .setParameter("name", name)
            .getSingleResult();
    }

    public Application findApp(String bundleName, String appName) {
        return entityManager.createQuery("FROM Application WHERE name = :appName AND bundle.name = :bundleName", Application.class)
            .setParameter("appName", appName)
            .setParameter("bundleName", bundleName)
            .getSingleResult();
    }

    @Transactional
    public void deleteApp(String bundleName, String appName) {
        entityManager.createQuery("DELETE FROM Application WHERE name = :appName AND bundle.name = :bundleName")
            .setParameter("appName", appName)
            .setParameter("bundleName", bundleName)
            .executeUpdate();
    }

    @Transactional
    public void deleteBundle(String bundleName) {
        entityManager.createQuery("DELETE FROM Bundle WHERE name = :bundleName")
            .setParameter("bundleName", bundleName)
            .executeUpdate();
    }

    public Bundle createBundle(String bundleName) {
        return createBundle(bundleName, "A bundle");
    }

    @Transactional
    public Bundle createBundle(String bundleName, String bundleDisplayName) {
        Bundle bundle = new Bundle(bundleName, bundleDisplayName);
        entityManager.persist(bundle);
        return bundle;
    }

    @Transactional
    public Application createApp(UUID bundleId, String appName) {
        Application app = new Application();
        app.setBundle(entityManager.find(Bundle.class, bundleId));
        app.setBundleId(bundleId);
        app.setName(appName);
        app.setDisplayName("The best app in the life");
        entityManager.persist(app);
        return app;
    }

    @Transactional
    public EventType createEventType(UUID appId, String eventTypeName) {
        EventType eventType = new EventType();
        eventType.setApplication(entityManager.find(Application.class, appId));
        eventType.setApplicationId(appId);
        eventType.setName(eventTypeName);
        eventType.setDisplayName("Policies will take care of the rules");
        eventType.setDescription("Policies is super cool, you should use it");
        eventType.setDefaultSeverity(Severity.MODERATE);
        eventType.setAvailableSeverities(Set.of(Severity.values()));
        entityManager.persist(eventType);
        return eventType;
    }

    public EventType findEventType(UUID appId, String eventTypeName) {
        String hql = "FROM EventType WHERE application.id = :applicationId AND name = :name";
        return entityManager.createQuery(hql, EventType.class)
                .setParameter("applicationId", appId)
                .setParameter("name", eventTypeName)
                .getSingleResult();
    }

    public Event createEvent(EventType eventType) {
        return createEvent(eventType, DEFAULT_ORG_ID, null, null);
    }

    @Transactional
    public Event createEvent(EventType eventType, String orgId, LocalDateTime created, String payload) {
        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setAccountId("account-id");
        event.setOrgId(orgId);
        event.setEventType(eventType);
        event.setEventTypeDisplayName(eventType.getDisplayName());
        event.setApplicationId(eventType.getApplication().getId());
        event.setApplicationDisplayName(eventType.getApplication().getDisplayName());
        event.setBundleId(eventType.getApplication().getBundle().getId());
        event.setBundleDisplayName(eventType.getApplication().getBundle().getDisplayName());
        if (created != null) {
            event.setCreated(created);
        }
        event.setPayload(payload);
        entityManager.persist(event);
        return event;
    }

    @Transactional
    public Event createEvent(Event event) {
        if (null == event.getOrgId()) {
            event.setOrgId(DEFAULT_ORG_ID);
        }
        event.setAccountId("account-id");
        event.setEventTypeDisplayName(event.getEventType().getDisplayName());
        event.setApplicationId(event.getEventType().getApplication().getId());
        event.setApplicationDisplayName(event.getEventType().getApplication().getDisplayName());
        event.setBundleId(event.getEventType().getApplication().getBundle().getId());
        event.setBundleDisplayName(event.getEventType().getApplication().getBundle().getDisplayName());
        entityManager.persist(event);
        entityManager.flush();
        return event;
    }

    public Endpoint createEndpoint(final EndpointType type, final String subType, final boolean enabled, final int serverErrors) {
        return this.createEndpoint(null, type, subType, enabled, serverErrors);
    }

    @Transactional
    public Endpoint createEndpoint(final String orgId, final EndpointType type, final String subType, final boolean enabled, final int serverErrors) {
        Endpoint endpoint = new Endpoint();
        if (orgId != null) {
            endpoint.setOrgId(orgId);
        }
        endpoint.setType(type);
        endpoint.setSubType(subType);
        endpoint.setName("endpoint-" + new SecureRandom().nextInt());
        endpoint.setDescription("Endpoint description");
        endpoint.setEnabled(enabled);
        endpoint.setServerErrors(serverErrors);
        entityManager.persist(endpoint);
        return endpoint;
    }

    public EventTypeEmailSubscription createEventTypeEmailSubscription(String orgId, String userId, EventType eventType, SubscriptionType subscriptionType) {
        return createEventTypeEmailSubscription(orgId, userId, eventType, subscriptionType, null);
    }

    @Transactional
    public EventTypeEmailSubscription createEventTypeEmailSubscription(String orgId, String userId, EventType eventType, SubscriptionType subscriptionType, Map<Severity, Boolean> severities) {
        EventTypeEmailSubscription eventTypeEmailSubscription = new EventTypeEmailSubscription();
        eventTypeEmailSubscription.setId(
            new EventTypeEmailSubscriptionId(orgId, userId, eventType.getId(), subscriptionType)
        );
        eventTypeEmailSubscription.setEventType(entityManager.find(EventType.class, eventType.getId()));
        eventTypeEmailSubscription.setSubscribed(true);
        eventTypeEmailSubscription.setSeverities(severities);
        entityManager.persist(eventTypeEmailSubscription);
        return eventTypeEmailSubscription;
    }

    @Transactional
    public void deleteEventTypeEmailSubscription(String orgId, String userId, EventType eventType, SubscriptionType subscriptionType) {
        EventTypeEmailSubscriptionId eventTypeEmailSubscriptionId = new EventTypeEmailSubscriptionId(orgId, userId, eventType.getId(), subscriptionType);
        entityManager.createQuery("DELETE FROM EventTypeEmailSubscription WHERE id = :id").setParameter("id", eventTypeEmailSubscriptionId).executeUpdate();
    }

    @Transactional
    public void deleteEndpoint(UUID id) {
        entityManager.createQuery("DELETE FROM Endpoint WHERE id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }

    public Bundle findOrCreateBundle(String bundleName) {
        try {
            return findBundle(bundleName);
        } catch (NoResultException nre) {
            return createBundle(bundleName);
        }
    }

    public Application findOrCreateApplication(String bundleName, String appName) {
        Bundle bundle = findOrCreateBundle(bundleName);

        try {
            return findApp(bundleName, appName);
        } catch (NoResultException nre) {
            return createApp(bundle.getId(), appName);
        }
    }

    public EventType findOrCreateEventType(UUID applicationId, String eventTypeName) {
        try {
            return findEventType(applicationId, eventTypeName);
        } catch (NoResultException nre) {
            return createEventType(applicationId, eventTypeName);
        }
    }

    @Transactional
    public void refreshEndpointLinksToEventType(String orgId, List<UUID> endpointsList) {
        if (endpointsList == null || endpointsList.size() == 0) {
            return;
        }

        String deleteQueryStr = "DELETE FROM EndpointEventType eet WHERE " +
            "endpoint.orgId " + (orgId == null ? "is null " : "= :orgId ") +
            "and id.endpointId in (:endpointList)";

        jakarta.persistence.Query deleteQuery = entityManager.createQuery(deleteQueryStr)
            .setParameter("endpointList", endpointsList);
        if (orgId != null) {
            deleteQuery.setParameter("orgId", orgId);
        }
        deleteQuery.executeUpdate();

        String insertQueryStr = "INSERT INTO EndpointEventType (eventType, endpoint) " +
            "SELECT DISTINCT etb.eventType, bga.endpoint " +
            "from EventTypeBehavior etb inner join BehaviorGroupAction bga on etb.behaviorGroup.id = bga.behaviorGroup.id where " +
            "bga.endpoint.orgId " + (orgId == null ? "is null " : "=: orgId ") +
            "and bga.endpoint.id in (:endpointList)";

        jakarta.persistence.Query insertQuery = entityManager.createQuery(insertQueryStr)
            .setParameter("endpointList", endpointsList);
        if (orgId != null) {
            insertQuery.setParameter("orgId", orgId);
        }
        insertQuery.executeUpdate();
    }

    public void refreshEndpointLinksToEventTypeFromBehaviorGroup(String orgId, Set<UUID> behaviorGroupIds) {
        refreshEndpointLinksToEventType(orgId, findEndpointsByBehaviorGroupId(orgId, behaviorGroupIds));
    }

    public List<UUID> findEndpointsByBehaviorGroupId(String orgId, Set<UUID> behaviorGroupIds) {
        String query = "SELECT bga.endpoint.id FROM BehaviorGroupAction bga WHERE bga.behaviorGroup.id in (:behaviorGroupIds) AND " +
            " bga.endpoint.orgId " + (orgId == null ? "is null " : "= :orgId ");

        TypedQuery<UUID> selectQuery = entityManager.createQuery(query, UUID.class)
            .setParameter("behaviorGroupIds", behaviorGroupIds);
        if (orgId != null) {
            selectQuery.setParameter("orgId", orgId);
        }

        return selectQuery.getResultList();
    }

    @Transactional
    public EventTypeEmailSubscription findOrCreateEventTypeEmailSubscription(String orgId, String userId, EventType eventType, SubscriptionType subscriptionType) {

        EventTypeEmailSubscriptionId subscriptionId = new EventTypeEmailSubscriptionId(orgId, userId, eventType.getId(), subscriptionType);
        EventTypeEmailSubscription eventTypeEmailSubscription = entityManager.find(EventTypeEmailSubscription.class, subscriptionId);
        if (eventTypeEmailSubscription == null) {
            eventTypeEmailSubscription = new EventTypeEmailSubscription();
            eventTypeEmailSubscription.setId(
                new EventTypeEmailSubscriptionId(orgId, userId, eventType.getId(), subscriptionType)
            );
            eventTypeEmailSubscription.setEventType(entityManager.find(EventType.class, eventType.getId()));
            eventTypeEmailSubscription.setSubscribed(true);
            entityManager.persist(eventTypeEmailSubscription);
        }
        return eventTypeEmailSubscription;
    }

    public com.redhat.cloud.notifications.models.Event addEventEmailAggregation(String orgId, String bundleName, String applicationName, JsonObject payload) {
        return addEventEmailAggregation(orgId, bundleName, applicationName, payload, true);
    }

    public com.redhat.cloud.notifications.models.Event addEventEmailAggregation(String orgId, String bundleName, String applicationName, JsonObject payload, boolean addUserSubscription) {
        Application application = findOrCreateApplication(bundleName, applicationName);
        EventType eventType = findOrCreateEventType(application.getId(), TestHelpers.eventType);
        if (addUserSubscription) {
            findOrCreateEventTypeEmailSubscription(orgId, "obiwan", eventType, SubscriptionType.DAILY);
        }

        com.redhat.cloud.notifications.models.Event event = new com.redhat.cloud.notifications.models.Event();
        event.setId(UUID.randomUUID());
        event.setOrgId(orgId);
        eventType.setApplication(application);
        event.setEventType(eventType);
        event.setPayload(payload.toString());
        event.setCreated(LocalDateTime.now(UTC));

        Event retevent = createEvent(event);

        return retevent;
    }

    @Transactional
    public void clearEvents() {
        entityManager.createQuery("DELETE FROM Event")
            .executeUpdate();
    }
}
