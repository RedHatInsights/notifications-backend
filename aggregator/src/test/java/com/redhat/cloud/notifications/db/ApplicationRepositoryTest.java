package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ApplicationRepositoryTest {

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    ApplicationRepository applicationRepository;

    @Test
    void testGetApplication_ReturnsCorrectApplication() {
        String bundleName = "bundle-" + new SecureRandom().nextInt();
        String appName = "app-" + new SecureRandom().nextInt();
        Bundle bundle = resourceHelpers.createBundle(bundleName);
        Application app = resourceHelpers.createApp(bundle.getId(), appName);

        Optional<Application> result = applicationRepository.getApplication(bundleName, appName);

        assertTrue(result.isPresent());
        assertEquals(app.getId(), result.get().getId());
    }

    @Test
    void testGetApplication_UnknownApplication_ReturnsEmpty() {
        int nonce = new SecureRandom().nextInt();
        Optional<Application> result = applicationRepository.getApplication("bundle-does-not-exist-" + nonce, "app-does-not-exist-" + nonce);

        assertTrue(result.isEmpty());
    }
}
