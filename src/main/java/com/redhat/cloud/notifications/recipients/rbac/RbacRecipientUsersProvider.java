package com.redhat.cloud.notifications.recipients.rbac;

import com.redhat.cloud.notifications.models.Page;
import com.redhat.cloud.notifications.recipients.RecipientUsersProvider;
import com.redhat.cloud.notifications.recipients.User;
import io.smallrye.mutiny.Multi;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

@ApplicationScoped
public class RbacRecipientUsersProvider implements RecipientUsersProvider {

    @Inject
    @RestClient
    RbacServiceToService rbacServiceToService;

    @ConfigProperty(name = "recipient_provider.rbac.elements_per_page")
    Integer rbacElementsPerPage;

    @Override
    public Multi<User> getUsers(String accountId, boolean adminOnly) {
        return getWithPagination(
                page -> rbacServiceToService
                        .getUsers(accountId, adminOnly, page * rbacElementsPerPage, rbacElementsPerPage)
                        .subscribeAsCompletionStage()
        );
    }

    @Override
    public Multi<User> getGroupUsers(String accountId, boolean adminOnly, String groupId) {
        return rbacServiceToService.getGroup(accountId, UUID.fromString(groupId))
                .onItem().transformToMulti(rbacGroup -> {
                    if (rbacGroup.platformDefault) {
                        return getUsers(accountId, adminOnly);
                    } else {
                        return getWithPagination(
                            page -> rbacServiceToService
                                    .getGroupUsers(accountId, UUID.fromString(groupId), page * rbacElementsPerPage, rbacElementsPerPage)
                                    .subscribeAsCompletionStage()
                        // getGroupUsers doesn't have an adminOnly param.
                        ).filter(user -> !adminOnly || user.isAdmin());
                    }
                });
    }

    private Multi<User> getWithPagination(Function<Integer, CompletionStage<Page<RbacUser>>> fetcher) {
        return Multi.createBy().repeating()
                .completionStage(
                        AtomicInteger::new,
                        state -> fetcher.apply(state.getAndIncrement())
                )
                .until(page -> page.getData().isEmpty())
                .onItem().transform(Page::getData)
                .onItem().disjoint()
                .onItem().transform(o -> {
                    RbacUser rbacUser = (RbacUser) o;
                    User user = new User();
                    user.setUsername(rbacUser.username);
                    user.setAdmin(rbacUser.isOrgAdmin);
                    user.setActive(rbacUser.isActive);
                    user.setEmail(rbacUser.email);

                    return user;
                });
    }
}
