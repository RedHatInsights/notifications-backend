package com.redhat.cloud.notifications.routers.handlers.userconfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import com.redhat.cloud.notifications.routers.models.SettingsValueByEventTypeJsonForm;
import com.redhat.cloud.notifications.routers.models.SettingsValuesByEventType;
import com.redhat.cloud.notifications.routers.models.UserConfigPreferences;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.restassured.http.Header;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.CrudTestHelpers.createApp;
import static com.redhat.cloud.notifications.CrudTestHelpers.createBundle;
import static com.redhat.cloud.notifications.CrudTestHelpers.createEventType;
import static com.redhat.cloud.notifications.CrudTestHelpers.deleteBundle;
import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.SubscriptionType.DRAWER;
import static com.redhat.cloud.notifications.models.SubscriptionType.INSTANT;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.MALWARE_APP_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.MALWARE_DETECTED_MALWARE;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class UserConfigResourceTemplateModuleTest extends DbIsolatedTest {

    static final String PATH_EVENT_TYPE_PREFERENCE_API = TestConstants.API_NOTIFICATIONS_V_1_0 + "/user-config/notification-event-type-preference";

    @InjectMock
    BackendConfig backendConfig;

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Inject
    EntityManager entityManager;

    @InjectSpy
    ApplicationRepository applicationRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    @InjectSpy
    TemplateService templateService;

    @InjectSpy
    TemplateRepository templateRepository;

    record TestRecordNameAndDisplayName(String name, String displayName) { }

    private static final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void beforeEach() {
        when(backendConfig.isInstantEmailsEnabled()).thenReturn(true);
        when(backendConfig.isUseCommonTemplateModuleForUserPrefApisToggle()).thenReturn(true);
    }

    @AfterEach
    void afterEach() {
        verifyNoInteractions(templateRepository);
    }

    private SettingsValueByEventTypeJsonForm.Application rhelPolicyForm(SettingsValueByEventTypeJsonForm settingsValuesByEventType) {
        return rhelAppForm(settingsValuesByEventType, "policies");
    }

    private SettingsValueByEventTypeJsonForm.Application rhelAppForm(SettingsValueByEventTypeJsonForm settingsValuesByEventType, String appName) {
        for (String bundleName : settingsValuesByEventType.bundles.keySet()) {
            if (settingsValuesByEventType.bundles.get(bundleName).applications.containsKey(appName)) {
                Assertions.assertEquals("Red Hat Enterprise Linux", settingsValuesByEventType.bundles.get(bundleName).label, "unexpected label for the bundle");
                return settingsValuesByEventType.bundles.get(bundleName).applications.get(appName);
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
        if (backendConfig.isDrawerEnabled()) {
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

    private String createTestBundleAppEventType(Header adminIdentity, TestRecordNameAndDisplayName bundleDetails, List<TestRecordNameAndDisplayName> applicationsDetails) {
        final String bundleId = createBundle(adminIdentity, bundleDetails.name, bundleDetails.displayName, 200).get();
        applicationsDetails.forEach(appDetail -> {
            final String appId = createApp(adminIdentity, bundleId, appDetail.name, appDetail.displayName, null, 200).get();
            createEventType(adminIdentity, appId, "event-type-name", "Event type", "Event type description", false, false, 200).get();
        });
        return bundleId;
    }

    @Test
    void testBundlesAndAppsOrder() throws JsonProcessingException {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        // to be able to return all event types without adding one template to each event types,
        // we need to enable the default template feature.
        when(templateService.isDefaultEmailTemplateEnabled()).thenReturn(true);
        templateService.init();

        // hide policy app
        EventType policyTriggered = resourceHelpers.getEventType("rhel", "policies", "policy-triggered");

        Map<TestRecordNameAndDisplayName, List<TestRecordNameAndDisplayName>> bundleAndApps = Map.of(
            new TestRecordNameAndDisplayName("bundle-name1", "zbundle"),        // declare a bundle
            List.of(new TestRecordNameAndDisplayName("app-name1", "appname1"),  // declare 3 apps under created bundle
                new TestRecordNameAndDisplayName("app-name2", "appname4"),
                new TestRecordNameAndDisplayName("app-name3", "appname3")),
            new TestRecordNameAndDisplayName("bundle-name2", "abundle"),        // declare a bundle
            List.of(new TestRecordNameAndDisplayName("app-name1", "appnamez"),  // declare 3 apps under created bundle
                new TestRecordNameAndDisplayName("app-name2", "appname3"),
                new TestRecordNameAndDisplayName("app-name3", "appname")),
            new TestRecordNameAndDisplayName("bundle-name3", "bbundle"),        // declare a bundle
            List.of(new TestRecordNameAndDisplayName("app-name1", "e-appname"), // declare 3 apps under created bundle
                new TestRecordNameAndDisplayName("app-name2", "r-appname"),
                new TestRecordNameAndDisplayName("app-name3", "a-appname"))
        );

        final List<String> bundleIdsToRemove = new ArrayList<>();
        // Let's create declared bundles and apps and record bundle ids to be able to delete them at the end of this test
        for (TestRecordNameAndDisplayName bundleName : bundleAndApps.keySet()) {
            bundleIdsToRemove.add(createTestBundleAppEventType(adminIdentity, bundleName, bundleAndApps.get(bundleName)));
        }

        String accountId = "empty";
        String orgId = "empty";
        String username = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, username);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        SettingsValueByEventTypeJsonForm settingsValuesByEventType = given()
            .header(identityHeader)
            .when().get(PATH_EVENT_TYPE_PREFERENCE_API)
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(SettingsValueByEventTypeJsonForm.class);

        // We want to validate bundles and apps orders returned as a json string
        String mappedString = mapper.writeValueAsString(settingsValuesByEventType);
        final String RESULT = """
            {"bundles":{"bundle-name2":{"applications":{"app-name3":{"eventTypes":[{"name":"event-type-name","label":"Event type","fields":[{"name":"bundles[bundle-name2].applications[app-name3].eventTypes[event-type-name].emailSubscriptionTypes[INSTANT]","label":"Instant notification","description":"Immediate email for each triggered application event.","initialValue":false,"component":"descriptiveCheckbox","validate":[],"checkedWarning":"Opting into this notification may result in a large number of emails","disabled":false,"severities":[{"name":"CRITICAL","initialValue":false,"disabled":false},{"name":"IMPORTANT","initialValue":false,"disabled":true},{"name":"MODERATE","initialValue":false,"disabled":false},{"name":"LOW","initialValue":false,"disabled":false},{"name":"NONE","initialValue":false,"disabled":true},{"name":"UNDEFINED","initialValue":false,"disabled":true}]}]}],"label":"appname"},"app-name2":{"eventTypes":[{"name":"event-type-name","label":"Event type","fields":[{"name":"bundles[bundle-name2].applications[app-name2].eventTypes[event-type-name].emailSubscriptionTypes[INSTANT]","label":"Instant notification","description":"Immediate email for each triggered application event.","initialValue":false,"component":"descriptiveCheckbox","validate":[],"checkedWarning":"Opting into this notification may result in a large number of emails","disabled":false,"severities":[{"name":"CRITICAL","initialValue":false,"disabled":false},{"name":"IMPORTANT","initialValue":false,"disabled":true},{"name":"MODERATE","initialValue":false,"disabled":false},{"name":"LOW","initialValue":false,"disabled":false},{"name":"NONE","initialValue":false,"disabled":true},{"name":"UNDEFINED","initialValue":false,"disabled":true}]}]}],"label":"appname3"},"app-name1":{"eventTypes":[{"name":"event-type-name","label":"Event type","fields":[{"name":"bundles[bundle-name2].applications[app-name1].eventTypes[event-type-name].emailSubscriptionTypes[INSTANT]","label":"Instant notification","description":"Immediate email for each triggered application event.","initialValue":false,"component":"descriptiveCheckbox","validate":[],"checkedWarning":"Opting into this notification may result in a large number of emails","disabled":false,"severities":[{"name":"CRITICAL","initialValue":false,"disabled":false},{"name":"IMPORTANT","initialValue":false,"disabled":true},{"name":"MODERATE","initialValue":false,"disabled":false},{"name":"LOW","initialValue":false,"disabled":false},{"name":"NONE","initialValue":false,"disabled":true},{"name":"UNDEFINED","initialValue":false,"disabled":true}]}]}],"label":"appnamez"}},"label":"abundle"},"bundle-name3":{"applications":{"app-name3":{"eventTypes":[{"name":"event-type-name","label":"Event type","fields":[{"name":"bundles[bundle-name3].applications[app-name3].eventTypes[event-type-name].emailSubscriptionTypes[INSTANT]","label":"Instant notification","description":"Immediate email for each triggered application event.","initialValue":false,"component":"descriptiveCheckbox","validate":[],"checkedWarning":"Opting into this notification may result in a large number of emails","disabled":false,"severities":[{"name":"CRITICAL","initialValue":false,"disabled":false},{"name":"IMPORTANT","initialValue":false,"disabled":true},{"name":"MODERATE","initialValue":false,"disabled":false},{"name":"LOW","initialValue":false,"disabled":false},{"name":"NONE","initialValue":false,"disabled":true},{"name":"UNDEFINED","initialValue":false,"disabled":true}]}]}],"label":"a-appname"},"app-name1":{"eventTypes":[{"name":"event-type-name","label":"Event type","fields":[{"name":"bundles[bundle-name3].applications[app-name1].eventTypes[event-type-name].emailSubscriptionTypes[INSTANT]","label":"Instant notification","description":"Immediate email for each triggered application event.","initialValue":false,"component":"descriptiveCheckbox","validate":[],"checkedWarning":"Opting into this notification may result in a large number of emails","disabled":false,"severities":[{"name":"CRITICAL","initialValue":false,"disabled":false},{"name":"IMPORTANT","initialValue":false,"disabled":true},{"name":"MODERATE","initialValue":false,"disabled":false},{"name":"LOW","initialValue":false,"disabled":false},{"name":"NONE","initialValue":false,"disabled":true},{"name":"UNDEFINED","initialValue":false,"disabled":true}]}]}],"label":"e-appname"},"app-name2":{"eventTypes":[{"name":"event-type-name","label":"Event type","fields":[{"name":"bundles[bundle-name3].applications[app-name2].eventTypes[event-type-name].emailSubscriptionTypes[INSTANT]","label":"Instant notification","description":"Immediate email for each triggered application event.","initialValue":false,"component":"descriptiveCheckbox","validate":[],"checkedWarning":"Opting into this notification may result in a large number of emails","disabled":false,"severities":[{"name":"CRITICAL","initialValue":false,"disabled":false},{"name":"IMPORTANT","initialValue":false,"disabled":true},{"name":"MODERATE","initialValue":false,"disabled":false},{"name":"LOW","initialValue":false,"disabled":false},{"name":"NONE","initialValue":false,"disabled":true},{"name":"UNDEFINED","initialValue":false,"disabled":true}]}]}],"label":"r-appname"}},"label":"bbundle"},"rhel":{"applications":{"policies":{"eventTypes":[{"name":"policy-triggered","label":"Policy triggered","fields":[{"name":"bundles[rhel].applications[policies].eventTypes[policy-triggered].emailSubscriptionTypes[INSTANT]","label":"Instant notification","description":"Immediate email for each triggered application event.","initialValue":false,"component":"descriptiveCheckbox","validate":[],"checkedWarning":"Opting into this notification may result in a large number of emails","disabled":false,"severities":[]},{"name":"bundles[rhel].applications[policies].eventTypes[policy-triggered].emailSubscriptionTypes[DAILY]","label":"Daily digest","description":"Daily summary of triggered application events in 24 hours span.","initialValue":false,"component":"descriptiveCheckbox","validate":[],"disabled":false,"severities":[]}]}],"label":"Policies"}},"label":"Red Hat Enterprise Linux"},"bundle-name1":{"applications":{"app-name1":{"eventTypes":[{"name":"event-type-name","label":"Event type","fields":[{"name":"bundles[bundle-name1].applications[app-name1].eventTypes[event-type-name].emailSubscriptionTypes[INSTANT]","label":"Instant notification","description":"Immediate email for each triggered application event.","initialValue":false,"component":"descriptiveCheckbox","validate":[],"checkedWarning":"Opting into this notification may result in a large number of emails","disabled":false,"severities":[{"name":"CRITICAL","initialValue":false,"disabled":false},{"name":"IMPORTANT","initialValue":false,"disabled":true},{"name":"MODERATE","initialValue":false,"disabled":false},{"name":"LOW","initialValue":false,"disabled":false},{"name":"NONE","initialValue":false,"disabled":true},{"name":"UNDEFINED","initialValue":false,"disabled":true}]}]}],"label":"appname1"},"app-name3":{"eventTypes":[{"name":"event-type-name","label":"Event type","fields":[{"name":"bundles[bundle-name1].applications[app-name3].eventTypes[event-type-name].emailSubscriptionTypes[INSTANT]","label":"Instant notification","description":"Immediate email for each triggered application event.","initialValue":false,"component":"descriptiveCheckbox","validate":[],"checkedWarning":"Opting into this notification may result in a large number of emails","disabled":false,"severities":[{"name":"CRITICAL","initialValue":false,"disabled":false},{"name":"IMPORTANT","initialValue":false,"disabled":true},{"name":"MODERATE","initialValue":false,"disabled":false},{"name":"LOW","initialValue":false,"disabled":false},{"name":"NONE","initialValue":false,"disabled":true},{"name":"UNDEFINED","initialValue":false,"disabled":true}]}]}],"label":"appname3"},"app-name2":{"eventTypes":[{"name":"event-type-name","label":"Event type","fields":[{"name":"bundles[bundle-name1].applications[app-name2].eventTypes[event-type-name].emailSubscriptionTypes[INSTANT]","label":"Instant notification","description":"Immediate email for each triggered application event.","initialValue":false,"component":"descriptiveCheckbox","validate":[],"checkedWarning":"Opting into this notification may result in a large number of emails","disabled":false,"severities":[{"name":"CRITICAL","initialValue":false,"disabled":false},{"name":"IMPORTANT","initialValue":false,"disabled":true},{"name":"MODERATE","initialValue":false,"disabled":false},{"name":"LOW","initialValue":false,"disabled":false},{"name":"NONE","initialValue":false,"disabled":true},{"name":"UNDEFINED","initialValue":false,"disabled":true}]}]}],"label":"appname4"}},"label":"zbundle"}}}""";
        assertEquals(RESULT, mappedString);

        // delete created bundles, apps and event types
        bundleIdsToRemove.forEach(bundleIdToRemove -> deleteBundle(adminIdentity, bundleIdToRemove, true));

        applicationRepository.updateEventTypeVisibility(policyTriggered.getId(), true);
    }


    @Test
    void testLegacySettingsByEventType() {
        testSettingsByEventType();
    }

    @Test
    void testSettingsByEventTypeWithDrawerEnabled() {
        when(backendConfig.isDrawerEnabled()).thenReturn(true);
        testSettingsByEventType();
    }

    void testSettingsByEventType() {
        String accountId = "empty";
        String orgId = "empty";
        String username = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, username);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        String bundle = "rhel";
        String application = "policies";
        String eventType = "policy-triggered";

        updatePoliciesEventTypeVisibility(false);
        SettingsValueByEventTypeJsonForm settingsValuesByEventType = given()
            .header(identityHeader)
            .queryParam("bundleName", bundle)
            .when().get(PATH_EVENT_TYPE_PREFERENCE_API)
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
            .when().get(PATH_EVENT_TYPE_PREFERENCE_API)
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
            .when().get(PATH_EVENT_TYPE_PREFERENCE_API)
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(SettingsValueByEventTypeJsonForm.class);
        rhelPolicy = rhelPolicyForm(settingsValuesByEventType);
        assertNotNull(rhelPolicy.eventTypes.get(0).fields.get(0).infoMessage);

        when(backendConfig.isInstantEmailsEnabled()).thenReturn(false);
        SettingsValuesByEventType settingsValues = createSettingsValue(bundle, application, eventType, true, true, false);
        postPreferencesByEventType(identityHeader, settingsValues, 400);

        when(backendConfig.isInstantEmailsEnabled()).thenReturn(true);
        postPreferencesByEventType(identityHeader, settingsValues, 200);

        when(backendConfig.isInstantEmailsEnabled()).thenReturn(false);
        SettingsValueByEventTypeJsonForm settingsValue = getPreferencesByEventType(identityHeader);
        rhelPolicy = rhelPolicyForm(settingsValue);
        boolean instantEmailSettingsReturned = extractNotificationValues(rhelPolicy.eventTypes, bundle, application, eventType)
                .keySet().stream().anyMatch(INSTANT::equals);
        assertFalse(instantEmailSettingsReturned, "Instant email subscription settings should not be returned when instant emails are disabled");

        when(backendConfig.isInstantEmailsEnabled()).thenReturn(true);

        // Daily and Instant to false
        updateAndCheckUserPreference(identityHeader, bundle, application, eventType, List.of(DRAWER), List.of(DRAWER));

        // Daily to true
        updateAndCheckUserPreference(identityHeader, bundle, application, eventType, List.of(DAILY, DRAWER), List.of(DAILY, DRAWER));

        // Instant to true
        updateAndCheckUserPreference(identityHeader, bundle, application, eventType, List.of(INSTANT, DRAWER), List.of(INSTANT, DRAWER));

        // Both to true
        updateAndCheckUserPreference(identityHeader, bundle, application, eventType, List.of(DAILY, INSTANT, DRAWER), List.of(DAILY, INSTANT, DRAWER));

        // Before this line, we're subscribed to everything. Now, we're locking the subscriptions.
        lockOrUnlockSubscriptionToPoliciesEventType(true);
        // Let's try to unsubscribe from everything. The subscriptions should remain the same.
        updateAndCheckUserPreference(identityHeader, bundle, application, eventType, emptyList(), List.of(DAILY, INSTANT, DRAWER));
        // We're now unlocking the subscriptions.
        lockOrUnlockSubscriptionToPoliciesEventType(false);
        // Unsubscribing from everything should work this time.
        updateAndCheckUserPreference(identityHeader, bundle, application, eventType, emptyList(), emptyList());

        if (backendConfig.isDrawerEnabled()) {
            // Daily, Instant and drawer to false
            updateAndCheckUserPreference(identityHeader, bundle, application, eventType, emptyList(), emptyList());

            // Daily to true
            updateAndCheckUserPreference(identityHeader, bundle, application, eventType, List.of(DAILY), List.of(DAILY));

            // Instant to true
            updateAndCheckUserPreference(identityHeader, bundle, application, eventType, List.of(INSTANT), List.of(INSTANT));

            // Daily and instant to true
            updateAndCheckUserPreference(identityHeader, bundle, application, eventType, List.of(DAILY, INSTANT), List.of(DAILY, INSTANT));

            // Daily and Instant to false, drawer to true
            updateAndCheckUserPreference(identityHeader, bundle, application, eventType, List.of(DRAWER), List.of(DRAWER));

            // Daily to true
            updateAndCheckUserPreference(identityHeader, bundle, application, eventType, List.of(DAILY, DRAWER), List.of(DAILY, DRAWER));

            // Instant to true
            updateAndCheckUserPreference(identityHeader, bundle, application, eventType, List.of(INSTANT, DRAWER), List.of(INSTANT, DRAWER));

            // Daily and instant to true
            updateAndCheckUserPreference(identityHeader, bundle, application, eventType, List.of(DAILY, INSTANT, DRAWER), List.of(DAILY, INSTANT, DRAWER));
        }

        // does not add if we try to create unknown bundle/apps
        settingsValues = createSettingsValue("not-found-bundle-2", "not-found-app-2", eventType, true, true, true);
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(settingsValues))
            .post(PATH_EVENT_TYPE_PREFERENCE_API)
            .then()
            .statusCode(200);
        settingsValuesByEventType = given()
            .header(identityHeader)
            .when().get(PATH_EVENT_TYPE_PREFERENCE_API)
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(SettingsValueByEventTypeJsonForm.class);
        rhelPolicy = rhelPolicyForm(settingsValuesByEventType);
        assertNotNull(rhelPolicy, "RHEL policies not found");
        Map<SubscriptionType, Boolean> initialValues = extractNotificationValues(rhelPolicy.eventTypes, "not-found-bundle-2", "not-found-app-2", eventType);
        assertEquals(0, initialValues.size());

        // hide policy app
        EventType policyTriggered = resourceHelpers.getEventType(bundle, application, eventType);
        applicationRepository.updateEventTypeVisibility(policyTriggered.getId(), false);

        // check for app without daily digest
        UUID malwareEventTypeId = resourceHelpers.createEventType(bundle, MALWARE_APP_NAME, MALWARE_DETECTED_MALWARE);
        SettingsValueByEventTypeJsonForm settingsValueJsonForm = given()
            .header(identityHeader)
            .when().get(PATH_EVENT_TYPE_PREFERENCE_API)
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(SettingsValueByEventTypeJsonForm.class);
        SettingsValueByEventTypeJsonForm.Application rhelMalware = rhelAppForm(settingsValueJsonForm, MALWARE_APP_NAME);
        assertNotNull(rhelMalware, String.format("RHEL app %s not found", MALWARE_APP_NAME));

        Map<SubscriptionType, Boolean> notificationPreferenes = extractNotificationValues(rhelMalware.eventTypes, bundle, MALWARE_APP_NAME, MALWARE_DETECTED_MALWARE);

        if (backendConfig.isDrawerEnabled()) {
            assertEquals(2, notificationPreferenes.size());
            assertTrue(notificationPreferenes.containsKey(DRAWER));
        } else {
            assertEquals(1, notificationPreferenes.size());
        }
        assertTrue(notificationPreferenes.containsKey(INSTANT));

        // delete malware created event type
        applicationRepository.deleteEventTypeById(malwareEventTypeId);

        // Skip the application if there are no supported types
        final String APP_WITHOUT_TEMPLATE = "app-without-template";
        resourceHelpers.createEventType(bundle, APP_WITHOUT_TEMPLATE, MALWARE_DETECTED_MALWARE);

        settingsValueJsonForm = given()
            .header(identityHeader)
            .when().get(PATH_EVENT_TYPE_PREFERENCE_API)
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(SettingsValueByEventTypeJsonForm.class);
        rhelMalware = rhelAppForm(settingsValueJsonForm, APP_WITHOUT_TEMPLATE);

        if (backendConfig.isDrawerEnabled()) {
            // drawer type will be always supported
            assertNotNull(rhelMalware);
            assertEquals(1, settingsValueJsonForm.bundles.size());
            notificationPreferenes = extractNotificationValues(rhelMalware.eventTypes, bundle, APP_WITHOUT_TEMPLATE, MALWARE_DETECTED_MALWARE);
            assertTrue(notificationPreferenes.containsKey(DRAWER));
        } else {
            assertNull(rhelMalware, String.format("RHEL %s was not supposed to be here", APP_WITHOUT_TEMPLATE));
            assertEquals(0, settingsValueJsonForm.bundles.size());
        }

        // Restore policy triggered event type visibility
        applicationRepository.updateEventTypeVisibility(policyTriggered.getId(), true);
        // delete malware app
        UUID malwareAppId = applicationRepository.getApplication(bundle, MALWARE_APP_NAME).getId();
        applicationRepository.deleteApplication(malwareAppId);
    }

    private void updateAndCheckUserPreference(Header identityHeader, String bundle, String application, String eventType, List<SubscriptionType> subscriptionsToSet, List<SubscriptionType> expectedResult) {
        SettingsValuesByEventType settingsValues = createSettingsValue(bundle, application, eventType, subscriptionsToSet.contains(DAILY), subscriptionsToSet.contains(INSTANT), subscriptionsToSet.contains(DRAWER));
        postPreferencesByEventType(identityHeader, settingsValues, 200);
        SettingsValueByEventTypeJsonForm settingsValuesByEventType = getPreferencesByEventType(identityHeader);
        final SettingsValueByEventTypeJsonForm.Application rhelPolicy = rhelPolicyForm(settingsValuesByEventType);
        assertNotNull(rhelPolicy, "RHEL policies not found");
        Map<SubscriptionType, Boolean> initialValues = extractNotificationValues(rhelPolicy.eventTypes, bundle, application, eventType);

        assertEquals(expectedResult.contains(DAILY), initialValues.get(DAILY));
        assertEquals(expectedResult.contains(INSTANT), initialValues.get(INSTANT));
        if (backendConfig.isDrawerEnabled()) {
            assertEquals(expectedResult.contains(DRAWER), initialValues.get(DRAWER));
        }

        final SettingsValueByEventTypeJsonForm.Application preferences = given()
            .header(identityHeader)
            .when().get(String.format(PATH_EVENT_TYPE_PREFERENCE_API + "/%s/%s", bundle, application))
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(SettingsValueByEventTypeJsonForm.Application.class);

        assertNotNull(preferences);
        Map<SubscriptionType, Boolean> notificationPreferences = extractNotificationValues(preferences.eventTypes, bundle, application, eventType);
        assertEquals(expectedResult.contains(DAILY), notificationPreferences.get(DAILY));
        assertEquals(expectedResult.contains(INSTANT), notificationPreferences.get(INSTANT));
        if (backendConfig.isDrawerEnabled()) {
            assertEquals(expectedResult.contains(DRAWER), notificationPreferences.get(DRAWER));
        }
    }

    @Transactional
    void updatePoliciesEventTypeVisibility(boolean visible) {
        entityManager.createQuery("UPDATE EventType SET visible = :visible where name='policy-triggered'")
            .setParameter("visible", visible)
            .executeUpdate();
    }

    @Transactional
    void lockOrUnlockSubscriptionToPoliciesEventType(boolean locked) {
        String hql = "UPDATE EventType " +
                "SET subscribedByDefault = :subscribedByDefault, subscriptionLocked = :subscriptionLocked " +
                "WHERE name = 'policy-triggered'";
        entityManager.createQuery(hql)
                .setParameter("subscribedByDefault", locked)
                .setParameter("subscriptionLocked", locked)
                .executeUpdate();
    }

    private void postPreferencesByEventType(Header identityHeader, SettingsValuesByEventType settingsValues, int expectedStatusCode) {
        given()
                .header(identityHeader)
                .when()
                .contentType(JSON)
                .body(Json.encode(settingsValues))
                .post(PATH_EVENT_TYPE_PREFERENCE_API)
                .then()
                .statusCode(expectedStatusCode);
    }

    private SettingsValueByEventTypeJsonForm getPreferencesByEventType(Header identityHeader) {
        return given()
                .header(identityHeader)
                .when().get(PATH_EVENT_TYPE_PREFERENCE_API)
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().as(SettingsValueByEventTypeJsonForm.class);
    }

    @Test
    void testSettingsUserPreferenceUsingDeprecatedApi() {
        String accountId = "empty";
        String orgId = "empty";
        String username = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, username);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        String bundle = "rhel";
        String application = "policies";
        String eventType = "policy-triggered";

        // Daily and Instant to false
        updateAndCheckUserPreferenceUsingDeprecatedApi(identityHeader, bundle, application, eventType, false, false, true);

        // Daily to true
        updateAndCheckUserPreferenceUsingDeprecatedApi(identityHeader, bundle, application, eventType, true, false, true);

        // Instant to true
        updateAndCheckUserPreferenceUsingDeprecatedApi(identityHeader, bundle, application, eventType, false, true, true);

        // Both to true
        updateAndCheckUserPreferenceUsingDeprecatedApi(identityHeader, bundle, application, eventType, true, true, true);

        given()
            .header(identityHeader)
            .when().get(String.format(TestConstants.API_NOTIFICATIONS_V_1_0 + "/user-config/notification-preference/%s/%s", bundle, "another-app"))
            .then()
            .statusCode(403)
            .contentType(JSON)
            .extract().body();

    }

    @Test
    void testUserPreferencesFromServiceAccountAuth() {
        String userId = UUID.randomUUID().toString();
        final String identityHeaderValue = TestHelpers.encodeRHServiceAccountIdentityInfo("123456", "johndoe", userId);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);

        String response = given()
            .header(identityHeader)
            .when().get(String.format(TestConstants.API_NOTIFICATIONS_V_1_0 + "/user-config/notification-preference/%s/%s", "rhel", "policies"))
            .then()
            .statusCode(403)
            .contentType(JSON)
            .extract().body().asString();

        assertTrue(response.contains("service account authentication"));

        SettingsValuesByEventType settingsValues = createSettingsValue("not-found-bundle-2", "not-found-app-2", "eventType", true, true, true);
        response = given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(settingsValues))
            .post(PATH_EVENT_TYPE_PREFERENCE_API)
            .then()
            .statusCode(403)
            .extract().body().asString();
        assertTrue(response.contains("service account authentication"));

        response = given()
            .header(identityHeader)
            .when().get(PATH_EVENT_TYPE_PREFERENCE_API)
            .then()
            .statusCode(403)
            .contentType(JSON)
            .extract().body().asString();
        assertTrue(response.contains("service account authentication"));

        response = given()
            .header(identityHeader)
            .when().get(PATH_EVENT_TYPE_PREFERENCE_API + "/bundle/app")
            .then()
            .statusCode(403)
            .contentType(JSON)
            .extract().body().asString();
        assertTrue(response.contains("service account authentication"));
    }

    private void updateAndCheckUserPreferenceUsingDeprecatedApi(Header identityHeader, String bundle, String application, String eventType, boolean daily, boolean instant, boolean drawer) {
        SettingsValuesByEventType settingsValues = createSettingsValue(bundle, application, eventType, daily, instant, drawer);
        postPreferencesByEventType(identityHeader, settingsValues, 200);

        final UserConfigPreferences preferences = given()
            .header(identityHeader)
            .when().get(String.format(TestConstants.API_NOTIFICATIONS_V_1_0 + "/user-config/notification-preference/%s/%s", bundle, application))
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(UserConfigPreferences.class);

        assertNotNull(preferences);

        assertEquals(daily, preferences.getDailyEmail());
        assertEquals(instant, preferences.getInstantEmail());
    }
}
