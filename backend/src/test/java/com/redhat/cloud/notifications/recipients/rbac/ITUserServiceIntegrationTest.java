package com.redhat.cloud.notifications.recipients.rbac;

import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.itservice.ITUserServiceWrapper;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.ITUserResponse;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class ITUserServiceIntegrationTest {

    @Inject
    ITUserServiceWrapper itUserService;

    @Inject
    RbacRecipientUsersProvider rbacRecipientUsersProvider;

    @Test
    public void shouldReturn5870NonAdminUsers() {
        final List<ITUserResponse> someAccountId = itUserService.getUsers("someAccountId", false).await().indefinitely();
        assertEquals(5870, someAccountId.size());
    }

    @Test
    public void shouldReturn83AdminUsers() {
        final List<ITUserResponse> someAccountId = itUserService.getUsers("someAccountId", true).await().indefinitely();
        assertEquals(83, someAccountId.size());
    }

    @Test
    void shouldBeNonAdmin() {
        final List<User> someAccountId = rbacRecipientUsersProvider.getUsers("someAccountId", false).await().indefinitely();
        assertFalse(someAccountId.get(0).isAdmin());
    }

    @Test
    void shouldBeAdmin() {
        final List<User> someAccountId = rbacRecipientUsersProvider.getUsers("someAccountId", true).await().indefinitely();
        assertTrue(someAccountId.get(0).isAdmin());
    }

    @Test
    void shouldBeActive() {
        final List<User> someAccountId = rbacRecipientUsersProvider.getUsers("someAccountId", false).await().indefinitely();
        assertTrue(someAccountId.get(0).isActive());
    }

    @Test
    void shouldBeActiveWhenAdminOnly() {
        final List<User> someAccountId = rbacRecipientUsersProvider.getUsers("someAccountId", true).await().indefinitely();
        assertTrue(someAccountId.get(0).isActive());
    }
}
