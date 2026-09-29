package com.redhat.cloud.notifications.helpers;

import com.redhat.cloud.notifications.models.AggregationOrgConfig;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SubscriptionType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.RandomStringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
public class ResourceHelpers extends com.redhat.cloud.notifications.models.ResourceHelpers {

    @Inject
    EntityManager entityManager;

    @Override
    protected EntityManager getEntityManager() {
        return entityManager;
    }

    @Transactional
    public void addAggregationOrgConfig(AggregationOrgConfig aggregationOrgConfig) {
        entityManager.persist(aggregationOrgConfig);
    }

    public AggregationOrgConfig findAggregationOrgConfigByOrgId(String orgId) {
        entityManager.clear();
        return entityManager.createQuery("SELECT acp FROM AggregationOrgConfig acp WHERE acp.id.orgId =:orgId AND acp.id.subscriptionType = 'DAILY'", AggregationOrgConfig.class)
                .setParameter("orgId", orgId)
                .getSingleResult();
    }

    @Transactional
    public void purgeAggregationOrgConfig() {
        entityManager.createQuery("DELETE FROM AggregationOrgConfig").executeUpdate();
        entityManager.clear();
    }

    @Transactional
    public void purgeEndpoints() {
        entityManager.createQuery("DELETE FROM Endpoint").executeUpdate();
        entityManager.clear();
    }

    @Transactional
    public void purgeEventAggregations() {
        entityManager.createQuery("DELETE FROM Event").executeUpdate();
    }

    @Transactional
    public Endpoint getOrCreateEmailEndpointAndLinkItToEventType(final String orgId, final EventType eventType, boolean useSystemEndpoint) {
        Endpoint emailEndpoint;
        try {
            final String query = "FROM Endpoint WHERE orgId = :orgId AND compositeType.type = :type";
            emailEndpoint = entityManager.createQuery(query, Endpoint.class)
                .setParameter("orgId", useSystemEndpoint ? null : orgId)
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

    public Event addEventEmailAggregation(String orgId, String bundleName, String applicationName, LocalDateTime created, String eventPayload, boolean useSystemEndpoint) {
        findOrCreateBundle(bundleName);
        Application application = findOrCreateApplication(bundleName, applicationName);
        EventType eventType = findOrCreateEventType(application.getId(), "event_type_test");
        findOrCreateEventTypeEmailSubscription(orgId, "obiwan", eventType, SubscriptionType.DAILY);

        getOrCreateEmailEndpointAndLinkItToEventType(orgId, eventType, useSystemEndpoint);

        Event event = new Event();
        event.setOrgId(orgId);
        eventType.setApplication(application);
        event.setEventType(eventType);
        event.setCreated(created);
        event.setPayload(eventPayload);

        return createEvent(event);
    }
}
