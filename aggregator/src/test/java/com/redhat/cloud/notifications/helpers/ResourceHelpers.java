package com.redhat.cloud.notifications.helpers;

import com.redhat.cloud.notifications.models.AggregationOrgConfig;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.DigestTriggerOrgConfig;
import com.redhat.cloud.notifications.models.DigestTriggerOrgConfigId;
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
    public void addDigestTriggerOrgConfig(DigestTriggerOrgConfig digestTriggerOrgConfig) {
        entityManager.persist(digestTriggerOrgConfig);
    }

    public DigestTriggerOrgConfig findDigestTriggerOrgConfig(String orgId, SubscriptionType subscriptionType) {
        entityManager.clear();
        DigestTriggerOrgConfigId id = new DigestTriggerOrgConfigId(orgId, subscriptionType);
        return entityManager.find(DigestTriggerOrgConfig.class, id);
    }

    @Transactional
    public void purgeDigestTriggerOrgConfig() {
        entityManager.createQuery("DELETE FROM DigestTriggerOrgConfig").executeUpdate();
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

    public Event addEventEmailAggregationWithSubscriptionType(String orgId, String bundleName, String applicationName, LocalDateTime created, String eventPayload, boolean useSystemEndpoint, SubscriptionType subscriptionType) {
        findOrCreateBundle(bundleName);
        Application application = findOrCreateApplication(bundleName, applicationName);
        EventType eventType = findOrCreateEventType(application.getId(), "event_type_test");
        findOrCreateEventTypeEmailSubscription(orgId, "obiwan", eventType, subscriptionType);

        getOrCreateEmailEndpointAndLinkItToEventType(orgId, eventType, useSystemEndpoint);

        Event event = new Event();
        event.setOrgId(orgId);
        eventType.setApplication(application);
        event.setEventType(eventType);
        event.setCreated(created);
        event.setPayload(eventPayload);

        return createEvent(event);
    }

    /*
     * The methods below are copied from the engine's ResourceHelpers, for the copied aggregation tests.
     */
    public com.redhat.cloud.notifications.models.EventTypeEmailSubscription createEventTypeEmailSubscription(String orgId, String userId, EventType eventType, SubscriptionType subscriptionType) {
        return createEventTypeEmailSubscription(orgId, userId, eventType, subscriptionType, null);
    }

    @Transactional
    public com.redhat.cloud.notifications.models.EventTypeEmailSubscription createEventTypeEmailSubscription(String orgId, String userId, EventType eventType, SubscriptionType subscriptionType, java.util.Map<com.redhat.cloud.notifications.Severity, Boolean> severities) {
        com.redhat.cloud.notifications.models.EventTypeEmailSubscription eventTypeEmailSubscription = new com.redhat.cloud.notifications.models.EventTypeEmailSubscription();
        eventTypeEmailSubscription.setId(
            new com.redhat.cloud.notifications.models.EventTypeEmailSubscriptionId(orgId, userId, eventType.getId(), subscriptionType)
        );
        eventTypeEmailSubscription.setEventType(entityManager.find(EventType.class, eventType.getId()));
        eventTypeEmailSubscription.setSubscribed(true);
        eventTypeEmailSubscription.setSeverities(severities);
        entityManager.persist(eventTypeEmailSubscription);
        return eventTypeEmailSubscription;
    }

    public Event addEventEmailAggregation(String orgId, String bundleName, String applicationName, io.vertx.core.json.JsonObject payload, boolean addUserSubscription, com.redhat.cloud.notifications.Severity severity, String eventTypeName) {
        Application application = findOrCreateApplication(bundleName, applicationName);
        EventType eventType = findOrCreateEventType(application.getId(), eventTypeName);
        if (addUserSubscription) {
            findOrCreateEventTypeEmailSubscription(orgId, "obiwan", eventType, SubscriptionType.DAILY);
        }

        Event event = new Event();
        event.setOrgId(orgId);
        eventType.setApplication(application);
        event.setEventType(eventType);
        event.setPayload(payload.toString());
        event.setCreated(LocalDateTime.now(java.time.ZoneOffset.UTC));
        event.setSeverity(severity);

        return createEvent(event);
    }

    @Transactional
    public void clearEmailSubscriptions() {
        entityManager.createQuery("DELETE FROM EventTypeEmailSubscription")
            .executeUpdate();
    }

    @Transactional
    public void clearEvents() {
        entityManager.createQuery("DELETE FROM Event")
            .executeUpdate();
    }

    /**
     * Creates an enabled and READY email subscription endpoint with its properties, linked to the given event type.
     * That is what the aggregation endpoint lookup requires. A null orgId creates a system endpoint.
     */
    @Transactional
    public Endpoint createReadyEmailEndpoint(String orgId, EventType eventType, boolean onlyAdmins) {
        Endpoint endpoint = new Endpoint();
        endpoint.setType(EndpointType.EMAIL_SUBSCRIPTION);
        endpoint.setOrgId(orgId);
        endpoint.setName(RandomStringUtils.secure().nextAlphabetic(10));
        endpoint.setDescription(RandomStringUtils.secure().nextAlphabetic(10));
        endpoint.setEnabled(true);
        endpoint.setStatus(com.redhat.cloud.notifications.models.EndpointStatus.READY);
        endpoint.setEventTypes(new HashSet<>(Set.of(entityManager.find(EventType.class, eventType.getId()))));
        entityManager.persist(endpoint);

        com.redhat.cloud.notifications.models.SystemSubscriptionProperties properties = new com.redhat.cloud.notifications.models.SystemSubscriptionProperties();
        properties.setOnlyAdmins(onlyAdmins);
        properties.setEndpoint(endpoint);
        entityManager.persist(properties);
        return endpoint;
    }
}
