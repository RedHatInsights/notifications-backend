package com.redhat.cloud.notifications.helpers;

import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.AggregationOrgConfig;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.models.Template;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.RandomStringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class ResourceHelpers extends com.redhat.cloud.notifications.models.ResourceHelpers {

    @Inject
    EntityManager entityManager;

    @Override
    protected EntityManager getEntityManager() {
        return entityManager;
    }

    public void addEmailAggregation(String orgId, String bundle, String application, String policyId, String insightsId) {
        addEmailAggregation(orgId, bundle, application, policyId, insightsId, LocalDateTime.now(ZoneOffset.UTC));
    }

    public void addEmailAggregation(String orgId, String bundle, String application, String policyId, String insightsId, LocalDateTime localDateTime) {
        EmailAggregation aggregation = TestHelpers.createEmailAggregation(orgId, bundle, application, policyId, insightsId, localDateTime);
        addEmailAggregation(aggregation);
    }

    @Transactional
    public void addAggregationOrgConfig(AggregationOrgConfig aggregationOrgConfig) {
        entityManager.persist(aggregationOrgConfig);
    }

    public AggregationOrgConfig findAggregationOrgConfigByOrgId(String orgId) {
        entityManager.clear();
        return entityManager.createQuery("SELECT acp FROM AggregationOrgConfig acp WHERE acp.orgId =:orgId", AggregationOrgConfig.class)
                .setParameter("orgId", orgId)
                .getSingleResult();
    }

    @Transactional
    public void purgeAggregationOrgConfig() {
        entityManager.createQuery("DELETE FROM AggregationOrgConfig").executeUpdate();
        entityManager.clear();
    }


    @Transactional
    public void addEmailAggregation(EmailAggregation aggregation) {
        entityManager.persist(aggregation);
    }

    @Transactional
    public void purgeEmailAggregations() {
        entityManager.createQuery("DELETE FROM EmailAggregation").executeUpdate();
    }

    @Transactional
    public void purgeEventAggregations() {
        entityManager.createQuery("DELETE FROM Event").executeUpdate();
    }

    @Transactional
    public Endpoint getOrCreateEmailEndpointAndLinkItToEventType(final String orgId, final EventType eventType) {
        Endpoint emailEndpoint;
        try {
            final String query = "FROM Endpoint WHERE orgId = :orgId AND compositeType.type = :type";
            emailEndpoint = entityManager.createQuery(query, Endpoint.class)
                .setParameter("orgId", orgId)
                .setParameter("type", EndpointType.EMAIL_SUBSCRIPTION)
                .getSingleResult();
        } catch (NoResultException e) {
            emailEndpoint = new Endpoint();
            emailEndpoint.setType(EndpointType.EMAIL_SUBSCRIPTION);
            emailEndpoint.setOrgId(orgId);
            emailEndpoint.setName(RandomStringUtils.secure().nextAlphabetic(10));
            emailEndpoint.setDescription(RandomStringUtils.randomAlphabetic(10));
            entityManager.persist(emailEndpoint);
        }

        Set<EventType> linkedEventTypes = emailEndpoint.getEventTypes();
        if (linkedEventTypes == null) {
            linkedEventTypes = new HashSet<>();
        }
        linkedEventTypes.add(eventType);
        emailEndpoint.setEventTypes(linkedEventTypes);
        return entityManager.merge(emailEndpoint);
    }

    @Transactional
    public AggregationEmailTemplate getOrCreateAggregationTemplate(Application application) {
        try {
            String query = "FROM AggregationEmailTemplate WHERE application.id = :appId";
            return entityManager.createQuery(query, AggregationEmailTemplate.class)
                .setParameter("appId", application.getId())
                .getSingleResult();
        } catch (NoResultException e) {
            Template emailTemplate = new Template();
            emailTemplate.setName(RandomStringUtils.randomAlphabetic(10));
            emailTemplate.setData(RandomStringUtils.randomAlphabetic(10));
            emailTemplate.setDescription(RandomStringUtils.randomAlphabetic(10));
            entityManager.persist(emailTemplate);

            AggregationEmailTemplate aggregationEmailTemplate = new AggregationEmailTemplate();
            aggregationEmailTemplate.setApplication(application);
            aggregationEmailTemplate.setApplicationId(application.getId());
            aggregationEmailTemplate.setSubscriptionType(SubscriptionType.DAILY);
            aggregationEmailTemplate.setBodyTemplate(emailTemplate);
            aggregationEmailTemplate.setBodyTemplateId(emailTemplate.getId());
            aggregationEmailTemplate.setSubjectTemplate(emailTemplate);
            aggregationEmailTemplate.setSubjectTemplateId(emailTemplate.getId());
            aggregationEmailTemplate.setApplication(application);
            entityManager.persist(aggregationEmailTemplate);
            return aggregationEmailTemplate;
        }
    }

    public Event addEventEmailAggregation(String orgId, String bundleName, String applicationName, LocalDateTime created, String eventPayload) {
        Application application = findOrCreateApplication(bundleName, applicationName);
        EventType eventType = findOrCreateEventType(application.getId(), "event_type_test");
        findOrCreateEventTypeEmailSubscription(orgId, "obiwan", eventType, SubscriptionType.DAILY);

        getOrCreateEmailEndpointAndLinkItToEventType(orgId, eventType);
        getOrCreateAggregationTemplate(application);

        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setOrgId(orgId);
        eventType.setApplication(application);
        event.setEventType(eventType);
        event.setCreated(created);
        event.setPayload(eventPayload);

        return createEvent(event);
    }
}
