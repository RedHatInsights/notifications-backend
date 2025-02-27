package com.redhat.cloud.notifications.recipients.resolver;

import com.redhat.cloud.notifications.recipients.config.RecipientsResolverConfig;
import com.redhat.cloud.notifications.recipients.model.User;
import com.redhat.cloud.notifications.recipients.resolver.itservice.ITUserService;
import com.redhat.cloud.notifications.recipients.resolver.itservice.pojo.request.ITUserRequest;
import com.redhat.cloud.notifications.recipients.resolver.itservice.pojo.response.AccountRelationship;
import com.redhat.cloud.notifications.recipients.resolver.itservice.pojo.response.Authentication;
import com.redhat.cloud.notifications.recipients.resolver.itservice.pojo.response.Email;
import com.redhat.cloud.notifications.recipients.resolver.itservice.pojo.response.ITUserResponse;
import com.redhat.cloud.notifications.recipients.resolver.itservice.pojo.response.Permission;
import com.redhat.cloud.notifications.recipients.resolver.itservice.pojo.response.PersonalInformation;
import com.redhat.cloud.notifications.recipients.resolver.mbop.MBOPService;
import com.redhat.cloud.notifications.recipients.resolver.mbop.MBOPUsers;
import com.redhat.cloud.notifications.recipients.resolver.mbop.MBOPUsers.MBOPUser;
import com.redhat.cloud.notifications.recipients.resolver.rbac.Meta;
import com.redhat.cloud.notifications.recipients.resolver.rbac.Page;
import com.redhat.cloud.notifications.recipients.resolver.rbac.RbacGroup;
import com.redhat.cloud.notifications.recipients.resolver.rbac.RbacServiceToService;
import com.redhat.cloud.notifications.recipients.resolver.rbac.RbacUser;
import dev.failsafe.FailsafeException;
import io.micrometer.core.instrument.Tags;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.apache.http.HttpStatus;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.recipients.resolver.FetchUsersFromExternalServices.COUNTER_REQUESTS;
import static com.redhat.cloud.notifications.recipients.resolver.FetchUsersFromExternalServices.COUNTER_TAG_FAILURES;
import static com.redhat.cloud.notifications.recipients.resolver.FetchUsersFromExternalServices.COUNTER_TAG_REQUEST_RESULT;
import static com.redhat.cloud.notifications.recipients.resolver.FetchUsersFromExternalServices.COUNTER_TAG_SUCCESSES;
import static com.redhat.cloud.notifications.recipients.resolver.FetchUsersFromExternalServices.COUNTER_TAG_USER_PROVIDER;
import static com.redhat.cloud.notifications.recipients.resolver.FetchUsersFromExternalServices.COUNTER_TAG_USER_PROVIDER_IT;
import static com.redhat.cloud.notifications.recipients.resolver.FetchUsersFromExternalServices.COUNTER_TAG_USER_PROVIDER_MBOP;
import static com.redhat.cloud.notifications.recipients.resolver.FetchUsersFromExternalServices.COUNTER_TAG_USER_PROVIDER_RBAC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
public class FetchUsersFromExternalServicesTest {

    private static final String DEFAULT_ORG_ID = "default-org-id";

    @InjectMock
    @RestClient
    RbacServiceToService rbacServiceToService;

    @InjectMock
    @RestClient
    ITUserService itUserService;

    @Inject
    FetchUsersFromExternalServices fetchUsersFromExternalServices;

    @InjectMock
    @RestClient
    MBOPService mbopService;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @InjectMock
    RecipientsResolverConfig recipientsResolverConfig;

    @BeforeEach
    void beforeEach() {
        // This is the default config. It has to be set because we're mocking RecipientsResolverConfig.
        when(recipientsResolverConfig.getInitialRetryBackoff()).thenReturn(Duration.ofMillis(100));
        when(recipientsResolverConfig.getMaxResultsPerPage()).thenReturn(1000);
        when(recipientsResolverConfig.getMaxRetryAttempts()).thenReturn(3);
        when(recipientsResolverConfig.getMaxRetryBackoff()).thenReturn(Duration.ofSeconds(1));
        when(recipientsResolverConfig.getMbopApiToken()).thenReturn("na");
        when(recipientsResolverConfig.getMbopClientId()).thenReturn("na");
        when(recipientsResolverConfig.getMbopEnv()).thenReturn("na");
    }

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

