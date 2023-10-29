package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.CrudTestHelpers;
import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.EmailSubscriptionRepository;
import com.redhat.cloud.notifications.db.repositories.EventTypeRepository;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.models.Template;
import com.redhat.cloud.notifications.routers.models.SettingsValueByEventTypeJsonForm;
import com.redhat.cloud.notifications.routers.models.SettingsValuesByEventType;
import com.redhat.cloud.notifications.routers.models.UserConfigPreferences;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.CrudTestHelpers.createAggregationEmailTemplate;
import static com.redhat.cloud.notifications.CrudTestHelpers.createInstantEmailTemplate;
import static com.redhat.cloud.notifications.CrudTestHelpers.createTemplate;
import static com.redhat.cloud.notifications.CrudTestHelpers.deleteAggregationEmailTemplate;
import static com.redhat.cloud.notifications.CrudTestHelpers.deleteInstantEmailTemplate;
import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.SubscriptionType.DRAWER;
import static com.redhat.cloud.notifications.models.SubscriptionType.INSTANT;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
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
        featureFlipper.setInstantEmailsEnabled(false);
    }

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    EmailSubscriptionRepository emailSubscriptionRepository;

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Inject
    EntityManager entityManager;

    @InjectSpy
    ApplicationRepository applicationRepository;

    @Inject
    EventTypeRepository eventTypeRepository;

    private SettingsValueByEventTypeJsonForm.Application rhelPolicyForm(SettingsValueByEventTypeJsonForm settingsValuesByEventType) {
        for (String bundleName : settingsValuesByEventType.bundles.keySet()) {
            if (settingsValuesByEventType.bundles.get(bundleName).applications.containsKey("policies")) {
                Assertions.assertEquals("Red Hat Enterprise Linux", settingsValuesByEventType.bundles.get(bundleName).label, "unexpected label for the bundle");
                Assertions.assertEquals("Policies", settingsValuesByEventType.bundles.get(bundleName).applications.get("policies").label, "unexpected label for the application");

                return settingsValuesByEventType.bundles.get(bundleName).applications.get("policies");
            }
        }
        return null;
    }

    private Map<SubscriptionType, Boolean> extractNotificationValues(List<SettingsValueByEventTypeJsonForm.EventType> eventTypes, String bundle, String application, String eventName) {
        Map<SubscriptionType, Boolean> result = new HashMap<>();
        for (SettingsValueByEventTypeJsonForm.EventType eventType : eventTypes) {
            for (SettingsValueByEventTypeJsonForm.Field field : eventType.fields) {
                for (SubscriptionType type : SubscriptionType.values()) {
                    if (field.name != null && field.name.equals(String.format("bundles[%s].applications[%s].eventTypes[%s].emailSubscriptionTypes[%s]", bundle, application, eventName, type))) {
                        result.put(type, (Boolean) field.initialValue);
                    }
                }
            }
        }

        return result;
    }

    private SettingsValuesByEventType createSettingsValue(String bundle, String application, String eventType, boolean daily, boolean instant, boolean drawer) {

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

        updatePoliciesEventTypeVisibility(false);
        SettingsValueByEventTypeJsonForm settingsValuesByEventType = given()
            .header(identityHeader)
            .queryParam("bundleName", bundle)
            .when().get(path)
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(SettingsValueByEventTypeJsonForm.class);

        SettingsValueByEventTypeJsonForm.Application rhelPolicy = rhelPolicyForm(settingsValuesByEventType);
        assertNull(rhelPolicy, "RHEL policies found");

        updatePoliciesEventTypeVisibility(true);
        settingsValuesByEventType = given()
            .header(identityHeader)
            .queryParam("bundleName", bundle)
            .when().get(path)
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(SettingsValueByEventTypeJsonForm.class);

        rhelPolicy = rhelPolicyForm(settingsValuesByEventType);
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
            // Daily, Instant and drawer to false
            updateAndCheckUserPreference(path, identityHeader, bundle, application, eventType, false, false, false);

            // Daily to true
            updateAndCheckUserPreference(path, identityHeader, bundle, application, eventType, true, false, false);

            // Instant to true
            updateAndCheckUserPreference(path, identityHeader, bundle, application, eventType, false, true, false);

            // Daily and instant to true
            updateAndCheckUserPreference(path, identityHeader, bundle, application, eventType, true, true, false);

            // Daily and Instant to false, drawer to true
            updateAndCheckUserPreference(path, identityHeader, bundle, application, eventType, false, false, true);

            // Daily to true
            updateAndCheckUserPreference(path, identityHeader, bundle, application, eventType, true, false, true);

            // Instant to true
            updateAndCheckUserPreference(path, identityHeader, bundle, application, eventType, false, true, true);

            // Daily and instant to true
            updateAndCheckUserPreference(path, identityHeader, bundle, application, eventType, true, true, true);
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
            .statusCode(200);
        settingsValuesByEventType = given()
            .header(identityHeader)
            .when().get(path)
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(SettingsValueByEventTypeJsonForm.class);
        rhelPolicy = rhelPolicyForm(settingsValuesByEventType);
        assertNotNull(rhelPolicy, "RHEL policies not found");
        Map<SubscriptionType, Boolean> initialValues = extractNotificationValues(rhelPolicy.eventTypes, "not-found-bundle-2", "not-found-app-2", eventType);
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

        Map<SubscriptionType, Boolean> notificationPreferenes = extractNotificationValues(rhelPolicy.eventTypes, bundle, application, eventType);

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
        final SettingsValueByEventTypeJsonForm.Application rhelPolicy = rhelPolicyForm(settingsValuesByEventType);
        assertNotNull(rhelPolicy, "RHEL policies not found");
        Map<SubscriptionType, Boolean> initialValues = extractNotificationValues(rhelPolicy.eventTypes, bundle, application, eventType);

        assertEquals(initialValues, settingsValues.bundles.get(bundle).applications.get(application).eventTypes.get(eventType).emailSubscriptionTypes);
        final SettingsValueByEventTypeJsonForm.Application preferences = given()
            .header(identityHeader)
            .when().get(String.format(path + "/%s/%s", bundle, application))
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(SettingsValueByEventTypeJsonForm.Application.class);

        assertNotNull(preferences);
        Map<SubscriptionType, Boolean> notificationPreferenes = extractNotificationValues(preferences.eventTypes, bundle, application, eventType);
        assertEquals(daily, notificationPreferenes.get(DAILY));
        assertEquals(instant, notificationPreferenes.get(INSTANT));
        if (featureFlipper.isDrawerEnabled()) {
            assertEquals(drawer, notificationPreferenes.get(DRAWER));
        }
    }

    @Transactional
    void updatePoliciesEventTypeVisibility(boolean visible) {
        entityManager.createQuery("UPDATE EventType SET visible= :visible where name='policy-triggered'")
            .setParameter("visible", visible)
            .executeUpdate();
    }

    private void postPreferencesByEventType(String path, Header identityHeader, SettingsValuesByEventType settingsValues, int expectedStatusCode) {
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(settingsValues))
                .post(path)
                .then()
                .statusCode(expectedStatusCode);
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

    @Test
    void testSettingsUserPreferenceUsingDeprecatedApi() {
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

        createInstantTemplate(bundle, application, eventType);
        createAggregationTemplate(bundle, application);

        // Daily and Instant to false
        updateAndCheckUserPreferenceUsingDeprecatedApi(path, identityHeader, bundle, application, eventType, false, false, true);

        // Daily to true
        updateAndCheckUserPreferenceUsingDeprecatedApi(path, identityHeader, bundle, application, eventType, true, false, true);

        // Instant to true
        updateAndCheckUserPreferenceUsingDeprecatedApi(path, identityHeader, bundle, application, eventType, false, true, true);

        // Both to true
        updateAndCheckUserPreferenceUsingDeprecatedApi(path, identityHeader, bundle, application, eventType, true, true, true);

        given()
            .header(identityHeader)
            .when().get(String.format("/user-config/notification-preference/%s/%s", bundle, "another-app"))
            .then()
            .statusCode(403)
            .contentType(JSON)
            .extract().body();

    }

    private void updateAndCheckUserPreferenceUsingDeprecatedApi(String path, Header identityHeader, String bundle, String application, String eventType, boolean daily, boolean instant, boolean drawer) {
        SettingsValuesByEventType settingsValues = createSettingsValue(bundle, application, eventType, daily, instant, drawer);
        postPreferencesByEventType(path, identityHeader, settingsValues, 200);

        final UserConfigPreferences preferences = given()
            .header(identityHeader)
            .when().get(String.format("/user-config/notification-preference/%s/%s", bundle, application))
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(UserConfigPreferences.class);

        assertNotNull(preferences);

        assertEquals(daily, preferences.getDailyEmail());
        assertEquals(instant, preferences.getInstantEmail());
    }
}
