package com.redhat.cloud.notifications.recipients.rbac;

import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.itservice.ITUserServiceWrapper;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.AccountRelationship;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.Authentication;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.Email;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.ITUserResponse;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.PersonalInformation;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.Page;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class RbacRecipientUsersProviderTest {

    private final String accountId = "test-account-id";

    @InjectMock
    @RestClient
    RbacServiceToService rbacServiceToService;

    @InjectMock
    ITUserServiceWrapper itUserService;

    @Inject
    RbacRecipientUsersProvider rbacRecipientUsersProvider;

    @Test
    public void getAllUsersFromDefaultGroup() {
        RbacGroup defaultGroup = new RbacGroup();
        defaultGroup.setPlatformDefault(true);
        defaultGroup.setUuid(UUID.randomUUID());

        int elements = 133;

        mockGetGroup(defaultGroup);
        mockGetUsers(elements, false, 0, 101);

        List<User> users = rbacRecipientUsersProvider.getGroupUsers(accountId, false, defaultGroup.getUuid());
        assertEquals(elements, users.size());
        for (int i = 0; i < elements; ++i) {
            assertEquals(String.format("username-%d", i), users.get(i).getUsername());
        }
    }

    @Test
    public void getAllUsersCache() {
        int initialSize = 1095;
        int updatedSize = 1323;
        mockGetUsers(initialSize, false, 0, 101);

        List<User> users = rbacRecipientUsersProvider.getUsers(accountId, false);
        assertEquals(initialSize, users.size());
        for (int i = 0; i < initialSize; ++i) {
            assertEquals(String.format("username-%d", i), users.get(i).getUsername());
        }

        mockGetUsers(updatedSize, false, 0, 101);

        users = rbacRecipientUsersProvider.getUsers(accountId, false);
        // Should still have the initial size because of the cache
        assertEquals(initialSize, users.size());
        clearCached();

        users = rbacRecipientUsersProvider.getUsers(accountId, false);
        assertEquals(updatedSize, users.size());
    }

    @Test
    public void getAllGroupsCache() {
        RbacGroup group = new RbacGroup();
        group.setPlatformDefault(false);
        group.setUuid(UUID.randomUUID());

        int initialSize = 133;
        int updatedSize = 323;

        mockGetGroup(group);
        mockGetGroupUsers(initialSize, group.getUuid());

        List<User> users = rbacRecipientUsersProvider.getGroupUsers(accountId, false, group.getUuid());
        assertEquals(initialSize, users.size());
        for (int i = 0; i < initialSize; ++i) {
            assertEquals(String.format("username-%d", i), users.get(i).getUsername());
        }

        mockGetGroupUsers(updatedSize, group.getUuid());

        users = rbacRecipientUsersProvider.getGroupUsers(accountId, false, group.getUuid());
        // Should still have the initial size because of the cache
        assertEquals(initialSize, users.size());
        clearCached();

        users = rbacRecipientUsersProvider.getGroupUsers(accountId, false, group.getUuid());
        assertEquals(updatedSize, users.size());
    }

    private void mockGetUsers(int elements, boolean adminsOnly, int pagingStart, int pagingEnd) {
        MockedUserAnswer answer = new MockedUserAnswer(elements, adminsOnly);
        Mockito.when(itUserService.getUsers(
                Mockito.eq(accountId),
                Mockito.eq(adminsOnly),
                Mockito.eq(pagingStart),
                Mockito.eq(pagingEnd)
                )).then(invocationOnMock -> answer.mockedUserAnswer(
                invocationOnMock.getArgument(1, Boolean.class)
        ));
    }

    private void mockGetGroup(RbacGroup group) {
        Mockito.when(rbacServiceToService.getGroup(
                Mockito.eq(accountId),
                Mockito.eq(group.getUuid())
        )).thenReturn(group);
    }

    private void mockGetGroupUsers(int elements, UUID groupId) {
        Mockito.when(rbacServiceToService.getGroupUsers(
                Mockito.eq(accountId),
                Mockito.eq(groupId),
                Mockito.anyInt(),
                Mockito.anyInt()
        )).then(invocationOnMock -> {
            OldMockedUserAnswer answer = new OldMockedUserAnswer(elements, false);
            return answer.mockedUserAnswer(false);
        });
    }

    /*
     * This would normally happen after a certain duration fixed in application.properties with the
     * quarkus.cache.caffeine.rbac-recipient-users-provider-get-group-users.expire-after-write
     * and
     * quarkus.cache.caffeine.rbac-recipient-users-provider-get-users.expire-after-write key.
     */
    @CacheInvalidateAll(cacheName = "rbac-recipient-users-provider-get-users")
    @CacheInvalidateAll(cacheName = "rbac-recipient-users-provider-get-group-users")
    @BeforeEach
    void clearCached() {
    }

    static class MockedUserAnswer {

        private final int expectedElements;
        private final boolean expectedAdminsOnly;

        MockedUserAnswer(int expectedElements, boolean expectedAdminsOnly) {
            this.expectedElements = expectedElements;
            this.expectedAdminsOnly = expectedAdminsOnly;
        }

        List<ITUserResponse> mockedUserAnswer(boolean adminsOnly) {
            Assertions.assertEquals(expectedAdminsOnly, adminsOnly);

            List<ITUserResponse> users = new ArrayList<>();
            for (int i = 0; i < expectedElements; ++i) {

                ITUserResponse user = new ITUserResponse();

                user.authentications = new LinkedList<>();
                user.authentications.add(new Authentication());
                user.authentications.get(0).principal = String.format("username-%d", i);

                Email email = new Email();
                email.address = String.format("username-%d@foobardotcom", i);
                user.accountRelationships = new LinkedList<>();
                user.accountRelationships.add(new AccountRelationship());
                user.accountRelationships.get(0).emails = List.of(email);

                user.personalInformation = new PersonalInformation();
                user.personalInformation.firstName = "foo";
                user.personalInformation.lastNames = "bar";

                users.add(user);
            }
            return users;
        }
    }

    static class OldMockedUserAnswer {

        private final int expectedElements;
        private final boolean expectedAdminsOnly;

        OldMockedUserAnswer(int expectedElements, boolean expectedAdminsOnly) {
            this.expectedElements = expectedElements;
            this.expectedAdminsOnly = expectedAdminsOnly;
        }

        Page<RbacUser> mockedUserAnswer(boolean adminsOnly) {
            assertEquals(expectedAdminsOnly, adminsOnly);

            List<RbacUser> users = new ArrayList<>();
            for (int i = 0; i < expectedElements; ++i) {
                RbacUser user = new RbacUser();
                user.setActive(true);
                user.setUsername(String.format("username-%d", i));
                user.setEmail(String.format("username-%d@foobardotcom", i));
                user.setFirstName("foo");
                user.setLastName("bar");
                user.setOrgAdmin(false);
                users.add(user);
            }

            Page<RbacUser> usersPage = new Page<>();
            usersPage.setMeta(new Meta());
            usersPage.setLinks(new HashMap<>());
            usersPage.setData(users);

            return usersPage;
        }
    }
}
