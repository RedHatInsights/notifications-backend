package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.BehaviorGroupRepository;
import com.redhat.cloud.notifications.db.repositories.BundleRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.NotificationStatus;
import com.redhat.cloud.notifications.models.WebhookProperties;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

@ApplicationScoped
@Transactional
public class ResourceHelpers {

    public static final String TEST_APP_NAME = "tester";
    public static final String TEST_APP_NAME_2 = "myothertester";
    public static final String TEST_EVENT_TYPE_FORMAT = "eventtype%d";
    public static final String TEST_BUNDLE_NAME = "testbundle";
    public static final String TEST_BUNDLE_2_NAME = "testbundle2";

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    BundleRepository bundleRepository;

    @Inject
    BehaviorGroupRepository behaviorGroupRepository;

    @Inject
    EntityManager entityManager;

    public Bundle createBundle() {
        return createBundle("name", "displayName");
    }

    public Bundle createBundle(String name, String displayName) {
        Bundle bundle = new Bundle(name, displayName);
        return bundleRepository.createBundle(bundle);
    }

    public UUID getBundleId(String bundleName) {
        return bundleRepository.getBundle(bundleName)
                .getId();
    }

    public Application createApplication(UUID bundleId) {
        return createApplication(bundleId, "name", "displayName");
    }

    public Application createApplication(UUID bundleId, String name, String displayName) {
        Application app = new Application();
        app.setBundleId(bundleId);
        app.setName(name);
        app.setDisplayName(displayName);
        return applicationRepository.createApp(app);
    }

    public UUID createEventType(String bundleName, String applicationName, String eventTypeName) {
        Application app = applicationRepository.getApplication(bundleName, applicationName);
        return createEventType(app.getId(), eventTypeName, eventTypeName, "new event type")
                .getId();
    }

    public EventType createEventType(UUID applicationId, String name, String displayName, String description) {
        EventType eventType = new EventType();
        eventType.setName(name);
        eventType.setDisplayName(displayName);
        eventType.setDescription(description);
        eventType.setApplicationId(applicationId);
        return applicationRepository.createEventType(eventType);
    }

    public UUID createTestAppAndEventTypes() {
        Bundle bundle = createBundle(TEST_BUNDLE_NAME, "...");
        Application app1 = createApplication(bundle.getId(), TEST_APP_NAME, "...");
        for (int i = 0; i < 100; i++) {
            String name = String.format(TEST_EVENT_TYPE_FORMAT, i);
            String displayName = "... -> " + i;
            String description = "Desc .. --> " + i;
            createEventType(app1.getId(), name, displayName, description);
        }
        Application app2 = createApplication(bundle.getId(), TEST_APP_NAME_2, "...");
        for (int i = 0; i < 100; i++) {
            String name = String.format(TEST_EVENT_TYPE_FORMAT, i);
            String displayName = "... -> " + i;
            createEventType(app2.getId(), name, displayName, null);
        }
        return bundle.getId();
    }

    public Endpoint getEndpoint(String orgId, UUID id) {
        return endpointRepository.getEndpoint(orgId, id);
    }

    public Endpoint createEndpoint(String accountId, String orgId, EndpointType type) {
        return createEndpoint(accountId, orgId, type, "null");
    }

    public Endpoint createEndpoint(String accountId, String orgId, EndpointType type, String subType) {
        return createEndpoint(accountId, orgId, type, subType, "name", "description", null, FALSE, null);
    }

    public UUID createWebhookEndpoint(String accountId, String orgId) {
        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(HttpType.POST);
        properties.setUrl("https://localhost");
        String name = "Endpoint " + UUID.randomUUID();
        return createEndpoint(accountId, orgId, WEBHOOK, null, name, "Automatically generated", properties, TRUE, null)
                .getId();
    }

    public Endpoint createEndpoint(String accountId, String orgId, EndpointType type, String subType, String name, String description, EndpointProperties properties, Boolean enabled) {
        return createEndpoint(accountId, orgId, type, subType, name, description, properties, enabled, null);
    }

    public Endpoint createEndpoint(String accountId, String orgId, EndpointType type, String subType, String name, String description, EndpointProperties properties, Boolean enabled, LocalDateTime created) {
        Endpoint endpoint = new Endpoint();
        endpoint.setAccountId(accountId);
        endpoint.setOrgId(orgId);
        endpoint.setType(type);
        endpoint.setSubType(subType);
        endpoint.setName(name);
        endpoint.setDescription(description);
        endpoint.setProperties(properties);
        endpoint.setEnabled(enabled);
        endpoint.setCreated(created);
        return endpointRepository.createEndpoint(endpoint);
    }

