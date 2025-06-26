package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.PagerDutyProperties;
import com.redhat.cloud.notifications.models.PagerDutySeverity;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.models.WebhookProperties;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.models.EndpointType.ANSIBLE;
import static com.redhat.cloud.notifications.models.EndpointType.CAMEL;
import static com.redhat.cloud.notifications.models.EndpointType.DRAWER;
import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static com.redhat.cloud.notifications.models.EndpointType.PAGERDUTY;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EndpointRepositoryTest {

    private static final int MAX_SERVER_ERRORS = 3;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    EntityManager entityManager;

    @InjectSpy
    EngineConfig engineConfig;

    @BeforeEach
    void setUp() {
        when(engineConfig.getMaxServerErrors()).thenReturn(MAX_SERVER_ERRORS);
    }

    @Test
    void testIncrementEndpointServerErrors() {
        Endpoint endpoint = resourceHelpers.createEndpoint(WEBHOOK, null, true, 0);
        assertTrue(endpoint.isEnabled());
        assertEquals(0, endpoint.getServerErrors());
        when(engineConfig.getMinDelaySinceFirstServerErrorBeforeDisabling()).thenReturn(Duration.ofNanos(1));

        for (int i = 1; i <= MAX_SERVER_ERRORS + 1; i++) {
            assertEquals(i > MAX_SERVER_ERRORS, endpointRepository.incrementEndpointServerErrors(endpoint.getId(), 1));
            entityManager.clear(); // The Hibernate L1 cache contains outdated data and needs to be cleared.
            Endpoint ep = getEndpoint(endpoint.getId());
            assertEquals(i <= MAX_SERVER_ERRORS, ep.isEnabled());
            // The server errors counter is not incremented on the last iteration. The endpoint is disabled instead.
            assertEquals(i <= MAX_SERVER_ERRORS ? i : i - 1, ep.getServerErrors());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 5}) // to test cases when endpoint have already some servers error or not
    void testIncrementEndpointServerErrorsAndWaitForMinDelay(int initialServerErrors) throws InterruptedException {
        Endpoint endpoint = resourceHelpers.createEndpoint(WEBHOOK, null, true, initialServerErrors);
        assertTrue(endpoint.isEnabled());
        assertEquals(initialServerErrors, endpoint.getServerErrors());
        when(engineConfig.getMinDelaySinceFirstServerErrorBeforeDisabling()).thenReturn(Duration.ofSeconds(2));

        for (int i = 1; i <= MAX_SERVER_ERRORS + 10; i++) {
            assertFalse(endpointRepository.incrementEndpointServerErrors(endpoint.getId(), 1));
            entityManager.clear(); // The Hibernate L1 cache contains outdated data and needs to be cleared.
        }

        Endpoint ep = getEndpoint(endpoint.getId());
        assertTrue(ep.isEnabled());
        assertTrue(ep.getServerErrors() > MAX_SERVER_ERRORS);
        Thread.sleep(Duration.ofSeconds(2));

        assertTrue(endpointRepository.incrementEndpointServerErrors(endpoint.getId(), 1));
        entityManager.clear(); // The Hibernate L1 cache contains outdated data and needs to be cleared.

        ep = getEndpoint(endpoint.getId());
        assertFalse(ep.isEnabled());
    }

    @Test
    void testIncrementEndpointServerErrorsWithUnknownId() {
        when(engineConfig.getMinDelaySinceFirstServerErrorBeforeDisabling()).thenReturn(Duration.ofSeconds(1));
        assertFalse(endpointRepository.incrementEndpointServerErrors(UUID.randomUUID(), 1));
    }

    @Test
    void testResetEndpointServerErrorsWithExistingErrors() {
        Endpoint endpoint = resourceHelpers.createEndpoint(WEBHOOK, null, true, 3);
        assertEquals(3, endpoint.getServerErrors());
        assertTrue(endpointRepository.resetEndpointServerErrors(endpoint.getId()), "Endpoints with serverErrors > 0 SHOULD be updated");
        assertEquals(0, getEndpoint(endpoint.getId()).getServerErrors());
    }

    @Test
    void testResetEndpointServerErrorsWithoutExistingErrors() {
        Endpoint endpoint = resourceHelpers.createEndpoint(WEBHOOK, null, true, 0);
        assertEquals(0, endpoint.getServerErrors());
        assertFalse(endpointRepository.resetEndpointServerErrors(endpoint.getId()), "Endpoints with serverErrors == 0 SHOULD NOT be updated");
        assertEquals(0, getEndpoint(endpoint.getId()).getServerErrors());
    }

    @Test
    void testResetEndpointServerErrorsWithUnknownId() {
        assertFalse(endpointRepository.resetEndpointServerErrors(UUID.randomUUID()));
    }

    @Test
    void testDisableEndpointWithEnabledEndpoint() {
        Endpoint endpoint = resourceHelpers.createEndpoint(WEBHOOK, null, true, 3);
        assertTrue(endpoint.isEnabled());
        assertTrue(endpointRepository.disableEndpoint(endpoint), "Enabled endpoints SHOULD be updated");
        assertFalse(getEndpoint(endpoint.getId()).isEnabled());
    }

    @Test
    void testDisableEndpointWithDisabledEndpoint() {
        Endpoint endpoint = resourceHelpers.createEndpoint(WEBHOOK, null, false, 0);
        assertFalse(endpoint.isEnabled());
        assertFalse(endpointRepository.disableEndpoint(endpoint), "Disabled endpoints SHOULD NOT be updated");
        assertFalse(getEndpoint(endpoint.getId()).isEnabled());
    }

    @Test
    void testDisableEndpointWithUnknownId() {
        final Endpoint endpoint = new Endpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setType(WEBHOOK);

        assertFalse(endpointRepository.disableEndpoint(endpoint));
    }

    /**
     * Tests that the function under test does not disable system endpoints.
     */
    @Test
    void testDoesNotDisableSystemEndpoints() {
        // Create the enabled system endpoints.
        final Set<Endpoint> systemEndpoints = new HashSet<>();
        for (final EndpointType endpointType : EndpointType.values()) {
            if (endpointType.isSystemEndpointType) {
                systemEndpoints.add(this.resourceHelpers.createEndpoint(endpointType, null, true, 0));
            }
        }

        for (final Endpoint systemEndpoint : systemEndpoints) {
            // Call the function under test and assert that the endpoints were
            // not disabled.
            Assertions.assertFalse(this.endpointRepository.incrementEndpointServerErrors(systemEndpoint.getId(), 25), String.format("system endpoint with type \"%s\" should not have been disabled", systemEndpoint.getType()));

            final Endpoint databaseEndpoint = this.entityManager.createQuery("FROM Endpoint WHERE id=:endpoint_id", Endpoint.class).setParameter("endpoint_id", systemEndpoint.getId()).getSingleResult();
            Assertions.assertTrue(databaseEndpoint.isEnabled(), "the system endpoint got disabled even though it should have been");
        }

    }

    Endpoint getEndpoint(UUID id) {
        String hql = "FROM Endpoint WHERE id = :id";
        return entityManager.createQuery(hql, Endpoint.class)
                .setParameter("id", id)
                .getSingleResult();
    }

    /**
     * Tests that the {@link EndpointRepository#findByUuidAndOrgId(UUID, String)} function works as expected for a
     * webhook endpoint and an Ansible endpoint.
     */
    @Test
    void findByUuidAndOrgIdWebhookAnsibleTest() {
        final WebhookProperties webhookProperties = new WebhookProperties();

        webhookProperties.setId(UUID.randomUUID());
        webhookProperties.setDisableSslVerification(true);
        webhookProperties.setMethod(HttpType.PUT);
        webhookProperties.setSecretTokenSourcesId(213L);
        webhookProperties.setUrl("https://example.org");

        WebhookProperties dbProperties = findByUuidAndOrgIdInternal(WEBHOOK, null, webhookProperties, WebhookProperties.class);

        Assertions.assertEquals(webhookProperties.getId(), dbProperties.getId(), "the ID of the associated webhook properties doesn't match");
        Assertions.assertEquals(webhookProperties.getDisableSslVerification(), dbProperties.getDisableSslVerification(), "unexpected ssl verification value");
        Assertions.assertEquals(webhookProperties.getMethod(), dbProperties.getMethod(), "unexpected http method value");
        Assertions.assertEquals(webhookProperties.getSecretTokenSourcesId(), dbProperties.getSecretTokenSourcesId(), "unexpected secret token sources ID value");
        Assertions.assertEquals(webhookProperties.getUrl(), dbProperties.getUrl(), "unexpected url value");

        // Repeat for Ansible endpoint, with some modified values
        final WebhookProperties ansibleProperties = webhookProperties;
        ansibleProperties.setId(UUID.randomUUID());
        ansibleProperties.setSecretTokenSourcesId(242L);
        ansibleProperties.setMethod(HttpType.POST);

        WebhookProperties dbAnsibleProperties = findByUuidAndOrgIdInternal(ANSIBLE, null, webhookProperties, WebhookProperties.class);

        Assertions.assertEquals(ansibleProperties.getId(), dbAnsibleProperties.getId(), "the ID of the associated Ansible properties doesn't match");
        Assertions.assertEquals(ansibleProperties.getDisableSslVerification(), dbAnsibleProperties.getDisableSslVerification(), "unexpected ssl verification value");
        Assertions.assertEquals(ansibleProperties.getMethod(), dbAnsibleProperties.getMethod(), "unexpected http method value");
        Assertions.assertEquals(ansibleProperties.getSecretTokenSourcesId(), dbAnsibleProperties.getSecretTokenSourcesId(), "unexpected secret token sources ID value");
        Assertions.assertEquals(ansibleProperties.getUrl(), dbAnsibleProperties.getUrl(), "unexpected url value");
    }

    /**
     * Tests that the {@link EndpointRepository#findByUuidAndOrgId(UUID, String)} function works as expected for an
     * email endpoint and a Drawer endpoint.
     */
    @Test
    void findByUuidAndOrgIdEmailDrawerTest() {
        final SystemSubscriptionProperties systemSubscriptionProperties = new SystemSubscriptionProperties();

        systemSubscriptionProperties.setId(UUID.randomUUID());
        systemSubscriptionProperties.setGroupId(UUID.randomUUID());
        systemSubscriptionProperties.setOnlyAdmins(true);
        systemSubscriptionProperties.setIgnorePreferences(false);

        SystemSubscriptionProperties dbProperties = findByUuidAndOrgIdInternal(EMAIL_SUBSCRIPTION, null, systemSubscriptionProperties, SystemSubscriptionProperties.class);

        Assertions.assertEquals(systemSubscriptionProperties.getId(), dbProperties.getId(), "the ID of the associated email properties doesn't match");
        Assertions.assertEquals(systemSubscriptionProperties.getGroupId(), dbProperties.getGroupId(), "unexpected group id value");
        Assertions.assertEquals(systemSubscriptionProperties.isOnlyAdmins(), dbProperties.isOnlyAdmins(), "unexpected only admins setting");
        Assertions.assertEquals(systemSubscriptionProperties.isIgnorePreferences(), dbProperties.isIgnorePreferences(), "unexpected ignore preferences setting");

        // Repeat for drawer endpoint, with some modified values
        final SystemSubscriptionProperties drawerProperties = systemSubscriptionProperties;
        drawerProperties.setId(UUID.randomUUID());
        drawerProperties.setGroupId(UUID.randomUUID());
        drawerProperties.setIgnorePreferences(true);

        SystemSubscriptionProperties dbDrawerProperties = findByUuidAndOrgIdInternal(DRAWER, null, systemSubscriptionProperties, SystemSubscriptionProperties.class);

        Assertions.assertEquals(drawerProperties.getId(), dbDrawerProperties.getId(), "the ID of the associated drawer properties doesn't match");
        Assertions.assertEquals(drawerProperties.getGroupId(), dbDrawerProperties.getGroupId(), "unexpected group id value");
        Assertions.assertEquals(drawerProperties.isOnlyAdmins(), dbDrawerProperties.isOnlyAdmins(), "unexpected only admins setting");
        Assertions.assertEquals(drawerProperties.isIgnorePreferences(), dbDrawerProperties.isIgnorePreferences(), "unexpected ignore preferences setting");
    }

    /**
     * Tests that the {@link EndpointRepository#findByUuidAndOrgId(UUID, String)} function works as expected for an
     * Camel (Slack) endpoint.
     */
    @Test
    void findByUuidAndOrgIdCamelTest() {
        final CamelProperties camelProperties = new CamelProperties();

        camelProperties.setId(UUID.randomUUID());
        camelProperties.setBasicAuthenticationSourcesId(513L);
        camelProperties.setDisableSslVerification(false);
        camelProperties.setExtras(Map.of("channel", "#notifications"));
        camelProperties.setSecretTokenSourcesId(214L);
        camelProperties.setUrl("https://example.org");

        CamelProperties dbProperties = findByUuidAndOrgIdInternal(CAMEL, "slack", camelProperties, CamelProperties.class);

        Assertions.assertEquals(camelProperties.getId(), dbProperties.getId(), "the ID of the associated camel properties doesn't match");
        Assertions.assertEquals(camelProperties.getBasicAuthenticationSourcesId(), dbProperties.getBasicAuthenticationSourcesId(), "unexpected basic authentication sources id value");
        Assertions.assertEquals(camelProperties.getDisableSslVerification(), dbProperties.getDisableSslVerification(), "unexpected ssl verification value");
        Assertions.assertEquals(camelProperties.getExtras(), dbProperties.getExtras(), "unexpected extras map (slack channel)");
        Assertions.assertEquals(camelProperties.getSecretTokenSourcesId(), dbProperties.getSecretTokenSourcesId(), "unexpected secret token sources ID value");
        Assertions.assertEquals(camelProperties.getUrl(), dbProperties.getUrl(), "unexpected url value");
    }

    /**
     * Tests that the {@link EndpointRepository#findByUuidAndOrgId(UUID, String)} function works as expected for an
     * PagerDuty endpoint.
     */
    @Test
    void findByUuidAndOrgIdPagerDutyTest() {
        final PagerDutyProperties pagerDutyProperties = new PagerDutyProperties();

        pagerDutyProperties.setId(UUID.randomUUID());
        pagerDutyProperties.setSeverity(PagerDutySeverity.WARNING);
        pagerDutyProperties.setSecretToken("23990f-1234e89ff-24t");
        pagerDutyProperties.setSecretTokenSourcesId(269L);

        PagerDutyProperties dbProperties = findByUuidAndOrgIdInternal(PAGERDUTY, null, pagerDutyProperties, PagerDutyProperties.class);

        Assertions.assertEquals(pagerDutyProperties.getId(), dbProperties.getId(), "the ID of the associated PagerDuty properties doesn't match");
        Assertions.assertEquals(pagerDutyProperties.getSeverity(), dbProperties.getSeverity(), "unexpected severity value");
        Assertions.assertEquals(pagerDutyProperties.getSecretTokenSourcesId(), dbProperties.getSecretTokenSourcesId(), "unexpected secret token sources ID value");
    }

    /**
     * Tests that the function under test throws a "NoResultException" whenever
     * the endpoint cannot be found.
     */
    @Test
    void findByUuidAndOrgIdNotFound() {
        final UUID randomEndpointUuid = UUID.randomUUID();
        final String randomOrgId = "random-org-id";

        // Call the function under test.
        final NoResultException exception = Assertions.assertThrows(NoResultException.class, () ->
            this.endpointRepository.findByUuidAndOrgId(randomEndpointUuid, randomOrgId)
        );

        final String expectedErrorMessage = String.format("Endpoint with id=%s and orgId=%s not found", randomEndpointUuid, randomOrgId);

        Assertions.assertEquals(expectedErrorMessage, exception.getMessage(), "unexpected error message received");
    }

    /**
     * Tests that the function under test throws a "NoResultException" when the
     * endpoint ID is correct, but the Org ID doesn't match.
     */
    @Test
    void findByUuidAndOrgIdWrongOrgId() {
        final UUID endpointUuid = UUID.randomUUID();

        final Endpoint endpoint = new Endpoint();
        endpoint.setCreated(LocalDateTime.now());
        endpoint.setDescription("Endpoint description");
        endpoint.setEnabled(true);
        endpoint.setId(endpointUuid);
        endpoint.setName("endpoint-" + new SecureRandom().nextInt());
        endpoint.setOrgId("find-by-uuid-org-id-wrong-org-id-test");
        endpoint.setServerErrors(123);
        endpoint.setType(WEBHOOK);

        persist(endpoint);

        // Call the function under test.
        final String randomOrgId = "random-org-id";

        final NoResultException exception = Assertions.assertThrows(NoResultException.class, () ->
                this.endpointRepository.findByUuidAndOrgId(endpoint.getId(), randomOrgId)
        );

        final String expectedErrorMessage = String.format("Endpoint with id=%s and orgId=%s not found", endpointUuid, randomOrgId);

        Assertions.assertEquals(expectedErrorMessage, exception.getMessage(), "unexpected error message received");
    }

    @Transactional
    void persist(Endpoint endpoint) {
        entityManager.persist(endpoint);
    }

    @Transactional
    <T extends EndpointProperties> void persist(Endpoint endpoint, T endpointProperties) {
        entityManager.persist(endpoint);
        entityManager.persist(endpointProperties);
    }

    /**
     * Persist an implementation of {@link com.redhat.cloud.notifications.models.EndpointProperties EndpointProperties} to the database, and verify that it can be retrieved. Perform some
     * assertions on the base endpoint.
     *
     * @return the cast endpoint implementation retrieved from the database
     */
    private <T extends EndpointProperties> T findByUuidAndOrgIdInternal(EndpointType endpointType, String endpointSubType, T endpointProperties, Class<T> endpointPropertiesClass) {
        final String orgId = "find-by-uuid-org-id-test";

        final Endpoint endpoint = new Endpoint();
        endpoint.setCreated(LocalDateTime.now());
        endpoint.setDescription("Endpoint description");
        endpoint.setEnabled(true);
        endpoint.setId(UUID.randomUUID());
        endpoint.setName("endpoint-" + new SecureRandom().nextInt());
        endpoint.setOrgId(orgId);
        endpoint.setServerErrors(123);
        endpoint.setType(endpointType);
        if (endpointSubType != null && !endpointSubType.isEmpty()) {
            endpoint.setSubType(endpointSubType);
        }

        endpointProperties.setEndpoint(endpoint);
        // Since we are not using a repository to create the associated
        // properties, we do it manually by first storing the endpoint and then
        // its properties.
        persist(endpoint, endpointProperties);

        final Endpoint[] dbEndpoints = new Endpoint[1];

        // Call the function under test.
        dbEndpoints[0] = this.endpointRepository.findByUuidAndOrgId(endpoint.getId(), orgId);

        Assertions.assertEquals(1, dbEndpoints.length, "only one endpoint should have been fetched");

        final Endpoint dbEndpoint = dbEndpoints[0];

        Assertions.assertEquals(endpoint.getId(), dbEndpoint.getId(), "unexpected id from the fetched endpoint");
        Assertions.assertEquals(endpoint.getType(), dbEndpoint.getType(), "unexpected endpoint type from the fetched endpoint");
        Assertions.assertTrue(dbEndpoint.isEnabled(), "unexpected enabled value from the fetched endpoint");
        Assertions.assertEquals(endpoint.getServerErrors(), dbEndpoint.getServerErrors(), "unexpected server errors value from the fetched endpoint");

        Assertions.assertNotNull(dbEndpoint.getProperties());

        return dbEndpoint.getProperties(endpointPropertiesClass);
    }

    /**
     * Tests that when the endpoint is a system endpoint, the function under
     * test does not disable it.
     */
    @Test
    void testSystemEndpointsNotDisabled() {
        // Create the system endpoints.
        final int originalServerErrors = 12;
        final Set<Endpoint> systemEndpoints = new HashSet<>();
        for (final EndpointType endpointType : EndpointType.values()) {
            if (endpointType.isSystemEndpointType) {
                systemEndpoints.add(this.resourceHelpers.createEndpoint(endpointType, null, true, originalServerErrors));
            }
        }

        for (final Endpoint systemEndpoint : systemEndpoints) {
            // Call the function under test and assert that the endpoints were
            // not disabled, nor their server errors were incremented.
            Assertions.assertFalse(this.endpointRepository.incrementEndpointServerErrors(systemEndpoint.getId(), 25), String.format("system endpoint with type \"%s\" should not have been disabled", systemEndpoint.getType()));

            final Endpoint databaseEndpoint = this.entityManager.createQuery("FROM Endpoint WHERE id=:endpoint_id", Endpoint.class).setParameter("endpoint_id", systemEndpoint.getId()).getSingleResult();
            Assertions.assertEquals(originalServerErrors, databaseEndpoint.getServerErrors(), "the server errors field was updated for a system endpoint, when it shouldn't have");
        }
    }

    /**
     * Tests that the function under test finds integration names for a
     * particular type and groups them by organization ID.
     */
    @Test
    @Transactional
    void testFindIntegrationNamesByTypeGroupedByOrganizationId() {
        final List<String> orgIds = List.of(
            DEFAULT_ORG_ID,
            DEFAULT_ORG_ID + "-two",
            DEFAULT_ORG_ID + "-three"
        );

        // Create five "Teams" integrations per organization.
        final Map<String, List<String>> expectedResult = new HashMap<>();

        for (final String orgId: orgIds) {
            for (int i = 0; i < 5; i++) {
                // The "createEndpoint" method does not set an org id, so we
                // need to set it ourselves.
                final Endpoint createdEndpoint = this.resourceHelpers.createEndpoint(CAMEL, "teams", true, 0);
                createdEndpoint.setOrgId(orgId);
                this.entityManager.persist(createdEndpoint);

                // Store the expected result to compare it later.
                final List<String> endpointNames = expectedResult.getOrDefault(orgId, new ArrayList<>());
                endpointNames.add(createdEndpoint.getName());

                expectedResult.putIfAbsent(orgId, endpointNames);
            }
        }

        // Call the function under test.
        final Map<String, List<String>> result = this.endpointRepository.findIntegrationNamesByTypeGroupedByOrganizationId(new CompositeEndpointType(CAMEL, "teams"));

        // Assert that the maps are the same.
        for (final Map.Entry<String, List<String>> entry : result.entrySet()) {
            if (!expectedResult.containsKey(entry.getKey())) {
                Assertions.fail(String.format("organization ID \"%s\" not present in the expected set", entry.getKey()));
            }

            final List<String> expectedResultList = expectedResult.get(entry.getKey());
            final List<String> resultList = entry.getValue();

            for (final String resultingIntegrationName : resultList) {
                if (!expectedResultList.contains(resultingIntegrationName)) {
                    Assertions.fail(String.format("unexpected integration \"%s\" fetched for organization ID \"%s\"", resultingIntegrationName, entry.getKey()));
                }
            }
        }
    }
}
