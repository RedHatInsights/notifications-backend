package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Bundle;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.Optional;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class BundleRepositoryTest extends DbIsolatedTest {

    @Inject
    BundleRepository bundleRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    /**
     * Tests that a bundle can be successfully found by its name.
     */
    @Test
    void testFindByName() {
        final String bundleName = "test-find-by-name-bundle";
        final Bundle bundle = this.resourceHelpers.createBundle(bundleName, "test-find-by-name-display-name-bundle");

        // Call the function under test.
        final Optional<Bundle> result = this.bundleRepository.findByName(bundleName);

        Assertions.assertTrue(result.isPresent(), "no bundle was fetched from the database");
        Assertions.assertEquals(bundle.getId(), result.get().getId(), "unexpected bundle fetched by name from the database");
    }

    /**
     * Tests that an empty Optional is returned when the bundle cannot be found by its name.
     */
    @Test
    void testFindByNameEmptyOptional() {
        Assertions.assertTrue(this.bundleRepository.findByName("made-up-name").isEmpty(), "the bundle shouldn't have been found in the database");
    }
}
