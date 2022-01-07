package com.redhat.cloud.notifications.recipients.rbac;

import com.redhat.cloud.notifications.recipients.User;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import java.util.List;

@QuarkusTest
public class RbacRecipientUsersProviderIntegrationTest {

    @Inject
    RbacRecipientUsersProvider rbacRecipientUsersProvider;

    /**
     * - returns 404 when LifecycleManager mockserver stuff is present
     * - returns 401 and others when changing parameters in application.properties
     *
     */
    @Test
    public void getAllUsersCache() {
        final List<User> users = rbacRecipientUsersProvider.getUsers("5910538", false).await().indefinitely();
        System.out.println(users);
    }
}
