package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import javax.inject.Inject;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    ResourceHelpers resourceHelpers;

    @Test
    void testAllMethods() {

        LocalDateTime start = LocalDateTime.now(UTC).minusHours(1L);
        LocalDateTime end = LocalDateTime.now(UTC).plusHours(1L);

        assertTrue(resourceHelpers.addEmailAggregation(ACCOUNT_ID, BUNDLE_NAME, APP_NAME, PAYLOAD1));
        assertTrue(resourceHelpers.addEmailAggregation(ACCOUNT_ID, BUNDLE_NAME, APP_NAME, PAYLOAD2));
        assertTrue(resourceHelpers.addEmailAggregation("other-account", BUNDLE_NAME, APP_NAME, PAYLOAD2));
        assertTrue(resourceHelpers.addEmailAggregation(ACCOUNT_ID, "other-bundle", APP_NAME, PAYLOAD2));
        assertTrue(resourceHelpers.addEmailAggregation(ACCOUNT_ID, BUNDLE_NAME, "other-app", PAYLOAD2));

        EmailAggregationKey key = new EmailAggregationKey(ACCOUNT_ID, BUNDLE_NAME, APP_NAME);

        List<EmailAggregation> aggregations = resourceHelpers.getEmailAggregation(key, start, end);
        assertEquals(2, aggregations.size());
        assertTrue(aggregations.stream().map(EmailAggregation::getAccountId).allMatch(ACCOUNT_ID::equals));
        assertTrue(aggregations.stream().map(EmailAggregation::getBundleName).allMatch(BUNDLE_NAME::equals));
        assertTrue(aggregations.stream().map(EmailAggregation::getApplicationName).allMatch(APP_NAME::equals));
        assertEquals(1, aggregations.stream().map(EmailAggregation::getPayload).filter(PAYLOAD1::equals).count());
        assertEquals(1, aggregations.stream().map(EmailAggregation::getPayload).filter(PAYLOAD2::equals).count());

        List<EmailAggregationKey> keys = resourceHelpers.getApplicationsWithPendingAggregation(start, end);
        assertEquals(4, keys.size());
        assertEquals(ACCOUNT_ID, keys.get(0).getAccountId());
        assertEquals(BUNDLE_NAME, keys.get(0).getBundle());
        assertEquals(APP_NAME, keys.get(0).getApplication());

        Integer purged = resourceHelpers.purgeOldAggregation(key, end);
        assertEquals(2, purged);

        aggregations = resourceHelpers.getEmailAggregation(key, start, end);
        assertEquals(0, aggregations.size());

        keys = resourceHelpers.getApplicationsWithPendingAggregation(start, end);
        assertEquals(3, keys.size());
    }

    @ParameterizedTest
    @MethodSource("constraintViolations")
    void addEmailAggregationWithConstraintViolations(String accountId, String bundleName, String applicationName, JsonObject payload) {
        assertFalse(resourceHelpers.addEmailAggregation(accountId, bundleName, applicationName, payload));
    }

    private static Stream<Arguments> constraintViolations() {
        return Stream.of(
                Arguments.of(ACCOUNT_ID, BUNDLE_NAME, APP_NAME, null),
                Arguments.of(ACCOUNT_ID, BUNDLE_NAME, null, PAYLOAD1),
                Arguments.of(null, BUNDLE_NAME, APP_NAME, PAYLOAD1),
                Arguments.of(null, BUNDLE_NAME, APP_NAME, PAYLOAD1)
        );
    }
}
