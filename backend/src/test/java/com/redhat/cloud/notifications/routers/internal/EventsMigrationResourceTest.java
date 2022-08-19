package com.redhat.cloud.notifications.routers.internal;

import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import java.util.UUID;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static com.redhat.cloud.notifications.TestHelpers.createTurnpikeIdentityHeader;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EventsMigrationResourceTest {

    @ConfigProperty(name = "internal.admin-role")
    String adminRole;

    @Inject
    EntityManager entityManager;

    @Test
    void testMigration() {
        MockServerConfig.mockBopOrgIdTranslation();

        EventType eventType = createEventType();

        Event event1 = createEvent(eventType, "account-123", "org-id-123");
        Event event2 = createEvent(eventType, "account-456", "org-id-456");
        Event event3 = createEvent(eventType, "account-789", "org-id-789");

        given()
                .basePath(API_INTERNAL)
                .header(createTurnpikeIdentityHeader("admin", adminRole))
                .when()
                .put("/org-id/migrate")
                .then()
                .statusCode(204);

        entityManager.clear();

        assertEquals("org-id-123", entityManager.find(Event.class, event1.getId()).getOrgId());
        assertEquals("org-id-456", entityManager.find(Event.class, event2.getId()).getOrgId());
        assertEquals("org-id-789", entityManager.find(Event.class, event3.getId()).getOrgId());
    }

    @Transactional
    EventType createEventType() {
        Bundle bundle = new Bundle();
        bundle.setName("bundle");
        bundle.setDisplayName("Bundle");
        bundle.prePersist();
        entityManager.persist(bundle);

        Application app = new Application();
        app.setBundle(bundle);
        app.setBundleId(bundle.getId());
        app.setName("app");
        app.setDisplayName("Application");
        app.prePersist();
        entityManager.persist(app);

        EventType eventType = new EventType();
        eventType.setApplication(app);
        eventType.setApplicationId(app.getId());
        eventType.setName("event-type");
        eventType.setDisplayName("Event type");
        entityManager.persist(eventType);

        return eventType;
    }

    @Transactional
    Event createEvent(EventType eventType, String accountId, String orgId) {
        Event event = new Event(accountId, orgId, eventType, UUID.randomUUID());
        entityManager.persist(event);
        return event;
    }
}
