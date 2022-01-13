package com.redhat.cloud.notifications.recipients.rbac;

import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.itservice.ITUserServiceWrapper;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.ITUserResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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

    @Test
    void shouldMapUsersCorrectly() {
        final RbacRecipientUsersProvider mock = Mockito.mock(RbacRecipientUsersProvider.class);
        User mockedUser = createMockedUser();
        List<User> users = List.of(mockedUser);

        Mockito.when(mock.getUsers(Mockito.anyString(), Mockito.anyBoolean())).thenReturn(Uni.createFrom().item(users));
        final List<User> someAccountId = mock.getUsers("someAccountId", true).await().indefinitely();

        final User user = someAccountId.get(0);
        assertEquals("firstName", user.getFirstName());
        assertEquals("lastName", user.getLastName());
        assertEquals("userName", user.getUsername());
        assertEquals("email@trashmail.xyz", user.getEmail());
        assertTrue(user.isActive());
        assertTrue(user.isAdmin());
    }

    private User createMockedUser() {
        User mockedUser = new User();
        mockedUser.setActive(true);
        mockedUser.setLastName("lastName");
        mockedUser.setFirstName("firstName");
        mockedUser.setUsername("userName");
        mockedUser.setEmail("email@trashmail.xyz");
        mockedUser.setAdmin(true);
        mockedUser.setActive(true);
        return mockedUser;
    }
}
