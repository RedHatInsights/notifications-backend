package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import java.time.LocalDateTime;
import java.time.ZoneId;

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
    Mutiny.SessionFactory sessionFactory;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EmailAggregationResources aggregationResources;

    @Test
    void testAllMethods() {

        LocalDateTime start = LocalDateTime.now(UTC).minusHours(1L);
        LocalDateTime end = LocalDateTime.now(UTC).plusHours(1L);
        EmailAggregationKey key = new EmailAggregationKey(ACCOUNT_ID, BUNDLE_NAME, APP_NAME);

        sessionFactory.withSession(session -> resourceHelpers.addEmailAggregation(ACCOUNT_ID, BUNDLE_NAME, APP_NAME, PAYLOAD1)
                .chain(() -> resourceHelpers.addEmailAggregation(ACCOUNT_ID, BUNDLE_NAME, APP_NAME, PAYLOAD2))
                .chain(() -> resourceHelpers.addEmailAggregation("other-account", BUNDLE_NAME, APP_NAME, PAYLOAD2))
                .chain(() -> resourceHelpers.addEmailAggregation(ACCOUNT_ID, "other-bundle", APP_NAME, PAYLOAD2))
                .chain(() -> resourceHelpers.addEmailAggregation(ACCOUNT_ID, BUNDLE_NAME, "other-app", PAYLOAD2))
                .chain(() -> aggregationResources.getEmailAggregation(key, start, end))
                .invoke(aggregations -> {
                    assertEquals(2, aggregations.size());
                    assertTrue(aggregations.stream().map(EmailAggregation::getAccountId).allMatch(ACCOUNT_ID::equals));
                    assertTrue(aggregations.stream().map(EmailAggregation::getBundleName).allMatch(BUNDLE_NAME::equals));
                    assertTrue(aggregations.stream().map(EmailAggregation::getApplicationName).allMatch(APP_NAME::equals));
                    assertEquals(1, aggregations.stream().map(EmailAggregation::getPayload).filter(PAYLOAD1::equals).count());
                    assertEquals(1, aggregations.stream().map(EmailAggregation::getPayload).filter(PAYLOAD2::equals).count());
                })
                .chain(() -> aggregationResources.getApplicationsWithPendingAggregation(start, end))
                .invoke(keys -> {
                    assertEquals(4, keys.size());
                    assertEquals(ACCOUNT_ID, keys.get(0).getAccountId());
                    assertEquals(BUNDLE_NAME, keys.get(0).getBundle());
                    assertEquals(APP_NAME, keys.get(0).getApplication());
                })
                .chain(() -> aggregationResources.purgeOldAggregation(key, end))
                .invoke(purged -> assertEquals(2, purged))
                .chain(() -> aggregationResources.getEmailAggregation(key, start, end))
                .invoke(aggregations -> assertEquals(0, aggregations.size()))
                .chain(aggregations -> aggregationResources.getApplicationsWithPendingAggregation(start, end))
                .invoke(keys -> assertEquals(3, keys.size()))
        ).await().indefinitely();
    }

    @Test
    void addEmailAggregationWithConstraintViolations() {
        sessionFactory.withSession(session -> resourceHelpers.addEmailAggregation(ACCOUNT_ID, BUNDLE_NAME, APP_NAME, null)
                .invoke(Assertions::assertFalse)
                .chain(() -> resourceHelpers.addEmailAggregation(ACCOUNT_ID, BUNDLE_NAME, null, PAYLOAD1))
                .invoke(Assertions::assertFalse)
                .chain(() -> resourceHelpers.addEmailAggregation(ACCOUNT_ID, null, APP_NAME, PAYLOAD1))
                .invoke(Assertions::assertFalse)
                .chain(() -> resourceHelpers.addEmailAggregation(null, BUNDLE_NAME, APP_NAME, PAYLOAD1))
                .invoke(Assertions::assertFalse)
        ).await().indefinitely();
    }
}
