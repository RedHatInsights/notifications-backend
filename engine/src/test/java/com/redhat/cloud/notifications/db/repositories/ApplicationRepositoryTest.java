package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ApplicationRepositoryTest {

    private static final String BUNDLE_NAME = "app-repo-test-bundle";
    private static final String APP_NAME = "app-repo-test-app";

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    ApplicationRepository applicationRepository;

    @CacheInvalidateAll(cacheName = "get-application-by-id")
    void invalidateApplicationCache() {
    }

    @AfterEach
    void afterEach() {
        invalidateApplicationCache();
        resourceHelpers.deleteApp(BUNDLE_NAME, APP_NAME);
        resourceHelpers.deleteBundle(BUNDLE_NAME);
    }

    @Test
    void testGetApplicationNameById_ReturnsCorrectName() {
        Bundle bundle = resourceHelpers.createBundle(BUNDLE_NAME);
        Application app = resourceHelpers.createApp(bundle.getId(), APP_NAME);

        String result = applicationRepository.getApplicationName(app.getId());

        assertEquals(APP_NAME, result);
    }

    @Test
    void testGetApplicationNameById_UnknownId_ReturnsNull() {
        String result = applicationRepository.getApplicationName(UUID.randomUUID());

        assertNull(result, "Should return null for an unknown application ID");
    }
}
