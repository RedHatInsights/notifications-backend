package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.BehaviorGroupRepository;
import com.redhat.cloud.notifications.db.repositories.BundleRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.BasicAuthentication;
import com.redhat.cloud.notifications.models.BehaviorGroup;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.NotificationStatus;
import com.redhat.cloud.notifications.models.Template;
import com.redhat.cloud.notifications.models.WebhookProperties;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
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

    public Bundle createBundle(String name) {
        Bundle bundle = new Bundle(name, "displayName");
        return bundleRepository.createBundle(bundle);
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

    public Application createApplication(UUID bundleId, String name) {
        return createApplication(bundleId, name, "displayName");
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

    public EventType createEventType(UUID applicationId, String name) {
        return createEventType(applicationId, name, "displayName", "description");
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
        return createEndpoint(accountId, orgId, type, type.requiresSubType ? "null" : null);
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
    public InstantEmailTemplate createInstantEmailTemplate(UUID eventTypeId, UUID subjectTemplateId, UUID bodyTemplateId) {
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
    public AggregationEmailTemplate createAggregationEmailTemplate(UUID appId, UUID subjectTemplateId, UUID bodyTemplateId) {
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
    public void deleteEmailTemplatesById(UUID templateId) {
        entityManager.createQuery("DELETE FROM InstantEmailTemplate WHERE id = :id").setParameter("id", templateId).executeUpdate();
        entityManager.createQuery("DELETE FROM AggregationEmailTemplate WHERE id = :id").setParameter("id", templateId).executeUpdate();
    }


    /**
     * Generates twelve endpoints with either {@link CamelProperties} or
     * {@link WebhookProperties} that shouldn't be picked up by
     * {@link EndpointRepository#findEndpointWithPropertiesWithStoredSecrets()},
     * because even though the properties have credentials stored in the
     * database, they also contain the references to Sources secrets.
     */
    @Deprecated(forRemoval = true)
    @Transactional
    public void createTwelveEndpointFixtures() {
        final Random random = new Random();

        // Create a few endpoints that shouldn't be picked by the function
        // under test, because they contain references to Sources secrets.
        for (int i = 0; i < 12; i++) {
            final Endpoint endpoint = new Endpoint();
            endpoint.setDescription(UUID.randomUUID().toString());
            endpoint.setName(UUID.randomUUID().toString());
            endpoint.setOrgId(DEFAULT_ORG_ID);

            // Should it be a camel, or a webhook endpoint?
            if (i % 2 == 0) {
                endpoint.setType(EndpointType.CAMEL);
                endpoint.setSubType("dromedary");

                final CamelProperties camelProperties = new CamelProperties();
                camelProperties.setDisableSslVerification(random.nextBoolean());
                camelProperties.setUrl("https://example.org");

                // Maybe set a basic authentication, maybe not...
                if (i % 3 == 0) {
                    camelProperties.setBasicAuthentication(
                        new BasicAuthentication(
                            UUID.randomUUID().toString(),
                            UUID.randomUUID().toString()
                        )
                    );
                }

                // Maybe set a secret token, maybe not...
                if (i % 3 == 0) {
                    camelProperties.setSecretToken(UUID.randomUUID().toString());
                }

                camelProperties.setBasicAuthenticationSourcesId(random.nextLong());
                camelProperties.setSecretTokenSourcesId(random.nextLong());

                endpoint.setProperties(camelProperties);
            } else {
                endpoint.setType(EndpointType.WEBHOOK);

                final WebhookProperties webhookProperties = new WebhookProperties();
                webhookProperties.setDisableSslVerification(random.nextBoolean());
                webhookProperties.setMethod(HttpType.GET);
                webhookProperties.setUrl("https://example.org");

                // Maybe set a basic authentication, maybe not...
                if (i % 3 == 0) {
                    webhookProperties.setBasicAuthentication(
                        new BasicAuthentication(
                            UUID.randomUUID().toString(),
                            UUID.randomUUID().toString()
                        )
                    );
                }

                // Maybe set a secret token, maybe not...
                if (i % 3 == 0) {
                    webhookProperties.setSecretToken(UUID.randomUUID().toString());
                }

                webhookProperties.setBasicAuthenticationSourcesId(random.nextLong());
                webhookProperties.setSecretTokenSourcesId(random.nextLong());

                endpoint.setProperties(webhookProperties);
            }

            this.endpointRepository.createEndpoint(endpoint);
        }
    }

    /**
     * Creates ten {@link Endpoint} fixtures: five containing
     * {@link CamelProperties} and five containing {@link WebhookProperties}.
     * They are specifically prepared so that the
     * {@link EndpointRepository#findEndpointWithPropertiesWithStoredSecrets()}
     * function picks them up, because the basic authentication and secret
     * token secrets are set, but no reference ID for a Source secret gets set.
     *
     * @return the map of the created endpoints.
     */
    @Deprecated(forRemoval = true)
    @Transactional
    public Map<UUID, Endpoint> createTenEndpointFixtures() {
        final Random random = new Random();

        final Map<UUID, Endpoint> createdEndpoints = new HashMap<>();

        // Create five endpoints with camel properties.
        for (int i = 0; i < 5; i++) {
            final CamelProperties camelProperties = new CamelProperties();
            camelProperties.setDisableSslVerification(random.nextBoolean());
            camelProperties.setUrl("https://example.org");

            camelProperties.setBasicAuthentication(
                new BasicAuthentication(UUID.randomUUID().toString(), UUID.randomUUID().toString())
            );
            camelProperties.setSecretToken(UUID.randomUUID().toString());

            final Endpoint endpoint = new Endpoint();
            endpoint.setDescription(UUID.randomUUID().toString());
            endpoint.setName(UUID.randomUUID().toString());
            endpoint.setProperties(camelProperties);
            endpoint.setType(EndpointType.CAMEL);
            endpoint.setSubType("dromedary");
            endpoint.setOrgId(DEFAULT_ORG_ID);

            this.endpointRepository.createEndpoint(endpoint);

            createdEndpoints.put(endpoint.getId(), endpoint);
        }

        // Create another five endpoints with webhook properties.
        for (int i = 0; i < 5; i++) {
            final WebhookProperties webhookProperties = new WebhookProperties();
            webhookProperties.setDisableSslVerification(random.nextBoolean());
            webhookProperties.setMethod(HttpType.GET);
            webhookProperties.setUrl("https://example.org");

            webhookProperties.setBasicAuthentication(
                new BasicAuthentication(UUID.randomUUID().toString(), UUID.randomUUID().toString())
            );
            webhookProperties.setSecretToken(UUID.randomUUID().toString());

            final Endpoint endpoint = new Endpoint();
            endpoint.setDescription(UUID.randomUUID().toString());
            endpoint.setName(UUID.randomUUID().toString());
            endpoint.setProperties(webhookProperties);
            endpoint.setType(EndpointType.WEBHOOK);
            endpoint.setOrgId(DEFAULT_ORG_ID);

            this.endpointRepository.createEndpoint(endpoint);

            createdEndpoints.put(endpoint.getId(), endpoint);
        }

        return  createdEndpoints;
    }

    /**
     * Generates five endpoints which contain either {@link CamelProperties} or
     * {@link WebhookProperties} properties, but that have either a null
     * or empty {@link BasicAuthentication}, mimicking what current basic
     * authentications look like right now in the database. The properties
     * always contain a secret token. They should be fetchable by
     * {@link EndpointRepository#findEndpointWithPropertiesWithStoredSecrets()}
     * because there are no Source ID secret references set.
     *
     * @return the map of the created endpoints.
     */
    @Deprecated(forRemoval = true)
    @Transactional
    public Map<UUID, Endpoint> createFiveEndpointsNullEmptyBasicAuths() {
        final Random random = new Random();

        final Map<UUID, Endpoint> createdEndpoints = new HashMap<>();

        // Generates an empty basic authentication or a null one, replicating
        // what we currently have in the database.
        final Supplier<BasicAuthentication> getRandomBasicAuth = () -> {
            if (random.nextBoolean()) {
                return new BasicAuthentication("", "");
            } else {
                return new BasicAuthentication(null, null);
            }
        };

        for (int i = 0; i < 5; i++) {
            final Endpoint endpoint = new Endpoint();
            endpoint.setDescription(UUID.randomUUID().toString());
            endpoint.setName(UUID.randomUUID().toString());
            endpoint.setOrgId(DEFAULT_ORG_ID);

            // Should it be a camel, or a webhook endpoint?
            if (i % 3 == 0) {
                endpoint.setType(EndpointType.CAMEL);
                endpoint.setSubType("dromedary");

                final CamelProperties camelProperties = new CamelProperties();
                camelProperties.setBasicAuthentication(getRandomBasicAuth.get());
                camelProperties.setDisableSslVerification(random.nextBoolean());
                camelProperties.setSecretToken(UUID.randomUUID().toString());
                camelProperties.setUrl("https://example.org");

                endpoint.setProperties(camelProperties);
            } else {
                endpoint.setType(EndpointType.WEBHOOK);

                final WebhookProperties webhookProperties = new WebhookProperties();
                webhookProperties.setBasicAuthentication(getRandomBasicAuth.get());
                webhookProperties.setDisableSslVerification(random.nextBoolean());
                webhookProperties.setMethod(HttpType.GET);
                webhookProperties.setSecretToken(UUID.randomUUID().toString());
                webhookProperties.setUrl("https://example.org");

                endpoint.setProperties(webhookProperties);
            }

            this.endpointRepository.createEndpoint(endpoint);

            createdEndpoints.put(endpoint.getId(), endpoint);
        }

        return createdEndpoints;
    }
}
