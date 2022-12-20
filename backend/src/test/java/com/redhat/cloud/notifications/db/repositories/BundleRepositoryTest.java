package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.persistence.EntityManager;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class BundleRepositoryTest {

    @Inject
    BundleRepository bundleRepository;

    @Inject
    EntityManager entityManager;

    @Inject
    ResourceHelpers resourceHelpers;

    /**
     * Tests that the {@link BundleRepository#getBundlesCount()} counts the number of bundles that are present in the
     * database. For that, it first counts ones that are preloaded, creates a number of bundles, and calls the function
     * under test to see if the numbers match.
     */
    @Test
    void testCountBundles() {
        final String query =
            "SELECT " +
                "count(b)" +
            "FROM " +
                "Bundle AS b";

        final long initialBundlesCount = this.entityManager.createQuery(query, Long.class).getSingleResult();

        final int bundlesToBeCreated = 5;
        for (int i = 0; i < bundlesToBeCreated; i++) {
            this.resourceHelpers.createBundle(
                String.format("name-%s", i),
                String.format("display-name-%s", i)
            );
        }

        final long count = this.bundleRepository.getBundlesCount();

        Assertions.assertEquals(initialBundlesCount + bundlesToBeCreated, count, "unexpected number of bundles counted");
    }
}
