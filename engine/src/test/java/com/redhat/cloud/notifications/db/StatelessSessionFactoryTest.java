package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.TestLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class StatelessSessionFactoryTest {

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    @Test
    void testValidUsage() {
        statelessSessionFactory.withSession(statelessSession -> {
            assertNotNull(statelessSessionFactory.getCurrentSession());
        });
    }

    @Test
    void testNestedWithSession() {
        assertThrows(IllegalStateException.class, () -> {
            statelessSessionFactory.withSession(statelessSession1 -> {
                statelessSessionFactory.withSession(statelessSession2 -> {
                });
            });
        });
    }

    @Test
    void testUninitializedGetCurrentSession() {
        assertThrows(IllegalStateException.class, () -> {
            statelessSessionFactory.getCurrentSession();
        });
    }
}
