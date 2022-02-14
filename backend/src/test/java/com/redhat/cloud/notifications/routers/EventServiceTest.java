package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.EndpointResources;
import com.redhat.cloud.notifications.db.ModelInstancesHolder;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.routers.models.EventLogEntry;
import com.redhat.cloud.notifications.routers.models.EventLogEntryAction;
import com.redhat.cloud.notifications.routers.models.Page;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.Header;
import io.restassured.specification.RequestSpecification;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.vertx.MutinyHelper;
import io.vertx.core.Vertx;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.MockServerClientConfig.RbacAccess;
import static com.redhat.cloud.notifications.MockServerClientConfig.RbacAccess.FULL_ACCESS;
import static com.redhat.cloud.notifications.MockServerClientConfig.RbacAccess.NOTIFICATIONS_ACCESS_ONLY;
import static com.redhat.cloud.notifications.MockServerClientConfig.RbacAccess.NOTIFICATIONS_READ_ACCESS_ONLY;
import static com.redhat.cloud.notifications.MockServerClientConfig.RbacAccess.NO_ACCESS;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestThreadHelper.runOnWorkerThread;
import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static com.redhat.cloud.notifications.routers.EventService.PATH;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.time.ZoneOffset.UTC;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EventServiceTest extends DbIsolatedTest {

    private static final String OTHER_ACCOUNT_ID = "other-account-id";
    private static final LocalDateTime NOW = LocalDateTime.now(UTC);
    private static final String PAYLOAD = "payload";

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Inject
    Vertx vertx;

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EndpointResources endpointResources;

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    // A new instance is automatically created by JUnit before each test is executed.
    private final ModelInstancesHolder model = new ModelInstancesHolder();

    @Test
    void shouldNotBeAllowedTogetEventLogsWhenUserHasNotificationsAccessRightsOnly() {
        Header defaultIdentityHeader = mockRbac(DEFAULT_ACCOUNT_ID, "user2", NOTIFICATIONS_ACCESS_ONLY);
        given()
                .header(defaultIdentityHeader)
                .when().get(PATH)
                .then()
                .statusCode(403);
    }

    @Test
    void testAllQueryParams() {
        /*
         * This method is very long, but splitting it into several smaller ones would mean we have to recreate lots of
         * database records for each test. To avoid doing that, the data is only persisted once and many tests are run
         * from the same data.
         */

        Header defaultIdentityHeader = mockRbac(DEFAULT_ACCOUNT_ID, "user", FULL_ACCESS);
        Header otherIdentityHeader = mockRbac(OTHER_ACCOUNT_ID, "other-username", FULL_ACCESS);

        sessionFactory.withSession(session -> resourceHelpers.createBundle("bundle-1", "Bundle 1")
                .invoke(model.bundles::add)
                .chain(() -> resourceHelpers.createBundle("bundle-2", "Bundle 2"))
                .invoke(model.bundles::add)
                .chain(() -> resourceHelpers.createApplication(model.bundles.get(0).getId(), "app-1", "Application 1"))
                .invoke(model.applications::add)
                .chain(() -> resourceHelpers.createApplication(model.bundles.get(1).getId(), "app-2", "Application 2"))
                .invoke(model.applications::add)
                .chain(() -> resourceHelpers.createEventType(model.applications.get(0).getId(), "event-type-1", "Event type 1", "Event type 1"))
                .invoke(model.eventTypes::add)
                .chain(() -> resourceHelpers.createEventType(model.applications.get(1).getId(), "event-type-2", "Event type 2", "Event type 2"))
                .invoke(model.eventTypes::add)
                .chain(() -> createEvent(DEFAULT_ACCOUNT_ID, model.eventTypes.get(0), NOW.minusDays(5L)))
                .invoke(model.events::add)
                .chain(() -> createEvent(DEFAULT_ACCOUNT_ID, model.eventTypes.get(1), NOW))
                .invoke(model.events::add)
                .chain(() -> createEvent(DEFAULT_ACCOUNT_ID, model.eventTypes.get(1), NOW.minusDays(2L)))
                .invoke(model.events::add)
                .chain(() -> createEvent(OTHER_ACCOUNT_ID, model.eventTypes.get(1), NOW.minusDays(10L)))
                .invoke(model.events::add)
                .chain(() -> resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, WEBHOOK))
                .invoke(model.endpoints::add)
                .chain(() -> resourceHelpers.createEndpoint(DEFAULT_ACCOUNT_ID, EMAIL_SUBSCRIPTION))
                .invoke(model.endpoints::add)
                .chain(() -> resourceHelpers.createNotificationHistory(model.events.get(0), model.endpoints.get(0), TRUE))
                .invoke(model.notificationHistories::add)
                .chain(() -> resourceHelpers.createNotificationHistory(model.events.get(0), model.endpoints.get(1), FALSE))
                .invoke(model.notificationHistories::add)
                .chain(() -> resourceHelpers.createNotificationHistory(model.events.get(1), model.endpoints.get(0), TRUE))
                .invoke(model.notificationHistories::add)
                .chain(() -> resourceHelpers.createNotificationHistory(model.events.get(2), model.endpoints.get(1), TRUE))
                .invoke(model.notificationHistories::add)
                .chain(() -> endpointResources.deleteEndpoint(DEFAULT_ACCOUNT_ID, model.endpoints.get(0).getId()))
                .chain(() -> endpointResources.deleteEndpoint(DEFAULT_ACCOUNT_ID, model.endpoints.get(1).getId()))
                .chain(runOnWorkerThread(() -> {
                    /*
                     * Test #1
                     * Account: DEFAULT_ACCOUNT_ID
                     * Request: No filter
                     * Expected response: All event log entries from DEFAULT_ACCOUNT_ID should be returned
                     */
                    return getEventLogPage(defaultIdentityHeader, null, null, null, null, null, null, null, null, null, null, false);
                }))
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .invoke(page -> {
                    assertEquals(3, page.getMeta().getCount());
                    assertEquals(3, page.getData().size());
                    assertSameEvent(page.getData().get(0), model.events.get(1), model.notificationHistories.get(2));
                    assertSameEvent(page.getData().get(1), model.events.get(2), model.notificationHistories.get(3));
                    assertSameEvent(page.getData().get(2), model.events.get(0), model.notificationHistories.get(0), model.notificationHistories.get(1));
                    assertNull(page.getData().get(0).getPayload());
                    assertLinks(page.getLinks(), "first", "last");
                })
                .chain(runOnWorkerThread(() -> {
                    /*
                     * Test #2
                     * Account: OTHER_ACCOUNT_ID
                     * Request: No filter
                     * Expected response: All event log entries from OTHER_ACCOUNT_ID should be returned
                     */
                    return getEventLogPage(otherIdentityHeader, null, null, null, null, null, null, null, null, null, null, false);
                }))
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .invoke(page -> {
                    assertEquals(1, page.getMeta().getCount());
                    assertEquals(1, page.getData().size());
                    assertSameEvent(page.getData().get(0), model.events.get(3));
                    assertNull(page.getData().get(0).getPayload());
                    assertLinks(page.getLinks(), "first", "last");
                })
                .chain(runOnWorkerThread(() -> {
                    /*
                     * Test #3
                     * Account: DEFAULT_ACCOUNT_ID
                     * Request: Unknown bundle
                     */
                    return getEventLogPage(defaultIdentityHeader, Set.of(randomUUID()), null, null, null, null, null, null, null, null, null, false);
                }))
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .invoke(page -> {
                    assertEquals(0, page.getMeta().getCount());
                    assertTrue(page.getData().isEmpty());
                    assertLinks(page.getLinks(), "first", "last");
                })
                .chain(runOnWorkerThread(() -> {
                    /*
                     * Test #4
                     * Account: DEFAULT_ACCOUNT_ID
                     * Request: One existing bundle
                     */
                    return getEventLogPage(defaultIdentityHeader, Set.of(model.bundles.get(0).getId()), null, null, null, null, null, null, null, null, null, false);
                }))
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .invoke(page -> {
                    assertEquals(1, page.getMeta().getCount());
                    assertEquals(1, page.getData().size());
                    assertSameEvent(page.getData().get(0), model.events.get(0), model.notificationHistories.get(0), model.notificationHistories.get(1));
                    assertNull(page.getData().get(0).getPayload());
                    assertLinks(page.getLinks(), "first", "last");
                })
                .chain(runOnWorkerThread(() -> {
                    /*
                     * Test #5
                     * Account: DEFAULT_ACCOUNT_ID
                     * Request: Multiple existing bundles, sort by ascending bundle names
                     */
                    return getEventLogPage(defaultIdentityHeader, Set.of(model.bundles.get(0).getId(), model.bundles.get(1).getId()), null, null, null, null, null, null, null, null, "bundle:asc", false);
                }))
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .invoke(page -> {
                    assertEquals(3, page.getMeta().getCount());
                    assertEquals(3, page.getData().size());
                    assertSameEvent(page.getData().get(0), model.events.get(0), model.notificationHistories.get(0), model.notificationHistories.get(1));
                    assertSameEvent(page.getData().get(1), model.events.get(1), model.notificationHistories.get(2));
                    assertSameEvent(page.getData().get(2), model.events.get(2), model.notificationHistories.get(3));
                    assertNull(page.getData().get(0).getPayload());
                    assertLinks(page.getLinks(), "first", "last");
                })
                .chain(runOnWorkerThread(() -> {
                    /*
                     * Test #6
                     * Account: DEFAULT_ACCOUNT_ID
                     * Request: Unknown application
                     */
                    return getEventLogPage(defaultIdentityHeader, null, Set.of(randomUUID()), null, null, null, null, null, null, null, null, false);
                }))
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .invoke(page -> {
                    assertEquals(0, page.getMeta().getCount());
                    assertTrue(page.getData().isEmpty());
                    assertLinks(page.getLinks(), "first", "last");
                })
                .chain(runOnWorkerThread(() -> {
                    /*
                     * Test #7
                     * Account: DEFAULT_ACCOUNT_ID
                     * Request: One existing application
                     */
                    return getEventLogPage(defaultIdentityHeader, null, Set.of(model.applications.get(1).getId()), null, null, null, null, null, null, null, null, false);
                }))
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .invoke(page -> {
                    assertEquals(2, page.getMeta().getCount());
                    assertEquals(2, page.getData().size());
                    assertSameEvent(page.getData().get(0), model.events.get(1), model.notificationHistories.get(2));
                    assertSameEvent(page.getData().get(1), model.events.get(2), model.notificationHistories.get(3));
                    assertNull(page.getData().get(0).getPayload());
                    assertLinks(page.getLinks(), "first", "last");
                })
                .chain(runOnWorkerThread(() -> {
                    /*
                     * Test #8
                     * Account: DEFAULT_ACCOUNT_ID
                     * Request: Multiple existing applications, sort by ascending application names
                     */
                    return getEventLogPage(defaultIdentityHeader, null, Set.of(model.applications.get(0).getId(), model.applications.get(1).getId()), null, null, null, null, null, null, null, "application:asc", false);
                }))
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .invoke(page -> {
                    assertEquals(3, page.getMeta().getCount());
                    assertEquals(3, page.getData().size());
                    assertSameEvent(page.getData().get(0), model.events.get(0), model.notificationHistories.get(0), model.notificationHistories.get(1));
                    assertSameEvent(page.getData().get(1), model.events.get(1), model.notificationHistories.get(2));
                    assertSameEvent(page.getData().get(2), model.events.get(2), model.notificationHistories.get(3));
                    assertNull(page.getData().get(0).getPayload());
                    assertLinks(page.getLinks(), "first", "last");
                })
                .chain(runOnWorkerThread(() -> {
                    /*
                     * Test #9
                     * Account: DEFAULT_ACCOUNT_ID
                     * Request: Unknown event type
                     */
                    return getEventLogPage(defaultIdentityHeader, null, null, "unknown", null, null, null, null, null, null, null, false);
                }))
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .invoke(page -> {
                    assertEquals(0, page.getMeta().getCount());
                    assertTrue(page.getData().isEmpty());
                    assertLinks(page.getLinks(), "first", "last");
                })
                .chain(runOnWorkerThread(() -> {
                    /*
                     * Test #10
                     * Account: DEFAULT_ACCOUNT_ID
                     * Request: Existing event type
                     */
                    return getEventLogPage(defaultIdentityHeader, null, null, model.eventTypes.get(0).getDisplayName().substring(2).toUpperCase(), null, null, null, null, null, null, null, false);
                }))
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .invoke(page -> {
                    assertEquals(1, page.getMeta().getCount());
                    assertEquals(1, page.getData().size());
                    assertSameEvent(page.getData().get(0), model.events.get(0), model.notificationHistories.get(0), model.notificationHistories.get(1));
                    assertNull(page.getData().get(0).getPayload());
                    assertLinks(page.getLinks(), "first", "last");
                })
                .chain(runOnWorkerThread(() -> {
                    /*
                     * Test #11
                     * Account: DEFAULT_ACCOUNT_ID
                     * Request: Start date three days in the past
                     */
                    return getEventLogPage(defaultIdentityHeader, null, null, null, NOW.minusDays(3L), null, null, null, null, null, null, false);
                }))
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .invoke(page -> {
                    assertEquals(2, page.getMeta().getCount());
                    assertEquals(2, page.getData().size());
                    assertSameEvent(page.getData().get(0), model.events.get(1), model.notificationHistories.get(2));
                    assertSameEvent(page.getData().get(1), model.events.get(2), model.notificationHistories.get(3));
                    assertNull(page.getData().get(0).getPayload());
                    assertLinks(page.getLinks(), "first", "last");
                })
                .chain(runOnWorkerThread(() -> {
                    /*
                     * Test #12
                     * Account: DEFAULT_ACCOUNT_ID
                     * Request: End date three days in the past
                     */
                    return getEventLogPage(defaultIdentityHeader, null, null, null, null, NOW.minusDays(3L), null, null, null, null, null, false);
                }))
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .invoke(page -> {
                    assertEquals(1, page.getMeta().getCount());
                    assertEquals(1, page.getData().size());
                    assertSameEvent(page.getData().get(0), model.events.get(0), model.notificationHistories.get(0), model.notificationHistories.get(1));
                    assertNull(page.getData().get(0).getPayload());
                    assertLinks(page.getLinks(), "first", "last");
                })
                .chain(runOnWorkerThread(() -> {
                    /*
                     * Test #13
                     * Account: DEFAULT_ACCOUNT_ID
                     * Request: Both start and end date are set
                     */
                    return getEventLogPage(defaultIdentityHeader, null, null, null, NOW.minusDays(3L), NOW.minusDays(1L), null, null, null, null, null, false);
                }))
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .invoke(page -> {
                    assertEquals(1, page.getMeta().getCount());
                    assertEquals(1, page.getData().size());
                    assertSameEvent(page.getData().get(0), model.events.get(2), model.notificationHistories.get(3));
                    assertNull(page.getData().get(0).getPayload());
                    assertLinks(page.getLinks(), "first", "last");
                })
                .chain(runOnWorkerThread(() -> {
                    /*
                     * Test #14
                     * Account: DEFAULT_ACCOUNT_ID
                     * Request: Let's try all request params at once!
                     */
                    return getEventLogPage(defaultIdentityHeader, Set.of(model.bundles.get(1).getId()), Set.of(model.applications.get(1).getId()), model.eventTypes.get(1).getDisplayName(), NOW.minusDays(3L), NOW.minusDays(1L), Set.of(EMAIL_SUBSCRIPTION), Set.of(TRUE), 10, 0, "created:desc", true);
                }))
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .invoke(page -> {
                    assertEquals(1, page.getMeta().getCount());
                    assertEquals(1, page.getData().size());
                    assertSameEvent(page.getData().get(0), model.events.get(2), model.notificationHistories.get(3));
                    assertEquals(PAYLOAD, page.getData().get(0).getPayload());
                    assertLinks(page.getLinks(), "first", "last");
                })
                .chain(runOnWorkerThread(() -> {
                    /*
                     * Test #15
                     * Account: DEFAULT_ACCOUNT_ID
                     * Request: No filter, limit without offset
                     */
                    return getEventLogPage(defaultIdentityHeader, null, null, null, null, null, null, null, 2, null, null, false);
                }))
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .invoke(page -> {
                    assertEquals(3, page.getMeta().getCount());
                    assertEquals(2, page.getData().size());
                    assertSameEvent(page.getData().get(0), model.events.get(1), model.notificationHistories.get(2));
                    assertSameEvent(page.getData().get(1), model.events.get(2), model.notificationHistories.get(3));
                    assertNull(page.getData().get(0).getPayload());
                    assertLinks(page.getLinks(), "first", "last", "next");
                })
                .chain(runOnWorkerThread(() -> {
                    /*
                     * Test #16
                     * Account: DEFAULT_ACCOUNT_ID
                     * Request: No filter, limit with offset
                     */
                    return getEventLogPage(defaultIdentityHeader, null, null, null, null, null, null, null, 1, 2, null, false);
                }))
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .invoke(page -> {
                    assertEquals(3, page.getMeta().getCount());
                    assertEquals(1, page.getData().size());
                    assertSameEvent(page.getData().get(0), model.events.get(0), model.notificationHistories.get(0), model.notificationHistories.get(1));
                    assertNull(page.getData().get(0).getPayload());
                    assertLinks(page.getLinks(), "first", "last", "prev");
                })
                .chain(runOnWorkerThread(() -> {
                    /*
                     * Test #17
                     * Account: DEFAULT_ACCOUNT_ID
                     * Request: No filter, sort by ascending event names
                     */
                    return getEventLogPage(defaultIdentityHeader, null, null, null, null, null, null, null, null, null, "event:asc", false);
                }))
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .invoke(page -> {
                    assertEquals(3, page.getMeta().getCount());
                    assertEquals(3, page.getData().size());
                    assertSameEvent(page.getData().get(0), model.events.get(0), model.notificationHistories.get(0), model.notificationHistories.get(1));
                    assertSameEvent(page.getData().get(1), model.events.get(1), model.notificationHistories.get(2));
                    assertSameEvent(page.getData().get(2), model.events.get(2), model.notificationHistories.get(3));
                    assertNull(page.getData().get(0).getPayload());
                    assertLinks(page.getLinks(), "first", "last");
                })
                .chain(runOnWorkerThread(() -> {
                    /*
                     * Test #18
                     * Account: DEFAULT_ACCOUNT_ID
                     * Request: WEBHOOK endpoints
                     */
                    return getEventLogPage(defaultIdentityHeader, null, null, null, null, null, Set.of(WEBHOOK), null, null, null, null, false);
                }))
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .invoke(page -> {
                    assertEquals(2, page.getMeta().getCount());
                    assertEquals(2, page.getData().size());
                    assertSameEvent(page.getData().get(0), model.events.get(1), model.notificationHistories.get(2));
                    assertSameEvent(page.getData().get(1), model.events.get(0), model.notificationHistories.get(0), model.notificationHistories.get(1));
                    assertNull(page.getData().get(0).getPayload());
                    assertLinks(page.getLinks(), "first", "last");
                })
                .chain(runOnWorkerThread(() -> {
                    /*
                     * Test #19
                     * Account: DEFAULT_ACCOUNT_ID
                     * Request: Invocation succeeded
                     */
                    return getEventLogPage(defaultIdentityHeader, null, null, null, null, null, null, Set.of(TRUE), null, null, null, false);
                }))
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .invoke(page -> {
                    assertEquals(3, page.getMeta().getCount());
                    assertEquals(3, page.getData().size());
                    assertSameEvent(page.getData().get(0), model.events.get(1), model.notificationHistories.get(2));
                    assertSameEvent(page.getData().get(1), model.events.get(2), model.notificationHistories.get(3));
                    assertSameEvent(page.getData().get(2), model.events.get(0), model.notificationHistories.get(0), model.notificationHistories.get(1));
                    assertNull(page.getData().get(0).getPayload());
                    assertLinks(page.getLinks(), "first", "last");
                })
                .chain(runOnWorkerThread(() -> {
                    /*
                     * Test #20
                     * Account: DEFAULT_ACCOUNT_ID
                     * Request: EMAIL_SUBSCRIPTION endpoints and invocation failed
                     */
                    return getEventLogPage(defaultIdentityHeader, null, null, null, null, null, Set.of(EMAIL_SUBSCRIPTION), Set.of(FALSE), null, null, null, false);
                }))
                .emitOn(MutinyHelper.executor(vertx.getOrCreateContext()))
                .invoke(page -> {
                    assertEquals(1, page.getMeta().getCount());
                    assertEquals(1, page.getData().size());
                    assertSameEvent(page.getData().get(0), model.events.get(0), model.notificationHistories.get(0), model.notificationHistories.get(1));
                    assertNull(page.getData().get(0).getPayload());
                    assertLinks(page.getLinks(), "first", "last");
                })
                .chain(runOnWorkerThread(() -> {
                    // TODO Temp test, remove ASAP
                    System.setProperty("notifications.event-service.legacy-mode", "false");
                    return getEventLogPage(defaultIdentityHeader, null, null, null, null, null, null, null, null, null, null, false);
                }))
        ).await().indefinitely();
    }

    @Test
    void testInsufficientPrivileges() {
        Header noAccessIdentityHeader = mockRbac("tenant", "noAccess", NO_ACCESS);
        given()
                .header(noAccessIdentityHeader)
                .when().get(PATH)
                .then()
                .statusCode(403);
    }

    @Test
    void testInvalidSortBy() {
        Header identityHeader = mockRbac(DEFAULT_ACCOUNT_ID, "user", FULL_ACCESS);
        given()
                .header(identityHeader)
                .param("sortBy", "I am not valid!")
                .when().get(PATH)
                .then()
                .statusCode(400)
                .contentType(JSON);
    }

    @Test
    void testInvalidLimit() {
        Header identityHeader = mockRbac(DEFAULT_ACCOUNT_ID, "user", FULL_ACCESS);
        given()
                .header(identityHeader)
                .param("limit", 0)
                .when().get(PATH)
                .then()
                .statusCode(400)
                .contentType(JSON);
        given()
                .header(identityHeader)
                .param("limit", 999999)
                .when().get(PATH)
                .then()
                .statusCode(400)
                .contentType(JSON);
    }

    @Test
    void shouldBeAllowedToGetEventLogs() {
        Header readAccessIdentityHeader = mockRbac("tenant", "user-read-access", NOTIFICATIONS_READ_ACCESS_ONLY);
        given()
                .header(readAccessIdentityHeader)
                .when().get(PATH)
                .then()
                .statusCode(200)
                .contentType(JSON);
    }

    private Uni<Event> createEvent(String accountId, EventType eventType, LocalDateTime created) {
        Event event = new Event();
        event.setAccountId(accountId);
        event.setEventType(eventType);
        event.setCreated(created);
        event.setPayload(PAYLOAD);
        return sessionFactory.withStatelessSession(statelessSession -> statelessSession.insert(event)
                .replaceWith(event)
        );
    }

    private Header mockRbac(String tenant, String username, RbacAccess access) {
        String identityHeaderValue = TestHelpers.encodeIdentityInfo(tenant, username);
        mockServerConfig.addMockRbacAccess(identityHeaderValue, access);
        return TestHelpers.createIdentityHeader(identityHeaderValue);
    }

    private static Page<EventLogEntry> getEventLogPage(Header identityHeader, Set<UUID> bundleIds, Set<UUID> appIds, String eventTypeDisplayName,
                                                       LocalDateTime startDate, LocalDateTime endDate, Set<EndpointType> endpointTypes,
                                                       Set<Boolean> invocationResults, Integer limit, Integer offset, String sortBy, boolean includePayload) {
        RequestSpecification request = given()
                .header(identityHeader);
        if (bundleIds != null) {
            request.param("bundleIds", bundleIds);
        }
        if (appIds != null) {
            request.param("appIds", appIds);
        }
        if (eventTypeDisplayName != null) {
            request.param("eventTypeDisplayName", eventTypeDisplayName);
        }
        if (startDate != null) {
            request.param("startDate", startDate.toLocalDate().toString());
        }
        if (endDate != null) {
            request.param("endDate", endDate.toLocalDate().toString());
        }
        if (endpointTypes != null) {
            request.param("endpointTypes", endpointTypes);
        }
        if (invocationResults != null) {
            request.param("invocationResults", invocationResults);
        }
        if (limit != null) {
            request.param("limit", limit);
        }
        if (offset != null) {
            request.param("offset", offset);
        }
        if (sortBy != null) {
            request.param("sortBy", sortBy);
        }
        if (includePayload) {
            request.param("includePayload", true);
        }
        return request
                .when().get(PATH)
                .then()
                .statusCode(200)
                .contentType(JSON)
                .extract().body().as(new TypeRef<>() {
                });
    }

    private static void assertSameEvent(EventLogEntry eventLogEntry, Event event, NotificationHistory... historyEntries) {
        assertEquals(event.getId(), eventLogEntry.getId());
        // Jackson's serialization gets rid of nanoseconds so an equals between the LocalDateTime objects won't work.
        assertEquals(event.getCreated().toEpochSecond(UTC), eventLogEntry.getCreated().toEpochSecond(UTC));
        assertEquals(event.getEventType().getApplication().getBundle().getDisplayName(), eventLogEntry.getBundle());
        assertEquals(event.getEventType().getApplication().getDisplayName(), eventLogEntry.getApplication());
        assertEquals(event.getEventType().getDisplayName(), eventLogEntry.getEventType());
        if (historyEntries == null) {
            assertTrue(eventLogEntry.getActions().isEmpty());
        } else {
            assertEquals(historyEntries.length, eventLogEntry.getActions().size());
            for (EventLogEntryAction eventLogEntryAction : eventLogEntry.getActions()) {
                Optional<NotificationHistory> historyEntry = Arrays.stream(historyEntries)
                        .filter(entry -> entry.getId().equals(eventLogEntryAction.getId())).findAny();
                assertTrue(historyEntry.isPresent());
                assertEquals(historyEntry.get().getEndpointType(), eventLogEntryAction.getEndpointType());
                assertEquals(historyEntry.get().isInvocationResult(), eventLogEntryAction.getInvocationResult());
            }
        }
    }

    private static void assertLinks(Map<String, String> links, String... expectedKeys) {
        assertEquals(expectedKeys.length, links.size());
        for (String key : expectedKeys) {
            assertTrue(links.containsKey(key));
        }
    }
}
