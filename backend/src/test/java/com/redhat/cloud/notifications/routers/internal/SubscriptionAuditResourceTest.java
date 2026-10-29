package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.SubscriptionChangeLogRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SubscriptionType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class SubscriptionAuditResourceTest extends DbIsolatedTest {

    private static final String STATS_PATH = "/internal/subscription-audit/stats";

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    SubscriptionChangeLogRepository subscriptionChangeLogRepository;

    @Test
    void testGetUnsubscribeStatsReturnsCorrectData() {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("admin-user", adminRole);

        Bundle bundle = resourceHelpers.createBundle("audit-bundle");
        Application app = resourceHelpers.createApplication(bundle.getId(), "audit-app");
        EventType eventType = resourceHelpers.createEventType(app.getId(), "audit-event");

        subscriptionChangeLogRepository.logChange("org-1", "user-1", eventType.getId(), SubscriptionType.INSTANT, false);
        subscriptionChangeLogRepository.logChange("org-2", "user-2", eventType.getId(), SubscriptionType.INSTANT, false);
        subscriptionChangeLogRepository.logChange("org-1", "user-1", eventType.getId(), SubscriptionType.INSTANT, true);

        String since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).toString();

        given()
            .header(adminIdentity)
            .contentType(JSON)
            .queryParam("since", since)
            .when()
            .get(STATS_PATH)
            .then()
            .statusCode(200)
            .body("$", hasSize(1))
            .body("[0].event_type_id", is(eventType.getId().toString()))
            .body("[0].unsubscribe_count", is(2));
    }

    @Test
    void testGetUnsubscribeStatsReturnsForbiddenWithoutAdminRole() {
        Header nonAdminIdentity = TestHelpers.createTurnpikeIdentityHeader("regular-user", "some-non-admin-role");

        given()
            .header(nonAdminIdentity)
            .contentType(JSON)
            .queryParam("since", OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).toString())
            .when()
            .get(STATS_PATH)
            .then()
            .statusCode(403);
    }

    @Test
    void testGetUnsubscribeStatsReturnsBadRequestWhenSinceMissing() {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("admin-user", adminRole);

        given()
            .header(adminIdentity)
            .contentType(JSON)
            .when()
            .get(STATS_PATH)
            .then()
            .statusCode(400);
    }

    @Test
    void testGetUnsubscribeStatsReturnsEmptyWhenNoDataMatches() {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("admin-user", adminRole);

        String futureSince = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).toString();

        given()
            .header(adminIdentity)
            .contentType(JSON)
            .queryParam("since", futureSince)
            .when()
            .get(STATS_PATH)
            .then()
            .statusCode(200)
            .body("$", empty());
    }
}
