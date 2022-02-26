package com.redhat.cloud.notifications.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import javax.inject.Inject;

/**
 * When a test class extends {@link DbIsolatedTest}, all of its tests are individually preceded and followed by a
 * complete database records reset. The tests are therefore isolated in terms of stored data.
 */
public abstract class DbIsolatedTest {

    @Inject
    DbCleaner dbCleaner;

    @BeforeEach
    @AfterEach
    void cleanDatabase() {
        dbCleaner.clean();
    }
}
