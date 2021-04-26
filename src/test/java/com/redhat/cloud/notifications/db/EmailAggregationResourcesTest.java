package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EmailAggregationResourcesTest extends DbIsolatedTest {

    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final String ACCOUNT_ID = "123456789";
    private static final String BUNDLE_NAME = "best-bundle";
    private static final String APP_NAME = "awesome-app";
    private static final JsonObject PAYLOAD1 = new JsonObject("{\"foo\":\"bar\"}");
    private static final JsonObject PAYLOAD2 = new JsonObject("{\"hello\":\"world\"}");

    @Inject
    EmailAggregationResources emailAggregationResources;

    @Test
    public void testAllMethods() {

        LocalDateTime start = LocalDateTime.now(UTC).minusHours(1L);
        LocalDateTime end = LocalDateTime.now(UTC).plusHours(1L);

        addEmailAggregation(ACCOUNT_ID, BUNDLE_NAME, APP_NAME, PAYLOAD1, true);
        addEmailAggregation(ACCOUNT_ID, BUNDLE_NAME, APP_NAME, PAYLOAD2, true);
        addEmailAggregation("other-account", BUNDLE_NAME, APP_NAME, PAYLOAD2, true);
        addEmailAggregation(ACCOUNT_ID, "other-bundle", APP_NAME, PAYLOAD2, true);
        addEmailAggregation(ACCOUNT_ID, BUNDLE_NAME, "other-app", PAYLOAD2, true);

        EmailAggregationKey key = buildEmailAggregationKey(ACCOUNT_ID, BUNDLE_NAME, APP_NAME);

        List<EmailAggregation> aggregations = getEmailAggregation(key, start, end);
        assertEquals(2, aggregations.size());
        assertTrue(aggregations.stream().map(EmailAggregation::getAccountId).allMatch(ACCOUNT_ID::equals));
        assertTrue(aggregations.stream().map(EmailAggregation::getBundleName).allMatch(BUNDLE_NAME::equals));
        assertTrue(aggregations.stream().map(EmailAggregation::getApplicationName).allMatch(APP_NAME::equals));
        assertEquals(1, aggregations.stream().map(EmailAggregation::getPayload).filter(PAYLOAD1::equals).count());
        assertEquals(1, aggregations.stream().map(EmailAggregation::getPayload).filter(PAYLOAD2::equals).count());

        List<EmailAggregationKey> keys = getApplicationsWithPendingAggregation(start, end);
        assertEquals(4, keys.size());
        assertEquals(ACCOUNT_ID, keys.get(0).getAccountId());
        assertEquals(BUNDLE_NAME, keys.get(0).getBundle());
        assertEquals(APP_NAME, keys.get(0).getApplication());

        Integer purged = purgeOldAggregation(key, end);
        assertEquals(2, purged);

        aggregations = getEmailAggregation(key, start, end);
        assertEquals(0, aggregations.size());

        keys = getApplicationsWithPendingAggregation(start, end);
        assertEquals(3, keys.size());
    }

    @Test
    public void testAddEmailAggregationWithConstraintViolations() {
        addEmailAggregation(ACCOUNT_ID, BUNDLE_NAME, APP_NAME, null, false);
        addEmailAggregation(ACCOUNT_ID, BUNDLE_NAME, null, PAYLOAD1, false);
        addEmailAggregation(ACCOUNT_ID, null, APP_NAME, PAYLOAD1, false);
        addEmailAggregation(null, BUNDLE_NAME, APP_NAME, PAYLOAD1, false);
    }

    private EmailAggregationKey buildEmailAggregationKey(String accountId, String bundleName, String applicationName) {
        EmailAggregationKey key = new EmailAggregationKey();
        key.setAccountId(accountId);
        key.setBundle(bundleName);
        key.setApplication(applicationName);
        return key;
    }

    private void addEmailAggregation(String accountId, String bundleName, String applicationName, JsonObject payload, boolean expectedResult) {
        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setAccountId(accountId);
        aggregation.setBundleName(bundleName);
        aggregation.setApplicationName(applicationName);
        aggregation.setPayload(payload);

        Boolean added = emailAggregationResources.addEmailAggregation(aggregation).await().indefinitely();
        assertEquals(expectedResult, added);
    }

    private List<EmailAggregation> getEmailAggregation(EmailAggregationKey key, LocalDateTime start, LocalDateTime end) {
        return emailAggregationResources.getEmailAggregation(key, start, end)
                .collect().asList().await().indefinitely();
    }

    private List<EmailAggregationKey> getApplicationsWithPendingAggregation(LocalDateTime start, LocalDateTime end) {
        return emailAggregationResources.getApplicationsWithPendingAggregation(start, end)
                .collect().asList().await().indefinitely();
    }

    private Integer purgeOldAggregation(EmailAggregationKey key, LocalDateTime lastUsedTime) {
        return emailAggregationResources.purgeOldAggregation(key, lastUsedTime)
                .await().indefinitely();
    }
}
