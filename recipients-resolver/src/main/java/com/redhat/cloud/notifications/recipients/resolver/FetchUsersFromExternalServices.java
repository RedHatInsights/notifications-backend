package com.redhat.cloud.notifications.recipients.resolver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.recipients.config.RecipientsResolverConfig;
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
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.ClientWebApplicationException;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.lang.Boolean.TRUE;

@ApplicationScoped
public class FetchUsersFromExternalServices {

    public static final String MBOP_SORT_ORDER = "asc";
    public static final String ORG_ADMIN_PERMISSION = "admin:org:all";

    @Inject
    @RestClient
    RbacServiceToService rbacServiceToService;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    RecipientsResolverConfig recipientsResolverConfig;

    @Inject
    @RestClient
    MBOPService mbopService;

    @Inject
    @RestClient
    ITUserService itUserService;

    @Inject
    ObjectMapper objectMapper;

    private Counter failuresCounter;

    private RetryPolicy<Object> retryPolicy;

    private Map</* orgId */ String, AtomicInteger> rbacUsers = new ConcurrentHashMap<>();

    @PostConstruct
    public void postConstruct() {
        if (recipientsResolverConfig.isFetchUsersWithRBAC()) {
            failuresCounter = meterRegistry.counter("rbac.failures");
        } else if (recipientsResolverConfig.isFetchUsersWithMbop()) {
            failuresCounter = meterRegistry.counter("mbop.failures");
        } else {
            failuresCounter = meterRegistry.counter("it.failures");
        }
        retryPolicy = RetryPolicy.builder()
                .onRetry(event -> failuresCounter.increment())
                .handle(IOException.class)
                .withBackoff(recipientsResolverConfig.getInitialRetryBackoff(), recipientsResolverConfig.getMaxRetryBackoff())
                .withMaxAttempts(recipientsResolverConfig.getMaxRetryAttempts())
                .onRetriesExceeded(event -> {
                    // All retry attempts failed, let's log a warning about the failure.
                    Log.warn("Users fetching from external service failed", event.getException());
                })
                .build();
    }

    @CacheResult(cacheName = "recipients-users-provider-get-users")
    public List<User> getUsers(String orgId, boolean adminsOnly) {
        Timer.Sample getUsersTotalTimer = Timer.start(meterRegistry);

        List<User> users;
        if (recipientsResolverConfig.isFetchUsersWithRBAC()) {
            users = getWithPagination(
                page -> retryOnError(() -> {
                    LocalDateTime startTime = LocalDateTime.now();
                    Page<RbacUser> rbacUserPage = rbacServiceToService.getUsers(orgId, adminsOnly, page * recipientsResolverConfig.getMaxResultsPerPage(), recipientsResolverConfig.getMaxResultsPerPage());
                    Duration duration = Duration.between(startTime, LocalDateTime.now());
                    if (recipientsResolverConfig.getLogTooLongRequestLimit().compareTo(duration) < 0) {
                        Log.warnf("Rbac service response time was %ds for request OrgId: %s, adminOnly: %s, page %d ", duration.toSeconds(), orgId, adminsOnly, page);
                    }
                    return rbacUserPage;
                }));
        } else if (recipientsResolverConfig.isFetchUsersWithMbop()) {
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
            ITUserRequest itRequest = new ITUserRequest(orgId, adminsOnly, firstResult, recipientsResolverConfig.getMaxResultsPerPage());
            final LocalDateTime startTime = LocalDateTime.now();
            usersPaging = retryOnError(() -> itUserService.getUsers(itRequest));
            Duration duration = Duration.between(startTime, LocalDateTime.now());
            if (recipientsResolverConfig.getLogTooLongRequestLimit().compareTo(duration) < 0) {
                try {
                    Log.warnf("It service response time was %ds for request %s", duration.toSeconds(), objectMapper.writeValueAsString(itRequest));
                } catch (JsonProcessingException e) {
                    Log.error("unable to convert itRequest into Json string");
                }
            }
            usersTotal.addAll(usersPaging);

            firstResult += recipientsResolverConfig.getMaxResultsPerPage();
        } while (usersPaging.size() == recipientsResolverConfig.getMaxResultsPerPage());

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
            final List<MBOPUser> receivedUsers = this.retryOnError(() -> {
                    LocalDateTime startTime = LocalDateTime.now();
                    List<MBOPUser> mbopUserlist = mbopService.getUsersByOrgId(
                            recipientsResolverConfig.getMbopApiToken(),
                            recipientsResolverConfig.getMbopClientId(),
                            recipientsResolverConfig.getMbopEnv(),
                            orgId,
                            adminsOnly,
                            MBOP_SORT_ORDER,
                            recipientsResolverConfig.getMaxResultsPerPage(),
                            finalOffset
                        );
                    Duration duration = Duration.between(startTime, LocalDateTime.now());
                    if (recipientsResolverConfig.getLogTooLongRequestLimit().compareTo(duration) < 0) {
                        Log.warnf("MBOP service response time was %ds for request OrgId: %s, adminOnly: %s, offset %d ", duration.toSeconds(), orgId, adminsOnly, finalOffset);
                    }
                    return mbopUserlist;
                }
            );

            mbopUsers.addAll(receivedUsers);

            offset += receivedUsers.size();
            pageSize = receivedUsers.size();
        } while (pageSize == recipientsResolverConfig.getMaxResultsPerPage());

