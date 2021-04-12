package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.routers.models.OldSettingsValueJsonForm;
import com.redhat.cloud.notifications.routers.models.OldSettingsValueJsonForm.Field;
import com.redhat.cloud.notifications.routers.models.SettingsValues;
import com.redhat.cloud.notifications.routers.models.SettingsValues.ApplicationSettingsValue;
import com.redhat.cloud.notifications.routers.models.SettingsValues.BundleSettingsValue;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.vertx.core.json.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class OldUserConfigServiceTest extends DbIsolatedTest {

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_NOTIFICATIONS_V_1_0;
    }

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    private OldSettingsValueJsonForm rhelPolicyForm(List<OldSettingsValueJsonForm> jsonForms) {
        for (OldSettingsValueJsonForm settingsValueJsonForm : jsonForms) {
            for (Field field : settingsValueJsonForm.fields) {
                if (field.name != null && field.name.startsWith("bundles[rhel].applications[policies]")) {
                    return settingsValueJsonForm;
                }
            }
        }

        return null;
    }

    private Map<EmailSubscriptionType, Boolean> extractNotificationValues(OldSettingsValueJsonForm settingsValueJsonForm, String bundle, String application) {
        Map<EmailSubscriptionType, Boolean> result = new HashMap<>();
        for (Field field : settingsValueJsonForm.fields) {
            for (EmailSubscriptionType type : EmailSubscriptionType.values()) {
                if (field.name != null && field.name.equals(String.format("bundles[%s].applications[%s].notifications[%s]", bundle, application, type))) {
                    result.put(type, (Boolean) field.initialValue);
                }
            }
        }

        return result;
    }

    private SettingsValues createSettingsValue(String bundle, String application, Boolean daily, Boolean instant) {
        ApplicationSettingsValue applicationSettingsValue = new ApplicationSettingsValue();
        applicationSettingsValue.notifications.put(EmailSubscriptionType.DAILY, daily);
        applicationSettingsValue.notifications.put(EmailSubscriptionType.INSTANT, instant);

        BundleSettingsValue bundleSettingsValue = new BundleSettingsValue();
        bundleSettingsValue.applications.put(application, applicationSettingsValue);

        SettingsValues settingsValues = new SettingsValues();
        settingsValues.bundles.put(bundle, bundleSettingsValue);

        return settingsValues;
    }

    @Test
    void testSettings() {
        String tenant = "empty";
        String username = "user";
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, username);
        Header identityHeader = TestHelpers.createIdentityHeader(identityHeaderValue);
        mockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerClientConfig.RbacAccess.FULL_ACCESS);

        String bundle = "rhel";
        String application = "policies";

        List<OldSettingsValueJsonForm> jsonForms = given()
                .header(identityHeader)
                .when().get("/user-config/email-preference")
                .then()
                .statusCode(200)
                .extract().body().jsonPath().getList(".", OldSettingsValueJsonForm.class);

        OldSettingsValueJsonForm rhelPolicy = rhelPolicyForm(jsonForms);
        assertNotNull(rhelPolicy, "RHEL policies not found");

        SettingsValues settingsValues = createSettingsValue(bundle, application, false, false);
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(settingsValues))
                .post("/user-config/email-preference")
                .then()
                .statusCode(200);
        jsonForms = given()
                .header(identityHeader)
                .when().get("/user-config/email-preference")
                .then()
                .statusCode(200)
                .extract().body().jsonPath().getList(".", OldSettingsValueJsonForm.class);
        rhelPolicy = rhelPolicyForm(jsonForms);
        assertNotNull(rhelPolicy, "RHEL policies not found");
        Map<EmailSubscriptionType, Boolean> initialValues = extractNotificationValues(rhelPolicy, bundle, application);

        assertEquals(initialValues, settingsValues.bundles.get(bundle).applications.get(application).notifications);

        // Daily to true
        settingsValues = createSettingsValue(bundle, application, true, false);
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(settingsValues))
                .post("/user-config/email-preference")
                .then()
                .statusCode(200);
        jsonForms = given()
                .header(identityHeader)
                .when().get("/user-config/email-preference")
                .then()
                .statusCode(200)
                .extract().body().jsonPath().getList(".", OldSettingsValueJsonForm.class);
        rhelPolicy = rhelPolicyForm(jsonForms);
        assertNotNull(rhelPolicy, "RHEL policies not found");
        initialValues = extractNotificationValues(rhelPolicy, bundle, application);

        assertEquals(initialValues, settingsValues.bundles.get(bundle).applications.get(application).notifications);

        // Instant to true
        settingsValues = createSettingsValue(bundle, application, false, true);
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(settingsValues))
                .post("/user-config/email-preference")
                .then()
                .statusCode(200);
        jsonForms = given()
                .header(identityHeader)
                .when().get("/user-config/email-preference")
                .then()
                .statusCode(200)
                .extract().body().jsonPath().getList(".", OldSettingsValueJsonForm.class);
        rhelPolicy = rhelPolicyForm(jsonForms);
        assertNotNull(rhelPolicy, "RHEL policies not found");
        initialValues = extractNotificationValues(rhelPolicy, bundle, application);

        assertEquals(initialValues, settingsValues.bundles.get(bundle).applications.get(application).notifications);

        // Both to true
        settingsValues = createSettingsValue(bundle, application, true, true);
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(settingsValues))
                .post("/user-config/email-preference")
                .then()
                .statusCode(200);
        jsonForms = given()
                .header(identityHeader)
                .when().get("/user-config/email-preference")
                .then()
                .statusCode(200)
                .extract().body().jsonPath().getList(".", OldSettingsValueJsonForm.class);
        rhelPolicy = rhelPolicyForm(jsonForms);
        assertNotNull(rhelPolicy, "RHEL policies not found");
        initialValues = extractNotificationValues(rhelPolicy, bundle, application);

        assertEquals(initialValues, settingsValues.bundles.get(bundle).applications.get(application).notifications);
    }

}
