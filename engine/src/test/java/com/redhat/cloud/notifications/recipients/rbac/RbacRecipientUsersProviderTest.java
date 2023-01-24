package com.redhat.cloud.notifications.recipients.rbac;

import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.itservice.ITUserService;
import com.redhat.cloud.notifications.recipients.itservice.pojo.request.ITUserRequest;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.AccountRelationship;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.Authentication;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.Email;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.ITUserResponse;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.Permission;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.PersonalInformation;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.Page;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
public class RbacRecipientUsersProviderTest {

    @ConfigProperty(name = "recipient-provider.it.max-results-per-page", defaultValue = "1000")
    int maxResultsPerPage;

    @ConfigProperty(name = "recipient-provider.rbac.elements-per-page", defaultValue = "1000")
    int rbacMaxResultsPerPage;

    @InjectMock
    @RestClient
    RbacServiceToService rbacServiceToService;

    @InjectMock
    @RestClient
    ITUserService itUserService;

    @Inject
    RbacRecipientUsersProvider rbacRecipientUsersProvider;

    @Inject
    FeatureFlipper featureFlipper;

    @Test
    void getGroupUsersShouldOnlyContainActiveUsers() {
        RbacGroup defaultGroup = new RbacGroup();
        defaultGroup.setPlatformDefault(false);
        defaultGroup.setUuid(UUID.randomUUID());
        mockGetGroup(defaultGroup);

        List<RbacUser> users = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            RbacUser user = new RbacUser();
            user.setEmail("my-email");
            user.setActive(i % 2 == 0);
            users.add(user);
        }

        assertTrue(users.stream().anyMatch(u -> !u.getActive()));
        assertTrue(users.stream().anyMatch(RbacUser::getActive));

        Meta meta = new Meta();
        meta.setCount(10L);

        Page<RbacUser> page = new Page<>();
        page.setData(users);
        page.setMeta(meta);

        when(rbacServiceToService.getGroupUsers(any(), any(), any(), any())).thenReturn(page);

        List<User> resolvedUsers = rbacRecipientUsersProvider.getGroupUsers(TestConstants.DEFAULT_ORG_ID, false, defaultGroup.getUuid());

