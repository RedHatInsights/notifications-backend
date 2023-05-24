package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.CrudTestHelpers;
import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.EmailSubscriptionRepository;
import com.redhat.cloud.notifications.db.repositories.EventTypeRepository;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EmailSubscription;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.EventTypeEmailSubscription;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.Template;
import com.redhat.cloud.notifications.routers.models.SettingsValueByEventTypeJsonForm;
import com.redhat.cloud.notifications.routers.models.SettingsValueJsonForm;
import com.redhat.cloud.notifications.routers.models.SettingsValueJsonForm.Field;
import com.redhat.cloud.notifications.routers.models.SettingsValues;
import com.redhat.cloud.notifications.routers.models.SettingsValues.ApplicationSettingsValue;
import com.redhat.cloud.notifications.routers.models.SettingsValues.BundleSettingsValue;
import com.redhat.cloud.notifications.routers.models.SettingsValuesByEventType;
import com.redhat.cloud.notifications.routers.models.UserConfigPreferences;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.PersistenceException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.CrudTestHelpers.createAggregationEmailTemplate;
import static com.redhat.cloud.notifications.CrudTestHelpers.createInstantEmailTemplate;
import static com.redhat.cloud.notifications.CrudTestHelpers.createTemplate;
import static com.redhat.cloud.notifications.CrudTestHelpers.deleteAggregationEmailTemplate;
import static com.redhat.cloud.notifications.CrudTestHelpers.deleteInstantEmailTemplate;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DRAWER;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.INSTANT;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static io.restassured.http.ContentType.TEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class UserConfigResourceTest extends DbIsolatedTest {

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_NOTIFICATIONS_V_1_0;
        featureFlipper.setInstantEmailsEnabled(true);
    }

    @AfterEach
    void afterEach() {
        featureFlipper.setUseEventTypeForSubscriptionEnabled(false);
        featureFlipper.setInstantEmailsEnabled(false);
    }

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    EmailSubscriptionRepository emailSubscriptionRepository;

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @InjectSpy
    ApplicationRepository applicationRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EventTypeRepository eventTypeRepository;


    private Field rhelPolicyForm(SettingsValueJsonForm jsonForm) {
        for (Field section : jsonForm.fields.get(0).sections) {
            if (section.name.equals("policies")) {
                return section;
            }
        }

        return null;
    }

    private SettingsValueByEventTypeJsonForm.EventTypes rhelPolicyForm(SettingsValueByEventTypeJsonForm settingsValuesByEventType) {
        for (String bundleName : settingsValuesByEventType.bundles.keySet()) {
            if (settingsValuesByEventType.bundles.get(bundleName).applications.containsKey("policies")) {
                return settingsValuesByEventType.bundles.get(bundleName).applications.get("policies");
            }
        }
        return null;
    }

    private Map<EmailSubscriptionType, Boolean> extractNotificationValues(List<SettingsValueByEventTypeJsonForm.EventType> eventTypes, String bundle, String application, String eventName) {
        Map<EmailSubscriptionType, Boolean> result = new HashMap<>();
        for (SettingsValueByEventTypeJsonForm.EventType eventType : eventTypes) {
            for (SettingsValueByEventTypeJsonForm.Field field : eventType.fields) {
                for (EmailSubscriptionType type : EmailSubscriptionType.values()) {
                    if (field.name != null && field.name.equals(String.format("bundles[%s].applications[%s].eventTypes[%s].emailSubscriptionTypes[%s]", bundle, application, eventName, type))) {
                        result.put(type, (Boolean) field.initialValue);
                    }
                }
            }
        }

        return result;
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

    private SettingsValuesByEventType createSettingsValue(String bundle, String application, String eventType, Boolean daily, Boolean instant, Boolean drawer) {

        SettingsValuesByEventType.EventTypeSettingsValue eventTypeSettingsValue = new SettingsValuesByEventType.EventTypeSettingsValue();
        eventTypeSettingsValue.emailSubscriptionTypes.put(DAILY, daily);
        eventTypeSettingsValue.emailSubscriptionTypes.put(INSTANT, instant);
        if (featureFlipper.isDrawerEnabled()) {
            eventTypeSettingsValue.emailSubscriptionTypes.put(DRAWER, drawer);
        }

        SettingsValuesByEventType.ApplicationSettingsValue applicationSettingsValue = new SettingsValuesByEventType.ApplicationSettingsValue();
        applicationSettingsValue.eventTypes.put(eventType, eventTypeSettingsValue);

        SettingsValuesByEventType.BundleSettingsValue bundleSettingsValue = new SettingsValuesByEventType.BundleSettingsValue();
        bundleSettingsValue.applications.put(application, applicationSettingsValue);

        SettingsValuesByEventType settingsValues = new SettingsValuesByEventType();
        settingsValues.bundles.put(bundle, bundleSettingsValue);

        return settingsValues;
    }

    @Test
    void testLegacySettings() {
        testSettings();
    }

    @Test
    void testLegacySettingsWithDrawerEnabled() {
        try {
            featureFlipper.setDrawerEnabled(true);
            testSettings();
        } finally {
            featureFlipper.setDrawerEnabled(false);
        }
    }

    void testSettings() {
        String accountId = "empty";
        String orgId = "empty";
        String username = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, username);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        String bundle = "rhel";
        String application = "policies";
        String eventType = "policy-triggered";

        String instantTemplateId = createInstantTemplate(bundle, application, eventType);
        String aggregationTemplateId = createAggregationTemplate(bundle, application);

        SettingsValueJsonForm jsonForm = getPreferencesByBundle(identityHeader, bundle);

        Field rhelPolicy = rhelPolicyForm(jsonForm);
        assertNotNull(rhelPolicy, "RHEL policies not found");

        SettingsValues settingsValues = createSettingsValue(bundle, application, false, false);

        featureFlipper.setInstantEmailsEnabled(false);
        postPreferences(identityHeader, settingsValues, 400);

        featureFlipper.setInstantEmailsEnabled(true);
        postPreferences(identityHeader, settingsValues, 200);

        featureFlipper.setInstantEmailsEnabled(false);
        jsonForm = getPreferencesByBundle(identityHeader, bundle);
        rhelPolicy = rhelPolicyForm(jsonForm);
        boolean instantEmailSettingsReturned = extractNotificationValues(rhelPolicy, bundle, application)
                .keySet().stream().anyMatch(INSTANT::equals);
        assertFalse(instantEmailSettingsReturned, "Instant email subscription settings should not be returned when instant emails are disabled");

        featureFlipper.setInstantEmailsEnabled(true);
        jsonForm = getPreferencesByBundle(identityHeader, bundle);
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
        postPreferences(identityHeader, settingsValues, 200);
        jsonForm = getPreferencesByBundle(identityHeader, bundle);
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
        postPreferences(identityHeader, settingsValues, 200);
        jsonForm = getPreferencesByBundle(identityHeader, bundle);
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
        postPreferences(identityHeader, settingsValues, 200);
        jsonForm = getPreferencesByBundle(identityHeader, bundle);
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
        emailSubscriptionRepository.subscribe(accountId, orgId, username, bundle, "not-found-app", DAILY);

        getPreferencesByBundle(identityHeader, bundle);

        emailSubscriptionRepository.unsubscribe(orgId, username, "not-found-bundle", "not-found-app", DAILY);

        // Fails if we don't specify the bundleName
        given()
                .header(identityHeader)
                .when()
                .get("/user-config/notification-preference")
                .then()
                .statusCode(400)
                .contentType(JSON);

        // does not add if we try to create unknown bundle/apps
        SettingsValues settingsValue = createSettingsValue("not-found-bundle-2", "not-found-app-2", true, true);
        postPreferences(identityHeader, settingsValues, 200);
        assertNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, "not-found-bundle-2", "not-found-app-2", DAILY));
        assertNull(emailSubscriptionRepository.getEmailSubscription(orgId, username, "not-found-bundle", "not-found-app", INSTANT));

        // Does not add event type if is not supported by the templates
        deleteAggregationTemplate(aggregationTemplateId);
        SettingsValueJsonForm settingsValueJsonForm = given()
                .header(identityHeader)
                .when().get("/user-config/notification-preference?bundleName=rhel")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().as(SettingsValueJsonForm.class);
        Field rhelPolicy2 = rhelPolicyForm(settingsValueJsonForm);
        assertNotNull(rhelPolicy2, "RHEL policies not found");
        assertEquals(1, rhelPolicy2.fields.get(0).fields.size());
        assertEquals("bundles[rhel].applications[policies].notifications[INSTANT]", rhelPolicy2.fields.get(0).fields.get(0).name);

        // Skip the application if there are no supported types
        deleteInstantTemplate(instantTemplateId);
        settingsValueJsonForm = given()
                .header(identityHeader)
                .when().get("/user-config/notification-preference?bundleName=rhel")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().as(SettingsValueJsonForm.class);
        rhelPolicy = rhelPolicyForm(settingsValueJsonForm);
        assertNull(rhelPolicy, "RHEL policies was not supposed to be here");

    }

    private SettingsValueJsonForm getPreferencesByBundle(Header identityHeader, String bundleName) {
        return given()
                .header(identityHeader)
                .queryParam("bundleName", bundleName)
                .when().get("/user-config/notification-preference")
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().as(SettingsValueJsonForm.class);
    }

    private void postPreferences(Header identityHeader, SettingsValues settingsValues, int expectedStatusCode) {
        given()
                .header(identityHeader)
                .contentType(JSON)
                .body(Json.encode(settingsValues))
                .when()
                .post("/user-config/notification-preference")
                .then()
                .statusCode(expectedStatusCode)
                .contentType(TEXT);
    }

    @Test
    void testMirroringUpdateToEmailSubscriptionByEventType() {
        String accountId = "empty";
        String orgId = "empty";
        String username = "user";
        String username2 = "user2";

        String bundle = "rhel";
        String application = "policies";

        UUID newEventTypeId = resourceHelpers.createEventType(bundle, application, "new-event-type");
        List<EmailSubscription> emailSubscriptionList = emailSubscriptionRepository.getEmailSubscriptionsForUser(orgId, username);
        List<EventTypeEmailSubscription> eventTypeEmailSubscriptionList = emailSubscriptionRepository.getEmailSubscriptionsPerEventTypeForUser(orgId, username);
        assertEquals(0, emailSubscriptionList.size());
        assertEquals(0, eventTypeEmailSubscriptionList.size());

        // users 1 and 2 subscribes to emails
        emailSubscriptionRepository.subscribe(accountId, orgId, username, bundle, application, INSTANT);
        emailSubscriptionRepository.subscribe(accountId, orgId, username2, bundle, application, INSTANT);
        emailSubscriptionList = emailSubscriptionRepository.getEmailSubscriptionsForUser(orgId, username);
        assertEquals(1, emailSubscriptionList.size());
        emailSubscriptionList = emailSubscriptionRepository.getEmailSubscriptionsForUser(orgId, username2);
        assertEquals(1, emailSubscriptionList.size());
        eventTypeEmailSubscriptionList = emailSubscriptionRepository.getEmailSubscriptionsPerEventTypeForUser(orgId, username);
        assertEquals(2, eventTypeEmailSubscriptionList.size());
        assertEquals(1, eventTypeEmailSubscriptionList.stream().filter(t -> t.getEventType().getId().equals(newEventTypeId)).count());
        eventTypeEmailSubscriptionList = emailSubscriptionRepository.getEmailSubscriptionsPerEventTypeForUser(orgId, username2);
        assertEquals(2, eventTypeEmailSubscriptionList.size());
        assertEquals(1, eventTypeEmailSubscriptionList.stream().filter(t -> t.getEventType().getId().equals(newEventTypeId)).count());

        // user 2 unsubscribe
        emailSubscriptionRepository.unsubscribe(orgId, username2, bundle, application, INSTANT);
        eventTypeEmailSubscriptionList = emailSubscriptionRepository.getEmailSubscriptionsPerEventTypeForUser(orgId, username2);
        emailSubscriptionList = emailSubscriptionRepository.getEmailSubscriptionsForUser(orgId, username2);
        assertEquals(0, eventTypeEmailSubscriptionList.size());
        assertEquals(0, emailSubscriptionList.size());

        // user 1 subscriptions are still here
        emailSubscriptionList = emailSubscriptionRepository.getEmailSubscriptionsForUser(orgId, username);
        eventTypeEmailSubscriptionList = emailSubscriptionRepository.getEmailSubscriptionsPerEventTypeForUser(orgId, username);
        assertEquals(1, emailSubscriptionList.size());
        assertEquals(2, eventTypeEmailSubscriptionList.size());
    }

    @Test
    void testLegacySettingsByEventType() {
        testSettingsByEventType();
    }

    @Test
    void testSettingsByEventTypeWithDrawerEnabled() {
        try {
            featureFlipper.setDrawerEnabled(true);
            testSettingsByEventType();
        } finally {
            featureFlipper.setDrawerEnabled(false);
        }
    }

    void testSettingsByEventType() {
        String path = "/user-config/notification-event-type-preference";
        String accountId = "empty";
        String orgId = "empty";
        String username = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, username);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        String bundle = "rhel";
        String application = "policies";
        String eventType = "policy-triggered";

        String instantTemplateId = createInstantTemplate(bundle, application, eventType);
        String aggregationTemplateId = createAggregationTemplate(bundle, application);

        // should return code 400 because the subscription by event type feature is not available yet
        given()
            .header(identityHeader)
            .queryParam("bundleName", bundle)
            .when().get(path)
            .then()
            .statusCode(400);

        featureFlipper.setUseEventTypeForSubscriptionEnabled(true);

        SettingsValueByEventTypeJsonForm settingsValuesByEventType = given()
            .header(identityHeader)
            .queryParam("bundleName", bundle)
            .when().get(path)
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(SettingsValueByEventTypeJsonForm.class);

        SettingsValueByEventTypeJsonForm.EventTypes rhelPolicy = rhelPolicyForm(settingsValuesByEventType);
        assertNotNull(rhelPolicy, "RHEL policies not found");
        assertNull(rhelPolicy.eventTypes.get(0).fields.get(0).infoMessage);

        Application applicationPolicies = new Application();
        applicationPolicies.setName("policies");
        when(applicationRepository.getApplicationsWithForcedEmail(any(), anyString())).thenReturn(List.of(applicationPolicies));

        settingsValuesByEventType = given()
            .header(identityHeader)
            .when().get(path)
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(SettingsValueByEventTypeJsonForm.class);
        rhelPolicy = rhelPolicyForm(settingsValuesByEventType);
        assertNotNull(rhelPolicy.eventTypes.get(0).fields.get(0).infoMessage);

        featureFlipper.setInstantEmailsEnabled(false);
        SettingsValuesByEventType settingsValues = createSettingsValue(bundle, application, eventType, true, true, false);
        postPreferencesByEventType(path, identityHeader, settingsValues, 400);

        featureFlipper.setInstantEmailsEnabled(true);
        postPreferencesByEventType(path, identityHeader, settingsValues, 200);

        featureFlipper.setInstantEmailsEnabled(false);
        SettingsValueByEventTypeJsonForm settingsValue = getPreferencesByEventType(path, identityHeader);
        rhelPolicy = rhelPolicyForm(settingsValue);
        boolean instantEmailSettingsReturned = extractNotificationValues(rhelPolicy.eventTypes, bundle, application, eventType)
                .keySet().stream().anyMatch(INSTANT::equals);
        assertFalse(instantEmailSettingsReturned, "Instant email subscription settings should not be returned when instant emails are disabled");

        featureFlipper.setInstantEmailsEnabled(true);

        // Daily and Instant to false
        updateAndCheckUserPreference(path, identityHeader, bundle, application, eventType, false, false, true);

        // Daily to true
        updateAndCheckUserPreference(path, identityHeader, bundle, application, eventType, true, false, true);

        // Instant to true
        updateAndCheckUserPreference(path, identityHeader, bundle, application, eventType, false, true, true);

        // Both to true
        updateAndCheckUserPreference(path, identityHeader, bundle, application, eventType, true, true, true);

        if (featureFlipper.isDrawerEnabled()) {
            // Daily and Instant to false
            updateAndCheckUserPreference(path, identityHeader, bundle, application, eventType, false, false, false);

            // Daily to true
            updateAndCheckUserPreference(path, identityHeader, bundle, application, eventType, true, false, false);

            // Instant to true
            updateAndCheckUserPreference(path, identityHeader, bundle, application, eventType, false, true, false);

            // Both to true
            updateAndCheckUserPreference(path, identityHeader, bundle, application, eventType, true, true, false);
        }

        // Fail if we have unknown event type on subscribe, but nothing will be added on database
        assertThrows(PersistenceException.class, () -> {
            emailSubscriptionRepository.subscribeEventType(orgId, username, UUID.randomUUID(), DAILY);
        });

        // not fail if we have unknown event type on unsubscibe, but nb affected rows must be 0
        assertEquals(0, emailSubscriptionRepository.unsubscribeEventType(orgId, username, UUID.randomUUID(), DAILY));

        // does not add if we try to create unknown bundle/apps
        settingsValues = createSettingsValue("not-found-bundle-2", "not-found-app-2", eventType, true, true, true);
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(settingsValues))
            .post(path)
            .then()
            .statusCode(200)
            .contentType(TEXT);
        settingsValuesByEventType = given()
            .header(identityHeader)
            .when().get(path)
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(SettingsValueByEventTypeJsonForm.class);
        rhelPolicy = rhelPolicyForm(settingsValuesByEventType);
        assertNotNull(rhelPolicy, "RHEL policies not found");
        Map<EmailSubscriptionType, Boolean> initialValues = extractNotificationValues(rhelPolicy.eventTypes, "not-found-bundle-2", "not-found-app-2", eventType);
        assertEquals(0, initialValues.size());

        // Does not add event type if is not supported by the templates
        deleteAggregationTemplate(aggregationTemplateId);
        SettingsValueByEventTypeJsonForm settingsValueJsonForm = given()
            .header(identityHeader)
            .when().get(path)
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(SettingsValueByEventTypeJsonForm.class);
        rhelPolicy = rhelPolicyForm(settingsValueJsonForm);
        assertNotNull(rhelPolicy, "RHEL policies not found");

        Map<EmailSubscriptionType, Boolean> notificationPreferenes = extractNotificationValues(rhelPolicy.eventTypes, bundle, application, eventType);

        if (featureFlipper.isDrawerEnabled()) {
            assertEquals(2, notificationPreferenes.size());
            assertTrue(notificationPreferenes.containsKey(DRAWER));
        } else {
            assertEquals(1, notificationPreferenes.size());
        }
        assertTrue(notificationPreferenes.containsKey(INSTANT));

        // Skip the application if there are no supported types
        deleteInstantTemplate(instantTemplateId);
        settingsValueJsonForm = given()
            .header(identityHeader)
            .when().get(path)
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(SettingsValueByEventTypeJsonForm.class);
        rhelPolicy = rhelPolicyForm(settingsValueJsonForm);
        if (featureFlipper.isDrawerEnabled()) {
            // drawer type will be always supported
            assertNotNull(rhelPolicy);
            assertEquals(1, settingsValueJsonForm.bundles.size());
            notificationPreferenes = extractNotificationValues(rhelPolicy.eventTypes, bundle, application, eventType);
            assertTrue(notificationPreferenes.containsKey(DRAWER));
        } else {
            assertNull(rhelPolicy, "RHEL policies was not supposed to be here");
            assertEquals(0, settingsValueJsonForm.bundles.size());
        }
    }

    private void updateAndCheckUserPreference(String path, Header identityHeader, String bundle, String application, String eventType, boolean daily, boolean instant, boolean drawer) {
        SettingsValuesByEventType settingsValues = createSettingsValue(bundle, application, eventType, daily, instant, drawer);
        postPreferencesByEventType(path, identityHeader, settingsValues, 200);
        SettingsValueByEventTypeJsonForm settingsValuesByEventType = getPreferencesByEventType(path, identityHeader);
        SettingsValueByEventTypeJsonForm.EventTypes rhelPolicy = rhelPolicyForm(settingsValuesByEventType);
        assertNotNull(rhelPolicy, "RHEL policies not found");
        Map<EmailSubscriptionType, Boolean> initialValues = extractNotificationValues(rhelPolicy.eventTypes, bundle, application, eventType);

        assertEquals(initialValues, settingsValues.bundles.get(bundle).applications.get(application).eventTypes.get(eventType).emailSubscriptionTypes);
        SettingsValueByEventTypeJsonForm.EventTypes preferences = given()
            .header(identityHeader)
            .when().get(String.format(path + "/%s/%s", bundle, application))
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(SettingsValueByEventTypeJsonForm.EventTypes.class);

        assertNotNull(preferences);
        Map<EmailSubscriptionType, Boolean> notificationPreferenes = extractNotificationValues(preferences.eventTypes, bundle, application, eventType);
        assertEquals(daily, notificationPreferenes.get(DAILY));
        assertEquals(instant, notificationPreferenes.get(INSTANT));
        if (featureFlipper.isDrawerEnabled()) {
            assertEquals(drawer, notificationPreferenes.get(DRAWER));
        }
    }

    private void postPreferencesByEventType(String path, Header identityHeader, SettingsValuesByEventType settingsValues, int expectedStatusCode) {
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(settingsValues))
                .post(path)
                .then()
                .statusCode(expectedStatusCode)
                .contentType(TEXT);
    }

    private SettingsValueByEventTypeJsonForm getPreferencesByEventType(String path, Header identityHeader) {
        return given()
                .header(identityHeader)
                .when().get(path)
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().as(SettingsValueByEventTypeJsonForm.class);
    }

    private String createInstantTemplate(String bundle, String application, String eventTypeName) {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);

        // We also need templates that will be linked with the instant email template tested below.
        Template subjectTemplate = CrudTestHelpers.buildTemplate("subject-template-name-" + RandomStringUtils.randomAlphabetic(20), "template-data");

        JsonObject subjectJsonTemplate = createTemplate(adminIdentity, subjectTemplate, 200).get();
        Template bodyTemplate = CrudTestHelpers.buildTemplate("body-template-name-" + RandomStringUtils.randomAlphabetic(20), "template-data");
        JsonObject bodyJsonTemplate = createTemplate(adminIdentity, bodyTemplate, 200).get();

        Application app = applicationRepository.getApplication(bundle, application);
        EventType eventType = eventTypeRepository.find(app.getId(), eventTypeName).get();

        InstantEmailTemplate emailTemplate = CrudTestHelpers.buildInstantEmailTemplate(eventType.getId().toString(), subjectJsonTemplate.getString("id"), bodyJsonTemplate.getString("id"));
        JsonObject jsonEmailTemplate = createInstantEmailTemplate(adminIdentity, emailTemplate, 200).get();
        return jsonEmailTemplate.getString("id");
    }

    private void deleteInstantTemplate(String templateId) {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        deleteInstantEmailTemplate(adminIdentity, templateId);
    }

    private String createAggregationTemplate(String bundle, String application) {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);

        Template subjectTemplate = CrudTestHelpers.buildTemplate("subject-aggregation-template-name-" + RandomStringUtils.randomAlphabetic(20), "template-data");
        JsonObject subjectJsonTemplate = createTemplate(adminIdentity, subjectTemplate, 200).get();
        Template bodyTemplate = CrudTestHelpers.buildTemplate("body-aggregation-template-name-" + RandomStringUtils.randomAlphabetic(20), "template-data");
        JsonObject bodyJsonTemplate = createTemplate(adminIdentity, bodyTemplate, 200).get();

        Application app = applicationRepository.getApplication(bundle, application);

        AggregationEmailTemplate emailTemplate = CrudTestHelpers.buildAggregationEmailTemplate(app.getId().toString(), subjectJsonTemplate.getString("id"), bodyJsonTemplate.getString("id"));
        JsonObject jsonEmailTemplate = createAggregationEmailTemplate(adminIdentity, emailTemplate, 200).get();
        return jsonEmailTemplate.getString("id");
    }

    private void deleteAggregationTemplate(String templateId) {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        deleteAggregationEmailTemplate(adminIdentity, templateId);
    }
}
