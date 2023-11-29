package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.models.GatewayCertificate;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static com.redhat.cloud.notifications.CrudTestHelpers.createApp;
import static com.redhat.cloud.notifications.CrudTestHelpers.createBundle;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class GatewayCertificateResourceTest extends DbIsolatedTest {


    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Test
    void testCrudGatewayCertificate() {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);

        final String bundleName = "bundle-name-gateway-certificate";
        final String applicationName = "application-name-gateway-certificate";
        String bundleId = createBundle(adminIdentity, bundleName, "GW Bundle", OK.getStatusCode()).get();
        createApp(adminIdentity, bundleId, applicationName, "GW Application", null, OK.getStatusCode());

        checkGatewayCertificate(adminIdentity, bundleName, applicationName, "certificate data", UNAUTHORIZED);

        GatewayCertificate gatewayCertificate = new GatewayCertificate();
        gatewayCertificate.setCertificateData("certificate data");
        gatewayCertificate.setBundle(bundleName);
        gatewayCertificate.setApplication(applicationName);
        gatewayCertificate.setEnvironment("stage");
        GatewayCertificate createdCertificate = given()
            .header(adminIdentity)
            .contentType(JSON)
            .body(Json.encode(gatewayCertificate))
            .when()
            .post("/internal/gatewayCertificate")
            .then()
            .statusCode(OK.getStatusCode())
            .extract().as(GatewayCertificate.class);

        assertNotNull(createdCertificate);
        assertNotNull(createdCertificate.getId());
        UUID certificateId = createdCertificate.getId();

        gatewayCertificate = checkGatewayCertificate(adminIdentity, bundleName, applicationName, "certificate data", OK);
        assertEquals("stage", gatewayCertificate.getEnvironment());

        GatewayCertificate gatewayCertificateUpdated = new GatewayCertificate();
        gatewayCertificateUpdated.setCertificateData("certificate data updated");
        gatewayCertificateUpdated.setEnvironment("prod");

        given()
            .header(adminIdentity)
            .contentType(JSON)
            .pathParam("gatewayCertificateId", certificateId)
            .body(Json.encode(gatewayCertificateUpdated))
            .when()
            .put("/internal/gatewayCertificate/{gatewayCertificateId}")
            .then()
            .statusCode(OK.getStatusCode());

        checkGatewayCertificate(adminIdentity, bundleName, applicationName, "certificate data", UNAUTHORIZED);
        gatewayCertificate = checkGatewayCertificate(adminIdentity, bundleName, applicationName, "certificate data updated", OK);
        assertEquals("prod", gatewayCertificate.getEnvironment());
        assertEquals("certificate data updated", gatewayCertificate.getCertificateData());

        given()
            .header(adminIdentity)
            .contentType(JSON)
            .pathParam("gatewayCertificateId", certificateId)
            .body(Json.encode(gatewayCertificateUpdated))
            .when()
            .delete("/internal/gatewayCertificate/{gatewayCertificateId}")
            .then()
            .statusCode(OK.getStatusCode());

        checkGatewayCertificate(adminIdentity, bundleName, applicationName, "certificate data updated", UNAUTHORIZED);
    }

    private static GatewayCertificate checkGatewayCertificate(Header adminIdentity, String bundleName, String applicationName, String certificateData, Response.Status status) {
        String responseBody = given()
            .header(adminIdentity)
            .contentType(JSON)
            .param("bundle", bundleName)
            .param("application", applicationName)
            .param("certificate", certificateData)
            .when()
            .get("/internal/validation/certificate-according-bundle-and-app")
            .then()
            .statusCode(status.getStatusCode()).extract().asString();

        if (Response.Status.OK == status) {
            JsonObject jsonApp = new JsonObject(responseBody);
            return jsonApp.mapTo(GatewayCertificate.class);
        }
        return null;
    }

}