        List<User> resolvedUsers = fetchUsersFromExternalServices.getGroupUsers(DEFAULT_ORG_ID, false, defaultGroup.getUuid());

        assertEquals(5, resolvedUsers.size());
    }

    @Test
    void shouldBeAdminWhenResponseContainsAdminPermission() {
        FetchUsersFromExternalServices testee = new FetchUsersFromExternalServices();

        List<ITUserResponse> itUserResponses = createTestResponse(FetchUsersFromExternalServices.ORG_ADMIN_PERMISSION);

        final List<User> users = testee.transformItUserToUser(itUserResponses);

        assertTrue(users.get(0).isAdmin());
    }

    @Test
    void shouldNotBeAdminWhenResponseDoesNotContainAdminPermission() {
        FetchUsersFromExternalServices testee = new FetchUsersFromExternalServices();

        List<ITUserResponse> itUserResponses = createTestResponse("portal_download");

        final List<User> users = testee.transformItUserToUser(itUserResponses);

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

        List<User> users = fetchUsersFromExternalServices.getGroupUsers(DEFAULT_ORG_ID, false, defaultGroup.getUuid());
        assertEquals(elements, users.size());
        for (int i = 0; i < elements; ++i) {
            assertEquals(String.format("username-%d", i), users.get(i).getUsername());
        }
    }

    @Test
    public void getAllUsersFromDefaultGroupRBAC() {
        when(recipientsResolverConfig.isFetchUsersWithRbacEnabled()).thenReturn(true);
        RbacGroup defaultGroup = new RbacGroup();
        defaultGroup.setPlatformDefault(true);
        defaultGroup.setUuid(UUID.randomUUID());

        int elements = 133;

        mockGetGroup(defaultGroup);
        mockGetUsersRBAC(elements, false);

        List<User> users = fetchUsersFromExternalServices.getGroupUsers(DEFAULT_ORG_ID, false, defaultGroup.getUuid());
        assertEquals(elements, users.size());
        for (int i = 0; i < elements; ++i) {
            assertEquals(String.format("username-%d", i), users.get(i).getUsername());
        }
    }

    @Test
    public void shouldReturnNoUsersWhenGroupNotFound() {
        UUID nonExistentGroup = UUID.randomUUID();
        mockNotFoundGroup(nonExistentGroup);

        List<User> users = fetchUsersFromExternalServices.getGroupUsers(DEFAULT_ORG_ID, false, nonExistentGroup);
        assertEquals(0, users.size());
    }

    @Test
    public void getAllUsersCache() {
        int initialSize = 1095;
        int updatedSize = 1323;
        mockGetUsers(initialSize, false);

        List<User> users = fetchUsersFromExternalServices.getUsers(DEFAULT_ORG_ID, false);
        assertEquals(initialSize, users.size());
        for (int i = 0; i < initialSize; ++i) {
            assertEquals(String.format("username-%d", i), users.get(i).getUsername());
        }

        mockGetUsers(updatedSize, false);

        users = fetchUsersFromExternalServices.getUsers(DEFAULT_ORG_ID, false);
        // Should still have the initial size because of the cache
        assertEquals(initialSize, users.size());
        clearCached();

        users = fetchUsersFromExternalServices.getUsers(DEFAULT_ORG_ID, false);
        assertEquals(updatedSize, users.size());
    }

    @Test
    public void getAllUsersCacheRBAC() {
        when(recipientsResolverConfig.isFetchUsersWithRbacEnabled()).thenReturn(true);

        int initialSize = 1095;
        int updatedSize = 1323;
        mockGetUsersRBAC(initialSize, false);

        List<User> users = fetchUsersFromExternalServices.getUsers(DEFAULT_ORG_ID, false);
        assertEquals(initialSize, users.size());
        for (int i = 0; i < initialSize; ++i) {
            assertEquals(String.format("username-%d", i), users.get(i).getUsername());
        }

        mockGetUsersRBAC(updatedSize, false);

        users = fetchUsersFromExternalServices.getUsers(DEFAULT_ORG_ID, false);
        // Should still have the initial size because of the cache
        assertEquals(initialSize, users.size());
        clearCached();

        users = fetchUsersFromExternalServices.getUsers(DEFAULT_ORG_ID, false);
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

        List<User> users = fetchUsersFromExternalServices.getGroupUsers(DEFAULT_ORG_ID, false, group.getUuid());
        assertEquals(initialSize, users.size());
        for (int i = 0; i < initialSize; ++i) {
            assertEquals(String.format("username-%d", i), users.get(i).getUsername());
        }

        mockGetGroupUsers(updatedSize, group.getUuid());

        users = fetchUsersFromExternalServices.getGroupUsers(DEFAULT_ORG_ID, false, group.getUuid());
        // Should still have the initial size because of the cache
        assertEquals(initialSize, users.size());
        clearCached();

        users = fetchUsersFromExternalServices.getGroupUsers(DEFAULT_ORG_ID, false, group.getUuid());
        assertEquals(updatedSize, users.size());
    }

    /**
     * Tests that calling MBOP for users works as expected.
     */
    @Test
    void testGetUsersMBOP() {
        when(recipientsResolverConfig.isFetchUsersWithMbopEnabled()).thenReturn(true);

        // Fake a REST call to MBOP.
        final MBOPUsers firstPageMBOPUsers = this.mockGetMBOPUsersPage(recipientsResolverConfig.getMaxResultsPerPage());
        final MBOPUsers secondPageMBOPUsers = this.mockGetMBOPUsersPage(recipientsResolverConfig.getMaxResultsPerPage());
        final MBOPUsers thirdPageMBOPUsers = this.mockGetMBOPUsersPage(recipientsResolverConfig.getMaxResultsPerPage() / 2);

        final boolean adminsOnly = false;

        // Return a few pages of results, with the last one being less than
        // the configured maximum limit, so that we can test that the loop
        // is working as expected.
        Mockito
            .when(this.mbopService.getUsersByOrgId(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyInt(), anyInt(), anyBoolean(), anyString(), anyBoolean()))
            .thenReturn(firstPageMBOPUsers, secondPageMBOPUsers, thirdPageMBOPUsers);

        // Call the function under test.
        final List<User> result = this.fetchUsersFromExternalServices.getUsers(DEFAULT_ORG_ID, adminsOnly);

        // Verify that the offset argument, which is the one that changes
        // on each iteration, has been correctly incremented.
        final ArgumentCaptor<Integer> capturedOffset = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(this.mbopService, Mockito.times(3)).getUsersByOrgId(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyInt(), capturedOffset.capture(), anyBoolean(), anyString(), anyBoolean());

        final List<Integer> capturedValues = capturedOffset.getAllValues();
        assertIterableEquals(
            List.of(
                0,
                recipientsResolverConfig.getMaxResultsPerPage(),
                recipientsResolverConfig.getMaxResultsPerPage() * 2
            ),
            capturedValues,
            "unexpected offset values used when calling MBOP"
        );

        // Transform the generated MBOP users in order to check that the
        // function under test did the transformations as expected.
        final List<User> mockUsers = this.fetchUsersFromExternalServices.transformMBOPUserToUser(firstPageMBOPUsers.users());
        mockUsers.addAll(this.fetchUsersFromExternalServices.transformMBOPUserToUser(secondPageMBOPUsers.users()));
        mockUsers.addAll(this.fetchUsersFromExternalServices.transformMBOPUserToUser(thirdPageMBOPUsers.users()));

        assertIterableEquals(mockUsers, result, "the list of users returned by the function under test is not correct");
    }

    /**
     * Tests that when an {@link IOException} gets thrown, the retry mechanism
     * makes the counters increment by {@link RecipientsResolverConfig#getMaxRetryAttempts()}
     * times.
     */
    @Test
    void testIoExceptionIncreaseFailuresCounters() {
        when(this.rbacServiceToService.getUsers(Mockito.anyString(), Mockito.anyBoolean(), Mockito.anyInt(), Mockito.anyInt())).thenAnswer((answer) -> { throw new IOException(); });
        when(this.mbopService.getUsersByOrgId(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyBoolean(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean(), Mockito.anyString(), Mockito.anyBoolean())).thenAnswer((answer) -> { throw new IOException(); });
        when(this.itUserService.getUsers(Mockito.any())).thenAnswer((answer) -> { throw new IOException(); });

        // Test that the RBAC failures counter gets incremented.
        when(this.recipientsResolverConfig.isFetchUsersWithRbacEnabled()).thenReturn(true);

        this.micrometerAssertionHelper.saveCounterValueBeforeTestFilteredByTags(COUNTER_REQUESTS, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES, COUNTER_TAG_USER_PROVIDER, COUNTER_TAG_USER_PROVIDER_RBAC));

        // Call the function under test.
        assertThrows(FailsafeException.class,
            () -> this.fetchUsersFromExternalServices.getUsers("12345", true)
        );

        this.micrometerAssertionHelper.assertCounterIncrementFilteredByTags(COUNTER_REQUESTS, this.recipientsResolverConfig.getMaxRetryAttempts(), Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES, COUNTER_TAG_USER_PROVIDER, COUNTER_TAG_USER_PROVIDER_RBAC));

        // Test that the MBOP failures counter gets incremented.
        when(this.recipientsResolverConfig.isFetchUsersWithRbacEnabled()).thenReturn(false);
        when(this.recipientsResolverConfig.isFetchUsersWithMbopEnabled()).thenReturn(true);

        this.micrometerAssertionHelper.saveCounterValueBeforeTestFilteredByTags(COUNTER_REQUESTS, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES, COUNTER_TAG_USER_PROVIDER, COUNTER_TAG_USER_PROVIDER_MBOP));

        // Call the function under test.
        assertThrows(FailsafeException.class,
            () -> this.fetchUsersFromExternalServices.getUsers("12345", true)
        );

        this.micrometerAssertionHelper.assertCounterIncrementFilteredByTags(COUNTER_REQUESTS, this.recipientsResolverConfig.getMaxRetryAttempts(), Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES, COUNTER_TAG_USER_PROVIDER, COUNTER_TAG_USER_PROVIDER_MBOP));

        // Test that the IT failures counter gets incremented.
        when(this.recipientsResolverConfig.isFetchUsersWithMbopEnabled()).thenReturn(false);

        this.micrometerAssertionHelper.saveCounterValueBeforeTestFilteredByTags(COUNTER_REQUESTS, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES, COUNTER_TAG_USER_PROVIDER, COUNTER_TAG_USER_PROVIDER_IT));

        // Call the function under test.
        assertThrows(FailsafeException.class,
            () -> this.fetchUsersFromExternalServices.getUsers("12345", true)
        );

        this.micrometerAssertionHelper.assertCounterIncrementFilteredByTags(COUNTER_REQUESTS, this.recipientsResolverConfig.getMaxRetryAttempts(), Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES, COUNTER_TAG_USER_PROVIDER, COUNTER_TAG_USER_PROVIDER_IT));
    }

    /**
     * Tests that when an {@link WebApplicationException} gets thrown, or any
     * other exception really, the failures counter only gets incremented once.
     */
    @Test
    void testWebApplicationExceptionIncreaseFailuresCounters() {
        when(this.rbacServiceToService.getUsers(Mockito.anyString(), Mockito.anyBoolean(), Mockito.anyInt(), Mockito.anyInt())).thenAnswer((answer) -> { throw new WebApplicationException(); });
        when(this.mbopService.getUsersByOrgId(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyBoolean(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyBoolean(), Mockito.anyString(), Mockito.anyBoolean())).thenAnswer((answer) -> { throw new WebApplicationException(); });
        when(this.itUserService.getUsers(Mockito.any())).thenAnswer((answer) -> { throw new WebApplicationException(); });

        // Test that the RBAC failures counter gets incremented.
        when(this.recipientsResolverConfig.isFetchUsersWithRbacEnabled()).thenReturn(true);
        when(this.recipientsResolverConfig.isFetchUsersWithMbopEnabled()).thenReturn(false);

        this.micrometerAssertionHelper.saveCounterValueBeforeTestFilteredByTags(COUNTER_REQUESTS, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES, COUNTER_TAG_USER_PROVIDER, COUNTER_TAG_USER_PROVIDER_RBAC));

        // Call the function under test.
        assertThrows(WebApplicationException.class,
            () -> this.fetchUsersFromExternalServices.getUsers("12345", true)
        );

        this.micrometerAssertionHelper.assertCounterIncrementFilteredByTags(COUNTER_REQUESTS, 1, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES, COUNTER_TAG_USER_PROVIDER, COUNTER_TAG_USER_PROVIDER_RBAC));

        // Test that the MBOP failures counter gets incremented.
        when(this.recipientsResolverConfig.isFetchUsersWithRbacEnabled()).thenReturn(false);
        when(this.recipientsResolverConfig.isFetchUsersWithMbopEnabled()).thenReturn(true);

        this.micrometerAssertionHelper.saveCounterValueBeforeTestFilteredByTags(COUNTER_REQUESTS, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES, COUNTER_TAG_USER_PROVIDER, COUNTER_TAG_USER_PROVIDER_MBOP));

        // Call the function under test.
        assertThrows(WebApplicationException.class,
            () -> this.fetchUsersFromExternalServices.getUsers("12345", true)
        );

        this.micrometerAssertionHelper.assertCounterIncrementFilteredByTags(COUNTER_REQUESTS, 1, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES, COUNTER_TAG_USER_PROVIDER, COUNTER_TAG_USER_PROVIDER_MBOP));

        // Test that the IT failures counter gets incremented.
        when(this.recipientsResolverConfig.isFetchUsersWithMbopEnabled()).thenReturn(false);

        this.micrometerAssertionHelper.saveCounterValueBeforeTestFilteredByTags(COUNTER_REQUESTS, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES, COUNTER_TAG_USER_PROVIDER, COUNTER_TAG_USER_PROVIDER_IT));

        // Call the function under test.
        assertThrows(WebApplicationException.class,
            () -> this.fetchUsersFromExternalServices.getUsers("12345", true)
        );

        this.micrometerAssertionHelper.assertCounterIncrementFilteredByTags(COUNTER_REQUESTS, 1, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES, COUNTER_TAG_USER_PROVIDER, COUNTER_TAG_USER_PROVIDER_IT));
    }

    /**
     * Tests that when the RBAC group is not found the failures counter is
     * increased.
     */
    @Test
    void testGroupUsersFetchingGroupNotFoundFailureCounter() {
        when(this.rbacServiceToService.getGroup(Mockito.anyString(), Mockito.any())).thenAnswer((answer) -> { throw new ClientWebApplicationException(HttpStatus.SC_NOT_FOUND); });

        // Test that the RBAC failures counter gets incremented.
        when(this.recipientsResolverConfig.isFetchUsersWithRbacEnabled()).thenReturn(true);
        when(this.recipientsResolverConfig.isFetchUsersWithMbopEnabled()).thenReturn(false);

        this.micrometerAssertionHelper.saveCounterValueBeforeTestFilteredByTags(COUNTER_REQUESTS, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES, COUNTER_TAG_USER_PROVIDER, COUNTER_TAG_USER_PROVIDER_RBAC));

        // Call the function under test.
        this.fetchUsersFromExternalServices.getGroupUsers("12345", true, UUID.randomUUID());

        this.micrometerAssertionHelper.assertCounterIncrementFilteredByTags(COUNTER_REQUESTS, 1, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES, COUNTER_TAG_USER_PROVIDER, COUNTER_TAG_USER_PROVIDER_RBAC));
    }

    /**
     * Tests that when the RBAC group cannot be fetched due to an {@link IOException}
     * then the counter is increased by {@link RecipientsResolverConfig#getMaxRetryAttempts()}
     * times.
     */
    @Test
    void testGroupUsersFetchingGroupIOExceptionFailureCounter() {
        when(this.rbacServiceToService.getGroup(Mockito.anyString(), Mockito.any())).thenAnswer((answer) -> { throw new IOException(); });

        // Test that the RBAC failures counter gets incremented.
        when(this.recipientsResolverConfig.isFetchUsersWithRbacEnabled()).thenReturn(true);
        when(this.recipientsResolverConfig.isFetchUsersWithMbopEnabled()).thenReturn(false);

        this.micrometerAssertionHelper.saveCounterValueBeforeTestFilteredByTags(COUNTER_REQUESTS, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES, COUNTER_TAG_USER_PROVIDER, COUNTER_TAG_USER_PROVIDER_RBAC));

        // Call the function under test.
        assertThrows(FailsafeException.class,
            () -> this.fetchUsersFromExternalServices.getGroupUsers("12345", true, UUID.randomUUID())
        );

        this.micrometerAssertionHelper.assertCounterIncrementFilteredByTags(COUNTER_REQUESTS, this.recipientsResolverConfig.getMaxRetryAttempts(), Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_FAILURES, COUNTER_TAG_USER_PROVIDER, COUNTER_TAG_USER_PROVIDER_RBAC));
    }

    /**
     * Tests that when fetching users from RBAC succeeds, the success counter
     * is incremented accordingly. It makes sure that it counts the number of
     * requests sent, which means that takes into account the paginated
     * requests.
     */
    @Test
    void testIncrementSuccessfulUserFetchesRBAC() {
        // Mock a call to RBAC.
        when(this.recipientsResolverConfig.isFetchUsersWithRbacEnabled()).thenReturn(true);
        when(this.recipientsResolverConfig.isFetchUsersWithMbopEnabled()).thenReturn(false);

        final int mockedRbacPagesToFetch = 2;
        this.mockGetUsersRBAC(this.recipientsResolverConfig.getMaxResultsPerPage() * mockedRbacPagesToFetch, false);

        this.micrometerAssertionHelper.saveCounterValueBeforeTestFilteredByTags(COUNTER_REQUESTS, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_SUCCESSES, COUNTER_TAG_USER_PROVIDER, COUNTER_TAG_USER_PROVIDER_RBAC));

        // Call the function under test.
        this.fetchUsersFromExternalServices.getUsers(DEFAULT_ORG_ID, false);

        // Assert that the success counter with the "rbac" tag got incremented.
        // We will simulate fetching 2 full pages, which means that we will
        // trigger one last call to fetch the fourth page that will be empty.
        this.micrometerAssertionHelper.assertCounterIncrementFilteredByTags(COUNTER_REQUESTS, mockedRbacPagesToFetch + 1, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_SUCCESSES, COUNTER_TAG_USER_PROVIDER, COUNTER_TAG_USER_PROVIDER_RBAC));
    }

    /**
     * Tests that when fetching users from MBOP succeeds, the success counter
     * is incremented accordingly. It makes sure that it counts the number of
     * requests sent, which means that takes into account the paginated
     * requests.
     */
    @Test
    void testIncrementSuccessfulUserFetchesMBOP() {
        // Mock a call to MBOP.
        when(this.recipientsResolverConfig.isFetchUsersWithRbacEnabled()).thenReturn(false);
        when(this.recipientsResolverConfig.isFetchUsersWithMbopEnabled()).thenReturn(true);

        final int mockedMbopPagesToFetch = 3;
        final int mockedMbopElements = this.recipientsResolverConfig.getMaxResultsPerPage();
        Mockito
            .when(this.mbopService.getUsersByOrgId(anyString(), anyString(), anyString(), anyString(), anyBoolean(), anyInt(), anyInt(), anyBoolean(), anyString(), anyBoolean()))
            .thenReturn(this.mockGetMBOPUsersPage(mockedMbopElements), this.mockGetMBOPUsersPage(mockedMbopElements), this.mockGetMBOPUsersPage(5));

        this.micrometerAssertionHelper.saveCounterValueBeforeTestFilteredByTags(COUNTER_REQUESTS, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_SUCCESSES, COUNTER_TAG_USER_PROVIDER, COUNTER_TAG_USER_PROVIDER_MBOP));

        // Call the function under test.
        this.fetchUsersFromExternalServices.getUsers(DEFAULT_ORG_ID, false);

        // Assert that the success counter with the "mbop" tag got incremented.
        this.micrometerAssertionHelper.assertCounterIncrementFilteredByTags(COUNTER_REQUESTS, mockedMbopPagesToFetch, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_SUCCESSES, COUNTER_TAG_USER_PROVIDER, COUNTER_TAG_USER_PROVIDER_MBOP));
    }

    /**
     * Tests that when fetching users from IT succeeds, the success counter is
     * incremented accordingly. It makes sure that it counts the number of
     * requests sent, which means that takes into account the paginated
     * requests.
     */
    @Test
    void testIncrementSuccessfulUserFetchesIT() {
        // Mock an IT call.
        when(this.recipientsResolverConfig.isFetchUsersWithRbacEnabled()).thenReturn(false);
        when(this.recipientsResolverConfig.isFetchUsersWithMbopEnabled()).thenReturn(false);

        final int mockedITUserPagesToFetch = 3;
        this.mockGetUsers(this.recipientsResolverConfig.getMaxResultsPerPage() * mockedITUserPagesToFetch, false);

        // Clear the cached results so that we don't fetch the cached results.
        // Otherwise, we wouldn't simulate a call to the user provider.
        this.micrometerAssertionHelper.saveCounterValueBeforeTestFilteredByTags(COUNTER_REQUESTS, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_SUCCESSES, COUNTER_TAG_USER_PROVIDER, COUNTER_TAG_USER_PROVIDER_IT));
        this.clearCached();

        // Call the function under test.
        this.fetchUsersFromExternalServices.getUsers(DEFAULT_ORG_ID, false);

        // Assert that the success counter with the "it" tag got incremented.
        // We will simulate fetching 3 full pages, which means that we will
        // trigger one last call to fetch the fourth page that will be empty.
        this.micrometerAssertionHelper.assertCounterIncrementFilteredByTags(COUNTER_REQUESTS, mockedITUserPagesToFetch + 1, Tags.of(COUNTER_TAG_REQUEST_RESULT, COUNTER_TAG_SUCCESSES, COUNTER_TAG_USER_PROVIDER, COUNTER_TAG_USER_PROVIDER_IT));
    }

    private void mockGetUsers(int elements, boolean adminsOnly) {
        MockedUserAnswer answer = new MockedUserAnswer(elements, adminsOnly);
        when(itUserService.getUsers(any(ITUserRequest.class)))
                .then(invocationOnMock -> answer.mockedUserAnswer(invocationOnMock.getArgument(0, ITUserRequest.class)));
    }

    private void mockGetUsersRBAC(int elements, boolean adminsOnly) {
        MockedUserAnswer answer = new MockedUserAnswer(elements, adminsOnly);
        Mockito.when(rbacServiceToService.getUsers(
                Mockito.eq(DEFAULT_ORG_ID),
                Mockito.eq(adminsOnly),
                Mockito.anyBoolean(),
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
                Mockito.eq(DEFAULT_ORG_ID),
                Mockito.eq(group.getUuid()),
                Mockito.anyBoolean()
        )).thenReturn(group);
    }

    private void mockNotFoundGroup(UUID groupId) {
        when(rbacServiceToService.getGroup(
                Mockito.eq(DEFAULT_ORG_ID),
                Mockito.eq(groupId),
                Mockito.anyBoolean()
        )).thenThrow(new ClientWebApplicationException(404));
    }

    private void mockGetGroupUsers(int elements, UUID groupId) {
        when(rbacServiceToService.getGroupUsers(
                Mockito.eq(DEFAULT_ORG_ID),
                Mockito.eq(groupId),
                Mockito.anyBoolean(),
                Mockito.anyInt(),
                Mockito.anyInt()
        )).then(invocationOnMock -> {
            OldMockedUserAnswer answer = new OldMockedUserAnswer(elements, false);
            return answer.mockedUserAnswer(false);
        });
    }

    /**
     * Generate mock {@link MBOPUser}s.
     * @param elements the number of users to generate.
     * @return a list containing the specified number of elements filled with
     * random data.
     */
    private MBOPUsers mockGetMBOPUsersPage(final int elements) {
        final List<MBOPUser> mbopUsers = new ArrayList<>(elements);

        for (int i = 0; i < elements; i++) {
            final String uuid = UUID.randomUUID().toString();

            final MBOPUser mbopUser = new MBOPUser(
                uuid,
                String.format("username-%s", uuid),
                String.format("%s@redhat.com", uuid)
            );

            mbopUsers.add(mbopUser);
        }
        return new MBOPUsers(mbopUsers, Long.valueOf(mbopUsers.size()));
    }

    /*
     * This would normally happen after a certain duration fixed in application.properties with the
     * quarkus.cache.caffeine.recipient-users-provider-get-group-users.expire-after-write
     * and
     * quarkus.cache.caffeine.recipient-users-provider-get-users.expire-after-write key.
     */
    @CacheInvalidateAll(cacheName = "recipients-users-provider-get-users")
    @CacheInvalidateAll(cacheName = "recipients-users-provider-get-group-users")
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
            assertEquals(recipientsResolverConfig.getMaxResultsPerPage(), limit);
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

            assertEquals(recipientsResolverConfig.getMaxResultsPerPage(), maxResults);
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