    public int[] createTestEndpoints(String accountId, String orgId, int count) {
        int[] statsValues = new int[3];
        statsValues[0] = count;
        for (int i = 0; i < count; i++) {
            // Add new endpoints
            WebhookProperties properties = new WebhookProperties();
            properties.setMethod(HttpType.POST);
            properties.setUrl("https://localhost");

            Endpoint ep = new Endpoint();
            ep.setType(WEBHOOK);
            ep.setName(String.format("Endpoint %d", count - i));
            ep.setDescription("Automatically generated");
            boolean enabled = (i % (count / 5)) != 0;
            if (!enabled) {
                statsValues[1]++;
            }
            ep.setEnabled(enabled);
            if (i > 0) {
                statsValues[2]++;
                ep.setProperties(properties);
            }

            ep.setAccountId(accountId);
            ep.setOrgId(orgId);
            endpointRepository.createEndpoint(ep);
        }
        return statsValues;
    }

    public NotificationHistory createNotificationHistory(Event event, Endpoint endpoint, NotificationStatus status) {
        NotificationHistory history = new NotificationHistory();
        history.setId(UUID.randomUUID());
        history.setInvocationTime(1L);
        history.setStatus(status);
        history.setEvent(event);
        history.setEndpoint(endpoint);
        history.setEndpointType(endpoint.getType());
        history.setEndpointSubType(endpoint.getSubType());
        history.prePersist();
        entityManager.persist(history);
        return history;
    }

    public BehaviorGroup getBehaviorGroup(UUID behaviorGroupId) {
        return entityManager.find(BehaviorGroup.class, behaviorGroupId);
    }

    public BehaviorGroup createBehaviorGroup(String accountId, String orgId, String displayName, UUID bundleId) {
        return createBehaviorGroup(accountId, orgId, displayName, bundleId, null);
    }

    public BehaviorGroup createBehaviorGroup(String accountId, String orgId, String displayName, UUID bundleId, LocalDateTime created) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setDisplayName(displayName);
        behaviorGroup.setBundleId(bundleId);
        behaviorGroup.setCreated(created);
        return behaviorGroupRepository.create(accountId, orgId, behaviorGroup);
    }

    public BehaviorGroup createDefaultBehaviorGroup(String displayName, UUID bundleId) {
        BehaviorGroup behaviorGroup = new BehaviorGroup();
        behaviorGroup.setDisplayName(displayName);
        behaviorGroup.setBundleId(bundleId);
        return behaviorGroupRepository.createDefault(behaviorGroup);
    }

    public List<EventType> findEventTypesByBehaviorGroupId(String orgId, UUID behaviorGroupId) {
        return behaviorGroupRepository.findEventTypesByBehaviorGroupId(orgId, behaviorGroupId);
    }

    public List<EventType> findEventTypesByBehaviorGroupId(UUID behaviorGroupId) {
        return findEventTypesByBehaviorGroupId(DEFAULT_ORG_ID, behaviorGroupId);
    }

    public List<Endpoint> findEndpointsByBehaviorGroupId(String orgId, UUID behaviorGroupId) {
        String query = "SELECT bga.endpoint FROM BehaviorGroupAction bga WHERE bga.endpoint.orgId = :orgId AND bga.behaviorGroup.id = :behaviorGroupId ORDER BY bga.endpoint.created";
        return entityManager.createQuery(query, Endpoint.class)
                .setParameter("orgId", orgId)
                .setParameter("behaviorGroupId", behaviorGroupId)
                .getResultList();
    }

    public List<BehaviorGroup> findBehaviorGroupsByEventTypeId(UUID eventTypeId) {
        return behaviorGroupRepository.findBehaviorGroupsByEventTypeId(DEFAULT_ORG_ID, eventTypeId, new Query());
    }

    public List<BehaviorGroup> findBehaviorGroupsByEndpointId(UUID endpointId) {
        return behaviorGroupRepository.findBehaviorGroupsByEndpointId(DEFAULT_ORG_ID, endpointId);
    }

    public void updateBehaviorGroup(BehaviorGroup behaviorGroup) {
        behaviorGroupRepository.update(DEFAULT_ORG_ID, behaviorGroup);
    }

    public Boolean deleteBehaviorGroup(UUID behaviorGroupId) {
        return behaviorGroupRepository.delete(DEFAULT_ORG_ID, behaviorGroupId);
    }

    public void updateDefaultBehaviorGroup(BehaviorGroup behaviorGroup) {
        behaviorGroupRepository.updateDefault(behaviorGroup);
    }

    public Boolean deleteDefaultBehaviorGroup(UUID behaviorGroupId) {
        return behaviorGroupRepository.deleteDefault(behaviorGroupId);
    }
}
