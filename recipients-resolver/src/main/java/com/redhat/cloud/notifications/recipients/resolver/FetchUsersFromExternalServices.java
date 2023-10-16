package com.redhat.cloud.notifications.recipients.resolver;

import com.redhat.cloud.notifications.recipients.config.RecipientResolverConfig;
import com.redhat.cloud.notifications.recipients.model.User;
import com.redhat.cloud.notifications.recipients.resolver.itservice.ITUserService;
import com.redhat.cloud.notifications.recipients.resolver.itservice.pojo.request.ITUserRequest;
import com.redhat.cloud.notifications.recipients.resolver.itservice.pojo.response.AccountRelationship;
import com.redhat.cloud.notifications.recipients.resolver.itservice.pojo.response.Email;
import com.redhat.cloud.notifications.recipients.resolver.itservice.pojo.response.ITUserResponse;
import com.redhat.cloud.notifications.recipients.resolver.itservice.pojo.response.Permission;
import com.redhat.cloud.notifications.recipients.resolver.mbop.MBOPService;
import com.redhat.cloud.notifications.recipients.resolver.mbop.MBOPUser;
import com.redhat.cloud.notifications.recipients.resolver.rbac.Page;
import com.redhat.cloud.notifications.recipients.resolver.rbac.RbacGroup;
import com.redhat.cloud.notifications.recipients.resolver.rbac.RbacServiceToService;
import com.redhat.cloud.notifications.recipients.resolver.rbac.RbacUser;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import dev.failsafe.function.CheckedSupplier;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.netty.channel.ConnectTimeoutException;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.ClientWebApplicationException;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class FetchUsersFromExternalServices {

    public static final String MBOP_SORT_ORDER = "asc";
    public static final String ORG_ADMIN_PERMISSION = "admin:org:all";

    @Inject
    @RestClient
    RbacServiceToService rbacServiceToService;

    @ConfigProperty(name = "recipient-provider.rbac.elements-per-page", defaultValue = "1000")
    Integer rbacElementsPerPage;

    @ConfigProperty(name = "recipient-provider.it.max-results-per-page", defaultValue = "1000")
    int maxResultsPerPage;

    @ConfigProperty(name = "recipient-provider.mbop.max-results-per-page", defaultValue = "1000")
    int MBOPMaxResultsPerPage;

    @ConfigProperty(name = "rbac.retry.max-attempts", defaultValue = "3")
    int maxRetryAttempts;

    @ConfigProperty(name = "it.retry.max-attempts", defaultValue = "3")
    int maxRetryAttemptsIt;

    @ConfigProperty(name = "mbop.retry.max-attempts", defaultValue = "3")
    int MBOPMaxRetryAttempts;

    @ConfigProperty(name = "it.retry.back-off.initial-value", defaultValue = "0.1S")
    Duration initialBackOffIt;

    @ConfigProperty(name = "it.retry.back-off.max-value", defaultValue = "1S")
    Duration maxBackOffIt;

    @ConfigProperty(name = "rbac.retry.back-off.initial-value", defaultValue = "0.1S")
    Duration initialBackOff;

    @ConfigProperty(name = "rbac.retry.back-off.max-value", defaultValue = "1S")
    Duration maxBackOff;

    @ConfigProperty(name = "mbop.retry.back-off.initial-value", defaultValue = "0.1S")
    Duration MBOPInitialBackOff;

    @ConfigProperty(name = "mbop.retry.back-off.max-value", defaultValue = "1S")
    Duration MBOPMaxBackOff;

    @ConfigProperty(name = "mbop.apitoken", defaultValue = "na")
    String bopApiToken;

    @ConfigProperty(name = "mbop.client_id", defaultValue = "na")
    String bopClientId;

    @ConfigProperty(name = "mbop.env", defaultValue = "na")
    String bopEnv;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    RecipientResolverConfig connectorConfig;

    @Inject
    @RestClient
    MBOPService mbopService;

    @Inject
    @RestClient
    ITUserService itUserService;

    private Counter rbacFailuresCounter;

    private RetryPolicy<Object> rbacRetryPolicy;

    private Counter itFailuresCounter;
    private RetryPolicy<Object> itRetryPolicy;

    private Counter mbopFailuresCounter;
    private RetryPolicy<Object> mbopRetryPolicy;

    private Map</* orgId */ String, AtomicInteger> rbacUsers = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        rbacFailuresCounter = meterRegistry.counter("rbac.failures");

        rbacRetryPolicy = RetryPolicy.builder()
            .onRetry(event -> rbacFailuresCounter.increment())
            .handle(IOException.class, ConnectTimeoutException.class)
            .withBackoff(initialBackOff, maxBackOff)
            .withMaxAttempts(maxRetryAttempts)
            .onRetriesExceeded(event -> {
                // All retry attempts failed, let's log a warning about the failure.
                Log.warnf("RBAC S2S call failed", event.getException().getMessage());
            })
            .build();

        itFailuresCounter = meterRegistry.counter("it.failures");

        itRetryPolicy = RetryPolicy.builder()
            .onRetry(event -> itFailuresCounter.increment())
            .handle(IOException.class, ConnectTimeoutException.class)
            .withBackoff(initialBackOffIt, maxBackOffIt)
            .withMaxAttempts(maxRetryAttemptsIt)
            .onRetriesExceeded(event -> {
                // All retry attempts failed, let's log a warning about the failure.
                Log.warnf("IT User Service call failed", event.getException().getMessage());
            })
            .build();

        this.mbopFailuresCounter = this.meterRegistry.counter("mbop.failures");

        this.mbopRetryPolicy = RetryPolicy.builder()
            .onRetry(ignoredEvent -> mbopFailuresCounter.increment())
            .handle(ConnectTimeoutException.class, IOException.class)
            .withBackoff(this.MBOPInitialBackOff, this.MBOPMaxBackOff)
            .withMaxAttempts(this.MBOPMaxRetryAttempts)
            .onRetriesExceeded(
                event -> {
                    // All retry attempts failed, let's log a warning about the failure.
                    Log.warnf("MBOP User Service call failed", event.getException().getMessage());
                }
            )
            .build();
    }

    @CacheResult(cacheName = "recipient-users-provider-get-users")
    public List<User> getUsers(String orgId, boolean adminsOnly) {
        Timer.Sample getUsersTotalTimer = Timer.start(meterRegistry);

        List<User> users;
        if (connectorConfig.isFetchUsersWithRBAC()) {
            users = getWithPagination(
                page -> retryOnRbacError(() -> rbacServiceToService.getUsers(orgId, adminsOnly, page * rbacElementsPerPage, rbacElementsPerPage)));
        } else if (connectorConfig.isFetchUsersWithMbop()) {
            users = fetchUsersWithMbop(orgId, adminsOnly);
        } else {
            users = fetchUsersWithItUserService(orgId, adminsOnly);
        }

        // Micrometer doesn't like when tags are null and throws a NPE.
        String orgIdTag = orgId == null ? "" : orgId;
        getUsersTotalTimer.stop(meterRegistry.timer("user-provider.get-users.total", "orgId", orgIdTag));
        getRbacUsersGauge(orgIdTag).set(users.size());

        return users;
    }

    private List<User> fetchUsersWithItUserService(String orgId, boolean adminsOnly) {
        List<User> users;
        List<ITUserResponse> usersPaging;
        List<ITUserResponse> usersTotal = new ArrayList<>();

        int firstResult = 0;

        do {
            ITUserRequest itRequest = new ITUserRequest(orgId, adminsOnly, firstResult, maxResultsPerPage);
            usersPaging = retryOnItError(() -> itUserService.getUsers(itRequest));
            usersTotal.addAll(usersPaging);

            firstResult += maxResultsPerPage;
        } while (usersPaging.size() == maxResultsPerPage);

        users = transformItUserToUser(usersTotal);
        return users;
    }

    private List<User> fetchUsersWithMbop(String orgId, boolean adminsOnly) {
        List<User> users;
        final List<MBOPUser> mbopUsers = new ArrayList<>();

        // Keep the offset to ask for more users in case we need it.
        int offset = 0;
        // The page size of the returned call. Used to see if we should
        // keep calling for more users!
        int pageSize;
        do {
            // Required variable since lambdas are not allowed to modify
            // the captured variables.
            int finalOffset = offset;
            final List<MBOPUser> receivedUsers = this.retryOnMBOPError(() ->
                mbopService.getUsersByOrgId(
                    this.bopApiToken,
                    this.bopClientId,
                    this.bopEnv,
                    orgId,
                    adminsOnly,
                    MBOP_SORT_ORDER,
                    this.MBOPMaxResultsPerPage,
                    finalOffset
                )
            );

            mbopUsers.addAll(receivedUsers);

            offset += receivedUsers.size();
            pageSize = receivedUsers.size();
        } while (pageSize == this.MBOPMaxResultsPerPage);

        users = this.transformMBOPUserToUser(mbopUsers);
        return users;
    }

    List<User> transformItUserToUser(List<ITUserResponse> itUserResponses) {
        List<User> users = new ArrayList<>();
        for (ITUserResponse itUserResponse : itUserResponses) {
            User user = new User();
            user.setId(itUserResponse.id);
            user.setUsername(itUserResponse.authentications.get(0).principal);

            final List<Email> emails = itUserResponse.accountRelationships.get(0).emails;
            for (Email email : emails) {
                if (email != null && email.isPrimary != null && email.isPrimary) {
                    String address = email.address;
                    user.setEmail(address);
                }
            }

            user.setAdmin(false);
            if (itUserResponse.accountRelationships != null) {
                for (AccountRelationship accountRelationship : itUserResponse.accountRelationships) {
                    if (accountRelationship.permissions != null) {
                        for (Permission permission : accountRelationship.permissions) {
                            if (ORG_ADMIN_PERMISSION.equals(permission.permissionCode)) {
                                user.setAdmin(true);
                            }
                        }
                    }
                }
            }

            user.setActive(true);

            user.setFirstName(itUserResponse.personalInformation.firstName);
            user.setLastName(itUserResponse.personalInformation.lastNames);

            users.add(user);
        }
        return users;
    }


    private AtomicInteger getRbacUsersGauge(String orgIdTag) {
        return rbacUsers.computeIfAbsent(orgIdTag, orgId -> {
            Set<Tag> tags = Set.of(Tag.of("orgId", orgId));
            return meterRegistry.gauge("user-provider.users", tags, new AtomicInteger());
        });
    }

    @CacheResult(cacheName = "recipient-users-provider-get-group-users")
    public List<User> getGroupUsers(String orgId, boolean adminOnly, UUID groupId) {
        Timer.Sample getGroupUsersTotalTimer = Timer.start(meterRegistry);
        RbacGroup rbacGroup;
        try {
            rbacGroup = retryOnRbacError(() -> rbacServiceToService.getGroup(orgId, groupId));
        } catch (ClientWebApplicationException exception) {
            // The group does not exist (or no longer exists - ignore)
            if (exception.getResponse().getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                return List.of();
            }

            throw exception;
        }

        List<User> users;
        if (rbacGroup.isPlatformDefault()) {
            users = getUsers(orgId, adminOnly);
        } else {
            users = getWithPagination(page -> {
                Timer.Sample getGroupUsersPageTimer = Timer.start(meterRegistry);
                Page<RbacUser> rbacUsers = retryOnRbacError(() ->
                    rbacServiceToService.getGroupUsers(orgId, groupId, page * rbacElementsPerPage, rbacElementsPerPage));
                // Micrometer doesn't like when tags are null and throws a NPE.
                String orgIdTag = orgId == null ? "" : orgId;
                getGroupUsersPageTimer.stop(meterRegistry.timer("rbac.get-group-users.page", "orgId", orgIdTag));
                return rbacUsers;
            });

            // Only include active users
            users = users.stream().filter(User::isActive).collect(Collectors.toList());

            // getGroupUsers doesn't have an adminOnly param.
            if (adminOnly) {
                users = users.stream().filter(User::isAdmin).collect(Collectors.toList());
            }
        }
        // Micrometer doesn't like when tags are null and throws a NPE.
        String orgIdTag = orgId == null ? "" : orgId;
        getGroupUsersTotalTimer.stop(meterRegistry.timer("rbac.get-group-users.total", "orgId", orgIdTag, "users", String.valueOf(users.size())));
        return users;
    }

    private <T> T retryOnRbacError(CheckedSupplier<T> rbacCall) {
        return Failsafe.with(rbacRetryPolicy).get(rbacCall);
    }

    private <T> T retryOnItError(CheckedSupplier<T> rbacCall) {
        return Failsafe.with(itRetryPolicy).get(rbacCall);
    }

    private <T> T retryOnMBOPError(final CheckedSupplier<T> mbopCall) {
        return Failsafe.with(this.mbopRetryPolicy).get(mbopCall);
    }

    private List<User> getWithPagination(Function<Integer, Page<RbacUser>> fetcher) {
        List<User> users = new ArrayList<>();
        int page = 0;
        Page<RbacUser> rbacUsers;
        do {
            rbacUsers = fetcher.apply(page++);
            for (RbacUser rbacUser : rbacUsers.getData()) {
                User user = new User();
                user.setUsername(rbacUser.getUsername());
                user.setEmail(rbacUser.getEmail());
                user.setAdmin(rbacUser.getOrgAdmin());
                user.setActive(rbacUser.getActive());
                user.setFirstName(rbacUser.getFirstName());
                user.setLastName(rbacUser.getLastName());
                users.add(user);
            }
        } while (rbacUsers.getData().size() == rbacElementsPerPage);
        return users;
    }

    List<User> transformMBOPUserToUser(final List<MBOPUser> mbopUsers) {
        final List<User> users = new ArrayList<>(mbopUsers.size());

        for (final MBOPUser mbopUser : mbopUsers) {
            final User user = new User();

            user.setId(mbopUser.id());
            user.setUsername(mbopUser.username());
            user.setEmail(mbopUser.email());
            user.setFirstName(mbopUser.firstName());
            user.setLastName(mbopUser.lastName());
            user.setActive(mbopUser.isActive());
            user.setAdmin(mbopUser.isOrgAdmin());

            users.add(user);
        }

        return users;
    }
}
