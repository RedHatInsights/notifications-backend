package com.redhat.cloud.notifications.recipients;

import com.redhat.cloud.notifications.recipients.rbac.RbacRecipientUsersProvider;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class RecipientResolver {

    @Inject
    RbacRecipientUsersProvider rbacRecipientUsersProvider;

    public Uni<Set<User>> recipientUsers(String accountId, Set<RecipientSettings> requests, Set<String> subscribers) {
        return Multi.createFrom().iterable(requests)
                .onItem().transformToUni(r -> recipientUsers(accountId, r, subscribers))
                .concatenate().collect().in(HashSet::new, Set::addAll);
    }

    private Uni<Set<User>> recipientUsers(String accountId, RecipientSettings request, Set<String> subscribers) {
        Uni<List<User>> usersUni;
        if (request.getGroupId() == null) {
            usersUni = rbacRecipientUsersProvider.getUsers(accountId, request.isOnlyAdmins());
        } else {
            usersUni = rbacRecipientUsersProvider.getGroupUsers(accountId, request.isOnlyAdmins(), request.getGroupId());
        }

        return usersUni
                .onItem().transform(Set::copyOf)
                .onItem().transform(users -> {
                    // This step requires a specific set of users and all others needs to be ignored (for this step)
                    if (request.getUsers().size() > 0) {
                        return filterUsers(users, request.getUsers());
                    }

                    return users;
                }).onItem().transform(users -> {
                    if (request.isIgnoreUserPreferences()) {
                        return Set.copyOf(users);
                    }

                    return filterUsers(users, subscribers);
                });
    }

    private Set<User> filterUsers(Set<User> users, Set<String> target) {
        return users.stream()
                .filter(
                    user -> target
                            .stream()
                            .anyMatch(requested -> requested.equalsIgnoreCase(user.getUsername()))
                )
                .collect(Collectors.toSet());
    }
}
