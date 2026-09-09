package com.redhat.cloud.notifications.routers.handlers.userconfig;

import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.db.repositories.SubscriptionRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.models.dto.v2.subscriptions.ApplicationSubscriptionUpdateDTO;
import com.redhat.cloud.notifications.models.dto.v2.subscriptions.BundleSubscriptionDTO;
import com.redhat.cloud.notifications.models.dto.v2.subscriptions.BundleSubscriptionUpdateDTO;
import com.redhat.cloud.notifications.models.dto.v2.subscriptions.EventTypeSubscriptionUpdateDTO;
import com.redhat.cloud.notifications.models.dto.v2.subscriptions.SeverityDTO;
import com.redhat.cloud.notifications.models.dto.v2.subscriptions.SubscriptionChannelDTO;
import com.redhat.cloud.notifications.models.dto.v2.subscriptions.SubscriptionTypeDTO;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.Header;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
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
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class UserConfigResourceV2Test extends DbIsolatedTest {

    private static final String SUBSCRIPTIONS_PATH = "/user-config/subscriptions";

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    ApplicationRepository applicationRepository;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @InjectMock
    BackendConfig backendConfig;

    Header identityHeader;

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_NOTIFICATIONS_V_2_0;
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);
        // Bypass the legacy per-event-type template existence check in SubscriptionRepository.updateSubscription:
        // this test suite is about the subscription tree shape, not about template wiring.
        when(backendConfig.isUseCommonTemplateModuleForUserPrefApisToggle()).thenReturn(true);
    }

    private EventType createEventType(UUID applicationId, String name, Set<Severity> availableSeverities, boolean subscribedByDefault) {
        EventType eventType = new EventType();
        eventType.setName(name);
        eventType.setDisplayName(name + "-display-name");
        eventType.setApplicationId(applicationId);
        eventType.setAvailableSeverities(availableSeverities);
        eventType.setSubscribedByDefault(subscribedByDefault);
        return applicationRepository.createEventType(eventType);
    }

    private ValidatableResponse requestSubscriptions(String bundle, String application, String eventType) {
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
            .then();
    }

    private List<BundleSubscriptionDTO> getSubscriptions(String bundle, String application, String eventType) {
        return requestSubscriptions(bundle, application, eventType)
            .statusCode(HttpStatus.SC_OK)
            .extract().body().as(new TypeRef<>() {
            });
    }

    private void assertGetSubscriptionsStatus(String bundle, String application, String eventType, int expectedStatusCode) {
        requestSubscriptions(bundle, application, eventType).statusCode(expectedStatusCode);
    }

    @Test
    void testGetSubscriptionsDefaultTree() {
        Bundle bundle = resourceHelpers.createBundle("bundle-a", "Bundle A");
        Application application = resourceHelpers.createApplication(bundle.getId(), "app-a", "App A");
        createEventType(application.getId(), "not-subscribed-by-default", Set.of(Severity.CRITICAL, Severity.IMPORTANT), false);
        createEventType(application.getId(), "subscribed-by-default", Set.of(Severity.CRITICAL, Severity.IMPORTANT, Severity.LOW), true);

        List<BundleSubscriptionDTO> tree = getSubscriptions(null, null, null);

        // The DB isolation cleaner reseeds a default "rhel" bundle after every test, so filter to ours.
        BundleSubscriptionDTO bundleDTO = tree.stream().filter(b -> b.getBundle().equals("bundle-a")).findFirst().orElseThrow();
        assertEquals("bundle-a", bundleDTO.getBundle());
        assertEquals("Bundle A", bundleDTO.getBundleDisplayName());
        assertEquals(1, bundleDTO.getApplications().size());
        assertEquals(2, bundleDTO.getApplications().get(0).getEventTypes().size());

        var eventTypes = bundleDTO.getApplications().get(0).getEventTypes();
        var notSubscribedByDefault = eventTypes.stream().filter(e -> e.getEventType().equals("not-subscribed-by-default")).findFirst().orElseThrow();
        assertEquals(List.of(SeverityDTO.CRITICAL, SeverityDTO.IMPORTANT), notSubscribedByDefault.getAvailableSeverities());
        assertChannelSeverities(notSubscribedByDefault.getSubscriptions(), SubscriptionTypeDTO.INSTANT, List.of());
        assertChannelSeverities(notSubscribedByDefault.getSubscriptions(), SubscriptionTypeDTO.DAILY, List.of());
        // DRAWER is subscribed-by-default at the channel level regardless of the event type's own default.
        assertChannelSeverities(notSubscribedByDefault.getSubscriptions(), SubscriptionTypeDTO.DRAWER, List.of(SeverityDTO.CRITICAL, SeverityDTO.IMPORTANT));

        var subscribedByDefault = eventTypes.stream().filter(e -> e.getEventType().equals("subscribed-by-default")).findFirst().orElseThrow();
        List<SeverityDTO> allSeverities = List.of(SeverityDTO.CRITICAL, SeverityDTO.IMPORTANT, SeverityDTO.LOW);
        assertChannelSeverities(subscribedByDefault.getSubscriptions(), SubscriptionTypeDTO.INSTANT, allSeverities);
        assertChannelSeverities(subscribedByDefault.getSubscriptions(), SubscriptionTypeDTO.DAILY, allSeverities);
        assertChannelSeverities(subscribedByDefault.getSubscriptions(), SubscriptionTypeDTO.DRAWER, allSeverities);
    }

    @Test
    void testGetSubscriptionsExcludesUndefinedSeverity() {
        // Severity.UNDEFINED has no SeverityDTO counterpart. It can end up in an event type's
        // available severities, or in a stored subscription's severities map (e.g. legacy data),
        // even though the write side of this API can never produce it. The GET side must ignore
        // it rather than fail the whole request.
        Bundle bundle = resourceHelpers.createBundle("bundle-undefined", "Bundle Undefined");
        Application application = resourceHelpers.createApplication(bundle.getId(), "app-a", "App A");
        EventType eventType = createEventType(application.getId(), "event-a",
            Set.of(Severity.CRITICAL, Severity.UNDEFINED), false);

        Map<Severity, Boolean> severities = new HashMap<>();
        severities.put(Severity.CRITICAL, true);
        severities.put(Severity.UNDEFINED, true);
        subscriptionRepository.updateSubscription(DEFAULT_ORG_ID, DEFAULT_USER, eventType.getId(), SubscriptionType.INSTANT, true, severities);

        List<BundleSubscriptionDTO> tree = getSubscriptions("bundle-undefined", "app-a", "event-a");

        var eventTypeDTO = tree.get(0).getApplications().get(0).getEventTypes().get(0);
        assertEquals(List.of(SeverityDTO.CRITICAL), eventTypeDTO.getAvailableSeverities());
        assertChannelSeverities(eventTypeDTO.getSubscriptions(), SubscriptionTypeDTO.INSTANT, List.of(SeverityDTO.CRITICAL));
    }

    private void assertChannelSeverities(List<SubscriptionChannelDTO> channels, SubscriptionTypeDTO type, List<SeverityDTO> expected) {
        SubscriptionChannelDTO channel = channels.stream().filter(c -> c.getSubscriptionType() == type).findFirst().orElseThrow();
        assertEquals(expected, channel.getSubscribedSeverities());
    }

    @Test
    void testGetSubscriptionsScopedByBundleAndApplication() {
        Bundle bundleA = resourceHelpers.createBundle("bundle-a", "Bundle A");
        Application applicationA = resourceHelpers.createApplication(bundleA.getId(), "app-a", "App A");
        createEventType(applicationA.getId(), "event-a", Set.of(Severity.CRITICAL), false);

        Bundle bundleB = resourceHelpers.createBundle("bundle-b", "Bundle B");
        Application applicationB = resourceHelpers.createApplication(bundleB.getId(), "app-b", "App B");
        createEventType(applicationB.getId(), "event-b", Set.of(Severity.CRITICAL), false);

        List<BundleSubscriptionDTO> scopedToBundle = getSubscriptions("bundle-a", null, null);
        assertEquals(1, scopedToBundle.size());
        assertEquals("bundle-a", scopedToBundle.get(0).getBundle());

        List<BundleSubscriptionDTO> scopedToApplication = getSubscriptions("bundle-a", "app-a", null);
        assertEquals(1, scopedToApplication.size());
        assertEquals(1, scopedToApplication.get(0).getApplications().size());
        assertEquals("event-a", scopedToApplication.get(0).getApplications().get(0).getEventTypes().get(0).getEventType());

        List<BundleSubscriptionDTO> scopedToEventType = getSubscriptions("bundle-a", "app-a", "event-a");
        assertEquals(1, scopedToEventType.get(0).getApplications().get(0).getEventTypes().size());
    }

    @Test
    void testGetSubscriptionsInvalidQueryParamCombinations() {
        assertGetSubscriptionsStatus(null, "app-a", null, HttpStatus.SC_BAD_REQUEST);
        assertGetSubscriptionsStatus("bundle-a", null, "event-a", HttpStatus.SC_BAD_REQUEST);
        assertGetSubscriptionsStatus(null, null, "event-a", HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void testGetSubscriptionsUnknownBundle() {
        assertGetSubscriptionsStatus("does-not-exist", null, null, HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void testGetSubscriptionsUnknownApplication() {
        resourceHelpers.createBundle("bundle-a", "Bundle A");
        assertGetSubscriptionsStatus("bundle-a", "does-not-exist", null, HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void testGetSubscriptionsUnknownEventType() {
        Bundle bundle = resourceHelpers.createBundle("bundle-a", "Bundle A");
        resourceHelpers.createApplication(bundle.getId(), "app-a", "App A");
        assertGetSubscriptionsStatus("bundle-a", "app-a", "does-not-exist", HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void testGetSubscriptionsApplicationWithNoEventTypesReturnsEmptyTree() {
        Bundle bundle = resourceHelpers.createBundle("bundle-a", "Bundle A");
        resourceHelpers.createApplication(bundle.getId(), "app-a", "App A");

        // The application exists but has no event types, so it (and its now-childless bundle) is
        // pruned from the response tree entirely, rather than coming back as an empty shell or a 404.
        List<BundleSubscriptionDTO> tree = getSubscriptions("bundle-a", "app-a", null);
        assertEquals(List.of(), tree);
    }

    @Test
    void testPutSubscriptionsPartialUpdate() {
        Bundle bundle = resourceHelpers.createBundle("bundle-a", "Bundle A");
        Application application = resourceHelpers.createApplication(bundle.getId(), "app-a", "App A");
        createEventType(application.getId(), "event-a", Set.of(Severity.CRITICAL, Severity.IMPORTANT), false);

        BundleSubscriptionUpdateDTO update = new BundleSubscriptionUpdateDTO();
        update.setBundle("bundle-a");
        ApplicationSubscriptionUpdateDTO applicationUpdate = new ApplicationSubscriptionUpdateDTO();
        applicationUpdate.setApplication("app-a");
        EventTypeSubscriptionUpdateDTO eventTypeUpdate = new EventTypeSubscriptionUpdateDTO();
        eventTypeUpdate.setEventType("event-a");
        eventTypeUpdate.setSubscriptions(List.of(new SubscriptionChannelDTO(SubscriptionTypeDTO.INSTANT, List.of(SeverityDTO.CRITICAL))));
        applicationUpdate.setEventTypes(List.of(eventTypeUpdate));
        update.setApplications(List.of(applicationUpdate));

        given()
            .header(identityHeader)
            .contentType(JSON)
            .body(List.of(update))
            .when().put(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_NO_CONTENT);

        List<BundleSubscriptionDTO> tree = getSubscriptions("bundle-a", "app-a", "event-a");
        var eventTypeDTO = tree.get(0).getApplications().get(0).getEventTypes().get(0);
        // The updated channel reflects the PUT...
        assertChannelSeverities(eventTypeDTO.getSubscriptions(), SubscriptionTypeDTO.INSTANT, List.of(SeverityDTO.CRITICAL));
        // ...while channels omitted from the request are untouched (still at their default).
        assertChannelSeverities(eventTypeDTO.getSubscriptions(), SubscriptionTypeDTO.DAILY, List.of());
        assertChannelSeverities(eventTypeDTO.getSubscriptions(), SubscriptionTypeDTO.DRAWER, List.of(SeverityDTO.CRITICAL, SeverityDTO.IMPORTANT));
    }

    @Test
    void testPutSubscriptionsIgnoresDrawerWhenDisabledForOrg() {
        Bundle bundle = resourceHelpers.createBundle("bundle-a", "Bundle A");
        Application application = resourceHelpers.createApplication(bundle.getId(), "app-a", "App A");
        createEventType(application.getId(), "event-a", Set.of(Severity.CRITICAL, Severity.IMPORTANT), false);

        // drawer.enabled is off for this org (the default from beforeEach), so a write to the DRAWER
        // channel must be silently ignored rather than persisted: otherwise a later GET (once the org's
        // flag flips on) would report the caller's own unsubscribe as never having "taken".
        BundleSubscriptionUpdateDTO update = buildSingleLeafUpdate("bundle-a", "app-a", "event-a", SubscriptionTypeDTO.DRAWER, List.of());

        given()
            .header(identityHeader)
            .contentType(JSON)
            .body(List.of(update))
            .when().put(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_NO_CONTENT);

        when(backendConfig.isDrawerEnabled(DEFAULT_ORG_ID)).thenReturn(true);
        List<BundleSubscriptionDTO> tree = getSubscriptions("bundle-a", "app-a", "event-a");
        var eventTypeDTO = tree.get(0).getApplications().get(0).getEventTypes().get(0);
        // Still at the hardcoded subscribed-by-default state: the unsubscribe PUT above never wrote a row.
        assertChannelSeverities(eventTypeDTO.getSubscriptions(), SubscriptionTypeDTO.DRAWER, List.of(SeverityDTO.CRITICAL, SeverityDTO.IMPORTANT));
    }

    @Test
    void testPutSubscriptionsInvalidSeverity() {
        Bundle bundle = resourceHelpers.createBundle("bundle-a", "Bundle A");
        Application application = resourceHelpers.createApplication(bundle.getId(), "app-a", "App A");
        createEventType(application.getId(), "event-a", Set.of(Severity.CRITICAL), false);

        BundleSubscriptionUpdateDTO update = buildSingleLeafUpdate("bundle-a", "app-a", "event-a", SubscriptionTypeDTO.INSTANT, List.of(SeverityDTO.MODERATE));

        given()
            .header(identityHeader)
            .contentType(JSON)
            .body(List.of(update))
            .when().put(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void testPutSubscriptionsUnknownEventType() {
        Bundle bundle = resourceHelpers.createBundle("bundle-a", "Bundle A");
        resourceHelpers.createApplication(bundle.getId(), "app-a", "App A");

        BundleSubscriptionUpdateDTO update = buildSingleLeafUpdate("bundle-a", "app-a", "does-not-exist", SubscriptionTypeDTO.INSTANT, List.of());

        given()
            .header(identityHeader)
            .contentType(JSON)
            .body(List.of(update))
            .when().put(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void testPutSubscriptionsRejectsNullTopLevelItem() {
        given()
            .header(identityHeader)
            .contentType(JSON)
            .body("[null]")
            .when().put(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void testPutSubscriptionsRejectsNullNestedItem() {
        given()
            .header(identityHeader)
            .contentType(JSON)
            .body("[{\"bundle\": \"bundle-a\", \"applications\": [null]}]")
            .when().put(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void testPutSubscriptionsRejectsEmptyBody() {
        given()
            .header(identityHeader)
            .contentType(JSON)
            .body("[]")
            .when().put(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void testPutSubscriptionsRejectsInvalidBundleNamePattern() {
        // Bundle/application/event type names are constrained to "[a-z][a-z_0-9-]*"; an uppercase
        // name should fail bean validation before any repository lookup happens.
        BundleSubscriptionUpdateDTO update = buildSingleLeafUpdate("Bundle-A", "app-a", "event-a", SubscriptionTypeDTO.INSTANT, List.of());

        given()
            .header(identityHeader)
            .contentType(JSON)
            .body(List.of(update))
            .when().put(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @Test
    void testPutSubscriptionsRejectsEmptyNestedList() {
        given()
            .header(identityHeader)
            .contentType(JSON)
            .body("[{\"bundle\": \"bundle-a\", \"applications\": []}]")
            .when().put(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);
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

        BundleSubscriptionUpdateDTO update = buildSingleLeafUpdate("bundle-a", "app-a", "event-a", SubscriptionTypeDTO.INSTANT, List.of());
        given()
            .header(serviceAccountHeader)
            .contentType(JSON)
            .body(List.of(update))
            .when().put(SUBSCRIPTIONS_PATH)
            .then()
            .statusCode(HttpStatus.SC_FORBIDDEN);
    }

    private BundleSubscriptionUpdateDTO buildSingleLeafUpdate(String bundle, String application, String eventType, SubscriptionTypeDTO channel, List<SeverityDTO> severities) {
        BundleSubscriptionUpdateDTO bundleUpdate = new BundleSubscriptionUpdateDTO();
        bundleUpdate.setBundle(bundle);
        ApplicationSubscriptionUpdateDTO applicationUpdate = new ApplicationSubscriptionUpdateDTO();
        applicationUpdate.setApplication(application);
        EventTypeSubscriptionUpdateDTO eventTypeUpdate = new EventTypeSubscriptionUpdateDTO();
        eventTypeUpdate.setEventType(eventType);
        eventTypeUpdate.setSubscriptions(List.of(new SubscriptionChannelDTO(channel, severities)));
        applicationUpdate.setEventTypes(List.of(eventTypeUpdate));
        bundleUpdate.setApplications(List.of(applicationUpdate));
        return bundleUpdate;
    }
}
