package com.redhat.cloud.notifications.recipients.rbac;

import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.itservice.ITUserService;
import com.redhat.cloud.notifications.recipients.itservice.pojo.request.ITUserRequest;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.Email;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.ITUserResponse;
import com.redhat.cloud.notifications.routers.models.Page;
import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import dev.failsafe.function.CheckedSupplier;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.netty.channel.ConnectTimeoutException;
import io.quarkus.cache.CacheResult;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class RbacRecipientUsersProvider {

    private static final Logger LOGGER = Logger.getLogger(RbacRecipientUsersProvider.class);

    public static final String ORG_ADMIN_PERMISSION = "admin:org:all";

    @Inject
    @RestClient
    RbacServiceToService rbacServiceToService;

    @Inject
    @RestClient
    ITUserService itUserService;

    @ConfigProperty(name = "recipient-provider.rbac.elements-per-page", defaultValue = "1000")
    Integer rbacElementsPerPage;

    @ConfigProperty(name = "recipient-provider.it.max-results-per-page", defaultValue = "1000")
    int maxResultsPerPage;

    @ConfigProperty(name = "rbac.retry.max-attempts", defaultValue = "3")
    int maxRetryAttempts;

    @ConfigProperty(name = "it.retry.max-attempts", defaultValue = "3")
    int maxRetryAttemptsIt;

    @ConfigProperty(name = "it.retry.back-off.initial-value", defaultValue = "0.1S")
    Duration initialBackOffIt;

    @ConfigProperty(name = "it.retry.back-off.max-value", defaultValue = "1S")
    Duration maxBackOffIt;

    @ConfigProperty(name = "rbac.retry.back-off.initial-value", defaultValue = "0.1S")
    Duration initialBackOff;

    @ConfigProperty(name = "rbac.retry.back-off.max-value", defaultValue = "1S")
    Duration maxBackOff;

    @ConfigProperty(name = "recipient-provider.use-it-impl", defaultValue = "false")
    public boolean retrieveUsersFromIt;

    @Inject
    MeterRegistry meterRegistry;

    private Counter rbacFailuresCounter;
    private RetryPolicy<Object> rbacRetryPolicy;

    private Counter itFailuresCounter;
    private RetryPolicy<Object> itRetryPolicy;

    @PostConstruct
    public void initCounters() {
        rbacFailuresCounter = meterRegistry.counter("rbac.failures");
        rbacRetryPolicy = RetryPolicy.builder()
                .onRetry(event -> rbacFailuresCounter.increment())
                .handle(IOException.class, ConnectTimeoutException.class)
                .withBackoff(initialBackOff, maxBackOff)
                .withMaxAttempts(maxRetryAttempts)
                .onRetriesExceeded(event -> {
                    // All retry attempts failed, let's log a warning about the failure.
                    LOGGER.warnf("RBAC S2S call failed", event.getException().getMessage());
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
                    LOGGER.warnf("IT User Service call failed", event.getException().getMessage());
                })
                .build();
    }

    @CacheResult(cacheName = "rbac-recipient-users-provider-get-users")
    public List<User> getUsers(String accountId, boolean adminsOnly) {
        Timer.Sample getUsersTotalTimer = Timer.start(meterRegistry);

        List<User> users;
        if (retrieveUsersFromIt) {
            List<ITUserResponse> usersPaging;
            List<ITUserResponse> usersTotal = new ArrayList<>();

            int firstResult = 0;

            do {
                ITUserRequest request = new ITUserRequest(accountId, adminsOnly, firstResult, maxResultsPerPage);
                usersPaging = retryOnItError(() -> itUserService.getUsers(request));
                usersTotal.addAll(usersPaging);

                firstResult += maxResultsPerPage;
            } while (usersPaging.size() == maxResultsPerPage);

            users = transformToUser(usersTotal);
        } else {
            users = getWithPagination(
                page -> {
                    Timer.Sample getUsersPageTimer = Timer.start(meterRegistry);
                    Page<RbacUser> rbacUsers = retryOnRbacError(() ->
                            rbacServiceToService.getUsers(accountId, adminsOnly, page * rbacElementsPerPage, rbacElementsPerPage));
                    getUsersPageTimer.stop(meterRegistry.timer("rbac.get-users.page", "accountId", accountId));
                    return rbacUsers;
                }
            );
        }
        getUsersTotalTimer.stop(meterRegistry.timer("rbac.get-users.total", "accountId", accountId, "users", String.valueOf(users.size())));
        return users;
    }

    @CacheResult(cacheName = "rbac-recipient-users-provider-get-group-users")
    public List<User> getGroupUsers(String accountId, boolean adminOnly, UUID groupId) {
        Timer.Sample getGroupUsersTotalTimer = Timer.start(meterRegistry);
        RbacGroup rbacGroup = retryOnRbacError(() -> rbacServiceToService.getGroup(accountId, groupId));
        List<User> users;
        if (rbacGroup.isPlatformDefault()) {
            users = getUsers(accountId, adminOnly);
        } else {
            users = getWithPagination(page -> {
                Timer.Sample getGroupUsersPageTimer = Timer.start(meterRegistry);
                Page<RbacUser> rbacUsers = retryOnRbacError(() ->
                        rbacServiceToService.getGroupUsers(accountId, groupId, page * rbacElementsPerPage, rbacElementsPerPage));
                getGroupUsersPageTimer.stop(meterRegistry.timer("rbac.get-group-users.page", "accountId", accountId));
                return rbacUsers;
            });
            // getGroupUsers doesn't have an adminOnly param.
            if (adminOnly) {
                users = users.stream().filter(User::isAdmin).collect(Collectors.toList());
            }
        }
        getGroupUsersTotalTimer.stop(meterRegistry.timer("rbac.get-group-users.total", "accountId", accountId, "users", String.valueOf(users.size())));
        return users;
    }

    private <T> T retryOnRbacError(CheckedSupplier<T> rbacCall) {
        return Failsafe.with(rbacRetryPolicy).get(rbacCall);
    }

    private <T> T retryOnItError(CheckedSupplier<T> rbacCall) {
        return Failsafe.with(itRetryPolicy).get(rbacCall);
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

    List<User> transformToUser(List<ITUserResponse> itUserResponses) {
        List<User> users = new ArrayList<>();
        for (ITUserResponse itUserResponse : itUserResponses) {
            User user = new User();
            user.setUsername(itUserResponse.authentications.get(0).principal);

            final List<Email> emails = itUserResponse.accountRelationships.get(0).emails;
            for (Email email : emails) {
                if (email != null && email.isPrimary != null && email.isPrimary) {
                    String address = email.address;
                    user.setEmail(address);
                }
            }

            user.setAdmin(itUserResponse.accountRelationships.stream().filter(Objects::nonNull).anyMatch(relationship -> relationship.permissions.stream().filter(Objects::nonNull).anyMatch(permission -> ORG_ADMIN_PERMISSION.equals(permission.permissionCode))));
            user.setActive(true);

            user.setFirstName(itUserResponse.personalInformation.firstName);
            user.setLastName(itUserResponse.personalInformation.lastNames);

            users.add(user);
        }
        return users;
    }
}
