package com.redhat.cloud.notifications.recipients.rbac;

import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.routers.models.Page;
import io.quarkus.cache.CacheResult;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
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

    @CacheResult(cacheName = "rbac-recipient-users-provider-get-users")
    public Uni<List<User>> getUsers(String accountId, boolean adminsOnly) {
        return getWithPagination(
                page -> rbacServiceToService
                        .getUsers(accountId, adminsOnly, page * rbacElementsPerPage, rbacElementsPerPage)
        // .memoize().indefinitely() should be removed after the Quarkus 2.0 bump
        ).memoize().indefinitely();
    }

    @CacheResult(cacheName = "rbac-recipient-users-provider-get-group-users")
    public Uni<List<User>> getGroupUsers(String accountId, boolean adminOnly, UUID groupId) {
        return rbacServiceToService.getGroup(accountId, groupId)
                .onItem().transformToUni(rbacGroup -> {
                    if (rbacGroup.isPlatformDefault()) {
                        return getUsers(accountId, adminOnly);
                    } else {
                        return getWithPagination(
                                page -> rbacServiceToService
                                        .getGroupUsers(accountId, groupId, page * rbacElementsPerPage, rbacElementsPerPage)
                        )
                        // getGroupUsers doesn't have an adminOnly param.
                        .onItem().transform(users -> {
                            if (adminOnly) {
                                return users.stream().filter(User::isAdmin).collect(Collectors.toList());
                            }

                            return users;
                        });
                    }
                // .memoize().indefinitely() should be removed after the Quarkus 2.0 bump
                }).memoize().indefinitely();
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
