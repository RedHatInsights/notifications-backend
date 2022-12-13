package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EmailAggregationRepositoryTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final String ORG_ID = "someOrgId";
    private static final String BUNDLE_NAME = "best-bundle";
    private static final String APP_NAME = "awesome-app";

    private static final String EVENT_TYPE = "amazing-event-type";

    private static final JsonObject PAYLOAD1 = new JsonObject("{\"foo\":\"bar\"}");
    private static final JsonObject PAYLOAD2 = new JsonObject("{\"hello\":\"world\"}");

    @Inject
    EntityManager entityManager;

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EmailAggregationRepository emailAggregationRepository;

    @Inject
    FeatureFlipper featureFlipper;

    @BeforeEach
    void setUp() {
        featureFlipper.setUseEventTypeForAggregationEnabled(false);
    }

    @Test
    void testAllMethods() {
        LocalDateTime start = LocalDateTime.now(UTC).minusHours(1L);
        LocalDateTime end = LocalDateTime.now(UTC).plusHours(1L);
        EmailAggregationKey key = new EmailAggregationKey(ORG_ID, BUNDLE_NAME, APP_NAME, EVENT_TYPE);

        statelessSessionFactory.withSession(statelessSession -> {
            clearEmailAggregations();
            resourceHelpers.addEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, PAYLOAD1);
            resourceHelpers.addEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, PAYLOAD2);
            resourceHelpers.addEmailAggregation("other-org-id", BUNDLE_NAME, APP_NAME, PAYLOAD2);
            resourceHelpers.addEmailAggregation(ORG_ID, "other-bundle", APP_NAME, PAYLOAD2);
            resourceHelpers.addEmailAggregation(ORG_ID, BUNDLE_NAME, "other-app", PAYLOAD2);

            List<EmailAggregation> aggregations = emailAggregationRepository.getEmailAggregation(key, start, end);
            assertEquals(2, aggregations.size());
            assertTrue(aggregations.stream().map(EmailAggregation::getOrgId).allMatch(ORG_ID::equals));
            assertTrue(aggregations.stream().map(EmailAggregation::getBundleName).allMatch(BUNDLE_NAME::equals));
            assertTrue(aggregations.stream().map(EmailAggregation::getApplicationName).allMatch(APP_NAME::equals));
            assertEquals(1, aggregations.stream().map(EmailAggregation::getPayload).filter(PAYLOAD1::equals).count());
            assertEquals(1, aggregations.stream().map(EmailAggregation::getPayload).filter(PAYLOAD2::equals).count());

            List<EmailAggregationKey> keys = getApplicationsWithPendingAggregation(start, end);
            assertEquals(4, keys.size());
            assertEquals(ORG_ID, aggregations.get(0).getOrgId());
            assertEquals("other-org-id", keys.get(0).getOrgId());
            assertEquals(BUNDLE_NAME, keys.get(0).getBundle());
            assertEquals(APP_NAME, keys.get(0).getApplication());

            assertEquals(2, emailAggregationRepository.purgeOldAggregation(key, end));
            assertEquals(0, emailAggregationRepository.getEmailAggregation(key, start, end).size());
            assertEquals(3, getApplicationsWithPendingAggregation(start, end).size());

            clearEmailAggregations();
        });
    }

    @Test
    void testAllMethodsWithEventTypeAndFeatureFlipDisabled() {
        LocalDateTime start = LocalDateTime.now(UTC).minusHours(1L);
        LocalDateTime end = LocalDateTime.now(UTC).plusHours(1L);
        EmailAggregationKey key = new EmailAggregationKey(ORG_ID, BUNDLE_NAME, APP_NAME, EVENT_TYPE);

        statelessSessionFactory.withSession(statelessSession -> {
            clearEmailAggregations();
            resourceHelpers.addEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, EVENT_TYPE, PAYLOAD1);
            resourceHelpers.addEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, EVENT_TYPE, PAYLOAD2);
            resourceHelpers.addEmailAggregation("other-org-id", BUNDLE_NAME, APP_NAME, EVENT_TYPE, PAYLOAD2);
            resourceHelpers.addEmailAggregation(ORG_ID, "other-bundle", APP_NAME, EVENT_TYPE, PAYLOAD2);
            resourceHelpers.addEmailAggregation(ORG_ID, BUNDLE_NAME, "other-app", EVENT_TYPE, PAYLOAD2);

            List<EmailAggregation> aggregations = emailAggregationRepository.getEmailAggregation(key, start, end);
            assertEquals(2, aggregations.size());
            assertTrue(aggregations.stream().map(EmailAggregation::getOrgId).allMatch(ORG_ID::equals));
            assertTrue(aggregations.stream().map(EmailAggregation::getBundleName).allMatch(BUNDLE_NAME::equals));
            assertTrue(aggregations.stream().map(EmailAggregation::getApplicationName).allMatch(APP_NAME::equals));
            assertEquals(1, aggregations.stream().map(EmailAggregation::getPayload).filter(PAYLOAD1::equals).count());
            assertEquals(1, aggregations.stream().map(EmailAggregation::getPayload).filter(PAYLOAD2::equals).count());

            List<EmailAggregationKey> keys = getApplicationsWithPendingAggregation(start, end);
            assertEquals(4, keys.size());
            assertEquals(ORG_ID, aggregations.get(0).getOrgId());
            assertEquals("other-org-id", keys.get(0).getOrgId());
            assertEquals(BUNDLE_NAME, keys.get(0).getBundle());
            assertEquals(APP_NAME, keys.get(0).getApplication());

            assertEquals(2, emailAggregationRepository.purgeOldAggregation(key, end));
            assertEquals(0, emailAggregationRepository.getEmailAggregation(key, start, end).size());
            assertEquals(3, getApplicationsWithPendingAggregation(start, end).size());

            clearEmailAggregations();
        });
    }

    @Test
    void testAllMethodsFiltredByEventType() {
        featureFlipper.setUseEventTypeForAggregationEnabled(true);
        LocalDateTime start = LocalDateTime.now(UTC).minusHours(1L);
        LocalDateTime end = LocalDateTime.now(UTC).plusHours(1L);
        EmailAggregationKey key = new EmailAggregationKey(ORG_ID, BUNDLE_NAME, APP_NAME, EVENT_TYPE);

        statelessSessionFactory.withSession(statelessSession -> {
            clearEmailAggregations();
            resourceHelpers.addEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, EVENT_TYPE, PAYLOAD1);
            resourceHelpers.addEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, EVENT_TYPE, PAYLOAD2);
            resourceHelpers.addEmailAggregation("other-org-id", BUNDLE_NAME, APP_NAME, EVENT_TYPE, PAYLOAD2);
            resourceHelpers.addEmailAggregation(ORG_ID, "other-bundle", APP_NAME, EVENT_TYPE, PAYLOAD2);
            resourceHelpers.addEmailAggregation(ORG_ID, BUNDLE_NAME, "other-app", EVENT_TYPE, PAYLOAD2);
            resourceHelpers.addEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, "other-event-type", PAYLOAD2);

            List<EmailAggregation> aggregations = emailAggregationRepository.getEmailAggregation(key, start, end);
            assertEquals(2, aggregations.size());
            assertTrue(aggregations.stream().map(EmailAggregation::getOrgId).allMatch(ORG_ID::equals));
            assertTrue(aggregations.stream().map(EmailAggregation::getBundleName).allMatch(BUNDLE_NAME::equals));
            assertTrue(aggregations.stream().map(EmailAggregation::getApplicationName).allMatch(APP_NAME::equals));
            assertTrue(aggregations.stream().map(EmailAggregation::getEventType).allMatch(EVENT_TYPE::equals));
            assertEquals(1, aggregations.stream().map(EmailAggregation::getPayload).filter(PAYLOAD1::equals).count());
            assertEquals(1, aggregations.stream().map(EmailAggregation::getPayload).filter(PAYLOAD2::equals).count());

            List<EmailAggregationKey> keys = getApplicationsWithPendingAggregation(start, end);
            assertEquals(5, keys.size());
            assertEquals(ORG_ID, aggregations.get(0).getOrgId());
            assertEquals("other-org-id", keys.get(0).getOrgId());
            assertEquals(BUNDLE_NAME, keys.get(0).getBundle());
            assertEquals(APP_NAME, keys.get(0).getApplication());
            assertEquals(EVENT_TYPE, keys.get(0).getEventType());
            assertEquals("other-event-type", keys.get(2).getEventType());

            assertEquals(2, emailAggregationRepository.purgeOldAggregation(key, end));
            assertEquals(0, emailAggregationRepository.getEmailAggregation(key, start, end).size());
            assertEquals(4, getApplicationsWithPendingAggregation(start, end).size());

            clearEmailAggregations();
        });
    }

    @Test
    void addEmailAggregationWithConstraintViolations() {
        statelessSessionFactory.withSession(statelessSession -> {
            assertFalse(resourceHelpers.addEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, EVENT_TYPE, null));
            assertTrue(resourceHelpers.addEmailAggregation(ORG_ID, BUNDLE_NAME, APP_NAME, null, PAYLOAD1));
            assertFalse(resourceHelpers.addEmailAggregation(ORG_ID, BUNDLE_NAME, null, EVENT_TYPE, PAYLOAD1));
            assertFalse(resourceHelpers.addEmailAggregation(ORG_ID, null, APP_NAME, EVENT_TYPE, PAYLOAD1));
            assertFalse(resourceHelpers.addEmailAggregation(null, BUNDLE_NAME, APP_NAME, EVENT_TYPE, PAYLOAD1));
        });
    }

    List<EmailAggregationKey> getApplicationsWithPendingAggregation(LocalDateTime start, LocalDateTime end) {
        String query = "SELECT DISTINCT NEW com.redhat.cloud.notifications.models.EmailAggregationKey(ea.orgId, ea.bundleName, ea.applicationName, ea.eventType) " +
                "FROM EmailAggregation ea WHERE ea.created > :start AND ea.created <= :end";
        return entityManager.createQuery(query, EmailAggregationKey.class)
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
    }

    @Transactional
    void clearEmailAggregations() {
        entityManager.createQuery("DELETE FROM EmailAggregation")
                .executeUpdate();
    }
}
