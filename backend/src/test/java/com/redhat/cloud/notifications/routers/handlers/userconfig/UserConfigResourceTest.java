package com.redhat.cloud.notifications.routers.handlers.userconfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.CrudTestHelpers;
import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.EventTypeRepository;
import com.redhat.cloud.notifications.db.repositories.SubscriptionRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.models.Template;
import com.redhat.cloud.notifications.routers.models.SettingsValueByEventTypeJsonForm;
import com.redhat.cloud.notifications.routers.models.SettingsValuesByEventType;
import com.redhat.cloud.notifications.routers.models.UserConfigPreferences;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.restassured.http.Header;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.CrudTestHelpers.createApp;
import static com.redhat.cloud.notifications.CrudTestHelpers.createBundle;
import static com.redhat.cloud.notifications.CrudTestHelpers.createEventType;
import static com.redhat.cloud.notifications.CrudTestHelpers.createInstantEmailTemplate;
import static com.redhat.cloud.notifications.CrudTestHelpers.createTemplate;
import static com.redhat.cloud.notifications.CrudTestHelpers.deleteAggregationEmailTemplate;
import static com.redhat.cloud.notifications.CrudTestHelpers.deleteBundle;
import static com.redhat.cloud.notifications.CrudTestHelpers.deleteInstantEmailTemplate;
import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.SubscriptionType.DRAWER;
import static com.redhat.cloud.notifications.models.SubscriptionType.INSTANT;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class UserConfigResourceTest extends DbIsolatedTest {

    static final String PATH_EVENT_TYPE_PREFERENCE_API = TestConstants.API_NOTIFICATIONS_V_1_0 + "/user-config/notification-event-type-preference";

    @InjectMock
    BackendConfig backendConfig;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Inject
    EntityManager entityManager;

    @InjectSpy
    ApplicationRepository applicationRepository;

    @Inject
    EventTypeRepository eventTypeRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    record TestRecordNameAndDisplayName(String name, String displayName) { }

    private static final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void beforeEach() {
        when(backendConfig.isInstantEmailsEnabled()).thenReturn(true);
        when(backendConfig.isUseCommonTemplateModuleForUserPrefApisToggle()).thenReturn(false);
    }

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

    private Map<SubscriptionType, Set<SettingsValueByEventTypeJsonForm.SeverityDetails>> extractNotificationSubscriptionTypeDetails(List<SettingsValueByEventTypeJsonForm.EventType> eventTypes, String bundle, String application, String eventName) {
        Map<SubscriptionType, Set<SettingsValueByEventTypeJsonForm.SeverityDetails>> result = new HashMap<>();
        for (SettingsValueByEventTypeJsonForm.EventType eventType : eventTypes) {
            for (SettingsValueByEventTypeJsonForm.Field field : eventType.fields) {
                for (SubscriptionType type : SubscriptionType.values()) {
                    if (field.name != null && field.name.equals(String.format("bundles[%s].applications[%s].eventTypes[%s].emailSubscriptionTypes[%s]", bundle, application, eventName, type))) {
                        result.put(type, field.severities);
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

    private Map<Severity, Boolean> createSeveritySubscriptionDetails(Set<Severity> severities) {
        Map<Severity, Boolean> result = new HashMap<>();
        for (Severity severity : Severity.values()) {
            if (severities != null && severities.contains(severity)) {
                result.put(severity, true);
            } else {
                result.put(severity, false);
            }
        }
        return result;
    }

    private SettingsValuesByEventType createSettingsValueWithSeverity(String bundle, String application, String eventType, Set<Severity> instantSeverity, Set<Severity> dailySeverities, Set<Severity> drawerSeverities) {

        SettingsValuesByEventType.EventTypeSettingsValue eventTypeSettingsValue = new SettingsValuesByEventType.EventTypeSettingsValue();
        eventTypeSettingsValue.subscriptionTypes.put(INSTANT, createSeveritySubscriptionDetails(instantSeverity));
        eventTypeSettingsValue.subscriptionTypes.put(DAILY, createSeveritySubscriptionDetails(dailySeverities));

        if (backendConfig.isDrawerEnabled()) {
            eventTypeSettingsValue.subscriptionTypes.put(DRAWER, createSeveritySubscriptionDetails(drawerSeverities));
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
        when(backendConfig.isDefaultTemplateEnabled()).thenReturn(true);

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
            {"bundles":{"bundle-name2":{"applications":{"app-name3":{"eventTypes":[{"name":"event-type-name","label":"Event type","fields":[{"name":"bundles[bundle-name2].applications[app-name3].eventTypes[event-type-name].emailSubscriptionTypes[INSTANT]","label":"Instant notification","description":"Immediate email for each triggered application event.","initialValue":false,"component":"descriptiveCheckbox","validate":[],"checkedWarning":"Opting into this notification may result in a large number of emails","disabled":false,"severities":[{"name":"CRITICAL","initialValue":false,"disabled":false},{"name":"IMPORTANT","initialValue":false,"disabled":true},{"name":"MODERATE","initialValue":false,"disabled":false},{"name":"LOW","initialValue":false,"disabled":false},{"name":"NONE","initialValue":false,"disabled":true},{"name":"UNDEFINED","initialValue":false,"disabled":true}]}]}],"label":"appname"},"app-name2":{"eventTypes":[{"name":"event-type-name","label":"Event type","fields":[{"name":"bundles[bundle-name2].applications[app-name2].eventTypes[event-type-name].emailSubscriptionTypes[INSTANT]","label":"Instant notification","description":"Immediate email for each triggered application event.","initialValue":false,"component":"descriptiveCheckbox","validate":[],"checkedWarning":"Opting into this notification may result in a large number of emails","disabled":false,"severities":[{"name":"CRITICAL","initialValue":false,"disabled":false},{"name":"IMPORTANT","initialValue":false,"disabled":true},{"name":"MODERATE","initialValue":false,"disabled":false},{"name":"LOW","initialValue":false,"disabled":false},{"name":"NONE","initialValue":false,"disabled":true},{"name":"UNDEFINED","initialValue":false,"disabled":true}]}]}],"label":"appname3"},"app-name1":{"eventTypes":[{"name":"event-type-name","label":"Event type","fields":[{"name":"bundles[bundle-name2].applications[app-name1].eventTypes[event-type-name].emailSubscriptionTypes[INSTANT]","label":"Instant notification","description":"Immediate email for each triggered application event.","initialValue":false,"component":"descriptiveCheckbox","validate":[],"checkedWarning":"Opting into this notification may result in a large number of emails","disabled":false,"severities":[{"name":"CRITICAL","initialValue":false,"disabled":false},{"name":"IMPORTANT","initialValue":false,"disabled":true},{"name":"MODERATE","initialValue":false,"disabled":false},{"name":"LOW","initialValue":false,"disabled":false},{"name":"NONE","initialValue":false,"disabled":true},{"name":"UNDEFINED","initialValue":false,"disabled":true}]}]}],"label":"appnamez"}},"label":"abundle"},"bundle-name3":{"applications":{"app-name3":{"eventTypes":[{"name":"event-type-name","label":"Event type","fields":[{"name":"bundles[bundle-name3].applications[app-name3].eventTypes[event-type-name].emailSubscriptionTypes[INSTANT]","label":"Instant notification","description":"Immediate email for each triggered application event.","initialValue":false,"component":"descriptiveCheckbox","validate":[],"checkedWarning":"Opting into this notification may result in a large number of emails","disabled":false,"severities":[{"name":"CRITICAL","initialValue":false,"disabled":false},{"name":"IMPORTANT","initialValue":false,"disabled":true},{"name":"MODERATE","initialValue":false,"disabled":false},{"name":"LOW","initialValue":false,"disabled":false},{"name":"NONE","initialValue":false,"disabled":true},{"name":"UNDEFINED","initialValue":false,"disabled":true}]}]}],"label":"a-appname"},"app-name1":{"eventTypes":[{"name":"event-type-name","label":"Event type","fields":[{"name":"bundles[bundle-name3].applications[app-name1].eventTypes[event-type-name].emailSubscriptionTypes[INSTANT]","label":"Instant notification","description":"Immediate email for each triggered application event.","initialValue":false,"component":"descriptiveCheckbox","validate":[],"checkedWarning":"Opting into this notification may result in a large number of emails","disabled":false,"severities":[{"name":"CRITICAL","initialValue":false,"disabled":false},{"name":"IMPORTANT","initialValue":false,"disabled":true},{"name":"MODERATE","initialValue":false,"disabled":false},{"name":"LOW","initialValue":false,"disabled":false},{"name":"NONE","initialValue":false,"disabled":true},{"name":"UNDEFINED","initialValue":false,"disabled":true}]}]}],"label":"e-appname"},"app-name2":{"eventTypes":[{"name":"event-type-name","label":"Event type","fields":[{"name":"bundles[bundle-name3].applications[app-name2].eventTypes[event-type-name].emailSubscriptionTypes[INSTANT]","label":"Instant notification","description":"Immediate email for each triggered application event.","initialValue":false,"component":"descriptiveCheckbox","validate":[],"checkedWarning":"Opting into this notification may result in a large number of emails","disabled":false,"severities":[{"name":"CRITICAL","initialValue":false,"disabled":false},{"name":"IMPORTANT","initialValue":false,"disabled":true},{"name":"MODERATE","initialValue":false,"disabled":false},{"name":"LOW","initialValue":false,"disabled":false},{"name":"NONE","initialValue":false,"disabled":true},{"name":"UNDEFINED","initialValue":false,"disabled":true}]}]}],"label":"r-appname"}},"label":"bbundle"},"rhel":{"applications":{"policies":{"eventTypes":[{"name":"policy-triggered","label":"Policy triggered","fields":[{"name":"bundles[rhel].applications[policies].eventTypes[policy-triggered].emailSubscriptionTypes[INSTANT]","label":"Instant notification","description":"Immediate email for each triggered application event.","initialValue":false,"component":"descriptiveCheckbox","validate":[],"checkedWarning":"Opting into this notification may result in a large number of emails","disabled":false,"severities":[{"name":"CRITICAL","initialValue":false,"disabled":true},{"name":"IMPORTANT","initialValue":false,"disabled":true},{"name":"MODERATE","initialValue":false,"disabled":true},{"name":"LOW","initialValue":false,"disabled":true},{"name":"NONE","initialValue":false,"disabled":true},{"name":"UNDEFINED","initialValue":false,"disabled":true}]}]}],"label":"Policies"}},"label":"Red Hat Enterprise Linux"},"bundle-name1":{"applications":{"app-name1":{"eventTypes":[{"name":"event-type-name","label":"Event type","fields":[{"name":"bundles[bundle-name1].applications[app-name1].eventTypes[event-type-name].emailSubscriptionTypes[INSTANT]","label":"Instant notification","description":"Immediate email for each triggered application event.","initialValue":false,"component":"descriptiveCheckbox","validate":[],"checkedWarning":"Opting into this notification may result in a large number of emails","disabled":false,"severities":[{"name":"CRITICAL","initialValue":false,"disabled":false},{"name":"IMPORTANT","initialValue":false,"disabled":true},{"name":"MODERATE","initialValue":false,"disabled":false},{"name":"LOW","initialValue":false,"disabled":false},{"name":"NONE","initialValue":false,"disabled":true},{"name":"UNDEFINED","initialValue":false,"disabled":true}]}]}],"label":"appname1"},"app-name3":{"eventTypes":[{"name":"event-type-name","label":"Event type","fields":[{"name":"bundles[bundle-name1].applications[app-name3].eventTypes[event-type-name].emailSubscriptionTypes[INSTANT]","label":"Instant notification","description":"Immediate email for each triggered application event.","initialValue":false,"component":"descriptiveCheckbox","validate":[],"checkedWarning":"Opting into this notification may result in a large number of emails","disabled":false,"severities":[{"name":"CRITICAL","initialValue":false,"disabled":false},{"name":"IMPORTANT","initialValue":false,"disabled":true},{"name":"MODERATE","initialValue":false,"disabled":false},{"name":"LOW","initialValue":false,"disabled":false},{"name":"NONE","initialValue":false,"disabled":true},{"name":"UNDEFINED","initialValue":false,"disabled":true}]}]}],"label":"appname3"},"app-name2":{"eventTypes":[{"name":"event-type-name","label":"Event type","fields":[{"name":"bundles[bundle-name1].applications[app-name2].eventTypes[event-type-name].emailSubscriptionTypes[INSTANT]","label":"Instant notification","description":"Immediate email for each triggered application event.","initialValue":false,"component":"descriptiveCheckbox","validate":[],"checkedWarning":"Opting into this notification may result in a large number of emails","disabled":false,"severities":[{"name":"CRITICAL","initialValue":false,"disabled":false},{"name":"IMPORTANT","initialValue":false,"disabled":true},{"name":"MODERATE","initialValue":false,"disabled":false},{"name":"LOW","initialValue":false,"disabled":false},{"name":"NONE","initialValue":false,"disabled":true},{"name":"UNDEFINED","initialValue":false,"disabled":true}]}]}],"label":"appname4"}},"label":"zbundle"}}}""";
        assertEquals(RESULT, mappedString);

        // delete created bundles, apps and event types
        bundleIdsToRemove.forEach(bundleIdToRemove -> deleteBundle(adminIdentity, bundleIdToRemove, true));
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

        String instantTemplateId = createInstantTemplate(bundle, application, eventType);
        String aggregationTemplateId = CrudTestHelpers.createAggregationTemplate(bundle, application, applicationRepository, adminRole);
        resourceHelpers.createDrawerTemplate(bundle, application, eventType);

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

        // the event type is not visible but the org have "show hidden event types" privilege
        when(backendConfig.isShowHiddenEventTypes(eq(orgId))).thenReturn(true);
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
        // remove extra privilege
        when(backendConfig.isShowHiddenEventTypes(eq(orgId))).thenReturn(false);

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

        // Fail if we have unknown event type on subscribe, but nothing will be added on database
        assertThrows(NotFoundException.class, () -> {
            subscriptionRepository.subscribe(orgId, username, UUID.randomUUID(), DAILY);
        });

        // Fail if we have unknown event type on unsubscribe, but nothing will be added on database
        assertThrows(PersistenceException.class, () -> {
            unsubscribe(orgId, username, UUID.randomUUID(), DAILY);
        });

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

        // Does not add event type if is not supported by the templates
        deleteAggregationTemplate(aggregationTemplateId);
        SettingsValueByEventTypeJsonForm settingsValueJsonForm = given()
            .header(identityHeader)
            .when().get(PATH_EVENT_TYPE_PREFERENCE_API)
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(SettingsValueByEventTypeJsonForm.class);
        rhelPolicy = rhelPolicyForm(settingsValueJsonForm);
        assertNotNull(rhelPolicy, "RHEL policies not found");

        Map<SubscriptionType, Boolean> notificationPreferenes = extractNotificationValues(rhelPolicy.eventTypes, bundle, application, eventType);

        if (backendConfig.isDrawerEnabled()) {
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
            .when().get(PATH_EVENT_TYPE_PREFERENCE_API)
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(SettingsValueByEventTypeJsonForm.class);
        rhelPolicy = rhelPolicyForm(settingsValueJsonForm);
        if (backendConfig.isDrawerEnabled()) {
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

    public void unsubscribe(String orgId, String username, UUID eventTypeId, SubscriptionType subscriptionType) {
        subscriptionRepository.updateSubscription(orgId, username, eventTypeId, subscriptionType, false, null);
    }

    @Test
    void testSettingsByEventTypeWithSeverityAndDrawerEnabled() {
        when(backendConfig.isDrawerEnabled()).thenReturn(true);
        testSettingsByEventTypeWithSeverity();
    }

    @Test
    void testSettingsByEventTypeWithSeverity() {
        String accountId = "empty";
        String orgId = "empty";
        String username = "user";
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(accountId, orgId, username);
        Header identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, MockServerConfig.RbacAccess.FULL_ACCESS);
        when(backendConfig.isUseCommonTemplateModuleForUserPrefApisToggle()).thenReturn(true);

        String bundle = "rhel";
        String application = "policies";
        String eventType = "policy-triggered";

        final Set<Severity> AVAILABLE_SEVERITY_SET = Set.of(Severity.MODERATE, Severity.CRITICAL, Severity.LOW, Severity.UNDEFINED);
        updatePoliciesEventTypeAvailableSeverities(AVAILABLE_SEVERITY_SET);

        // Policy event should not be returned because it is not visible
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

        // Policy event should be returned because it is visible
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
        SettingsValuesByEventType settingsValues = createSettingsValueWithSeverity(bundle, application, eventType, Set.of(Severity.MODERATE, Severity.LOW), Set.of(Severity.CRITICAL), null);
        // should return an error because we try to set a instant email preference, while the instantEmailsEnabled flag is false.
        postPreferencesByEventType(identityHeader, settingsValues, 400);

        // same config with instant email enabled should work
        when(backendConfig.isInstantEmailsEnabled()).thenReturn(true);
        postPreferencesByEventType(identityHeader, settingsValues, 200);

        SettingsValueByEventTypeJsonForm settingsValue = getPreferencesByEventType(identityHeader);
        rhelPolicy = rhelPolicyForm(settingsValue);
        Map<SubscriptionType, Set<SettingsValueByEventTypeJsonForm.SeverityDetails>> policiesSubscriptionDetails = extractNotificationSubscriptionTypeDetails(rhelPolicy.eventTypes, bundle, application, eventType);
        assertEquals(Set.of(Severity.MODERATE, Severity.LOW), getSubscribedSeveritiesSet(policiesSubscriptionDetails.get(INSTANT)));
        assertEquals(Set.of(Severity.CRITICAL), getSubscribedSeveritiesSet(policiesSubscriptionDetails.get(DAILY)));
        assertEquals(AVAILABLE_SEVERITY_SET, getAvailableSeveritiesSet(policiesSubscriptionDetails.get(INSTANT)));

        if (backendConfig.isDrawerEnabled()) {
            assertTrue(getSubscribedSeveritiesSet(policiesSubscriptionDetails.get(DRAWER)).isEmpty());
        }

        when(backendConfig.isInstantEmailsEnabled()).thenReturn(false);
        settingsValue = getPreferencesByEventType(identityHeader);
        rhelPolicy = rhelPolicyForm(settingsValue);

        policiesSubscriptionDetails = extractNotificationSubscriptionTypeDetails(rhelPolicy.eventTypes, bundle, application, eventType);

        boolean instantEmailSettingsReturned = policiesSubscriptionDetails.keySet().stream().anyMatch(INSTANT::equals);
        assertFalse(instantEmailSettingsReturned, "Instant email subscription settings should not be returned when instant emails are disabled");

        when(backendConfig.isInstantEmailsEnabled()).thenReturn(true);

        // Daily and Instant to false
        updateAndCheckUserPreferenceWithSeverities(identityHeader, bundle, application, eventType, new HashMap<>());

        // Daily to true
        updateAndCheckUserPreferenceWithSeverities(identityHeader, bundle, application, eventType, Map.of(DRAWER, Set.of(Severity.LOW, Severity.MODERATE), DAILY, Set.of(Severity.CRITICAL)));

        // Instant to true
        updateAndCheckUserPreferenceWithSeverities(identityHeader, bundle, application, eventType, Map.of(INSTANT, Set.of(Severity.IMPORTANT), DRAWER, Set.of(Severity.LOW)));

        // Both to true
        updateAndCheckUserPreferenceWithSeverities(identityHeader, bundle, application, eventType, Map.of(INSTANT, Set.of(Severity.MODERATE, Severity.CRITICAL), DRAWER, Set.of(Severity.LOW, Severity.MODERATE), DAILY, Set.of(Severity.CRITICAL)));

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
            updateAndCheckUserPreferenceWithSeverities(identityHeader, bundle, application, eventType, new HashMap<>());

            // Daily to true
            updateAndCheckUserPreferenceWithSeverities(identityHeader, bundle, application, eventType, Map.of(DAILY, Set.of(Severity.LOW, Severity.MODERATE)));

            // Instant to true
            updateAndCheckUserPreferenceWithSeverities(identityHeader, bundle, application, eventType, Map.of(INSTANT, Set.of(Severity.IMPORTANT)));

            // Daily and instant to true
            updateAndCheckUserPreferenceWithSeverities(identityHeader, bundle, application, eventType, Map.of(INSTANT, Set.of(Severity.MODERATE, Severity.CRITICAL), DAILY, Set.of(Severity.LOW, Severity.MODERATE)));

            // Daily and Instant to false, drawer to true
            updateAndCheckUserPreferenceWithSeverities(identityHeader, bundle, application, eventType, Map.of(DRAWER, Set.of(Severity.LOW, Severity.MODERATE)));

            // Daily to true
            updateAndCheckUserPreferenceWithSeverities(identityHeader, bundle, application, eventType, Map.of(DRAWER, Set.of(Severity.IMPORTANT, Severity.MODERATE), DAILY, Set.of(Severity.LOW, Severity.MODERATE)));

            // Instant to true
            updateAndCheckUserPreferenceWithSeverities(identityHeader, bundle, application, eventType, Map.of(DRAWER, Set.of(Severity.MODERATE, Severity.CRITICAL), INSTANT, Set.of(Severity.LOW, Severity.MODERATE)));

            // Daily and instant to true
            updateAndCheckUserPreferenceWithSeverities(identityHeader, bundle, application, eventType, Map.of(DRAWER, Set.of(Severity.MODERATE, Severity.CRITICAL), INSTANT, Set.of(Severity.LOW, Severity.MODERATE), DAILY, Set.of(Severity.UNDEFINED, Severity.NONE)));
        }

        // Fail if we have unknown event type on subscribe, but nothing will be added on database
        assertThrows(ConstraintViolationException.class, () -> {
            subscriptionRepository.subscribe(orgId, username, UUID.randomUUID(), DAILY);
        });

        // Fail if we have unknown event type on unsubscribe, but nothing will be added on database
        assertThrows(PersistenceException.class, () -> {
            unsubscribe(orgId, username, UUID.randomUUID(), DAILY);
        });

        // does not add if we try to create unknown bundle/apps
        settingsValues = createSettingsValueWithSeverity("not-found-bundle-2", "not-found-app-2", eventType, Set.of(Severity.MODERATE, Severity.LOW), Set.of(Severity.CRITICAL), Set.of(Severity.IMPORTANT, Severity.LOW));
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
        Map<SubscriptionType, Set<SettingsValueByEventTypeJsonForm.SeverityDetails>> initialValues = extractNotificationSubscriptionTypeDetails(rhelPolicy.eventTypes, "not-found-bundle-2", "not-found-app-2", eventType);
        assertEquals(0, initialValues.size());

        // Does not add event type if is not supported by the templates
        SettingsValueByEventTypeJsonForm settingsValueJsonForm = given()
            .header(identityHeader)
            .when().get(PATH_EVENT_TYPE_PREFERENCE_API)
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(SettingsValueByEventTypeJsonForm.class);
        rhelPolicy = rhelPolicyForm(settingsValueJsonForm);
        assertNotNull(rhelPolicy, "RHEL policies not found");

        Map<SubscriptionType, Set<SettingsValueByEventTypeJsonForm.SeverityDetails>> notificationPreferences = extractNotificationSubscriptionTypeDetails(rhelPolicy.eventTypes, bundle, application, eventType);

        if (backendConfig.isDrawerEnabled()) {
            assertEquals(3, notificationPreferences.size());
            assertTrue(notificationPreferences.containsKey(DRAWER));
        } else {
            assertEquals(2, notificationPreferences.size());
        }
        assertTrue(notificationPreferences.containsKey(INSTANT));
    }

    private Set<Severity> getSubscribedSeveritiesSet(Set<SettingsValueByEventTypeJsonForm.SeverityDetails> severitySubscriptionSet) {
        if (severitySubscriptionSet == null) {
            return Set.of();
        }
        return severitySubscriptionSet.stream()
            .filter(severity -> severity.initialValue)
            .map(severity -> Severity.valueOf(severity.name))
            .collect(Collectors.toSet());
    }

    private Set<Severity> getAvailableSeveritiesSet(Set<SettingsValueByEventTypeJsonForm.SeverityDetails> severitySubscriptionSet) {
        if (severitySubscriptionSet == null) {
            return Set.of();
        }
        return severitySubscriptionSet.stream()
            .filter(severity -> !severity.disabled)
            .map(severity -> Severity.valueOf(severity.name))
            .collect(Collectors.toSet());
    }

    private void updateAndCheckUserPreferenceWithSeverities(Header identityHeader, String bundle, String application, String eventType, Map<SubscriptionType, Set<Severity>> severity) {
        SettingsValuesByEventType settingsValues = createSettingsValueWithSeverity(bundle, application, eventType, severity.get(INSTANT), severity.get(DAILY), severity.get(DRAWER));
        postPreferencesByEventType(identityHeader, settingsValues, 200);

        // verify get all preferences for all bundles and applications response
        SettingsValueByEventTypeJsonForm settingsValuesByEventType = getPreferencesByEventType(identityHeader);
        final SettingsValueByEventTypeJsonForm.Application rhelPolicy = rhelPolicyForm(settingsValuesByEventType);
        assertNotNull(rhelPolicy, "RHEL policies not found");
        Map<SubscriptionType, Set<SettingsValueByEventTypeJsonForm.SeverityDetails>> initialValues = extractNotificationSubscriptionTypeDetails(rhelPolicy.eventTypes, bundle, application, eventType);

        final Set<Severity> expectedDailySeverities = severity.get(DAILY) == null ? Set.of() : severity.get(DAILY);
        final Set<Severity> expectedInstantSeverities = severity.get(INSTANT) == null ? Set.of() : severity.get(INSTANT);
        final Set<Severity> expectedDrawerSeverities = severity.get(DRAWER) == null ? Set.of() : severity.get(DRAWER);

        Set<Severity> subscribedSeverities = getSubscribedSeveritiesSet(initialValues.get(DAILY));
        assertEquals(expectedDailySeverities, subscribedSeverities);

        subscribedSeverities = getSubscribedSeveritiesSet(initialValues.get(INSTANT));
        assertEquals(expectedInstantSeverities, subscribedSeverities);

        if (backendConfig.isDrawerEnabled()) {
            subscribedSeverities = getSubscribedSeveritiesSet(initialValues.get(DRAWER));
            assertEquals(expectedDrawerSeverities, subscribedSeverities);
        }

        // verify get all preferences for a specific application response
        final SettingsValueByEventTypeJsonForm.Application preferences = given()
            .header(identityHeader)
            .when().get(String.format(PATH_EVENT_TYPE_PREFERENCE_API + "/%s/%s", bundle, application))
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(SettingsValueByEventTypeJsonForm.Application.class);

        assertNotNull(preferences);
        Map<SubscriptionType, Set<SettingsValueByEventTypeJsonForm.SeverityDetails>> notificationPreferences = extractNotificationSubscriptionTypeDetails(preferences.eventTypes, bundle, application, eventType);
        subscribedSeverities = getSubscribedSeveritiesSet(notificationPreferences.get(DAILY));
        assertEquals(expectedDailySeverities, subscribedSeverities);

        subscribedSeverities = getSubscribedSeveritiesSet(notificationPreferences.get(INSTANT));
        assertEquals(expectedInstantSeverities, subscribedSeverities);

        if (backendConfig.isDrawerEnabled()) {
            subscribedSeverities = getSubscribedSeveritiesSet(notificationPreferences.get(DRAWER));
            assertEquals(expectedDrawerSeverities, subscribedSeverities);
        }
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
    void updatePoliciesEventTypeAvailableSeverities(Set<Severity> availableSeverities) {
        entityManager.createQuery("UPDATE EventType SET availableSeverities = :availableSeverities where name='policy-triggered'")
            .setParameter("availableSeverities", availableSeverities)
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

    private void deleteAggregationTemplate(String templateId) {
        Header adminIdentity = TestHelpers.createTurnpikeIdentityHeader("user", adminRole);
        deleteAggregationEmailTemplate(adminIdentity, templateId);
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

        createInstantTemplate(bundle, application, eventType);
        CrudTestHelpers.createAggregationTemplate(bundle, application, applicationRepository, adminRole);

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

        //String path = TestConstants.API_NOTIFICATIONS_V_1_0 + "/user-config/notification-event-type-preference";
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
