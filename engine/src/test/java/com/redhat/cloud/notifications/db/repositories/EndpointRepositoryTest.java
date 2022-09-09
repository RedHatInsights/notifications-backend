package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.models.Endpoint;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EndpointRepositoryTest {

    private static final int MAX_SERVER_ERRORS = 3;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    @Inject
    EndpointRepository endpointRepository;

    @Inject
    FeatureFlipper featureFlipper;

    @BeforeEach
    void beforeEach() {
        featureFlipper.setDisableWebhookEndpointsOnFailure(true);
    }

    @AfterEach
    void afterEach() {
        featureFlipper.setDisableWebhookEndpointsOnFailure(false);
    }

    @Test
    void testIncrementEndpointServerErrors() {
        Endpoint endpoint = resourceHelpers.createEndpoint(WEBHOOK, null, true, 0);
        assertTrue(endpoint.isEnabled());
        assertEquals(0, endpoint.getServerErrors());

        statelessSessionFactory.withSession(statelessSession -> {
            for (int i = 1; i <= MAX_SERVER_ERRORS + 1; i++) {
                assertEquals(i > MAX_SERVER_ERRORS, endpointRepository.incrementEndpointServerErrors(endpoint.getId(), MAX_SERVER_ERRORS));
                Endpoint ep = getEndpoint(endpoint.getId());
                assertEquals(i <= MAX_SERVER_ERRORS, ep.isEnabled());
                assertEquals(i > MAX_SERVER_ERRORS ? 0 : i, ep.getServerErrors());
            }
        });
    }

    @Test
    void testIncrementEndpointServerErrorsWithUnknownId() {
        statelessSessionFactory.withSession(statelessSession -> {
            assertFalse(endpointRepository.incrementEndpointServerErrors(UUID.randomUUID(), 10));
        });
    }

    @Test
    void testResetEndpointServerErrorsWithExistingErrors() {
        Endpoint endpoint = resourceHelpers.createEndpoint(WEBHOOK, null, true, 3);
        assertEquals(3, endpoint.getServerErrors());
        statelessSessionFactory.withSession(statelessSession -> {
            assertTrue(endpointRepository.resetEndpointServerErrors(endpoint.getId()), "Endpoints with serverErrors > 0 SHOULD be updated");
            assertEquals(0, getEndpoint(endpoint.getId()).getServerErrors());
        });
    }

    @Test
    void testResetEndpointServerErrorsWithoutExistingErrors() {
        Endpoint endpoint = resourceHelpers.createEndpoint(WEBHOOK, null, true, 0);
        assertEquals(0, endpoint.getServerErrors());
        statelessSessionFactory.withSession(statelessSession -> {
            assertFalse(endpointRepository.resetEndpointServerErrors(endpoint.getId()), "Endpoints with serverErrors == 0 SHOULD NOT be updated");
            assertEquals(0, getEndpoint(endpoint.getId()).getServerErrors());
        });
    }

    @Test
    void testResetEndpointServerErrorsWithUnknownId() {
        statelessSessionFactory.withSession(statelessSession -> {
            assertFalse(endpointRepository.resetEndpointServerErrors(UUID.randomUUID()));
        });
    }

    @Test
    void testDisableEndpointWithEnabledEndpoint() {
        Endpoint endpoint = resourceHelpers.createEndpoint(WEBHOOK, null, true, 3);
        assertTrue(endpoint.isEnabled());
        statelessSessionFactory.withSession(statelessSession -> {
            assertTrue(endpointRepository.disableEndpoint(endpoint.getId()), "Enabled endpoints SHOULD be updated");
            assertFalse(getEndpoint(endpoint.getId()).isEnabled());
        });
    }

    @Test
    void testDisableEndpointWithDisabledEndpoint() {
        Endpoint endpoint = resourceHelpers.createEndpoint(WEBHOOK, null, false, 0);
        assertFalse(endpoint.isEnabled());
        statelessSessionFactory.withSession(statelessSession -> {
            assertFalse(endpointRepository.disableEndpoint(endpoint.getId()), "Disabled endpoints SHOULD NOT be updated");
            assertFalse(getEndpoint(endpoint.getId()).isEnabled());
        });
    }

    @Test
    void testDisableEndpointWithUnknownId() {
        statelessSessionFactory.withSession(statelessSession -> {
            assertFalse(endpointRepository.disableEndpoint(UUID.randomUUID()));
        });
    }

    Endpoint getEndpoint(UUID id) {
        String hql = "FROM Endpoint WHERE id = :id";
        return statelessSessionFactory.getCurrentSession().createQuery(hql, Endpoint.class)
                .setParameter("id", id)
                .getSingleResult();
    }
}
