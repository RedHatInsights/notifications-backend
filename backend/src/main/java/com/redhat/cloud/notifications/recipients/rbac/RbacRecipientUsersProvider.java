package com.redhat.cloud.notifications.recipients.rbac;

import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.recipients.itservice.ITUserServiceWrapper;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.ITUserResponse;
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
import org.jboss.logging.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class RbacRecipientUsersProvider {

    private static final Logger LOGGER = Logger.getLogger(RbacRecipientUsersProvider.class);

    @Inject
    @RestClient
    RbacServiceToService rbacServiceToService;

    @Inject
    ITUserServiceWrapper itUserService;

    @ConfigProperty(name = "recipient-provider.rbac.elements-per-page", defaultValue = "1000")
    Integer rbacElementsPerPage;

    @ConfigProperty(name = "rbac.retry.max-attempts", defaultValue = "3")
    long maxRetryAttempts;

    @ConfigProperty(name = "rbac.retry.back-off.initial-value", defaultValue = "0.1S")
    Duration initialBackOff;

    @ConfigProperty(name = "rbac.retry.back-off.max-value", defaultValue = "1S")
    Duration maxBackOff;

    @Inject
    MeterRegistry meterRegistry;

    private Counter failuresCounter;

    @PostConstruct
    public void initCounters() {
        failuresCounter = meterRegistry.counter("rbac.failures");
    }

    @CacheResult(cacheName = "rbac-recipient-users-provider-get-users")
    public Uni<List<User>> getUsers(String accountId, boolean adminsOnly) {
        return getWithPagination(itUserService.getUserss(accountId, adminsOnly));
    }

    @CacheResult(cacheName = "rbac-recipient-users-provider-get-group-users")
    public Uni<List<User>> getGroupUsers(String accountId, boolean adminOnly, UUID groupId) {
        Timer.Sample getGroupUsersTotalTimer = Timer.start(meterRegistry);
        return retryOnError(rbacServiceToService.getGroup(accountId, groupId))
                .onItem().transformToUni(rbacGroup -> {
                    if (rbacGroup.isPlatformDefault()) {
                        return getUsers(accountId, adminOnly);
                    } else {
                        return getWithPaginationGroup(
                            page -> {
                                Timer.Sample getGroupUsersPageTimer = Timer.start(meterRegistry);
                                return retryOnError(
                                        rbacServiceToService.getGroupUsers(accountId, groupId, page * rbacElementsPerPage, rbacElementsPerPage)
                                )
                                .onItem().invoke(() -> getGroupUsersPageTimer.stop(meterRegistry.timer("rbac.get-group-users.page", "accountId", accountId)));
                            }
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
                .onItem().invoke(users -> getGroupUsersTotalTimer.stop(meterRegistry.timer("rbac.get-group-users.total", "accountId", accountId, "users", String.valueOf(users.size()))));
    }

    private <T> Uni<T> retryOnError(Uni<T> uni) {
        return uni
                .onFailure(failure -> {
                    failuresCounter.increment();
                    return failure.getClass() == IOException.class || failure.getClass() == ConnectTimeoutException.class;
                })
                .retry()
                .withBackOff(initialBackOff, maxBackOff)
                .atMost(maxRetryAttempts)
                // All retry attempts failed, let's log a warning about the failure.
                .onFailure().invoke(failure -> LOGGER.warnf("RBAC S2S call failed: %s", failure.getMessage()));
    }

    private Uni<List<User>> getWithPagination(Uni<List<ITUserResponse>> itUserResponses) {
        return itUserResponses.onItem().transform(page -> page.stream().map(itUserResponse -> {
                    User user = new User();
//                    user.setUsername(itUserResponse.getPersonalInformation().getUsername());
                    user.setEmail(itUserResponse.getAccountRelationships().get(0).getEmails().toString());
//                    user.setAdmin(rbacUser.getOrgAdmin());
//                    user.setActive(rbacUser.getActive());
                    user.setFirstName(itUserResponse.getPersonalInformation().getFirstName());
                    user.setLastName(itUserResponse.getPersonalInformation().getLastNames());
                    return user;
        }).collect(Collectors.toList()));
//        return Multi.createBy().repeating()
//                .whilst(page -> page.getData().size() == rbacElementsPerPage)
//                .onItem().transform(page -> page.getData().stream().map(rbacUser -> {
//                    User user = new User();
//                    user.setUsername(rbacUser.getUsername());
//                    user.setEmail(rbacUser.getEmail());
//                    user.setAdmin(rbacUser.getOrgAdmin());
//                    user.setActive(rbacUser.getActive());
//                    user.setFirstName(rbacUser.getFirstName());
//                    user.setLastName(rbacUser.getLastName());
//                    return user;
//                }).collect(Collectors.toList())).collect().in(ArrayList::new, List::addAll);
    }

    private Uni<List<User>> getWithPaginationGroup(Function<Integer, Uni<Page<RbacUser>>> fetcher) {
        return Multi.createBy().repeating()
                .uni(
                    AtomicInteger::new,
                    state -> fetcher.apply(state.getAndIncrement())
                )
                .whilst(page -> page.getData().size() == rbacElementsPerPage)
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
