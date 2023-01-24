package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Bundle;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class BehaviorGroupCleanupResourceTest extends DbIsolatedTest {

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Inject
    ResourceHelpers resourceHelpers;

    @Test
    void testConfirmWithoutInitiate() {
        Header header = TestHelpers.createTurnpikeIdentityHeader("admin", adminRole);
        given()
                .header(header)
                .pathParam("orgId", "foo")
                .when()
                .put("/internal/behavior-group-cleanup/{orgId}/confirm")
                .then()
                .statusCode(400);
    }

    @Test
    void testInitiateThenConfirm() {
        Bundle bundle = resourceHelpers.createBundle("bundle", "Bundle");
        resourceHelpers.createBehaviorGroup("account-1", "org-1", "Behavior group 1", bundle.getId());
        resourceHelpers.createBehaviorGroup("account-1", "org-1", "Behavior group 2", bundle.getId());
        resourceHelpers.createBehaviorGroup("account-2", "org-2", "Behavior group 3", bundle.getId());

        Header header = TestHelpers.createTurnpikeIdentityHeader("admin", adminRole);

        given()
                .header(header)
                .pathParam("orgId", "org-1")
                .when()
                .put("/internal/behavior-group-cleanup/{orgId}/initiate")
                .then()
                .statusCode(204);

        String count = given()
                .header(header)
                .pathParam("orgId", "org-1")
                .when()
                .put("/internal/behavior-group-cleanup/{orgId}/confirm")
                .then()
                .statusCode(200)
                .extract().asString();
        assertEquals("2", count);

        given()
                .header(header)
                .pathParam("orgId", "org-2")
                .when()
                .put("/internal/behavior-group-cleanup/{orgId}/initiate")
                .then()
                .statusCode(204);

        count = given()
                .header(header)
                .pathParam("orgId", "org-2")
                .when()
                .put("/internal/behavior-group-cleanup/{orgId}/confirm")
                .then()
                .statusCode(200)
                .extract().asString();
        assertEquals("1", count);
    }

    @Test
    void testInitiateThenWaitOneMinuteThenConfirm() throws InterruptedException {
        Header header = TestHelpers.createTurnpikeIdentityHeader("admin", adminRole);

        given()
                .header(header)
                .pathParam("orgId", "foo")
                .when()
                .put("/internal/behavior-group-cleanup/{orgId}/initiate")
                .then()
                .statusCode(204);

        Thread.sleep(61000);

        given()
                .header(header)
                .pathParam("orgId", "foo")
                .when()
                .put("/internal/behavior-group-cleanup/{orgId}/confirm")
                .then()
                .statusCode(400);
    }
}
