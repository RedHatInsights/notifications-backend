package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.routers.models.SettingsValueJsonForm;
import com.redhat.cloud.notifications.routers.models.SettingsValueJsonForm.Field;
import com.redhat.cloud.notifications.routers.models.SettingsValues;
import com.redhat.cloud.notifications.routers.models.SettingsValues.ApplicationSettingsValue;
import com.redhat.cloud.notifications.routers.models.SettingsValues.BundleSettingsValue;
import com.redhat.cloud.notifications.routers.models.UserConfigPreferences;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.vertx.core.json.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class UserConfigServiceTest extends DbIsolatedTest {

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_NOTIFICATIONS_V_1_0;
    }

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    private Field rhelPolicyForm(SettingsValueJsonForm jsonForm) {
        for (Field section : jsonForm.fields.get(0).sections) {
            if (section.name.equals("policies")) {
                return section;
            }
        }

        return null;
    }

    private Map<EmailSubscriptionType, Boolean> extractNotificationValues(Field sectionField, String bundle, String application) {
        Map<EmailSubscriptionType, Boolean> result = new HashMap<>();
        for (Field field : sectionField.fields.get(0).fields) {
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

        SettingsValueJsonForm jsonForm = given()
                .header(identityHeader)
                .queryParam("bundleName", bundle)
                .when().get("/user-config/notification-preference")
                .then()
                .statusCode(200)
                .extract().body().as(SettingsValueJsonForm.class);

        Field rhelPolicy = rhelPolicyForm(jsonForm);
        assertNotNull(rhelPolicy, "RHEL policies not found");

        SettingsValues settingsValues = createSettingsValue(bundle, application, false, false);
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(settingsValues))
                .post("/user-config/notification-preference")
                .then()
                .statusCode(200);
        jsonForm = given()
                .header(identityHeader)
                .when().get("/user-config/notification-preference?bundleName=rhel")
                .then()
                .statusCode(200)
                .extract().body().as(SettingsValueJsonForm.class);
        rhelPolicy = rhelPolicyForm(jsonForm);
        assertNotNull(rhelPolicy, "RHEL policies not found");
        Map<EmailSubscriptionType, Boolean> initialValues = extractNotificationValues(rhelPolicy, bundle, application);

        assertEquals(initialValues, settingsValues.bundles.get(bundle).applications.get(application).notifications);
        UserConfigPreferences preferences = given()
                .header(identityHeader)
                .when().get(String.format("/user-config/notification-preference/%s/%s", bundle, application))
                .then()
                .statusCode(200)
                .extract().body().as(UserConfigPreferences.class);

        assertEquals(false, preferences.getDailyEmail());
        assertEquals(false, preferences.getInstantEmail());

        // Daily to true
        settingsValues = createSettingsValue(bundle, application, true, false);
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(settingsValues))
                .post("/user-config/notification-preference")
                .then()
                .statusCode(200);
        jsonForm = given()
                .header(identityHeader)
                .when().get("/user-config/notification-preference?bundleName=rhel")
                .then()
                .statusCode(200)
                .extract().body().as(SettingsValueJsonForm.class);
        rhelPolicy = rhelPolicyForm(jsonForm);
        assertNotNull(rhelPolicy, "RHEL policies not found");
        initialValues = extractNotificationValues(rhelPolicy, bundle, application);

        assertEquals(initialValues, settingsValues.bundles.get(bundle).applications.get(application).notifications);
        preferences = given()
                .header(identityHeader)
                .when().get(String.format("/user-config/notification-preference/%s/%s", bundle, application))
                .then()
                .statusCode(200)
                .extract().body().as(UserConfigPreferences.class);

        assertEquals(true, preferences.getDailyEmail());
        assertEquals(false, preferences.getInstantEmail());

        // Instant to true
        settingsValues = createSettingsValue(bundle, application, false, true);
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(settingsValues))
                .post("/user-config/notification-preference")
                .then()
                .statusCode(200);
        jsonForm = given()
                .header(identityHeader)
                .when().get("/user-config/notification-preference?bundleName=rhel")
                .then()
                .statusCode(200)
                .extract().body().as(SettingsValueJsonForm.class);
        rhelPolicy = rhelPolicyForm(jsonForm);
        assertNotNull(rhelPolicy, "RHEL policies not found");
        initialValues = extractNotificationValues(rhelPolicy, bundle, application);

        assertEquals(initialValues, settingsValues.bundles.get(bundle).applications.get(application).notifications);
        preferences = given()
                .header(identityHeader)
                .when().get(String.format("/user-config/notification-preference/%s/%s", bundle, application))
                .then()
                .statusCode(200)
                .extract().body().as(UserConfigPreferences.class);

        assertEquals(false, preferences.getDailyEmail());
        assertEquals(true, preferences.getInstantEmail());

        // Both to true
        settingsValues = createSettingsValue(bundle, application, true, true);
        given()
                .header(identityHeader)
                .when()
                .contentType(ContentType.JSON)
                .body(Json.encode(settingsValues))
                .post("/user-config/notification-preference")
                .then()
                .statusCode(200);
        jsonForm = given()
                .header(identityHeader)
                .when().get("/user-config/notification-preference?bundleName=rhel")
                .then()
                .statusCode(200)
                .extract().body().as(SettingsValueJsonForm.class);
        rhelPolicy = rhelPolicyForm(jsonForm);
        assertNotNull(rhelPolicy, "RHEL policies not found");
        initialValues = extractNotificationValues(rhelPolicy, bundle, application);

        assertEquals(initialValues, settingsValues.bundles.get(bundle).applications.get(application).notifications);
        preferences = given()
                .header(identityHeader)
                .when().get(String.format("/user-config/notification-preference/%s/%s", bundle, application))
                .then()
                .statusCode(200)
                .extract().body().as(UserConfigPreferences.class);

        assertEquals(true, preferences.getDailyEmail());
        assertEquals(true, preferences.getInstantEmail());
    }

}
