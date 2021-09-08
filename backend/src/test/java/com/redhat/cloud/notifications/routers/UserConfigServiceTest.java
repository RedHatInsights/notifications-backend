package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.routers.models.SettingsValueJsonForm;
import com.redhat.cloud.notifications.routers.models.SettingsValueJsonForm.Field;
import com.redhat.cloud.notifications.routers.models.SettingsValues;
import com.redhat.cloud.notifications.routers.models.SettingsValues.ApplicationSettingsValue;
import com.redhat.cloud.notifications.routers.models.SettingsValues.BundleSettingsValue;
import com.redhat.cloud.notifications.routers.models.UserConfigPreferences;
import com.redhat.cloud.notifications.templates.EmailTemplate;
import com.redhat.cloud.notifications.templates.EmailTemplateFactory;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.vertx.core.json.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.INSTANT;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.TEXT;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class UserConfigServiceTest extends DbIsolatedTest {

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_NOTIFICATIONS_V_1_0;
    }

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EndpointEmailSubscriptionResources subscriptionResources;

    @InjectMock
    EmailTemplateFactory emailTemplateFactory;

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
        applicationSettingsValue.notifications.put(DAILY, daily);
        applicationSettingsValue.notifications.put(INSTANT, instant);

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

        Mockito.when(emailTemplateFactory.get(bundle, application)).thenCallRealMethod();

        SettingsValueJsonForm jsonForm = given()
                .header(identityHeader)
                .queryParam("bundleName", bundle)
                .when().get("/user-config/notification-preference")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().as(SettingsValueJsonForm.class);

        Field rhelPolicy = rhelPolicyForm(jsonForm);
        assertNotNull(rhelPolicy, "RHEL policies not found");

        SettingsValues settingsValues = createSettingsValue(bundle, application, false, false);
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(settingsValues))
                .post("/user-config/notification-preference")
                .then()
                .statusCode(200)
                .contentType(TEXT);
        jsonForm = given()
                .header(identityHeader)
                .when().get("/user-config/notification-preference?bundleName=rhel")
                .then()
                .statusCode(200)
                .contentType(JSON)
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
                .contentType(JSON)
                .extract().body().as(UserConfigPreferences.class);

        assertEquals(false, preferences.getDailyEmail());
        assertEquals(false, preferences.getInstantEmail());

        // Daily to true
        settingsValues = createSettingsValue(bundle, application, true, false);
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(settingsValues))
                .post("/user-config/notification-preference")
                .then()
                .statusCode(200)
                .contentType(TEXT);
        jsonForm = given()
                .header(identityHeader)
                .when().get("/user-config/notification-preference?bundleName=rhel")
                .then()
                .statusCode(200)
                .contentType(JSON)
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
                .contentType(JSON)
                .extract().body().as(UserConfigPreferences.class);

        assertEquals(true, preferences.getDailyEmail());
        assertEquals(false, preferences.getInstantEmail());

        // Instant to true
        settingsValues = createSettingsValue(bundle, application, false, true);
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(settingsValues))
                .post("/user-config/notification-preference")
                .then()
                .statusCode(200)
                .contentType(TEXT);
        jsonForm = given()
                .header(identityHeader)
                .when().get("/user-config/notification-preference?bundleName=rhel")
                .then()
                .statusCode(200)
                .contentType(JSON)
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
                .contentType(JSON)
                .extract().body().as(UserConfigPreferences.class);

        assertEquals(false, preferences.getDailyEmail());
        assertEquals(true, preferences.getInstantEmail());

        // Both to true
        settingsValues = createSettingsValue(bundle, application, true, true);
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(settingsValues))
                .post("/user-config/notification-preference")
                .then()
                .statusCode(200)
                .contentType(TEXT);
        jsonForm = given()
                .header(identityHeader)
                .when().get("/user-config/notification-preference?bundleName=rhel")
                .then()
                .statusCode(200)
                .contentType(JSON)
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
                .contentType(JSON)
                .extract().body().as(UserConfigPreferences.class);

        assertEquals(true, preferences.getDailyEmail());
        assertEquals(true, preferences.getInstantEmail());

        // does not fail if we have unknown apps in our bundle's settings
        try {
            subscriptionResources.subscribe(tenant, username, bundle, "not-found-app", DAILY)
                    .await().indefinitely();
            given()
                    .header(identityHeader)
                    .when()
                    .queryParam("bundleName", bundle)
                    .get("/user-config/notification-preference")
                    .then()
                    .statusCode(200)
                    .contentType(JSON);
        } finally {
            subscriptionResources.unsubscribe(tenant, username, "not-found-bundle", "not-found-app", DAILY)
                    .await().indefinitely();
        }

        // Fails if we don't specify the bundleName
        given()
                .header(identityHeader)
                .when()
                .get("/user-config/notification-preference")
                .then()
                .statusCode(400)
                .contentType(JSON);

        // does not add if we try to create unknown bundle/apps
        settingsValues = createSettingsValue("not-found-bundle-2", "not-found-app-2", true, true);
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(settingsValues))
                .post("/user-config/notification-preference")
                .then()
                .statusCode(200)
                .contentType(TEXT);

        assertNull(subscriptionResources.getEmailSubscription(tenant, username, "not-found-bundle-2", "not-found-app-2", DAILY)
                .await().indefinitely());
        assertNull(subscriptionResources.getEmailSubscription(tenant, username, "not-found-bundle", "not-found-app", INSTANT)
                .await().indefinitely());

        // Does not add event type if is not supported by the templates
        Mockito
                .when(emailTemplateFactory.get(bundle, application))
                .thenReturn(new EmailTemplate() {
                    @Override
                    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
                        return null;
                    }

                    @Override
                    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
                        return null;
                    }

                    @Override
                    public boolean isSupported(String eventType, EmailSubscriptionType type) {
                        return false;
                    }

                    @Override
                    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
                        return type == INSTANT;
                    }
                });

        jsonForm = given()
                .header(identityHeader)
                .when().get("/user-config/notification-preference?bundleName=rhel")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().as(SettingsValueJsonForm.class);
        rhelPolicy = rhelPolicyForm(jsonForm);
        assertNotNull(rhelPolicy, "RHEL policies not found");
        assertEquals(1, rhelPolicy.fields.get(0).fields.size());
        assertEquals("bundles[rhel].applications[policies].notifications[INSTANT]", rhelPolicy.fields.get(0).fields.get(0).name);
    }

}
