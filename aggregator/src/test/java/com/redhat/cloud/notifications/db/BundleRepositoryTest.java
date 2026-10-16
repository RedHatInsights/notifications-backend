package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.helpers.ResourceHelpers;
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
public class BundleRepositoryTest {

    @Inject
    ResourceHelpers resourceHelpers;

    @Inject
    BundleRepository bundleRepository;

    @Test
    void testGetBundle_ReturnsCorrectBundle() {
        String bundleName = "bundle-" + new SecureRandom().nextInt();
        Bundle bundle = resourceHelpers.createBundle(bundleName);

        Optional<Bundle> result = bundleRepository.getBundle(bundleName);

        assertTrue(result.isPresent());
        assertEquals(bundle.getId(), result.get().getId());
    }

    @Test
    void testGetBundle_UnknownBundle_ReturnsEmpty() {
        Optional<Bundle> result = bundleRepository.getBundle("bundle-does-not-exist-" + new SecureRandom().nextInt());

        assertTrue(result.isEmpty());
    }
}
