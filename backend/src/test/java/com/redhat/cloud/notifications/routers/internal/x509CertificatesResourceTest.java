package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.models.X509Certificate;
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
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.OK;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class x509CertificatesResourceTest extends DbIsolatedTest {


    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Test
    void testCrudX509Certificate() {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        Header intenralUserIdentity = TestHelpers.createTurnpikeIdentityHeader("internal_user", "not_use");

        final String bundleName = "bundle-name-x509-certificate";
        final String applicationName = "application-name-x509-certificate";
        String bundleId = createBundle(adminIdentity, bundleName, "Certificate Bundle", OK.getStatusCode()).get();
        createApp(adminIdentity, bundleId, applicationName, "Certificate Application", null, OK.getStatusCode());

        checkGatewayCertificate(intenralUserIdentity, bundleName, applicationName, "certificate data", UNAUTHORIZED);

        X509Certificate x509Certificate = new X509Certificate();
        x509Certificate.setSubjectDn("certificate data");
        x509Certificate.setBundle(bundleName);
        x509Certificate.setApplication(applicationName);
        x509Certificate.setSourceEnvironment("stage");
        X509Certificate createdCertificate = given()
            .header(adminIdentity)
            .contentType(JSON)
            .body(Json.encode(x509Certificate))
            .when()
            .post("/internal/x509Certificates")
            .then()
            .statusCode(OK.getStatusCode())
            .extract().as(X509Certificate.class);

        assertNotNull(createdCertificate);
        assertNotNull(createdCertificate.getId());
        UUID certificateId = createdCertificate.getId();

        x509Certificate = checkGatewayCertificate(intenralUserIdentity, bundleName, applicationName, "certificate data", OK);
        assertEquals("stage", x509Certificate.getSourceEnvironment());

        X509Certificate certificateUpdated = new X509Certificate();
        certificateUpdated.setSubjectDn("certificate data updated");
        certificateUpdated.setSourceEnvironment("prod");

        given()
            .header(adminIdentity)
            .contentType(JSON)
            .pathParam("certificateId", certificateId)
            .body(Json.encode(certificateUpdated))
            .when()
            .put("/internal/x509Certificates/{certificateId}")
            .then()
            .statusCode(OK.getStatusCode());

        checkGatewayCertificate(intenralUserIdentity, bundleName, applicationName, "certificate data", UNAUTHORIZED);
        x509Certificate = checkGatewayCertificate(intenralUserIdentity, bundleName, applicationName, "certificate data updated", OK);
        assertEquals("prod", x509Certificate.getSourceEnvironment());
        assertEquals("certificate data updated", x509Certificate.getSubjectDn());

        given()
            .header(adminIdentity)
            .contentType(JSON)
            .pathParam("certificateId", certificateId)
            .body(Json.encode(certificateUpdated))
            .when()
            .delete("/internal/x509Certificates/{certificateId}")
            .then()
            .statusCode(OK.getStatusCode());

        checkGatewayCertificate(intenralUserIdentity, bundleName, applicationName, "certificate data updated", UNAUTHORIZED);
    }

    @Test
    void testX509CertificateAPIRestrictions() {
        Header intenralUserIdentity = TestHelpers.createTurnpikeIdentityHeader("internal_user", "not_use");

        given()
            .header(intenralUserIdentity)
            .contentType(JSON)
            .body(Json.encode(new X509Certificate()))
            .when()
            .post("/internal/x509Certificates")
            .then()
            .statusCode(FORBIDDEN.getStatusCode());

        given()
            .header(intenralUserIdentity)
            .contentType(JSON)
            .pathParam("certificateId", UUID.randomUUID().toString())
            .body(Json.encode(new X509Certificate()))
            .when()
            .put("/internal/x509Certificates/{certificateId}")
            .then()
            .statusCode(FORBIDDEN.getStatusCode());

        given()
            .header(intenralUserIdentity)
            .contentType(JSON)
            .pathParam("certificateId", UUID.randomUUID().toString())
            .body(Json.encode(new X509Certificate()))
            .when()
            .delete("/internal/x509Certificates/{certificateId}")
            .then()
            .statusCode(FORBIDDEN.getStatusCode());

    }

    private static X509Certificate checkGatewayCertificate(Header adminIdentity, String bundleName, String applicationName, String certificateData, Response.Status status) {
        String responseBody = given()
            .header(adminIdentity)
            .contentType(JSON)
            .param("bundle", bundleName)
            .param("application", applicationName)
            .param("certificateSubjectDn", certificateData)
            .when()
            .get("/internal/validation/certificate")
            .then()
            .statusCode(status.getStatusCode()).extract().asString();

        if (Response.Status.OK == status) {
            JsonObject jsonApp = new JsonObject(responseBody);
            return jsonApp.mapTo(X509Certificate.class);
        }
        return null;
    }

}
