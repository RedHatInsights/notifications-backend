package com.redhat.cloud.notifications.recipients.rbac;

import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.Page;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@QuarkusTest
public class RbacRecipientUsersProviderTest {

    private final String accountId = "test-account-id";

    @ConfigProperty(name = "recipient-provider.rbac.elements-per-page")
    Integer rbacElementsPerPage;

    @InjectMock
    @RestClient
    RbacServiceToService rbacServiceToService;

    @Inject
    RbacRecipientUsersProvider rbacRecipientUsersProvider;

    @Test
    public void getAllUsersFromDefaultGroup() {
        RbacGroup defaultGroup = new RbacGroup();
        defaultGroup.setPlatformDefault(true);
        defaultGroup.setUuid(UUID.randomUUID());

        int elements = 133;

        mockGetGroup(defaultGroup);
        mockGetUsers(elements, false);

        List<User> users = rbacRecipientUsersProvider.getGroupUsers(accountId, false, defaultGroup.getUuid()).await().indefinitely();
        Assertions.assertEquals(elements, users.size());
        for (int i = 0; i < elements; ++i) {
            Assertions.assertEquals(String.format("username-%d", i), users.get(i).getUsername());
        }
    }

    @Test
    public void getAllUsersCache() {
        int initialSize = 1095;
        mockGetUsers(initialSize, false);

        List<User> users = rbacRecipientUsersProvider.getUsers(accountId, false).await().indefinitely();
        Assertions.assertEquals(initialSize, users.size());

        for (int i = 0; i < initialSize; ++i) {
            Assertions.assertEquals(String.format("username-%d", i), users.get(i).getUsername());
        }

        int updatedSize = 1323;
        mockGetUsers(updatedSize, false);

        // Should still have the initial size because of the cache
        users = rbacRecipientUsersProvider.getUsers(accountId, false).await().indefinitely();
        Assertions.assertEquals(initialSize, users.size());

        clearCached();

        users = rbacRecipientUsersProvider.getUsers(accountId, false).await().indefinitely();
        Assertions.assertEquals(updatedSize, users.size());
    }

    @Test
    public void getAllGroupsCache() {
        RbacGroup group = new RbacGroup();
        group.setPlatformDefault(false);
        group.setUuid(UUID.randomUUID());

        int initialSize = 133;

        mockGetGroup(group);
        mockGetGroupUsers(initialSize, group.getUuid());

        List<User> users = rbacRecipientUsersProvider.getGroupUsers(accountId, false, group.getUuid()).await().indefinitely();
        Assertions.assertEquals(initialSize, users.size());
        for (int i = 0; i < initialSize; ++i) {
            Assertions.assertEquals(String.format("username-%d", i), users.get(i).getUsername());
        }

        int updatedSize = 323;
        mockGetGroupUsers(updatedSize, group.getUuid());

        // Should still have the initial size because of the cache
        users = rbacRecipientUsersProvider.getGroupUsers(accountId, false, group.getUuid()).await().indefinitely();
        Assertions.assertEquals(initialSize, users.size());

        clearCached();

        users = rbacRecipientUsersProvider.getGroupUsers(accountId, false, group.getUuid()).await().indefinitely();
        Assertions.assertEquals(updatedSize, users.size());
    }

    private void mockGetUsers(int elements, boolean adminsOnly) {
        MockedUserAnswer answer = new MockedUserAnswer(elements, adminsOnly);
        Mockito.when(rbacServiceToService.getUsers(
                Mockito.eq(accountId),
                Mockito.eq(adminsOnly),
                Mockito.anyInt(),
                Mockito.anyInt()
        )).then(invocationOnMock -> answer.mockedUserAnswer(
                invocationOnMock.getArgument(2, Integer.class),
                invocationOnMock.getArgument(3, Integer.class),
                invocationOnMock.getArgument(1, Boolean.class)
        ));
    }

    private void mockGetGroup(RbacGroup group) {
        Mockito.when(rbacServiceToService.getGroup(
                Mockito.eq(accountId),
                Mockito.eq(group.getUuid())
        )).thenReturn(Uni.createFrom().item(group));
    }

    private void mockGetGroupUsers(int elements, UUID groupId) {
        Mockito.when(rbacServiceToService.getGroupUsers(
                Mockito.eq(accountId),
                Mockito.eq(groupId),
                Mockito.anyInt(),
                Mockito.anyInt()
        )).then(invocationOnMock -> {
            MockedUserAnswer answer = new MockedUserAnswer(elements, false);
            return answer.mockedUserAnswer(
                    invocationOnMock.getArgument(2, Integer.class),
                    invocationOnMock.getArgument(3, Integer.class),
                    false
            );
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

    class MockedUserAnswer {

        private final int expectedElements;
        private final boolean expectedAdminsOnly;

        MockedUserAnswer(int expectedElements, boolean expectedAdminsOnly) {
            this.expectedElements = expectedElements;
            this.expectedAdminsOnly = expectedAdminsOnly;
        }

        Uni<Page<RbacUser>> mockedUserAnswer(int offset, int limit, boolean adminsOnly) {

            Assertions.assertEquals(rbacElementsPerPage.intValue(), limit);
            Assertions.assertEquals(expectedAdminsOnly, adminsOnly);

            int bound = Math.min(offset + limit, expectedElements);

            List<RbacUser> users = new ArrayList<>();
            for (int i = offset; i < bound; ++i) {
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

            return Uni.createFrom().item(usersPage);
        }
    }
}
