package com.redhat.cloud.notifications.db;

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
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;

import java.security.SecureRandom;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;

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
        entityManager.persist(event);
        return event;
    }

    @Transactional
    public Event createEvent(Event event) {
        event.setOrgId(DEFAULT_ORG_ID);
        event.setAccountId("account-id");
        event.setEventTypeDisplayName(event.getEventType().getDisplayName());
        event.setApplicationId(event.getEventType().getApplication().getId());
        event.setApplicationDisplayName(event.getEventType().getApplication().getDisplayName());
        event.setBundleId(event.getEventType().getApplication().getBundle().getId());
        event.setBundleDisplayName(event.getEventType().getApplication().getBundle().getDisplayName());
        entityManager.persist(event);
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
}
