package com.redhat.cloud.notifications.routers.handlers.userconfig;

import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.auth.kessel.KesselCheckClient;
import com.redhat.cloud.notifications.auth.kessel.KesselTestHelper;
import com.redhat.cloud.notifications.auth.rbac.workspace.WorkspaceUtils;
import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.ApplicationRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
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
import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.NO_ACCESS;
import static com.redhat.cloud.notifications.MockServerConfig.RbacAccess.READ_ACCESS;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_USER;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.project_kessel.api.inventory.v1beta2.Allowed.ALLOWED_FALSE;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class UserConfigResourceV2Test extends DbIsolatedTest {

    private static final String SUBSCRIPTIONS_PATH = "/user-config/subscriptions";

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    ApplicationRepository applicationRepository;

    @InjectMock
    BackendConfig backendConfig;

    @InjectMock
    KesselCheckClient kesselCheckClient;

    @InjectMock
    WorkspaceUtils workspaceUtils;

    @Inject
    KesselTestHelper kesselTestHelper;

    Header identityHeader;

    @BeforeEach
    void beforeEach() {
        RestAssured.basePath = TestConstants.API_NOTIFICATIONS_V_2_0;
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, DEFAULT_USER);
        identityHeader = TestHelpers.createRHIdentityHeader(identityHeaderValue);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, FULL_ACCESS);
        // Since BackendConfig is mocked, isRBACEnabled() defaults to false, which makes
        // ConsoleIdentityProvider build an all-privileges principal regardless of the RBAC mock
        // responses below. Enable it so the NO_ACCESS/READ_ACCESS tests actually exercise RBAC.
        when(backendConfig.isRBACEnabled()).thenReturn(true);
        when(workspaceUtils.getDefaultWorkspaceId(DEFAULT_ORG_ID)).thenReturn(KesselTestHelper.RBAC_DEFAULT_WORKSPACE_ID);
        when(kesselCheckClient.check(any())).thenReturn(kesselTestHelper.buildCheckResponse(ALLOWED_FALSE));
        when(kesselCheckClient.checkForUpdate(any())).thenReturn(kesselTestHelper.buildCheckForUpdateResponse(ALLOWED_FALSE));
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

    @Test
    void testInsufficientPrivileges() {
        Header noAccessIdentityHeader = initRbacMock(DEFAULT_USER + "-no-access", NO_ACCESS);
        Header readAccessIdentityHeader = initRbacMock(DEFAULT_USER + "-read-access", READ_ACCESS);

        given().header(noAccessIdentityHeader).when().get(SUBSCRIPTIONS_PATH).then().statusCode(HttpStatus.SC_FORBIDDEN);
        given().header(noAccessIdentityHeader).when().put(SUBSCRIPTIONS_PATH).then().statusCode(HttpStatus.SC_FORBIDDEN);

        given().header(readAccessIdentityHeader).when().get(SUBSCRIPTIONS_PATH).then().statusCode(HttpStatus.SC_OK);
        given().header(readAccessIdentityHeader).when().put(SUBSCRIPTIONS_PATH).then().statusCode(HttpStatus.SC_FORBIDDEN);
    }

    private Header initRbacMock(String username, MockServerConfig.RbacAccess access) {
        String identityHeaderValue = TestHelpers.encodeRHIdentityInfo(DEFAULT_ACCOUNT_ID, DEFAULT_ORG_ID, username);
        MockServerConfig.addMockRbacAccess(identityHeaderValue, access);
        return TestHelpers.createRHIdentityHeader(identityHeaderValue);
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