        assertEquals(5, resolvedUsers.size());
        assertTrue(resolvedUsers.stream().allMatch(User::isActive));
    }

    @Test
    void shouldBeAdminWhenResponseContainsAdminPermission() {
        RbacRecipientUsersProvider testee = new RbacRecipientUsersProvider();

        List<ITUserResponse> itUserResponses = createTestResponse(RbacRecipientUsersProvider.ORG_ADMIN_PERMISSION);

        final List<User> users = testee.transformToUser(itUserResponses);

        assertTrue(users.get(0).isAdmin());
    }

    @Test
    void shouldNotBeAdminWhenResponseDoesNotContainAdminPermission() {
        RbacRecipientUsersProvider testee = new RbacRecipientUsersProvider();

        List<ITUserResponse> itUserResponses = createTestResponse("portal_download");

        final List<User> users = testee.transformToUser(itUserResponses);

        assertFalse(users.get(0).isAdmin());
    }

    private List<ITUserResponse> createTestResponse(String permissionCode) {
        List<ITUserResponse> itUserResponses = new ArrayList<>();

        ITUserResponse itUserResponse = new ITUserResponse();

        final AccountRelationship accountRelationship = new AccountRelationship();
        accountRelationship.permissions = new ArrayList<>();
        Permission permission = new Permission();
        permission.permissionCode = permissionCode;
        accountRelationship.permissions.add(permission);

        itUserResponse.authentications = new ArrayList<>();
        final Authentication authentication = new Authentication();
        authentication.principal = "somePrincipal";
        authentication.providerName = "someProviderName";
        itUserResponse.authentications.add(authentication);

        itUserResponse.accountRelationships = new ArrayList<>();
        itUserResponse.accountRelationships.add(accountRelationship);

        accountRelationship.emails = new ArrayList<>();

        itUserResponse.personalInformation = new PersonalInformation();
        itUserResponse.personalInformation.firstName = "someFirstName";
        itUserResponse.personalInformation.lastNames = "someLastName";

        itUserResponses.add(itUserResponse);

        return itUserResponses;
    }

    @Test
    public void getAllUsersFromDefaultGroup() {
        RbacGroup defaultGroup = new RbacGroup();
        defaultGroup.setPlatformDefault(true);
        defaultGroup.setUuid(UUID.randomUUID());

        int elements = 133;

        mockGetGroup(defaultGroup);
        mockGetUsers(elements, false);

        List<User> users = rbacRecipientUsersProvider.getGroupUsers(TestConstants.DEFAULT_ORG_ID, false, defaultGroup.getUuid());
        assertEquals(elements, users.size());
        for (int i = 0; i < elements; ++i) {
            assertEquals(String.format("username-%d", i), users.get(i).getUsername());
        }
    }

    @Test
    public void getAllUsersFromDefaultGroupRBAC() {
        try {
            featureFlipper.setUseRbacForFetchingUsers(true);
            RbacGroup defaultGroup = new RbacGroup();
            defaultGroup.setPlatformDefault(true);
            defaultGroup.setUuid(UUID.randomUUID());

            int elements = 133;

            mockGetGroup(defaultGroup);
            mockGetUsersRBAC(elements, false);

            List<User> users = rbacRecipientUsersProvider.getGroupUsers(TestConstants.DEFAULT_ORG_ID, false, defaultGroup.getUuid());
            assertEquals(elements, users.size());
            for (int i = 0; i < elements; ++i) {
                assertEquals(String.format("username-%d", i), users.get(i).getUsername());
            }
        } finally {
            featureFlipper.setUseRbacForFetchingUsers(false);
        }
    }

    @Test
    public void shouldReturnNoUsersWhenGroupNotFound() {
        UUID nonExistentGroup = UUID.randomUUID();
        mockNotFoundGroup(nonExistentGroup);

        List<User> users = rbacRecipientUsersProvider.getGroupUsers(TestConstants.DEFAULT_ORG_ID, false, nonExistentGroup);
        assertEquals(0, users.size());
    }

    @Test
    public void getAllUsersCache() {
        int initialSize = 1095;
        int updatedSize = 1323;
        mockGetUsers(initialSize, false);

        List<User> users = rbacRecipientUsersProvider.getUsers(TestConstants.DEFAULT_ORG_ID, false);
        assertEquals(initialSize, users.size());
        for (int i = 0; i < initialSize; ++i) {
            assertEquals(String.format("username-%d", i), users.get(i).getUsername());
        }

        mockGetUsers(updatedSize, false);

        users = rbacRecipientUsersProvider.getUsers(TestConstants.DEFAULT_ORG_ID, false);
        // Should still have the initial size because of the cache
        assertEquals(initialSize, users.size());
        clearCached();

        users = rbacRecipientUsersProvider.getUsers(TestConstants.DEFAULT_ORG_ID, false);
        assertEquals(updatedSize, users.size());
    }

    @Test
    public void getAllUsersCacheRBAC() {
        try {
            featureFlipper.setUseRbacForFetchingUsers(true);
            int initialSize = 1095;
            int updatedSize = 1323;
            mockGetUsersRBAC(initialSize, false);

            List<User> users = rbacRecipientUsersProvider.getUsers(TestConstants.DEFAULT_ORG_ID, false);
            assertEquals(initialSize, users.size());
            for (int i = 0; i < initialSize; ++i) {
                assertEquals(String.format("username-%d", i), users.get(i).getUsername());
            }

            mockGetUsersRBAC(updatedSize, false);

            users = rbacRecipientUsersProvider.getUsers(TestConstants.DEFAULT_ORG_ID, false);
            // Should still have the initial size because of the cache
            assertEquals(initialSize, users.size());
            clearCached();

            users = rbacRecipientUsersProvider.getUsers(TestConstants.DEFAULT_ORG_ID, false);
            assertEquals(updatedSize, users.size());
        } finally {
            featureFlipper.setUseRbacForFetchingUsers(false);
        }

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

        List<User> users = rbacRecipientUsersProvider.getGroupUsers(TestConstants.DEFAULT_ORG_ID, false, group.getUuid());
        assertEquals(initialSize, users.size());
        for (int i = 0; i < initialSize; ++i) {
            assertEquals(String.format("username-%d", i), users.get(i).getUsername());
        }

        mockGetGroupUsers(updatedSize, group.getUuid());

        users = rbacRecipientUsersProvider.getGroupUsers(TestConstants.DEFAULT_ORG_ID, false, group.getUuid());
        // Should still have the initial size because of the cache
        assertEquals(initialSize, users.size());
        clearCached();

        users = rbacRecipientUsersProvider.getGroupUsers(TestConstants.DEFAULT_ORG_ID, false, group.getUuid());
        assertEquals(updatedSize, users.size());
    }

    private void mockGetUsers(int elements, boolean adminsOnly) {
        MockedUserAnswer answer = new MockedUserAnswer(elements, adminsOnly);
        when(itUserService.getUsers(any(ITUserRequest.class)))
                .then(invocationOnMock -> answer.mockedUserAnswer(invocationOnMock.getArgument(0, ITUserRequest.class)));
    }

    private void mockGetUsersRBAC(int elements, boolean adminsOnly) {
        MockedUserAnswer answer = new MockedUserAnswer(elements, adminsOnly);
        Mockito.when(rbacServiceToService.getUsers(
                Mockito.eq(TestConstants.DEFAULT_ORG_ID),
                Mockito.eq(adminsOnly),
                Mockito.anyInt(),
                Mockito.anyInt()
        )).then(invocationOnMock -> answer.mockedUserAnswerRBAC(
                invocationOnMock.getArgument(2, Integer.class),
                invocationOnMock.getArgument(3, Integer.class),
                invocationOnMock.getArgument(1, Boolean.class)
        ));
    }

    private void mockGetGroup(RbacGroup group) {
        when(rbacServiceToService.getGroup(
                Mockito.eq(TestConstants.DEFAULT_ORG_ID),
                Mockito.eq(group.getUuid())
        )).thenReturn(group);
    }

    private void mockNotFoundGroup(UUID groupId) {
        when(rbacServiceToService.getGroup(
                Mockito.eq(TestConstants.DEFAULT_ORG_ID),
                Mockito.eq(groupId)
        )).thenThrow(new ClientWebApplicationException(404));
    }

    private void mockGetGroupUsers(int elements, UUID groupId) {
        when(rbacServiceToService.getGroupUsers(
                Mockito.eq(TestConstants.DEFAULT_ORG_ID),
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

    class MockedUserAnswer {

        private final int expectedElements;
        private final boolean expectedAdminsOnly;

        MockedUserAnswer(int expectedElements, boolean expectedAdminsOnly) {
            this.expectedElements = expectedElements;
            this.expectedAdminsOnly = expectedAdminsOnly;
        }

        Page<RbacUser> mockedUserAnswerRBAC(int offset, int limit, boolean adminsOnly) {
            assertEquals(rbacMaxResultsPerPage, limit);
            assertEquals(expectedAdminsOnly, adminsOnly);
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
            return usersPage;
        }

        List<ITUserResponse> mockedUserAnswer(ITUserRequest request) {
            boolean adminsOnly = request.by.allOf.permissionCode != null;
            int firstResult = request.by.withPaging.firstResultIndex;
            int maxResults = request.by.withPaging.maxResults;

            assertEquals(maxResultsPerPage, maxResults);
            assertEquals(expectedAdminsOnly, adminsOnly);

            int bound = Math.min(firstResult + maxResults, expectedElements);

            List<ITUserResponse> users = new ArrayList<>();
            for (int i = firstResult; i < bound; i++) {

                ITUserResponse user = new ITUserResponse();

                user.authentications = new ArrayList<>();
                user.authentications.add(new Authentication());
                user.authentications.get(0).principal = String.format("username-%d", i);

                Email email = new Email();
                email.address = String.format("username-%d@foobardotcom", i);
                user.accountRelationships = new ArrayList<>();
                user.accountRelationships.add(new AccountRelationship());
                user.accountRelationships.get(0).emails = List.of(email);

                user.accountRelationships.get(0).permissions = List.of();

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
            for (int i = 0; i < expectedElements; i++) {
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
