package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EventTypeRepositoryTest {

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    EventTypeRepository eventTypeRepository;

    @Test
    void testGetEventType_ReturnsCorrectEventType() {
        String bundleName = "bundle-" + new SecureRandom().nextInt();
        String appName = "app-" + new SecureRandom().nextInt();
        String eventTypeName = "event-type-" + new SecureRandom().nextInt();
        Bundle bundle = resourceHelpers.createBundle(bundleName);
        Application app = resourceHelpers.createApp(bundle.getId(), appName);
        EventType eventType = resourceHelpers.createEventType(app.getId(), eventTypeName);

        EventType result = eventTypeRepository.getEventType(bundleName, appName, eventTypeName);

        Assertions.assertEquals(eventType.getId(), result.getId());
        Assertions.assertEquals(appName, result.getApplication().getName());
        Assertions.assertEquals(bundleName, result.getApplication().getBundle().getName());
    }

    @Test
    void testGetEventType_UnknownTriplet_Throws() {
        int nonce = new SecureRandom().nextInt();
        Assertions.assertThrows(NoResultException.class,
            () -> eventTypeRepository.getEventType("bundle-does-not-exist-" + nonce, "app-does-not-exist-" + nonce, "event-type-does-not-exist-" + nonce));
    }
}
