package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.db.repositories.EmailAggregationRepository;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscription;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscriptionId;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.models.Template;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static java.time.ZoneOffset.UTC;

@ApplicationScoped
public class ResourceHelpers {

    @Inject
    EmailAggregationRepository emailAggregationRepository;

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

    public Boolean addEmailAggregation(String orgId, String bundleName, String applicationName, JsonObject payload) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setOrgId(orgId);
        aggregation.setBundleName(bundleName);
        aggregation.setApplicationName(applicationName);
        aggregation.setPayload(payload);
        return emailAggregationRepository.addEmailAggregation(aggregation);
    }

    @Transactional
    public Bundle createBundle(String bundleName) {
        Bundle bundle = new Bundle(bundleName, "A bundle");
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

    @Transactional
    public Endpoint createEndpoint(EndpointType type, String subType, boolean enabled, int serverErrors) {
        Endpoint endpoint = new Endpoint();
        endpoint.setType(type);
        endpoint.setSubType(subType);
        endpoint.setName("endpoint-" + new SecureRandom().nextInt());
        endpoint.setDescription("Endpoint description");
        endpoint.setEnabled(enabled);
        endpoint.setServerErrors(serverErrors);
        entityManager.persist(endpoint);
        return endpoint;
    }

    @Transactional
    public Template createTemplate(String name, String description, String data) {
        Template template = new Template();
        template.setName(name);
        template.setDescription(description);
        template.setData(data);
        entityManager.persist(template);
        return template;
    }

    @Transactional
    public InstantEmailTemplate createInstantEmailTemplate(UUID eventTypeId, UUID subjectTemplateId, UUID bodyTemplateId, boolean enabled) {
        InstantEmailTemplate instantEmailTemplate = new InstantEmailTemplate();
        instantEmailTemplate.setEventType(entityManager.find(EventType.class, eventTypeId));
        instantEmailTemplate.setEventTypeId(eventTypeId);
        instantEmailTemplate.setSubjectTemplate(entityManager.find(Template.class, subjectTemplateId));
        instantEmailTemplate.setSubjectTemplateId(subjectTemplateId);
        instantEmailTemplate.setBodyTemplate(entityManager.find(Template.class, bodyTemplateId));
        instantEmailTemplate.setBodyTemplateId(bodyTemplateId);
        entityManager.persist(instantEmailTemplate);
        return instantEmailTemplate;
    }

    @Transactional
    public AggregationEmailTemplate createAggregationEmailTemplate(UUID appId, UUID subjectTemplateId, UUID bodyTemplateId, boolean enabled) {
        AggregationEmailTemplate aggregationEmailTemplate = new AggregationEmailTemplate();
        aggregationEmailTemplate.setApplication(entityManager.find(Application.class, appId));
        aggregationEmailTemplate.setApplicationId(appId);
        aggregationEmailTemplate.setSubjectTemplate(entityManager.find(Template.class, subjectTemplateId));
        aggregationEmailTemplate.setSubjectTemplateId(subjectTemplateId);
        aggregationEmailTemplate.setBodyTemplate(entityManager.find(Template.class, bodyTemplateId));
        aggregationEmailTemplate.setBodyTemplateId(bodyTemplateId);
        aggregationEmailTemplate.setSubscriptionType(DAILY);
        entityManager.persist(aggregationEmailTemplate);
        return aggregationEmailTemplate;
    }

    @Transactional
    public EventTypeEmailSubscription createEventTypeEmailSubscription(String orgId, String userId, EventType eventType, SubscriptionType subscriptionType) {
        EventTypeEmailSubscription eventTypeEmailSubscription = new EventTypeEmailSubscription();
        eventTypeEmailSubscription.setId(
            new EventTypeEmailSubscriptionId(orgId, userId, eventType.getId(), subscriptionType)
        );
        eventTypeEmailSubscription.setEventType(entityManager.find(EventType.class, eventType.getId()));
        eventTypeEmailSubscription.setSubscribed(true);
        entityManager.persist(eventTypeEmailSubscription);
        return eventTypeEmailSubscription;
    }

    @Transactional
    public void deleteEventTypeEmailSubscription(String orgId, String userId, EventType eventType, SubscriptionType subscriptionType) {
        EventTypeEmailSubscriptionId eventTypeEmailSubscriptionId = new EventTypeEmailSubscriptionId(orgId, userId, eventType.getId(), subscriptionType);
        entityManager.createQuery("DELETE FROM EventTypeEmailSubscription WHERE id = :id").setParameter("id", eventTypeEmailSubscriptionId).executeUpdate();
    }

    @Transactional
    public void deleteEmailTemplatesById(UUID templateId) {
        entityManager.createQuery("DELETE FROM InstantEmailTemplate WHERE id = :id").setParameter("id", templateId).executeUpdate();
        entityManager.createQuery("DELETE FROM AggregationEmailTemplate WHERE id = :id").setParameter("id", templateId).executeUpdate();
    }

    @Transactional
    public void deleteEndpoint(UUID id) {
        entityManager.createQuery("DELETE FROM Endpoint WHERE id = :id")
                .setParameter("id", id)
                .executeUpdate();
    }

    public AggregationEmailTemplate createBlankAggregationEmailTemplate(String bundleName, String appName) {
        Application app = findOrCreateApplication(bundleName, appName);

        Template blankTemplate = createTemplate("blank_" + UUID.randomUUID(), "test blank template", StringUtils.EMPTY);

        return createAggregationEmailTemplate(app.getId(), blankTemplate.getId(), blankTemplate.getId(), true);
    }

    public InstantEmailTemplate createBlankInstantEmailTemplate(String bundleName, String appName, String eventTypeName) {
        Application app = findOrCreateApplication(bundleName, appName);

        EventType eventType = null;
        try {
            eventType = findEventType(app.getId(), eventTypeName);
        } catch (NoResultException nre) {
            eventType = createEventType(app.getId(), eventTypeName);
        }

        Template blankTemplate = createTemplate("blank_" + UUID.randomUUID(), "test blank template", StringUtils.EMPTY);

        return createInstantEmailTemplate(eventType.getId(), blankTemplate.getId(), blankTemplate.getId(), true);
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

    @Transactional
    public void updateTemplates(Optional<InstantEmailTemplate> instantTemplateToUpdate, Optional<InstantEmailTemplate> instantTemplateToCopy) {
        entityManager.createQuery("UPDATE InstantEmailTemplate SET subjectTemplate = :subjectTemplate, bodyTemplate = :bodyTemplate WHERE id = :id")
            .setParameter("subjectTemplate", entityManager.find(Template.class, instantTemplateToCopy.get().getSubjectTemplate().getId()))
            .setParameter("bodyTemplate", entityManager.find(Template.class, instantTemplateToCopy.get().getBodyTemplate().getId()))
            .setParameter("id", instantTemplateToUpdate.get().getId())
            .executeUpdate();
    }
}
