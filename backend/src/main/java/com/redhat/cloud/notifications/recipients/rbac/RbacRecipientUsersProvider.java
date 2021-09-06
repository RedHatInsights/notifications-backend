package com.redhat.cloud.notifications.recipients.rbac;

import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.routers.models.Page;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.netty.channel.ConnectTimeoutException;
import io.quarkus.cache.CacheResult;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class RbacRecipientUsersProvider {

    @Inject
    @RestClient
    RbacServiceToService rbacServiceToService;

    @ConfigProperty(name = "recipient-provider.rbac.elements-per-page", defaultValue = "40")
    Integer rbacElementsPerPage;

    @ConfigProperty(name = "rbac.retry.max-attempts", defaultValue = "3")
    long maxRetryAttempts;

    @ConfigProperty(name = "rbac.retry.back-off.initial-value", defaultValue = "0.1S")
    Duration initialBackOff;

    @ConfigProperty(name = "rbac.retry.back-off.max-value", defaultValue = "1S")
    Duration maxBackOff;

    @Inject
    MeterRegistry meterRegistry;

    private Counter getUsersCalls;
    private Timer getUsersProcessTime;
    private Counter getGroupUsersCalls;
    private Timer getGroupUsersProcessTime;

    @PostConstruct
    public void initCounters() {
        getUsersCalls = meterRegistry.counter("rbac-recipient-users-provider.get-users.calls");
        getUsersProcessTime = meterRegistry.timer("rbac-recipient-users-provider.get-users.process-time");
        getGroupUsersCalls = meterRegistry.counter("rbac-recipient-users-provider.get-group-users.calls");
        getGroupUsersProcessTime = meterRegistry.timer("rbac-recipient-users-provider.get-group-users.process-time");
    }

    @CacheResult(cacheName = "rbac-recipient-users-provider-get-users")
    public Uni<List<User>> getUsers(String accountId, boolean adminsOnly) {
        getUsersCalls.increment();
        LocalDateTime start = LocalDateTime.now();
        return getWithPagination(
            page -> retryOnError(
                    rbacServiceToService
                            .getUsers(accountId, adminsOnly, page * rbacElementsPerPage, rbacElementsPerPage)
            )
        )
        .onItem().invoke(() -> getUsersProcessTime.record(Duration.between(start, LocalDateTime.now())))
        // .memoize().indefinitely() should be removed after the Quarkus 2.0 bump
        .memoize().indefinitely();
    }

    @CacheResult(cacheName = "rbac-recipient-users-provider-get-group-users")
    public Uni<List<User>> getGroupUsers(String accountId, boolean adminOnly, UUID groupId) {
        getGroupUsersCalls.increment();
        LocalDateTime start = LocalDateTime.now();
        return retryOnError(rbacServiceToService.getGroup(accountId, groupId))
                .onItem().transformToUni(rbacGroup -> {
                    if (rbacGroup.isPlatformDefault()) {
                        return getUsers(accountId, adminOnly);
                    } else {
                        return getWithPagination(
                            page -> retryOnError(
                                    rbacServiceToService
                                            .getGroupUsers(accountId, groupId, page * rbacElementsPerPage, rbacElementsPerPage)
                            )
                        )
                        // getGroupUsers doesn't have an adminOnly param.
                        .onItem().transform(users -> {
                            if (adminOnly) {
                                return users.stream().filter(User::isAdmin).collect(Collectors.toList());
                            }

                            return users;
                        });
                    }
                })
                .onItem().invoke(() -> getGroupUsersProcessTime.record(Duration.between(start, LocalDateTime.now())))
                // .memoize().indefinitely() should be removed after the Quarkus 2.0 bump
                .memoize().indefinitely();
    }

    private <T> Uni<T> retryOnError(Uni<T> uni) {
        return uni
                .onFailure(failure -> failure.getClass() == IOException.class || failure.getClass() == ConnectTimeoutException.class)
                .retry()
                .withBackOff(initialBackOff, maxBackOff)
                .atMost(maxRetryAttempts);
    }

    private Uni<List<User>> getWithPagination(Function<Integer, Uni<Page<RbacUser>>> fetcher) {
        return Multi.createBy().repeating()
                .uni(
                    AtomicInteger::new,
                    state -> fetcher.apply(state.getAndIncrement())
                )
                .until(page -> page.getData().isEmpty())
                .onItem().transform(page -> page.getData().stream().map(rbacUser -> {
                    User user = new User();
                    user.setUsername(rbacUser.getUsername());
                    user.setEmail(rbacUser.getEmail());
                    user.setAdmin(rbacUser.getOrgAdmin());
                    user.setActive(rbacUser.getActive());
                    user.setFirstName(rbacUser.getFirstName());
                    user.setLastName(rbacUser.getLastName());
                    return user;
                }).collect(Collectors.toList())).collect().in(ArrayList::new, List::addAll);
    }
}
