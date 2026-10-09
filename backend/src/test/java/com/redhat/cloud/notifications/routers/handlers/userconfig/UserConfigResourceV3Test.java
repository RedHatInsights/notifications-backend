package com.redhat.cloud.notifications.routers.handlers.userconfig;

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
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.models.dto.v3.subscriptions.ApplicationSubscriptionUpdateDTO;
import com.redhat.cloud.notifications.models.dto.v3.subscriptions.BundleSubscriptionDTO;
import com.redhat.cloud.notifications.models.dto.v3.subscriptions.BundleSubscriptionUpdateDTO;
import com.redhat.cloud.notifications.models.dto.v3.subscriptions.EventTypeSubscriptionUpdateDTO;
import com.redhat.cloud.notifications.models.dto.v3.subscriptions.SeverityDTO;
import com.redhat.cloud.notifications.models.dto.v3.subscriptions.SubscriptionChannelDTO;
import com.redhat.cloud.notifications.models.dto.v3.subscriptions.SubscriptionTypeDTO;
import com.redhat.cloud.notifications.routers.models.SettingsValueByEventTypeJsonForm;
import com.redhat.cloud.notifications.routers.models.SettingsValuesByEventType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.Header;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.FULL_ACCESS;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_USER;
import static com.redhat.cloud.notifications.models.SubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.SubscriptionType.DRAWER;
import static com.redhat.cloud.notifications.models.SubscriptionType.INSTANT;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class UserConfigResourceV3Test extends DbIsolatedTest {

    private static final String SUBSCRIPTIONS_PATH = "/user-config/subscriptions";
    private static final String NOTIFICATION_PREFERENCE_PATH = "/user-config/notification-event-type-preference";

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    EntityManager entityManager;

    @InjectMock
    BackendConfig backendConfig;

    Header identityHeader;

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_NOTIFICATIONS_V_3_0;
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);
        when(backendConfig.isInstantEmailsEnabled()).thenReturn(true);
        when(backendConfig.isUseCommonTemplateModuleForUserPrefApisToggle()).thenReturn(true);
    }

    private EventType createEventType(Application application, String name, Set<Severity> availableSeverities, boolean subscribedByDefault) {
        EventType eventType = new EventType();
        eventType.setName(name);
        eventType.setDisplayName(name + "-display-name");
        eventType.setApplicationId(application.getId());
        eventType.setAvailableSeverities(availableSeverities);
        eventType.setSubscribedByDefault(subscribedByDefault);
        return applicationRepository.createEventType(eventType);
    }

    private List<BundleSubscriptionDTO> getSubscriptions(String bundle, String application, String eventType) {
        Map<String, Object> params = new HashMap<>();
        if (bundle != null) {
            params.put("bundle", bundle);
        }
        if (application != null) {
            params.put("application", application);
        }
        if (eventType != null) {
            params.put("event_type", eventType);
        }
        return given()
            .header(identityHeader)
            .queryParams(params)
            .when().get(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_OK)
            .extract().body().as(new TypeRef<>() {
            });
    }

    @Test
    void testGetSubscriptionsReturnsTree() {
        Bundle bundle = resourceHelpers.createBundle("bundle-a", "Bundle A");
        Application application = resourceHelpers.createApplication(bundle.getId(), "app-a", "App A");
        createEventType(application, "event-a", Set.of(Severity.CRITICAL, Severity.IMPORTANT), false);
        createEventType(application, "event-b", Set.of(Severity.CRITICAL), true);

        List<BundleSubscriptionDTO> tree = getSubscriptions(null, null, null);

        BundleSubscriptionDTO bundleDTO = tree.stream().filter(b -> b.getBundle().equals("bundle-a")).findFirst().orElseThrow();
        assertEquals("bundle-a", bundleDTO.getBundle());
        assertEquals("Bundle A", bundleDTO.getBundleDisplayName());
        assertEquals(1, bundleDTO.getApplications().size());
        assertEquals(2, bundleDTO.getApplications().get(0).getEventTypes().size());
    }

    @Test
    void testGetSubscriptionsWithBundleFilter() {
        Bundle bundleA = resourceHelpers.createBundle("bundle-a", "Bundle A");
        Application applicationA = resourceHelpers.createApplication(bundleA.getId(), "app-a", "App A");
        createEventType(applicationA, "event-a", Set.of(Severity.CRITICAL), false);

        Bundle bundleB = resourceHelpers.createBundle("bundle-b", "Bundle B");
        Application applicationB = resourceHelpers.createApplication(bundleB.getId(), "app-b", "App B");
        createEventType(applicationB, "event-b", Set.of(Severity.CRITICAL), false);

        List<BundleSubscriptionDTO> filtered = getSubscriptions("bundle-a", null, null);
        assertEquals(1, filtered.size());
        assertEquals("bundle-a", filtered.get(0).getBundle());
    }

    @Test
    void testGetSubscriptionsMissingParentParam() {
        given()
            .header(identityHeader)
            .queryParam("application", "app-a")
            .when().get(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);

        given()
            .header(identityHeader)
            .queryParam("event_type", "event-a")
            .when().get(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);

        given()
            .header(identityHeader)
            .queryParam("bundle", "bundle-a")
            .queryParam("event_type", "event-a")
            .when().get(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void testUpdateSubscriptionsReturns204() {
        Bundle bundle = resourceHelpers.createBundle("bundle-a", "Bundle A");
        Application application = resourceHelpers.createApplication(bundle.getId(), "app-a", "App A");
        createEventType(application, "event-a", Set.of(Severity.CRITICAL, Severity.IMPORTANT), false);

        BundleSubscriptionUpdateDTO update = new BundleSubscriptionUpdateDTO();
        update.setBundle("bundle-a");
        ApplicationSubscriptionUpdateDTO appUpdate = new ApplicationSubscriptionUpdateDTO();
        appUpdate.setApplication("app-a");
        EventTypeSubscriptionUpdateDTO etUpdate = new EventTypeSubscriptionUpdateDTO();
        etUpdate.setEventType("event-a");
        etUpdate.setSubscriptions(List.of(new SubscriptionChannelDTO(SubscriptionTypeDTO.INSTANT, List.of(SeverityDTO.CRITICAL))));
        appUpdate.setEventTypes(List.of(etUpdate));
        update.setApplications(List.of(appUpdate));

        given()
            .header(identityHeader)
            .contentType(JSON)
            .body(List.of(update))
            .when().put(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_NO_CONTENT);

        List<BundleSubscriptionDTO> tree = getSubscriptions("bundle-a", "app-a", "event-a");
        var eventTypeDTO = tree.get(0).getApplications().get(0).getEventTypes().get(0);
        SubscriptionChannelDTO instantChannel = eventTypeDTO.getSubscriptions().stream()
            .filter(c -> c.getSubscriptionType() == SubscriptionTypeDTO.INSTANT)
            .findFirst().orElseThrow();
        assertEquals(List.of(SeverityDTO.CRITICAL), instantChannel.getSubscribedSeverities());
    }

    @Test
    void testServiceAccountForbidden() {
        String identityHeaderValue = TestHelpers.encodeRHServiceAccountIdentityInfo(DEFAULT_ORG_ID, "service-account", UUID.randomUUID().toString());
        Header serviceAccountHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        given()
            .header(serviceAccountHeader)
            .when().get(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);

        given()
            .header(serviceAccountHeader)
            .contentType(JSON)
            .body(List.of())
            .when().put(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);
    }

    @Test
    void testGetSubscriptionsUnknownBundle() {
        given()
            .header(identityHeader)
            .queryParam("bundle", "does-not-exist")
            .when().get(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void testGetSubscriptionsUnknownApplication() {
        resourceHelpers.createBundle("bundle-a", "Bundle A");

        given()
            .header(identityHeader)
            .queryParam("bundle", "bundle-a")
            .queryParam("application", "does-not-exist")
            .when().get(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void testGetSubscriptionsUnknownEventType() {
        Bundle bundle = resourceHelpers.createBundle("bundle-a", "Bundle A");
        resourceHelpers.createApplication(bundle.getId(), "app-a", "App A");

        given()
            .header(identityHeader)
            .queryParam("bundle", "bundle-a")
            .queryParam("application", "app-a")
            .queryParam("event_type", "does-not-exist")
            .when().get(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void testGetSubscriptionsAppWithNoEventTypesReturnsEmptyTree() {
        Bundle bundle = resourceHelpers.createBundle("bundle-a", "Bundle A");
        resourceHelpers.createApplication(bundle.getId(), "app-a", "App A");

        List<BundleSubscriptionDTO> tree = getSubscriptions("bundle-a", "app-a", null);
        assertEquals(List.of(), tree);
    }

    @Test
    void testGetSubscriptionsWithApplicationFilter() {
        Bundle bundle = resourceHelpers.createBundle("bundle-a", "Bundle A");
        Application appA = resourceHelpers.createApplication(bundle.getId(), "app-a", "App A");
        Application appB = resourceHelpers.createApplication(bundle.getId(), "app-b", "App B");
        createEventType(appA, "event-a", Set.of(Severity.CRITICAL), false);
        createEventType(appB, "event-b", Set.of(Severity.CRITICAL), false);

        List<BundleSubscriptionDTO> filtered = getSubscriptions("bundle-a", "app-a", null);
        assertEquals(1, filtered.size());
        assertEquals(1, filtered.get(0).getApplications().size());
        assertEquals("app-a", filtered.get(0).getApplications().get(0).getApplication());
    }

    @Test
    void testGetSubscriptionsWithEventTypeFilter() {
        Bundle bundle = resourceHelpers.createBundle("bundle-a", "Bundle A");
        Application application = resourceHelpers.createApplication(bundle.getId(), "app-a", "App A");
        createEventType(application, "event-a", Set.of(Severity.CRITICAL), false);
        createEventType(application, "event-b", Set.of(Severity.IMPORTANT), false);

        List<BundleSubscriptionDTO> filtered = getSubscriptions("bundle-a", "app-a", "event-a");
        assertEquals(1, filtered.size());
        assertEquals(1, filtered.get(0).getApplications().get(0).getEventTypes().size());
        assertEquals("event-a", filtered.get(0).getApplications().get(0).getEventTypes().get(0).getEventType());
    }

    @Test
    void testUpdateAndVerifyMultipleSeverities() {
        Bundle bundle = resourceHelpers.createBundle("bundle-a", "Bundle A");
        Application application = resourceHelpers.createApplication(bundle.getId(), "app-a", "App A");
        createEventType(application, "event-a", Set.of(Severity.CRITICAL, Severity.IMPORTANT, Severity.MODERATE), false);

        BundleSubscriptionUpdateDTO update = buildSingleLeafUpdate("bundle-a", "app-a", "event-a",
                SubscriptionTypeDTO.INSTANT, List.of(SeverityDTO.CRITICAL, SeverityDTO.IMPORTANT));

        given()
            .header(identityHeader)
            .contentType(JSON)
            .body(List.of(update))
            .when().put(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_NO_CONTENT);

        List<BundleSubscriptionDTO> tree = getSubscriptions("bundle-a", "app-a", "event-a");
        var eventTypeDTO = tree.get(0).getApplications().get(0).getEventTypes().get(0);
        SubscriptionChannelDTO instantChannel = eventTypeDTO.getSubscriptions().stream()
            .filter(c -> c.getSubscriptionType() == SubscriptionTypeDTO.INSTANT)
            .findFirst().orElseThrow();
        assertEquals(2, instantChannel.getSubscribedSeverities().size());
    }

    @Test
    void testUpdateSubscriptionsInvalidSeverity() {
        Bundle bundle = resourceHelpers.createBundle("bundle-a", "Bundle A");
        Application application = resourceHelpers.createApplication(bundle.getId(), "app-a", "App A");
        createEventType(application, "event-a", Set.of(Severity.CRITICAL), false);

        BundleSubscriptionUpdateDTO update = buildSingleLeafUpdate("bundle-a", "app-a", "event-a",
                SubscriptionTypeDTO.INSTANT, List.of(SeverityDTO.MODERATE));

        given()
            .header(identityHeader)
            .contentType(JSON)
            .body(List.of(update))
            .when().put(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void testUpdateSubscriptionsUnknownEventType() {
        Bundle bundle = resourceHelpers.createBundle("bundle-a", "Bundle A");
        resourceHelpers.createApplication(bundle.getId(), "app-a", "App A");

        BundleSubscriptionUpdateDTO update = buildSingleLeafUpdate("bundle-a", "app-a", "does-not-exist",
                SubscriptionTypeDTO.INSTANT, List.of());

        given()
            .header(identityHeader)
            .contentType(JSON)
            .body(List.of(update))
            .when().put(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void testUpdateSubscriptionsRejectsEmptyBody() {
        given()
            .header(identityHeader)
            .contentType(JSON)
            .body("[]")
            .when().put(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void testUpdateSubscriptionsRejectsNullTopLevelItem() {
        given()
            .header(identityHeader)
            .contentType(JSON)
            .body("[null]")
            .when().put(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    private BundleSubscriptionUpdateDTO buildSingleLeafUpdate(String bundleName, String applicationName, String eventTypeName,
                                                               SubscriptionTypeDTO subscriptionType, List<SeverityDTO> severities) {
        BundleSubscriptionUpdateDTO update = new BundleSubscriptionUpdateDTO();
        update.setBundle(bundleName);
        ApplicationSubscriptionUpdateDTO appUpdate = new ApplicationSubscriptionUpdateDTO();
        appUpdate.setApplication(applicationName);
        EventTypeSubscriptionUpdateDTO etUpdate = new EventTypeSubscriptionUpdateDTO();
        etUpdate.setEventType(eventTypeName);
        etUpdate.setSubscriptions(List.of(new SubscriptionChannelDTO(subscriptionType, severities)));
        appUpdate.setEventTypes(List.of(etUpdate));
        update.setApplications(List.of(appUpdate));
        return update;
    }

    // --- notification-event-type-preference (private legacy API) tests ---

    private SettingsValuesByEventType createSettingsValue(String bundle, String application, String eventType, boolean daily, boolean instant, boolean drawer) {
        SettingsValuesByEventType.EventTypeSettingsValue eventTypeSettingsValue = new SettingsValuesByEventType.EventTypeSettingsValue();
        eventTypeSettingsValue.emailSubscriptionTypes.put(DAILY, daily);
        eventTypeSettingsValue.emailSubscriptionTypes.put(INSTANT, instant);
        eventTypeSettingsValue.emailSubscriptionTypes.put(DRAWER, drawer);

        SettingsValuesByEventType.ApplicationSettingsValue applicationSettingsValue = new SettingsValuesByEventType.ApplicationSettingsValue();
        applicationSettingsValue.eventTypes.put(eventType, eventTypeSettingsValue);

        SettingsValuesByEventType.BundleSettingsValue bundleSettingsValue = new SettingsValuesByEventType.BundleSettingsValue();
        bundleSettingsValue.applications.put(application, applicationSettingsValue);

        SettingsValuesByEventType settingsValues = new SettingsValuesByEventType();
        settingsValues.bundles.put(bundle, bundleSettingsValue);

        return settingsValues;
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

    @Transactional
    void updateEventTypeAvailableSeverities(String eventTypeName, Set<Severity> availableSeverities) {
        entityManager.createQuery("UPDATE EventType SET availableSeverities = :availableSeverities where name = :name")
            .setParameter("availableSeverities", availableSeverities)
            .setParameter("name", eventTypeName)
            .executeUpdate();
    }

    @Transactional
    void updateEventTypeIncludedInDrawer(String eventTypeName, boolean includedInDrawer) {
        entityManager.createQuery("UPDATE EventType SET includedInDrawer = :includedInDrawer where name = :name")
            .setParameter("includedInDrawer", includedInDrawer)
            .setParameter("name", eventTypeName)
            .executeUpdate();
    }

    @Test
    void testGetSettingsSchemaByEventType() {
        SettingsValueByEventTypeJsonForm settingsValuesByEventType = given()
            .header(identityHeader)
            .when().get(NOTIFICATION_PREFERENCE_PATH)
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(SettingsValueByEventTypeJsonForm.class);

        assertNotNull(settingsValuesByEventType);
        assertNotNull(settingsValuesByEventType.bundles);
    }

    @Test
    void testSaveAndGetSettingsByEventType() {
        when(backendConfig.isDrawerEnabled(anyString())).thenReturn(true);

        String bundle = "rhel";
        String application = "advisor";
        String eventType = "new-recommendation";

        updateEventTypeAvailableSeverities(eventType, Set.of());
        updateEventTypeIncludedInDrawer(eventType, true);

        SettingsValuesByEventType settingsValues = createSettingsValue(bundle, application, eventType, true, true, true);
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(settingsValues))
            .post(NOTIFICATION_PREFERENCE_PATH)
            .then()
            .statusCode(200);

        SettingsValueByEventTypeJsonForm settingsValuesByEventType = given()
            .header(identityHeader)
            .when().get(NOTIFICATION_PREFERENCE_PATH)
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(SettingsValueByEventTypeJsonForm.class);

        assertNotNull(settingsValuesByEventType.bundles.get(bundle));
        SettingsValueByEventTypeJsonForm.Application rhelAdvisor = settingsValuesByEventType.bundles.get(bundle).applications.get(application);
        assertNotNull(rhelAdvisor, "RHEL advisor not found");
        Map<SubscriptionType, Boolean> notificationValues = extractNotificationValues(rhelAdvisor.eventTypes, bundle, application, eventType);
        assertTrue(notificationValues.get(INSTANT));
        assertTrue(notificationValues.get(DAILY));
        assertTrue(notificationValues.get(DRAWER));
    }

    @Test
    void testGetPreferencesByEventTypeForBundleAndApplication() {
        when(backendConfig.isDrawerEnabled(anyString())).thenReturn(true);

        String bundle = "rhel";
        String application = "advisor";
        String eventType = "new-recommendation";

        updateEventTypeAvailableSeverities(eventType, Set.of());
        updateEventTypeIncludedInDrawer(eventType, true);

        SettingsValuesByEventType settingsValues = createSettingsValue(bundle, application, eventType, false, true, false);
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(settingsValues))
            .post(NOTIFICATION_PREFERENCE_PATH)
            .then()
            .statusCode(200);

        SettingsValueByEventTypeJsonForm.Application preferences = given()
            .header(identityHeader)
            .when().get(NOTIFICATION_PREFERENCE_PATH + "/" + bundle + "/" + application)
            .then()
            .statusCode(200)
            .contentType(JSON)
            .extract().body().as(SettingsValueByEventTypeJsonForm.Application.class);

        assertNotNull(preferences);
        Map<SubscriptionType, Boolean> notificationValues = extractNotificationValues(preferences.eventTypes, bundle, application, eventType);
        assertTrue(notificationValues.get(INSTANT));
        assertFalse(notificationValues.get(DAILY));
    }

    @Test
    void testSaveSettingsRejectsInstantWhenDisabled() {
        when(backendConfig.isInstantEmailsEnabled()).thenReturn(false);

        String bundle = "rhel";
        String application = "advisor";
        String eventType = "new-recommendation";

        SettingsValuesByEventType settingsValues = createSettingsValue(bundle, application, eventType, true, true, false);
        given()
            .header(identityHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(settingsValues))
            .post(NOTIFICATION_PREFERENCE_PATH)
            .then()
            .statusCode(400);
    }

    @Test
    void testNotificationPreferenceServiceAccountForbidden() {
        String identityHeaderValue = TestHelpers.encodeRHServiceAccountIdentityInfo(DEFAULT_ORG_ID, "service-account", UUID.randomUUID().toString());
        Header serviceAccountHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);

        SettingsValuesByEventType settingsValues = createSettingsValue("rhel", "advisor", "new-recommendation", true, true, true);
        given()
            .header(serviceAccountHeader)
            .when()
            .contentType(JSON)
            .body(Json.encode(settingsValues))
            .post(NOTIFICATION_PREFERENCE_PATH)
            .then()
            .statusCode(403);

        given()
            .header(serviceAccountHeader)
            .when().get(NOTIFICATION_PREFERENCE_PATH)
            .then()
            .statusCode(403);

        given()
            .header(serviceAccountHeader)
            .when().get(NOTIFICATION_PREFERENCE_PATH + "/rhel/advisor")
            .then()
            .statusCode(403);
    }
}
