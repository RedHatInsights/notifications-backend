package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EmailAggregationResourcesTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final String ACCOUNT_ID = "123456789";
    private static final String ORG_ID = "987654321";
    private static final String BUNDLE_NAME = "best-bundle";
    private static final String APP_NAME = "awesome-app";
    private static final JsonObject PAYLOAD1 = new JsonObject("{\"foo\":\"bar\"}");
    private static final JsonObject PAYLOAD2 = new JsonObject("{\"hello\":\"world\"}");

    @Inject
    EmailAggregationResources emailAggregationResources;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EntityManager entityManager;

    @Test
    void testAllMethods() {

        LocalDateTime start = LocalDateTime.now(UTC).minusHours(1L);
        LocalDateTime end = LocalDateTime.now(UTC).plusHours(1L);

        addEmailAggregation(ACCOUNT_ID, BUNDLE_NAME, APP_NAME, PAYLOAD1);
        addEmailAggregation(ACCOUNT_ID, BUNDLE_NAME, APP_NAME, PAYLOAD2);
        addEmailAggregation("other-account", BUNDLE_NAME, APP_NAME, PAYLOAD2);
        addEmailAggregation(ACCOUNT_ID, "other-bundle", APP_NAME, PAYLOAD2);
        addEmailAggregation(ACCOUNT_ID, BUNDLE_NAME, "other-app", PAYLOAD2);

        EmailAggregationKey key = new EmailAggregationKey(ACCOUNT_ID, ORG_ID, BUNDLE_NAME, APP_NAME);

        List<EmailAggregationKey> keys = emailAggregationResources.getApplicationsWithPendingAggregation(start, end);
        assertEquals(4, keys.size());
        assertEquals(ACCOUNT_ID, keys.get(0).getAccountId());
        assertNull(keys.get(0).getOrgId());
        assertEquals(BUNDLE_NAME, keys.get(0).getBundle());
        assertEquals(APP_NAME, keys.get(0).getApplication());

        Integer purged = resourceHelpers.purgeOldAggregation(key, end);
        assertEquals(2, purged);

        keys = emailAggregationResources.getApplicationsWithPendingAggregation(start, end);
        assertEquals(3, keys.size());
    }

    @Test
    void shouldNotMapOrgId() {
        LocalDateTime start = LocalDateTime.now(UTC).minusHours(1L);
        LocalDateTime end = LocalDateTime.now(UTC).plusHours(1L);

        addEmailAggregation(ACCOUNT_ID, BUNDLE_NAME, APP_NAME, PAYLOAD1);
        addEmailAggregation(ACCOUNT_ID, BUNDLE_NAME, APP_NAME, PAYLOAD2);
        addEmailAggregation("other-account", BUNDLE_NAME, APP_NAME, PAYLOAD2);
        addEmailAggregation(ACCOUNT_ID, "other-bundle", APP_NAME, PAYLOAD2);
        addEmailAggregation(ACCOUNT_ID, BUNDLE_NAME, "other-app", PAYLOAD2);

        List<EmailAggregationKey> keys = emailAggregationResources.getApplicationsWithPendingAggregation(start, end);

        assertNull(keys.get(0).getOrgId());

        clearEmailAggregations();
    }

    @Test
    void shouldMapOrgId() {
        LocalDateTime start = LocalDateTime.now(UTC).minusHours(1L);
        LocalDateTime end = LocalDateTime.now(UTC).plusHours(1L);

        addEmailAggregation(ACCOUNT_ID, ORG_ID, BUNDLE_NAME, APP_NAME, PAYLOAD1);
        addEmailAggregation(ACCOUNT_ID, ORG_ID, BUNDLE_NAME, APP_NAME, PAYLOAD2);
        addEmailAggregation("other-account", ORG_ID, BUNDLE_NAME, APP_NAME, PAYLOAD2);
        addEmailAggregation(ACCOUNT_ID, ORG_ID, "other-bundle", APP_NAME, PAYLOAD2);
        addEmailAggregation(ACCOUNT_ID, ORG_ID, BUNDLE_NAME, "other-app", PAYLOAD2);

        List<EmailAggregationKey> keys = emailAggregationResources.getApplicationsWithPendingAggregation(start, end);

        assertEquals("987654321", keys.get(0).getOrgId());

        clearEmailAggregations();
    }

    private void addEmailAggregation(String accountId, String orgId, String bundleName, String applicationName, JsonObject payload) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setAccountId(accountId);
        aggregation.setOrgId(orgId);
        aggregation.setBundleName(bundleName);
        aggregation.setApplicationName(applicationName);
        aggregation.setPayload(payload);

        resourceHelpers.addEmailAggregation(aggregation);
    }

    private void addEmailAggregation(String accountId, String bundleName, String applicationName, JsonObject payload) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setAccountId(accountId);
        aggregation.setBundleName(bundleName);
        aggregation.setApplicationName(applicationName);
        aggregation.setPayload(payload);

        resourceHelpers.addEmailAggregation(aggregation);
    }

    @Transactional
    void clearEmailAggregations() {
        entityManager.createQuery("DELETE FROM EmailAggregation")
                .executeUpdate();
    }
}