        users = this.transformMBOPUserToUser(mbopUsers);
        return users;
    }

    List<User> transformItUserToUser(List<ITUserResponse> itUserResponses) {
        List<User> users = new ArrayList<>();
        for (ITUserResponse itUserResponse : itUserResponses) {
            User user = new User();
            user.setId(itUserResponse.id);
            user.setUsername(itUserResponse.authentications.get(0).principal);

            for (Email email : itUserResponse.accountRelationships.get(0).emails) {
                if (email != null && email.isPrimary != null && email.isPrimary) {
                    user.setEmail(email.address);
                }
            }

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

    @CacheResult(cacheName = "recipients-users-provider-get-group-users")
    public List<User> getGroupUsers(String orgId, boolean adminOnly, UUID groupId) {
        Timer.Sample getGroupUsersTotalTimer = Timer.start(meterRegistry);
        RbacGroup rbacGroup;
        try {
            rbacGroup = retryOnError(() -> rbacServiceToService.getGroup(orgId, groupId));
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
                Page<RbacUser> rbacUsers = retryOnError(() ->
                    rbacServiceToService.getGroupUsers(orgId, groupId, page * recipientsResolverConfig.getMaxResultsPerPage(), recipientsResolverConfig.getMaxResultsPerPage()));
                // Micrometer doesn't like when tags are null and throws a NPE.
                String orgIdTag = orgId == null ? "" : orgId;
                getGroupUsersPageTimer.stop(meterRegistry.timer("rbac.get-group-users.page", "orgId", orgIdTag));
                return rbacUsers;
            });

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

    private <T> T retryOnError(final CheckedSupplier<T> usersServiceCall) {
        return Failsafe.with(retryPolicy).get(usersServiceCall);
    }

    private List<User> getWithPagination(Function<Integer, Page<RbacUser>> fetcher) {
        List<User> users = new ArrayList<>();
        int page = 0;
        Page<RbacUser> rbacUsers;
        do {
            rbacUsers = fetcher.apply(page++);
            for (RbacUser rbacUser : rbacUsers.getData()) {
                if (rbacUser.getActive()) {
                    User user = new User();
                    user.setUsername(rbacUser.getUsername());
                    user.setEmail(rbacUser.getEmail());
                    user.setAdmin(TRUE.equals(rbacUser.getOrgAdmin()));
                    users.add(user);
                }
            }
        } while (rbacUsers.getData().size() == recipientsResolverConfig.getMaxResultsPerPage());
        return users;
    }

    List<User> transformMBOPUserToUser(final List<MBOPUser> mbopUsers) {
        final List<User> users = new ArrayList<>(mbopUsers.size());

        for (final MBOPUser mbopUser : mbopUsers) {
            if (mbopUser.isActive()) {
                final User user = new User();

                user.setId(mbopUser.id());
                user.setUsername(mbopUser.username());
                user.setEmail(mbopUser.email());
                user.setAdmin(mbopUser.isOrgAdmin());

                users.add(user);
            }
        }

        return users;
    }
}
